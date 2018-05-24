package eloc.flow.documents

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.InvoiceContract
import eloc.flow.SignWithoutCheckingFlow
import eloc.state.InvoiceState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

object InvoiceFlow {

    @InitiatingFlow
    @StartableByRPC
    class UploadAndSend(val buyer: Party, val submittedInvoice: InvoiceState) : FlowLogic<SignedTransaction>() {
        companion object {
            object CREATING_BUILDER : ProgressTracker.Step("Creating builder")
            object ISSUING_INVOICE : ProgressTracker.Step("Creating and Signing Invoice")
            object ADDING_STATES : ProgressTracker.Step("Adding invoice state")
            object SENDING_INVOICE : ProgressTracker.Step("Sending Invoice to Applicant")
            object VERIFYING_TX : ProgressTracker.Step("Verifying transaction send by issuer")
            object SIGNING_TX : ProgressTracker.Step("Signing transaction")
            object SENDING_TX : ProgressTracker.Step("Sending to buyer")

            fun tracker() = ProgressTracker(CREATING_BUILDER, ISSUING_INVOICE, ADDING_STATES, SENDING_INVOICE, VERIFYING_TX, SIGNING_TX, SENDING_TX)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Step 1. Get a reference to the notary service on our network and our key pair.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep = CREATING_BUILDER
            // Step 2. Create a new TransactionBuilder object.
            val builder = TransactionBuilder(notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // Step 3. Create invoice and command
            progressTracker.currentStep = ISSUING_INVOICE
            val invoice = submittedInvoice
            val issueCommand = Command(InvoiceContract.Commands.Issue(), listOf(ourIdentity.owningKey))

            // Step 4. Add the invoice as an output state, as well as a command to the transaction builder.
            progressTracker.currentStep = ADDING_STATES
            builder.addOutputState(invoice, InvoiceContract.CONTRACT_ID)
            builder.addCommand(issueCommand)

            // Step 5. Verify and sign it with our KeyPair.
            progressTracker.currentStep = VERIFYING_TX
            builder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TX
            val ptx = serviceHub.signInitialTransaction(builder)

            // Step 6. Get counter-party signature
            progressTracker.currentStep = SENDING_TX
            val flowSession = initiateFlow(buyer)
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(flowSession)))

            // Step 6. Assuming no exceptions, we can now finalise the transaction.
            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatedBy(UploadAndSend::class)
    class ReceiveInvoice(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(SignWithoutCheckingFlow(counterpartySession))
        }
    }
}