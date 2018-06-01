package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.BillOfLadingContract
import eloc.contract.LetterOfCreditContract
import eloc.state.BillOfLadingState
import eloc.state.LetterOfCreditState
import eloc.state.LetterOfCreditStatus
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
class CreateBoLFlow(val letterOfCreditID: String, val billOfLading: BillOfLadingState) : FlowLogic<SignedTransaction>() {
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
        // Step 0. Retrieve letter of credit.
        // TODO: Can change this to querying using a schema.
        val lettersOfCredit = serviceHub.vaultService.queryBy<LetterOfCreditState>().states
        val letterOfCreditStateAndRef = lettersOfCredit.find { stateAndRef -> stateAndRef.state.data.props.letterOfCreditID == letterOfCreditID }
                ?: throw IllegalArgumentException("No letter of credit with ID $letterOfCreditID found.")

        // Step 1. Create output letter of credit, where the status has been updated.
        val outputLetterOfCredit = letterOfCreditStateAndRef.state.data.copy(status = LetterOfCreditStatus.LADED)

        progressTracker.currentStep = ISSUING_INVOICE
        // Step 2. Get a reference to the notary service on our network and our key pair.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 3. Create a new TransactionBuilder object.
        val builder = TransactionBuilder(notary)
        builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

        // Step 4. Create command
        val issueCommand = Command(BillOfLadingContract.Commands.Issue(), ourIdentity.owningKey)
        val addBillOfLadingCommand = Command(LetterOfCreditContract.Commands.AddBillOfLading(), ourIdentity.owningKey)

        // Step 5. Add the bol as an output state, as well as a command to the transaction builder.
        builder.addInputState(letterOfCreditStateAndRef)
        builder.addOutputState(billOfLading, BillOfLadingContract.CONTRACT_ID)
        builder.addOutputState(outputLetterOfCredit, LetterOfCreditContract.CONTRACT_ID)
        builder.addCommand(issueCommand)
        builder.addCommand(addBillOfLadingCommand)

        // Step 6. Verify
        progressTracker.currentStep = VERIFYING_TX
        builder.verify(serviceHub)

        // Step 7. Create signed transaction
        progressTracker.currentStep = SIGNING_TX
        val stx = serviceHub.signInitialTransaction(builder)

        // Step 8. Assuming no exceptions, we can now finalise the transaction.
        return subFlow(FinalityFlow(stx))
    }
}