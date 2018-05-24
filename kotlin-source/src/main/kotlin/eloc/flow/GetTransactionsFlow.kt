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
import net.corda.finance.contracts.asset.Cash
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
        val ledgerTransactions = signedTransactions.map { signedTx -> signedTx.toLedgerTransaction(serviceHub) }
        return ledgerTransactions.map { ledgerTx ->
            val inputStateTypes = ledgerTx.inputStates.map { inputState -> mapToStateSubclass(inputState) }
            val outputStateTypes = ledgerTx.outputStates.map { outputState -> mapToStateSubclass(outputState) }
            val signers = ledgerTx.commands.flatMap { it.signingParties }
            val signersAndNotary = signers + ledgerTx.notary!!
            val signerNames = signersAndNotary.map { it.name.organisation }.toSet()
            TransactionSummary(ledgerTx.id, inputStateTypes, outputStateTypes, signerNames)
        }
    }

    private fun mapToStateSubclass(state: ContractState) = when (state) {
        is InvoiceState -> "Invoice"
        is LetterOfCreditApplicationState -> "Letter Of Credit App. (${state.status})"
        is LetterOfCreditState -> "Letter Of Credit (${state.status})"
        is BillOfLadingState -> "Bill Of Lading"
        is Cash.State -> "Cash"
        else -> "ContractState"
    }
}

@CordaSerializable
data class TransactionSummary(val hash: SecureHash, val inputs: List<String>, val outputs: List<String>, val signers: Set<String>)