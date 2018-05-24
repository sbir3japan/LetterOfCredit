package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.LetterOfCreditApplicationContract
import eloc.state.LetterOfCreditApplicationState
import eloc.state.LetterOfCreditApplicationStatus
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

object LOCApplicationFlow {

    @InitiatingFlow
    @StartableByRPC
    class Apply(val application: LetterOfCreditApplicationState) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_APPLICATION_TRANSACTION : ProgressTracker.Step("Generating LOC application transaction.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our key.")
            object NOTARIZING_TRANSACTION : ProgressTracker.Step("Sending it to the notary.")
            object RECORDING_TRANSACTION : ProgressTracker.Step("Recording transaction.")
        }

        override val progressTracker = ProgressTracker(GENERATING_APPLICATION_TRANSACTION, SIGNING_TRANSACTION, NOTARIZING_TRANSACTION, RECORDING_TRANSACTION)

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_APPLICATION_TRANSACTION
            // Step 1. Get a reference to the notary service on our network and our key pair.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // Step 2. Create a new TransactionBuilder object.
            val builder = TransactionBuilder(notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // Step 3. Create command
            val issueCommand = Command(LetterOfCreditApplicationContract.Commands.Apply(), listOf(ourIdentity.owningKey))

            // Step 4. Add the application as an output state, as well as a command to the transaction builder.
            val state = LetterOfCreditApplicationState(application.owner, application.issuer, LetterOfCreditApplicationStatus.IN_REVIEW, application.props, null)
            builder.addOutputState(state, LetterOfCreditApplicationContract.CONTRACT_ID)
            builder.addCommand(issueCommand)

            // Step 5. Verify
            builder.verify(serviceHub)

            // Step 6. Sign transaction
            val stx = serviceHub.signInitialTransaction(builder)

            // Step 7. Assuming no exceptions, we can now finalise the transaction.
            progressTracker.currentStep = RECORDING_TRANSACTION

            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatingFlow
    @InitiatedBy(Apply::class)
    class ReceiveApplication(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(SignWithoutCheckingFlow(counterpartySession))
        }
    }
}