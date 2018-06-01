package eloc

import com.wildfire.contract.PledgeContract
import com.wildfire.flow.CashFlow.Issue
import com.wildfire.flow.PledgeFlow.InitiatePledge
import com.wildfire.state.PledgeState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.issuedBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import java.util.concurrent.atomic.AtomicInteger

private object CustomPortAllocation : PortAllocation() {
    private val index = AtomicInteger(0)

    private val portList = listOf(
            10002, 10003, 10040, 10004,
            10005, 10006, 10041, 10007,
            10008, 10009, 10042, 10010,
            10011, 10012, 10043, 10013,
            10014, 10015, 10044, 10016,
            10017, 10018, 10045, 10019)

    override fun nextPort(): Int {
        return portList[index.andIncrement]
    }
}

fun main(args: Array<String>) {
    driver(DriverParameters(
            portAllocation = CustomPortAllocation,
            startNodesInProcess = true,
            waitForAllNodesToFinish = true,
            extraCordappPackagesToScan = listOf("net.corda.finance.contracts.asset", "net.corda.finance.schemas", "com.wildfire.contract"),
            notarySpecs = listOf(NotarySpec(CordaX500Name.parse("O=Notary Pool,L=Sao Paolo,C=BR"), validating = false)),
            networkParameters = testNetworkParameters(maxTransactionSize = Int.MAX_VALUE)),
            dsl = {
                val rpcUserList = listOf(User("user1", "test", permissions = setOf("ALL")))

                val bankOne = startNode(providedName = CordaX500Name.parse("O=First Bank of London,L=London,C=GB"), rpcUsers = rpcUserList).get()
                startWebserver(bankOne)
                val bankTwo = startNode(providedName = CordaX500Name.parse("O=Shenzhen State Bank,L=Shenzhen,C=CN"), rpcUsers = rpcUserList).get()
                startWebserver(bankTwo)
                val buyer = startNode(providedName = CordaX500Name.parse("O=Analog Importers,L=Liverpool,C=GB"), rpcUsers = rpcUserList).get()
                startWebserver(buyer)
                val seller = startNode(providedName = CordaX500Name.parse("O=Lok Ma Exporters,L=Shenzhen,C=CN"), rpcUsers = rpcUserList).get()
                startWebserver(seller)
                val centralBank = startNode(providedName = CordaX500Name.parse("O=Central Bank,L=New York,C=US"), rpcUsers = rpcUserList).get()
                startWebserver(centralBank)

                val pledgeNonceOne = pledgeTenMillion(bankOne, centralBank)
                val pledgeNonceTwo = pledgeTenMillion(bankTwo, centralBank)

                confirmPledge(centralBank, pledgeNonceOne)
                confirmPledge(centralBank, pledgeNonceTwo)
            })
}

private fun pledgeTenMillion(nodeHandle: NodeHandle, centralBankHandle: NodeHandle): Long {
    val me = nodeHandle.nodeInfo.legalIdentities.first()
    val centralBank = centralBankHandle.nodeInfo.legalIdentities.first()

    val pledgeState = PledgeState(
            10000000.DOLLARS.issuedBy(me.ref(0.toByte())),
            listOf(centralBank, me),
            me)

    val transaction = nodeHandle.rpc
            .startFlow(::InitiatePledge, pledgeState)
            .returnValue
            .getOrThrow()

    return transaction.tx.outputsOfType<PledgeState>().single().nonce
}

private fun confirmPledge(centralBankHandle: NodeHandle, nonce: Long) {
    centralBankHandle.rpc
            .startFlow(::Issue, nonce.toString())
            .returnValue
            .getOrThrow()
}