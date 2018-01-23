package eloc.contract

import eloc.state.LOCApplicationProperties
import eloc.state.LOCApplicationState
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Letter of Credit Application
 */

class LOCApplication : Contract {

    companion object {
        @JvmStatic
        val LOC_APPLICATION_CONTRACT_ID = "eloc.contract.LOCApplication"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.ApplyForLOC -> {
                val output = tx.outputsOfType<LOCApplicationState>().single()
                requireThat {
                    // the issuing bank needs to be the owner of the state
                    //TODO: the next clause is causing some issue
                    "the owner must be the applicant" using (output.owner == output.props.applicant)
                    "there is no input state" using tx.inputStates.isEmpty()
                    "the output status must be pending issuer review" using (output.status.equals(Status.PENDING_ISSUER_REVIEW))
                    //TODO - sales contract attached
                    //TODO - purchase order attached
                    //TODO - application confirms to required template
                }
            }
        }
    }
    @CordaSerializable
    enum class Status {
        PENDING_ISSUER_REVIEW,
        PENDING_ADVISORY_REVIEW,
        APPROVED,
        REJECTED,
    }

    fun generateApply(props: LOCApplicationProperties, notary: Party, purchaseOrder: Attachment?): TransactionBuilder {
        val state = LOCApplicationState(props.applicant, props.issuer, Status.PENDING_ISSUER_REVIEW, props, purchaseOrder)
        return TransactionBuilder( notary = notary ).withItems(StateAndContract(state, LOC_APPLICATION_CONTRACT_ID), Command(Commands.ApplyForLOC(), props.issuer.owningKey))
    }

    interface Commands : CommandData {
        class ApplyForLOC : TypeOnlyCommandData(), Commands
        class Approve : TypeOnlyCommandData(), Commands
    }
}