package eloc.contract

import eloc.state.PackingListState
import eloc.state.PackingListStatus
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * A packing list story...
 *
 * The thinking is that the buyer/beneficiary would provide this data and an attachment, which represents the same, to the advising
 * node. A packing list details the goods that are to be shipped.
 *
 * Packing List was added in phase II aka Voltron
 *
 * @author Mike
 */

class PackingListContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_ID = "eloc.contract.PackingListContract"
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Update : TypeOnlyCommandData(), Commands
        class Communicate : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val txOutputStates: List<PackingListState> = tx.outputsOfType()
        val txInputStates: List<PackingListState> = tx.inputsOfType()

        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    "there is no input state" using txInputStates.isEmpty()
                    "there is one output state" using (txOutputStates.size == 1)
                }
            }
            is Commands.Update -> {
                requireThat {
                    "the transaction is signed by the buyer" using (command.signers.contains(txOutputStates.single().seller.owningKey))
                    "same buyer" using (txInputStates.single().seller == txOutputStates.single().seller)
                    "there is one input state" using (txInputStates.size == 1)
                    "there is one output state" using (txOutputStates.size == 1)
                }
            }
            is Commands.Communicate -> {
                requireThat {
                    "the status is Signed" using (txOutputStates.single().status == PackingListStatus.SIGNED)
                }
            }
        }
    }
}
