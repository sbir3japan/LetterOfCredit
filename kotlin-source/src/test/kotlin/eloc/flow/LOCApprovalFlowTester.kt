package eloc.flow

import eloc.contract.LOCApplication
import eloc.contract.LocDataStructures
import eloc.state.LOCApplicationProperties
import eloc.state.LOCApplicationState
import eloc.state.LOCState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Period
import java.util.*

/**
 * Created by natixis on 4/13/17.
 */

class LOCApprovalFlowTester {
    lateinit var net: MockNetwork
    lateinit var buyerNode: StartedMockNode
    lateinit var issuerNode: StartedMockNode
    lateinit var advisingBankNode: StartedMockNode

    @Before
    fun setup() {
        net = MockNetwork(listOf("eloc.contract", "net.corda.finance.contracts.asset"))
        issuerNode = net.createNode()
        buyerNode = net.createNode()
        advisingBankNode = net.createNode()
        net.runNetwork()
    }

    @Test
    fun `approve LOC application`() {
        val application = makeApplication(issuerNode.info.legalIdentities.first())

        val appFuture = buyerNode.startFlow(LOCApplicationFlow.Apply(application)).toCompletableFuture()
        net.runNetwork()
        appFuture.getOrThrow()

        val locApp = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LOCApplicationState>().states
        }

        assert(locApp.count() > 0)

        val approvalFuture = issuerNode.startFlow(LOCApprovalFlow.Approve(locApp.first().ref)).toCompletableFuture()
        net.runNetwork()
        approvalFuture.getOrThrow()

        val locState = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LOCState>().states
        }

        assert(locState.count() > 0)
    }

    private fun makeApplication(issuer: Party): LOCApplicationState {
        val applicationProps = LOCApplicationProperties(
                letterOfCreditApplicationID = "LOC01",
                applicationDate = LocalDate.of(2016, 5, 15),
                typeCredit = LocDataStructures.CreditType.SIGHT,
                amount = 100000.DOLLARS,
                expiryDate = LocalDate.of(2017, 12, 14),
                portLoading = LocDataStructures.Port("SG", "Singapore", null, null, null),
                portDischarge = LocDataStructures.Port("US", "Oakland", null, null, null),
                goods = listOf(LocDataStructures.PricedGood(description = "Tiger balm",
                        quantity = 10000,
                        grossWeight = null,
                        unitPrice = Amount(1, Currency.getInstance("USD")),
                        purchaseOrderRef = null
                )),
                placePresentation = LocDataStructures.Location("US", "California", "Oakland"),
                lastShipmentDate = LocalDate.of(2016, 6, 12), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(31),
                beneficiary = buyerNode.info.legalIdentities.first(),
                issuer = issuer,
                applicant = buyerNode.info.legalIdentities.first(),
                advisingBank = advisingBankNode.info.legalIdentities.first(),
                invoiceRef = StateRef(SecureHash.Companion.randomSHA256(), 0)
        )

        val application = LOCApplicationState(
                owner = buyerNode.info.legalIdentities.first(),
                issuer = issuer,
                status = LOCApplication.Status.PENDING_ISSUER_REVIEW,
                props = applicationProps,
                purchaseOrder = null
        )
        return application
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }
}