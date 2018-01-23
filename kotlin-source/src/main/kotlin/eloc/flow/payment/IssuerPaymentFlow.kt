package eloc.flow.payment

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.BillOfLadingAgreement
import eloc.contract.LOC
import eloc.state.BillofLadingState
import eloc.state.LOCState
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import java.time.Duration
import java.time.Instant

object IssuerPaymentFlow {

    @InitiatingFlow
    @StartableByRPC
    class MakePayment(val locId: String) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_APPLICATION_TRANSACTION : ProgressTracker.Step("Generating payment transaction.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our key.")
            object NOTARIZING_TRANSACTION : ProgressTracker.Step("Sending it to the notary.")
            object RECORDING_TRANSACTION : ProgressTracker.Step("Recording transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_APPLICATION_TRANSACTION,
                    SIGNING_TRANSACTION,
                    NOTARIZING_TRANSACTION,
                    RECORDING_TRANSACTION,
                    COLLECTING
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call() : SignedTransaction {

            // #1 Pull state from vault and reference to payee
            val locState = serviceHub.vaultService.queryBy<LOCState>().states.single { !it.state.data.terminated && it.state.data.props.letterOfCreditID == locId }
            val bolState = serviceHub.vaultService.queryBy<BillofLadingState>().states.single { it.state.data.props.billOfLadingID == locId }
            val payee = locState.state.data.props.issuingBank
            val newOwner = serviceHub.myInfo.legalIdentities.first()

            // #2 Let's get the basics of a transaction built beginning with obtaining a reference to the notary
            progressTracker.currentStep = GENERATING_APPLICATION_TRANSACTION
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // #3 Create output state where the beneficiary is marked as being paid
            val outputStateLoc = locState.state.data.issuerPaid()
            val outputStateBol = bolState.state.data.copy(owner = newOwner, timestamp = Instant.now())

            // #4 Create builder and command
            val builder = TransactionBuilder(notary = notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // #5 Let's create the payment to the beneficiary
            Cash.generateSpend(serviceHub, builder, (locState.state.data.props.amount * 110), payee)

            // #6 Add other states
            builder.addInputState(locState)
            builder.addInputState(bolState)
            builder.addOutputState(outputStateLoc, LOC.LOC_CONTRACT_ID)
            builder.addOutputState(outputStateBol, BillOfLadingAgreement.BOL_CONTRACT_ID)
            builder.addCommand(LOC.Commands.AddPaymentToIssuer(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))
            builder.addCommand(BillOfLadingAgreement.Commands.TransferPossession(), serviceHub.myInfo.legalIdentities.first().owningKey)

            // #7 Let's formalise the transaction by verifying and signing
            builder.verify(serviceHub)
            progressTracker.currentStep = SIGNING_TRANSACTION

            val stx = serviceHub.signInitialTransaction(builder)

            // #8 Send to other participants
            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatingFlow
    @InitiatedBy(MakePayment::class)
    class ReceivePayment(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        companion object {
            object RECEIVING : ProgressTracker.Step("Receiving payment")
            object VALIDATING : ProgressTracker.Step("Validating payment signature")
            object SIGNING : ProgressTracker.Step("Signing payment")
            object SUCCESS : ProgressTracker.Step("Payment successful")
            object BROADCAST : ProgressTracker.Step("Broadcast payment state to required parties")

            fun tracker() = ProgressTracker(
                    RECEIVING,
                    VALIDATING,
                    SIGNING,
                    SUCCESS,
                    BROADCAST
            )
        }
        override val progressTracker = tracker()
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(counterpartySession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    //Do we need to do anything?
                }
            }

            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }
}