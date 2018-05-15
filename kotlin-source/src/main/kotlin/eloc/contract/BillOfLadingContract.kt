package eloc.contract

import eloc.state.BillOfLadingState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Bill of Lading Contract
//

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

    /** The Invoice contract needs to handle two commands
     * 1: IssueBL --
     * 2: TransferPossession --
     */
    override fun verify(tx: LedgerTransaction) {
        // We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<Commands>()

        val txOutputStates: List<BillOfLadingState> = tx.outputsOfType()
        val txInputStates: List<BillOfLadingState> = tx.inputsOfType()

        when (command.value) {
            is Commands.IssueBillOfLading -> {
                requireThat {
                    "there is no input state" using txInputStates.isEmpty()
                    "there is one output state" using (txOutputStates.size == 1)
                    // We'll relax this requirement for the demo since we don't have a carrier node
                    //"the transaction is signed by the carrier" by (command.signers.contains(txOutputStates.single().props.carrierOwner.owningKey))
                }
            }
            is Commands.TransferPossession -> {
                requireThat {
                    //"the transaction is signed by the state object owner" by (command.signers.contains(txInputStates.single().owner))
                    "the state object owner has been updated" using (txInputStates.single().owner != txOutputStates.single().owner)
                    "the beneficiary is unchanged" using (txInputStates.single().buyer == txOutputStates.single().buyer)
                    "the bill of lading agreement properties are unchanged" using (txInputStates.single().props == txOutputStates.single().props)
                }
            }
        }
    }
}
