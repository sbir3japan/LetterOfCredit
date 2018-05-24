package eloc.contract

import eloc.state.BillOfLadingState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * A bill of lading is a standard-form document. It is transferable by endorsement (or by lawful transfer of possession)
 * and is a receipt from shipping company regarding the number of packages with a particular weight and markings and a
 * contract for the transportation of same to a port of destination mentioned therein.
 *
 * An order bill of lading is used when shipping merchandise prior to loc, requiring a carrier to deliver the
 * merchandise to the importer, and at the endorsement of the exporter the carrier may transfer title to the importer.
 * Endorsed order bills of lading can be traded as a security or serve as collateral against debt obligations.
 */
class BillOfLadingContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "eloc.contract.BillOfLadingContract"
    }

    interface Commands : CommandData {
        class IssueBillOfLading : TypeOnlyCommandData(), Commands
        class TransferPossession : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val outputStates: List<BillOfLadingState> = tx.outputsOfType()
        val inputStates: List<BillOfLadingState> = tx.inputsOfType()

        when (command.value) {
            is Commands.IssueBillOfLading -> requireThat {
                "There is no input state" using inputStates.isEmpty()
                "There is one output state" using (outputStates.size == 1)
                // TODO: Signer constraints.
            }
            is Commands.TransferPossession -> requireThat {
                "the state object owner has been updated" using (inputStates.single().owner != outputStates.single().owner)
                "the beneficiary is unchanged" using (inputStates.single().buyer == outputStates.single().buyer)
                "the bill of lading agreement properties are unchanged" using (inputStates.single().props == outputStates.single().props)
                // TODO: Signer constraints.
            }
        }
    }
}
