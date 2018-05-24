package eloc

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_NOTARY_NAME
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
            notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false))),
            dsl = {
                val rpcUserList = listOf(User("user1", "test", permissions = setOf("ALL")))

                val nodeNames = listOf(
                        CordaX500Name("First Bank of London", "London", "GB"),
                        CordaX500Name("Shenzhen State Bank", "Shenzhen", "CN"),
                        CordaX500Name("Analog Importers", "London", "FR"),
                        CordaX500Name("Lok Ma Exporters", "Shenzhen", "HK"),
                        CordaX500Name("Federal Reserve", "New York", "US"))

                nodeNames.forEach { name ->
                    val node = startNode(providedName = name, rpcUsers = rpcUserList).getOrThrow()
                    startWebserver(node)
                }
            })
}