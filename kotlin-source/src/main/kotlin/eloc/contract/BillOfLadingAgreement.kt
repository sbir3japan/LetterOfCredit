package eloc.contract

import eloc.state.BillOfLadingProperties
import eloc.state.BillofLadingState
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey
import java.time.Instant

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Bill of Lading Agreement
//

/**
 * A bill of lading is a standard-form document. It is transferable by endorsement (or by lawful transfer of possession)
 * and is a receipt from shipping company regarding the number of packages with a particular weight and markings and a
 * contract for the transportation of same to a port of destination mentioned therein.
 *
 * An order bill of lading is used when shipping merchandise prior to payment, requiring a carrier to deliver the
 * merchandise to the importer, and at the endorsement of the exporter the carrier may transfer title to the importer.
 * Endorsed order bills of lading can be traded as a security or serve as collateral against debt obligations.
 */

class BillOfLadingAgreement : Contract {

    companion object {
        @JvmStatic
        val BOL_CONTRACT_ID = "eloc.contract.BillOfLadingAgreement"
    }

    interface Commands : CommandData {
        class IssueBL : TypeOnlyCommandData(), Commands
        class TransferAndEndorseBL : TypeOnlyCommandData(), Commands
        class TransferPossession : TypeOnlyCommandData(), Commands
    }

    /** The Invoice contract needs to handle three commands
     * 1: IssueBL --
     * 2: TransferAndEndorseBL --
     * 3: TransferPossession --
     */
    override fun verify(tx: LedgerTransaction) {
        // We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<BillOfLadingAgreement.Commands>()

        val txOutputStates: List<BillofLadingState> = tx.outputsOfType()
        val txInputStates: List<BillofLadingState> = tx.inputsOfType()

        when (command.value) {
            is Commands.IssueBL -> {
                requireThat {
                    "there is no input state" using txInputStates.isEmpty()
                    "there is one output state" using (txOutputStates.size == 1)
                    // We'll relax this requirement for the demo since we don't have a carrier node
                    //"the transaction is signed by the carrier" by (command.signers.contains(txOutputStates.single().props.carrierOwner.owningKey))
                }
            }
            is Commands.TransferAndEndorseBL -> {
                requireThat {
                    "the transaction is signed by the beneficiary" using (command.signers.contains(txInputStates.single().buyer.owningKey))
                    //"the transaction is signed by the state object owner" by (command.signers.contains(txInputStates.single().owner))
                    "the bill of lading agreement properties are unchanged" using (txInputStates.single().props == txOutputStates.single().props)
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
