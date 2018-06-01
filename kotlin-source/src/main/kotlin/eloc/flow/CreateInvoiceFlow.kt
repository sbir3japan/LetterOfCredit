package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.InvoiceContract
import eloc.state.InvoiceProperties
import eloc.state.InvoiceState
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class CreateInvoiceFlow(val buyerName: String, val invoiceProperties: InvoiceProperties) : FlowLogic<SignedTransaction>() {
    companion object {
        object GETTING_NOTARY : ProgressTracker.Step("Retrieving notary identity.")
        object CREATING_COMPONENTS : ProgressTracker.Step("Creating transaction components.")
        object BUILDING_TX : ProgressTracker.Step("Building the transaction.")
        object VERIFYING_TX : ProgressTracker.Step("Verifying the transaction.")
        object SIGNING_TX : ProgressTracker.Step("Signing the transaction.")
        object FINALISING_TX : ProgressTracker.Step("Finalising the transaction.")

        fun tracker() = ProgressTracker(GETTING_NOTARY, CREATING_COMPONENTS, BUILDING_TX, VERIFYING_TX, SIGNING_TX, FINALISING_TX)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = CREATING_COMPONENTS
        val buyers = serviceHub.identityService.partiesFromName(buyerName, false)
        if (buyers.size != 1) throw IllegalArgumentException("Buyer not found.")
        val buyer = buyers.single()
        val invoice = InvoiceState(ourIdentity, buyer, true, invoiceProperties)
        val issueCommand = Command(InvoiceContract.Commands.Issue(), listOf(ourIdentity.owningKey))

        progressTracker.currentStep = BUILDING_TX
        val builder = TransactionBuilder(notary)
                .addOutputState(invoice, InvoiceContract.CONTRACT_ID)
                .addCommand(issueCommand)

        progressTracker.currentStep = VERIFYING_TX
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TX
        val stx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = FINALISING_TX
        return subFlow(FinalityFlow(stx))
    }
}