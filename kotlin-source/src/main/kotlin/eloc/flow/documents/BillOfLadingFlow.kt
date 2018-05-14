package eloc.flow.documents

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.BillOfLadingContract
import eloc.state.BillOfLadingState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

object BillOfLadingFlow {

    @InitiatingFlow
    @StartableByRPC
    class UploadAndSend(val billOfLading: BillOfLadingState) : FlowLogic<SignedTransaction>() {
        companion object {
            object ISSUING_INVOICE : ProgressTracker.Step("Creating and Signing Bill of Lading")
            object SENDING_INVOICE : ProgressTracker.Step("Sending Bill of Lading to Advisory bank")
            object VERIFYING_TX : ProgressTracker.Step("Verifying transaction")
            object SIGNING_TX : ProgressTracker.Step("Signing transaction")
            object SENDING_TX : ProgressTracker.Step("Sending transaction")

            fun tracker() = ProgressTracker(ISSUING_INVOICE, SENDING_INVOICE, VERIFYING_TX, SENDING_TX, SIGNING_TX)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = ISSUING_INVOICE
            // Step 1. Get a reference to the notary service on our network and our key pair.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // Step 2. Create a new TransactionBuilder object.
            val builder = TransactionBuilder(notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // Step 3. Create command
            val issueCommand = Command(BillOfLadingContract.Commands.IssueBL(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))

            // Step 4. Add the bol as an output state, as well as a command to the transaction builder.
            builder.addOutputState(billOfLading, BillOfLadingContract.CONTRACT_ID)
            builder.addCommand(issueCommand)

            // Step 5. Verify
            progressTracker.currentStep = VERIFYING_TX
            builder.verify(serviceHub)

            // Step 6. Create signed transaction
            progressTracker.currentStep = SIGNING_TX
            val stx = serviceHub.signInitialTransaction(builder)

            // Step 6. Assuming no exceptions, we can now finalise the transaction.
            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatedBy(UploadAndSend::class)
    class ReceiveBol(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
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