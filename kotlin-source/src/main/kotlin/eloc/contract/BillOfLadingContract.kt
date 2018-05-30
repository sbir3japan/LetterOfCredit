package eloc.contract

import eloc.state.BillOfLadingState
import eloc.state.LetterOfCreditState
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
        class Issue : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val inputStates = tx.inputsOfType<ContractState>()
        val inputBillsOfLading = tx.inputsOfType<BillOfLadingState>()
        val inputLettersOfCredit = tx.inputsOfType<LetterOfCreditState>()
        val outputStates = tx.outputsOfType<ContractState>()
        val outputBillsOfLading = tx.outputsOfType<BillOfLadingState>()
        val outputLettersOfCredit = tx.outputsOfType<LetterOfCreditState>()

        when (command.value) {
            is Commands.Issue -> requireThat {
                "There are no input states." using inputStates.isEmpty()
                "There is one output state" using (outputStates.size == 1)
                "The output state is a bill of lading" using (outputBillsOfLading.size == 1)
                val billOfLading = outputBillsOfLading.single()

                "The owner of the bill of lading is the beneficiary" using
                        (billOfLading.owner == billOfLading.seller)

                "The owner of the bill of lading is a required signer" using
                        (billOfLading.owner.owningKey in command.signers)
            }

            is Commands.Transfer -> requireThat {
                "There are two input states" using (inputStates.size == 2)
                "One input state is a bill of lading" using (inputBillsOfLading.size == 1)
                "One input state is a letter of credit" using (inputLettersOfCredit.size == 1)
                val inputBillOfLading = inputBillsOfLading.single()
                "There are two output states" using (outputStates.size == 2)
                "One output state is a bill of lading" using (outputBillsOfLading.size == 1)
                "One output state is a letter of credit" using (outputLettersOfCredit.size == 1)
                val outputBillOfLading = inputBillsOfLading.single()

                "The owner of the bill of lading has changed" using
                        (inputBillOfLading.owner != outputBillOfLading.owner)
                "the bill of lading agreement properties are unchanged" using
                        (inputBillOfLading.props == outputBillOfLading.props)

                "The owner of the input bill of lading is a required signer" using
                        (inputBillOfLading.owner.owningKey in command.signers)

                // TODO: Constants around the input/output letter-of-credit state.
            }
        }
    }
}
