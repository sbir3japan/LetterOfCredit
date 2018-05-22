package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.state.BillOfLadingState
import eloc.state.InvoiceState
import eloc.state.LetterOfCreditApplicationState
import eloc.state.LetterOfCreditState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.flows.CashIssueFlow
import java.util.*

/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/option purposes!
 */

@CordaSerializable
@StartableByRPC
class GetTransactionsFlow : FlowLogic<List<TransactionSummary>>() {
    @Suspendable
    override fun call(): List<TransactionSummary> {
        val signedTransactions = serviceHub.validatedTransactions.track().snapshot
        val ledgerTransactions = signedTransactions.map { tx -> tx.toLedgerTransaction(serviceHub) }
        return ledgerTransactions.map { tx ->
            val inputStateTypes = tx.inputStates.map { inputState -> mapToStateSubclass(inputState) }
            val outputStateTypes = tx.outputStates.map { outputState -> mapToStateSubclass(outputState) }
            TransactionSummary(tx.id, inputStateTypes, outputStateTypes)
        }
    }

    private fun mapToStateSubclass(state: ContractState) = when (state) {
        is InvoiceState -> "InvoiceState"
        is LetterOfCreditApplicationState -> "LetterOfCreditApplicationState"
        is LetterOfCreditState -> "LetterOfCreditState"
        is BillOfLadingState -> "BillOfLadingState"
        else -> "ContractState"
    }
}

@CordaSerializable
data class TransactionSummary(val hash: SecureHash, val inputs: List<String>, val output: List<String>)