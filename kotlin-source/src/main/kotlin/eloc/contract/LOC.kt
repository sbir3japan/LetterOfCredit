package eloc.contract

import eloc.state.LOCApplicationState
import eloc.state.LOCProperties
import eloc.state.LOCState
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash

open class LOC : Contract {
    companion object {
        @JvmStatic
        val LOC_CONTRACT_ID = "eloc.contract.LOC"
    }

    interface Commands : CommandData {
        class Issuance : TypeOnlyCommandData(), Commands
        class AddDocuments : TypeOnlyCommandData(), Commands
        class ConfirmShipment : TypeOnlyCommandData(), Commands
        class AddPaymentToAdvisory :TypeOnlyCommandData(), Commands
        class AddPaymentToIssuer :TypeOnlyCommandData(), Commands
        class AddPaymentToBeneficiary : TypeOnlyCommandData(), Commands
        class Terminate : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Issuance -> {
                requireThat {
                    val output = tx.outputsOfType<LOCState>().single()
                    // confirms the LOCApplication is included in the transaction
                    tx.inputsOfType<LOCApplicationState>().single()
                    requireThat {
                        //"the transaction is not signed by the advising bank" by (command.signers.contains(output.props.advisingBank.owningKey))
                        "the LOC must be Issued" using (output.issued == true)
                        "Demand Presentation must not be preformed successfully" using (output.beneficiaryPaid == false)
                        "LOC must not be terminated" using (output.terminated == false)
                        "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                    }
                }
            }

            is Commands.AddDocuments -> {
                requireThat {
                    val output = tx.outputsOfType<LOCState>().single()
                    "the transaction is not signed by the issuing bank" using (command.signers.contains(output.props.issuingBank.owningKey))
                    "Demand Presentation must not be preformed successfully" using (output.beneficiaryPaid == false)
                    "LOC must not be terminated" using (output.terminated == false)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.Terminate -> {
                val input = tx.inputsOfType<LOCState>().single()
                val output = tx.outputsOfType<LOCState>().single()
                requireThat {
                    "the transaction is signed by the issuing bank" using (command.signers.contains(output.props.issuingBank.owningKey))
                    "the beneficiary has not been paid, status not changed" using (output.beneficiaryPaid == true)
                    "the LOC must be Issued" using (output.issued == true)
                    "LOC should be terminated" using (output.terminated == true)
                    "the LOC properties do not remain the same" using (input.props.equals(output.props))
                }
            }

            is Commands.ConfirmShipment -> {
                val output = tx.outputsOfType<LOCState>().single()
                requireThat {
                    "the transaction is signed by the seller" using (command.signers.contains(output.props.beneficiary.owningKey))
                    "the LOC must be Issued" using (output.issued == true)
                }
            }

            is Commands.AddPaymentToAdvisory -> {
                val input = tx.inputsOfType<LOCState>().single()
                val output = tx.outputsOfType<LOCState>().single()
                requireThat {
                    "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                    "Beneficiary has not already been paid" using (input.advisoryPaid == false)
                    "Beneficiary is marked as being paid in output state" using (output.advisoryPaid == true)
                    "LOC must not be terminated" using (output.terminated == false)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddPaymentToIssuer -> {
                val input = tx.inputsOfType<LOCState>().single()
                val output = tx.outputsOfType<LOCState>().single()
                requireThat {
                    "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                    "Beneficiary has not already been paid" using (input.issuerPaid == false)
                    "Beneficiary is marked as being paid in output state" using (output.issuerPaid == true)
                    "LOC must not be terminated" using (output.terminated == false)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddPaymentToBeneficiary -> {
                val input = tx.inputsOfType<LOCState>().single()
                val output = tx.outputsOfType<LOCState>().single()
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