package eloc.flow.documents

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.PackingListContract
import eloc.state.PackingListState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

object PackingListFlow {

    @InitiatingFlow
    @StartableByRPC
    class UploadAndSend(val packingList: PackingListState) : FlowLogic<SignedTransaction>() {
        companion object {
            object ISSUING_PACKINGLIST : ProgressTracker.Step("Creating and Signing packing list")
            object SENDING_PACKINGLIST : ProgressTracker.Step("Sending packing list to Applicant")
            object VERIFYING_TX : ProgressTracker.Step("Verifying transaction")
            object SIGNING_TX : ProgressTracker.Step("Signing transaction")
            object SENDING_TX : ProgressTracker.Step("Sending to advisory")

            fun tracker() = ProgressTracker(ISSUING_PACKINGLIST, SENDING_PACKINGLIST, VERIFYING_TX, SIGNING_TX, SENDING_TX)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = ISSUING_PACKINGLIST
            // Step 1. Get a reference to the notary service on our network and our key pair.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // Step 2. Create a new TransactionBuilder object.
            val builder = TransactionBuilder(notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // Step 3. Create command
            val issueCommand = Command(PackingListContract.Commands.Create(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))

            // Step 4. Add the packing list as an output state, as well as a command to the transaction builder.
            builder.addOutputState(packingList, PackingListContract.CONTRACT_ID)
            builder.addCommand(issueCommand)

            // Step 5. Verify and sign it with our KeyPair.
            builder.verify(serviceHub)
            val stx = serviceHub.signInitialTransaction(builder)

            // Step 6. Assuming no exceptions, we can now finalise the transaction.
            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatedBy(UploadAndSend::class)
    class ReceivePackingList(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        companion object {
            object RECEIVING : ProgressTracker.Step("Receiving bol")
            object VALIDATING : ProgressTracker.Step("Validating bol")
            object SIGNING : ProgressTracker.Step("Signing bol")
            object SUCCESS : ProgressTracker.Step("bol successfully recorded")

            fun tracker() = ProgressTracker(
                    RECEIVING,
                    VALIDATING,
                    SIGNING,
                    SUCCESS
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(counterpartySession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {

                }
            }

            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }
}