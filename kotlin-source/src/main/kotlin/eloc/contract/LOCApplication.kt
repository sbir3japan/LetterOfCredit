package eloc.contract

import eloc.state.LOCApplicationState
import eloc.state.LOCApplicationStatus
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

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
                    "the owner must be the applicant" using (output.owner == output.props.applicant)
                    "there is no input state" using tx.inputStates.isEmpty()
                    "the output status must be pending issuer review" using (output.status == LOCApplicationStatus.PENDING_ISSUER_REVIEW)
                }
            }
        }
    }

    interface Commands : CommandData {
        class ApplyForLOC : TypeOnlyCommandData(), Commands
        class Approve : TypeOnlyCommandData(), Commands
    }
}