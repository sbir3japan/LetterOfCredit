package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.InvoiceContract
import eloc.contract.LetterOfCreditApplicationContract
import eloc.state.InvoiceState
import eloc.state.LetterOfCreditApplicationState
import eloc.state.LetterOfCreditApplicationStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
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
            val issueCommand = Command(LetterOfCreditApplicationContract.Commands.ApplyForLetterOfCredit(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))
            val invoiceCommand = Command(InvoiceContract.Commands.LockInvoice(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))

            //Step 4. Add original invoice to the input state. Invoice stateRef is present in the "application" object
            val invoiceStateRef = application.props.invoiceRef
            val invoiceStateAndRef: StateAndRef<InvoiceState> = serviceHub.toStateAndRef<InvoiceState>(invoiceStateRef)
            builder.addInputState(invoiceStateAndRef)

            //Step5. Add invoice state to the output with Issuing Bank as a participant
            val outputInvoiceState = invoiceStateAndRef.state.data.copy(participants = listOf(invoiceStateAndRef.state.data.buyer, application.issuer ), isConsumeable = false)
            builder.addOutputState(outputInvoiceState, InvoiceContract.CONTRACT_ID)
            builder.addCommand(invoiceCommand)

            // Step 6. Add the application as an output state, as well as a command to the transaction builder.
            val state = LetterOfCreditApplicationState(application.owner, application.issuer, LetterOfCreditApplicationStatus.PENDING_ISSUER_REVIEW, application.props, null)
            builder.addOutputState(state, LetterOfCreditApplicationContract.CONTRACT_ID)
            builder.addCommand(issueCommand)

            // Step 7. Verify
            builder.verify(serviceHub)

            // Step 8. Sign transaction
            val stx = serviceHub.signInitialTransaction(builder)

            // Step 9. Assuming no exceptions, we can now finalise the transaction.
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