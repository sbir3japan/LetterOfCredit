package eloc.flow.documents

import co.paralleluniverse.fibers.Suspendable
import eloc.state.BillofLadingState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

@StartableByRPC
class BillOfLadingTimeline(val ref: String) : FlowLogic<List<Pair<Party, String>>>() {
    @Suspendable
    override fun call(): List<Pair<Party, String>> {

        var priorStates = serviceHub.vaultService.queryBy<BillofLadingState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)).states.filter { it.state.data.props.billOfLadingID == ref }.map {
            Pair(it.state.data.owner, convertDate(it.state.data.timestamp))
        }
        val currentState = serviceHub.vaultService.queryBy<BillofLadingState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)).states.filter { it.state.data.props.billOfLadingID == ref }.map {
            Pair(it.state.data.owner, convertDate(it.state.data.timestamp))
        }

        return priorStates.union(currentState).toList()
    }

    private fun convertDate(instant: Instant): String {
        val date = Date.from(instant)
        val formatter = SimpleDateFormat("dd MM yyyy HH:mm:ss")
        return formatter.format(date)
    }
}



