package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.InvoiceContract
import eloc.contract.LetterOfCreditApplicationContract
import eloc.contract.LetterOfCreditContract
import eloc.state.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import java.time.LocalDate

@InitiatingFlow
@StartableByRPC
class ApproveLoCFlow(val reference: String) : FlowLogic<SignedTransaction>() {
    companion object {
        object GENERATING_APPROVAL_TRANSACTION : ProgressTracker.Step("Generating LOC transaction.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction.")
        object FINALIZING : ProgressTracker.Step("Recording and distributing transaction.")

        fun tracker() = ProgressTracker(
                GENERATING_APPROVAL_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALIZING
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val applicationStateAndRef = serviceHub.vaultService.queryBy<LetterOfCreditApplicationState>().states.find {
            it.state.data.props.letterOfCreditApplicationID == reference
        } ?: throw IllegalArgumentException("No letter-of-credit application with ID $reference found.")

        val invoiceStateAndRef = serviceHub.vaultService.queryBy<InvoiceState>().states.find {
            it.state.data.props.invoiceID == reference
        } ?: throw IllegalArgumentException("No invoice with ID $reference found.")

        val application = applicationStateAndRef.state.data
        // Step 1. Generate transaction
        progressTracker.currentStep = GENERATING_APPROVAL_TRANSACTION
        val locProps = LetterOfCreditProperties(application.props, LocalDate.now())

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val loc = LetterOfCreditState(
                application.beneficiary,
                application.advisingBank,
                application.issuer,
                application.applicant,
                status = LetterOfCreditStatus.ISSUED,
                props = locProps)

        val builder = TransactionBuilder(notary = notary)
                .addInputState(applicationStateAndRef)
                .addInputState(invoiceStateAndRef)
                .addOutputState(loc, LetterOfCreditContract.CONTRACT_ID)
                .addCommand(LetterOfCreditApplicationContract.Commands.Approve(), ourIdentity.owningKey)
                .addCommand(LetterOfCreditContract.Commands.Issue(), ourIdentity.owningKey)
                .addCommand(InvoiceContract.Commands.Extinguish(), ourIdentity.owningKey)

        // Step 2. Add timestamp
        progressTracker.currentStep = SIGNING_TRANSACTION
        val currentTime = serviceHub.clock.instant()
        builder.setTimeWindow(currentTime, 30.seconds)

        // Step 3. Verify transaction
        builder.verify(serviceHub)

        // Step 4. Sign transaction
        progressTracker.currentStep = FINALIZING
        val stx = serviceHub.signInitialTransaction(builder)

        // Step 5. Assuming no exceptions, we can now finalise the transaction.
        return subFlow(FinalityFlow(stx))
    }
}