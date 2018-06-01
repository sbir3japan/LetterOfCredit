package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.InvoiceContract
import eloc.contract.LetterOfCreditApplicationContract
import eloc.contract.LetterOfCreditContract
import eloc.state.InvoiceState
import eloc.state.LetterOfCreditApplicationProperties
import eloc.state.LetterOfCreditApplicationState
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class ApplyForLoCFlow(val beneficiaryName: String, val issuingBankName: String, val advisingBankName: String,
                      val applicationProperties: LetterOfCreditApplicationProperties) : FlowLogic<SignedTransaction>() {
    companion object {
        object GENERATING_APPLICATION_TRANSACTION : ProgressTracker.Step("Generating LOC application transaction.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our key.")
        object NOTARIZING_TRANSACTION : ProgressTracker.Step("Sending it to the notary.")
        object RECORDING_TRANSACTION : ProgressTracker.Step("Recording transaction.")
    }

    override val progressTracker = ProgressTracker(GENERATING_APPLICATION_TRANSACTION, SIGNING_TRANSACTION, NOTARIZING_TRANSACTION, RECORDING_TRANSACTION)

    @Suspendable
    override fun call(): SignedTransaction {
        val beneficiary = serviceHub.identityService.partiesFromName(beneficiaryName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for beneficiary name $beneficiaryName.")
        val issuingBank = serviceHub.identityService.partiesFromName(issuingBankName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for issuing bank name $issuingBankName.")
        val advisingBank = serviceHub.identityService.partiesFromName(advisingBankName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for advising bank name $advisingBankName.")

        val application = LetterOfCreditApplicationState(
                applicant = ourIdentity,
                beneficiary = beneficiary,
                issuer = issuingBank,
                advisingBank = advisingBank,
                props = applicationProperties)

        progressTracker.currentStep = GENERATING_APPLICATION_TRANSACTION
        // Step 1. Get a reference to the notary service on our network and our key pair.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Create a new TransactionBuilder object.
        val builder = TransactionBuilder(notary)
        builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

        // Step 3. Create command
        val issueCommand = Command(LetterOfCreditApplicationContract.Commands.Apply(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))
        val invoiceCommand = Command(InvoiceContract.Commands.LockInvoice(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))

        // Step 4. Add original invoice to the input state. Invoice stateRef is present in the "application" object
        // TODO: Can change this to querying using a schema.
        val invoices = serviceHub.vaultService.queryBy<InvoiceState>().states
        val invoiceStateAndRef = invoices.find { stateAndRef -> stateAndRef.state.data.props.invoiceID == application.props.letterOfCreditApplicationID }
                ?: throw IllegalArgumentException("No invoice with ID ${application.props.letterOfCreditApplicationID} found.")
        builder.addInputState(invoiceStateAndRef)

        //Step5. Add invoice state to the output with Issuing Bank as a participant
        val outputInvoiceState = invoiceStateAndRef.state.data.copy(participants = listOf(invoiceStateAndRef.state.data.buyer, application.issuer), consumable = false)
        builder.addOutputState(outputInvoiceState, InvoiceContract.CONTRACT_ID)
        builder.addCommand(invoiceCommand)

        // Step 6. Add the application as an output state, as well as a command to the transaction builder.
        val state = LetterOfCreditApplicationState(application.applicant, application.issuer, application.beneficiary, application.advisingBank, application.props)
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