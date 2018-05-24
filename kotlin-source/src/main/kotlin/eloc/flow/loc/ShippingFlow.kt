package eloc.flow.loc

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.LetterOfCreditContract
import eloc.flow.SignWithoutCheckingFlow
import eloc.state.BillOfLadingState
import eloc.state.LetterOfCreditState
import eloc.state.LetterOfCreditStatus
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

object ShippingFlow {

    @InitiatingFlow
    @StartableByRPC
    class Ship(val locId: String) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_APPLICATION_TRANSACTION : ProgressTracker.Step("Generating update loc transaction.")
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
            val locStateAndRefs = serviceHub.vaultService.queryBy<LetterOfCreditState>().states.filter { it.state.data.props.letterOfCreditID == locId }
            if (locStateAndRefs.isEmpty()) throw Exception("Order could not be shipped. Letter of credit state with ID $locId not found.")
            if (locStateAndRefs.size > 1) throw Exception("Several unshipped letter of credit states with ID $locId found.")
            val locStateAndRef = locStateAndRefs.single()
            if (locStateAndRef.state.data.status != LetterOfCreditStatus.ISSUED) throw Exception("Order could not be shipped. It has already been shipped or terminated.")

            // #2 Check that bill of lading has been created, this will throw an error if exactly one of each doesn't exist.
            val bolStateCount = serviceHub.vaultService.queryBy<BillOfLadingState>().states.count { it.state.data.props.billOfLadingID == locId }
            if (bolStateCount == 0) throw Exception("Order could not be shipped. Bill of lading has not been created.")
            if (bolStateCount > 1) throw Exception("Several bill of lading states with ID $locId found.")

            // #3 Let's get the basics of a transaction built beginning with obtaining a reference to the notary
            progressTracker.currentStep = GENERATING_APPLICATION_TRANSACTION
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // #4 Create output state where the status is marked as shipped
            val outputState = locStateAndRef.state.data.shipped()

            // #5 Create builder and command
            val builder = TransactionBuilder(notary = notary)
            builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

            // #6 Add other states
            builder.addInputState(locStateAndRef)
            builder.addOutputState(outputState, LetterOfCreditContract.CONTRACT_ID)
            builder.addCommand(LetterOfCreditContract.Commands.ConfirmShipment(), listOf(ourIdentity.owningKey))

            // #7 Let's formalise the transaction by verifying and signing
            builder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val stx = serviceHub.signInitialTransaction(builder)

            // #8 Send to other participants
            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatingFlow
    @InitiatedBy(Ship::class)
    class ReceiveShipped(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(SignWithoutCheckingFlow(counterpartySession))
        }
    }
}