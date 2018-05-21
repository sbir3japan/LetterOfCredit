package eloc.flow.loc

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.BillOfLadingContract
import eloc.contract.LetterOfCreditContract
import eloc.state.BillOfLadingState
import eloc.state.LetterOfCreditState
import eloc.state.LetterOfCreditStatus
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import java.time.Duration
import java.time.Instant

object AdvisoryPaymentFlow {

    @InitiatingFlow
    @StartableByRPC
    class MakePayment(val locId: String) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_APPLICATION_TRANSACTION : ProgressTracker.Step("Generating loc transaction.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our key.")
            object NOTARIZING_TRANSACTION : ProgressTracker.Step("Sending it to the notary.")
            object RECORDING_TRANSACTION : ProgressTracker.Step("Recording transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
        }

        override val progressTracker = ProgressTracker(
                GENERATING_APPLICATION_TRANSACTION,
                SIGNING_TRANSACTION,
                NOTARIZING_TRANSACTION,
                RECORDING_TRANSACTION,
                COLLECTING
        )

        @Suspendable
        override fun call() : SignedTransaction {
            // #1 Pull state from vault and reference to payee
            val locStates = serviceHub.vaultService.queryBy<LetterOfCreditState>().states.filter {
                it.state.data.status != LetterOfCreditStatus.TERMINATED && it.state.data.props.letterOfCreditID == locId
            }
            if (locStates.isEmpty()) throw Exception("Advising bank could not be paid. Letter of credit state with ID $locId not found.")
            if (locStates.size > 1) throw Exception("Several letter of credit states with ID $locId found.")
            val locState = locStates.single()

            val bolStates = serviceHub.vaultService.queryBy<BillOfLadingState>().states.filter {
                it.state.data.props.billOfLadingID == locId
            }
            if (bolStates.isEmpty()) throw Exception("Advising bank could not be paid. Bill of lading has not been created.")
            if (bolStates.size > 1) throw Exception("Several bill of lading states with ID $locId found.")
            val bolState = bolStates.single()

            val payee = locState.state.data.props.advisingBank
            val newOwner = serviceHub.myInfo.legalIdentities.first()

            // #2 Let's get the basics of a transaction built beginning with obtaining a reference to the notary
            progressTracker.currentStep = GENERATING_APPLICATION_TRANSACTION
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // #3 Create output state where the beneficiary is marked as being paid
            val outputState = locState.state.data.advisoryPaid()
            val outputStateBol = bolState.state.data.copy(owner = newOwner, timestamp = Instant.now())

            // #4 Create builder and command
            val builder = TransactionBuilder(notary = notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // #5 Let's create the loc to the beneficiary
            Cash.generateSpend(serviceHub, builder, (locState.state.data.props.amount * 100), payee)

            // #6 Add other states
            builder.addInputState(locState)
            builder.addInputState(bolState)
            builder.addOutputState(outputState, LetterOfCreditContract.CONTRACT_ID)
            builder.addOutputState(outputStateBol, BillOfLadingContract.CONTRACT_ID)
            builder.addCommand(LetterOfCreditContract.Commands.AddPaymentToAdvisory(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))
            builder.addCommand(BillOfLadingContract.Commands.TransferPossession(), serviceHub.myInfo.legalIdentities.first().owningKey)

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
            object RECEIVING : ProgressTracker.Step("Receiving loc")
            object VALIDATING : ProgressTracker.Step("Validating loc signature")
            object SIGNING : ProgressTracker.Step("Signing loc")
            object SUCCESS : ProgressTracker.Step("Payment successful")
            object BROADCAST : ProgressTracker.Step("Broadcast loc state to required parties")

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