package eloc.contract

import eloc.state.InvoiceState
import eloc.state.LetterOfCreditApplicationState
import eloc.state.LetterOfCreditState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash

open class LetterOfCreditContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "eloc.contract.LetterOfCreditContract"
    }

    interface Commands : CommandData {
        class Issuance : TypeOnlyCommandData(), Commands
        class ConfirmShipment : TypeOnlyCommandData(), Commands
        class AddPaymentToAdvisory :TypeOnlyCommandData(), Commands
        class AddPaymentToIssuer :TypeOnlyCommandData(), Commands
        class AddPaymentToBeneficiary : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Issuance -> {
                requireThat {
                    val invoice = tx.inputsOfType<InvoiceState>().single()
                    val output = tx.outputsOfType<LetterOfCreditState>().single()
                    // confirms the LetterOfCreditApplication is included in the transaction
                    tx.inputsOfType<LetterOfCreditApplicationState>().single()
                    requireThat {
                        "input invoice should not be consumable" using (invoice.isConsumeable == false)
                        //"the transaction is not signed by the advising bank" by (command.signers.contains(output.props.advisingBank.owningKey))
                        "the LOC must be Issued" using (output.issued == true)
                        "Demand Presentation must not be preformed successfully" using (output.beneficiaryPaid == false)
                        "LOC must not be terminated" using (output.terminated == false)
                        "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                    }
                }
            }

            is Commands.ConfirmShipment -> {
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "the transaction is signed by the seller" using (command.signers.contains(output.props.beneficiary.owningKey))
                    "the LOC must be Issued" using (output.issued == true)
                }
            }

            is Commands.AddPaymentToAdvisory -> {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                    "Beneficiary has not already been paid" using (input.advisoryPaid == false)
                    "Beneficiary is marked as being paid in output state" using (output.advisoryPaid == true)
                    "LOC must not be terminated" using (output.terminated == false)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddPaymentToIssuer -> {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                    "Beneficiary has not already been paid" using (input.issuerPaid == false)
                    "Beneficiary is marked as being paid in output state" using (output.issuerPaid == true)
                    "LOC must not be terminated" using (output.terminated == false)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddPaymentToBeneficiary -> {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                    "Beneficiary has not already been paid" using (input.beneficiaryPaid == false)
                    "Beneficiary is marked as being paid in output state" using (output.beneficiaryPaid == true)
                    "LOC must not be terminated" using (output.terminated == false)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }
        }
    }
}