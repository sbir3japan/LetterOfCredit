package eloc

import eloc.flow.SelfIssueCashFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.driver.DriverParameters
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
            extraCordappPackagesToScan = listOf("net.corda.finance.contracts.asset", "net.corda.finance.schemas"),
            notarySpecs = listOf(NotarySpec(CordaX500Name.parse("O=Notary Pool,L=London,C=BR"), validating = false))),
            dsl = {
                val rpcUserList = listOf(User("user1", "test", permissions = setOf("ALL")))

                // These two bank nodes are pre-issued cash.
                val bankNodeNames = listOf("O=First Bank of London,L=London,C=GB", "O=Shenzhen State Bank,L=Shenzhen,C=CN")
                bankNodeNames.forEach { name ->
                    val node = startNode(providedName = CordaX500Name.parse(name), rpcUsers = rpcUserList).get()
                    node.rpc.startFlow(::SelfIssueCashFlow, 10000000.DOLLARS).returnValue.get()
                    startWebserver(node)
                }

                val regularNodeNames = listOf("O=Analog Importers,L=London,C=FR", "O=Lok Ma Exporters,L=Shenzhen,C=HK", "O=Central Bank of Corda,L=New York,C=US")
                regularNodeNames.forEach { name ->
                    val node = startNode(providedName = CordaX500Name.parse(name), rpcUsers = rpcUserList).get()
                    startWebserver(node)
                }
            })
}