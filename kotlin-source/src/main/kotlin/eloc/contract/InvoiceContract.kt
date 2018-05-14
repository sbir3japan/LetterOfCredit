package eloc.contract

import eloc.state.InvoiceProperties
import eloc.state.InvoiceState
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.Instant
import java.time.ZoneOffset

/**
 * An invoice is a document that describes a trade between a buyer and a buyer. It is issued on a particular date,
 * it lists goods being sold by the buyer, the cost of each good and the total amount owed by the buyer and when
 * the buyer expects to be paid by.
 *
 * In the trade finance world, invoices are used to create other contracts (for example AccountsReceivable), newly
 * created invoices start off with a status of "unassigned", once they're used to create other contracts the status
 * is changed to "assigned". This ensures that an invoice is used only once when creating a financial product like
 * AccountsReceivable.
 *
 */

class InvoiceContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "eloc.contract.InvoiceContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Assign : TypeOnlyCommandData(), Commands
        class Extinguish : TypeOnlyCommandData(), Commands
    }

    /** The Invoice contract needs to handle three commands
     * 1: Issue -- the creation of the Invoice contract. We need to confirm that the correct
     *             party signed the contract and that the relevant fields are populated with valid data.
     * 2: Assign -- the invoice is used to create another type of Contract. The assigned boolean has to change from
     *             false to true.
     * 3: Extinguish -- the invoice is deleted. Proper signing is required.
     *
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val time = Instant.now()

        when (command.value) {
            is Commands.Issue -> {
                if (tx.outputs.size != 1) {
                    throw IllegalArgumentException("Failed requirement: during issuance of the invoice, only " +
                            "one output invoice state should be include in the transaction. " +
                            "Number of output states included was " + tx.outputs.size)
                }
                val issueOutput: InvoiceState = tx.outputsOfType<InvoiceState>().single()

                requireThat {
                    "there is no input state" using tx.inputsOfType<InvoiceState>().isEmpty()
                    "the transaction is signed by the invoice owner" using (command.signers.contains(issueOutput.owner.owningKey))
                    "the buyer and buyer must be different" using (issueOutput.props.buyer.name != issueOutput.props.seller.name)
                    "the invoice ID must not be blank" using (issueOutput.props.invoiceID.length > 0)
                    "the term must be a positive number" using (issueOutput.props.term > 0)
                    "the loc date must be in the future" using (issueOutput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                            > time)
                    "there must be goods associated with the invoice" using (issueOutput.props.goods.isNotEmpty())
                }
            }
            is Commands.Assign -> {
                val assignInput: InvoiceState = tx.inputsOfType<InvoiceState>().single()
                val assignOutput: InvoiceState = tx.outputsOfType<InvoiceState>().single()

                requireThat {
                    "input state owner must be the same as the output state owner" using (assignInput.owner == assignOutput.owner)
                    "the transaction must be signed by the owner" using (command.signers.contains(assignInput.owner.owningKey))
                    "the input invoice must not be assigned" using (assignInput.assigned == false)
                    "the output invoice must be assigned" using (assignOutput.assigned == true)
                    "the loc date must be in the future" using (assignInput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC) > time)
                }
            }
            is Commands.Extinguish -> {
                val extinguishInput: InvoiceState = tx.inputsOfType<InvoiceState>().single()
                val extinguishOutput: InvoiceState? = tx.outputsOfType<InvoiceState>().singleOrNull()

                requireThat {
                    "there shouldn't be an output state" using (extinguishOutput == null)
                    "the transaction must be signed by the owner" using (command.signers.contains(extinguishInput.owner.owningKey))
                    "the loc date must be today or in the past" using (extinguishInput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC) < time)
                }
            }
        }
    }
}