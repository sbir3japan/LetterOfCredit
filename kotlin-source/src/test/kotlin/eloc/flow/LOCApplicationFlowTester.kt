package eloc.flow

import eloc.contract.LOCApplication
import eloc.contract.LocDataStructures
import eloc.flow.documents.InvoiceFlow
import eloc.state.*
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
 * Created by natixis on 4/12/17.
 */

class LOCApplicationFlowTester {
    lateinit var net: MockNetwork
    lateinit var issuerNode: StartedMockNode
    lateinit var buyerNode: StartedMockNode
    lateinit var advisingBankNode: StartedMockNode

    @Before
    fun setup() {
        net = MockNetwork(listOf("eloc.contract", "net.corda.finance.contracts.asset"))
        issuerNode = net.createNode()
        buyerNode = net.createNode()
        advisingBankNode = net.createNode()

        val nodes = listOf(issuerNode, buyerNode, advisingBankNode)

        nodes.forEach {
            it.registerInitiatedFlow(InvoiceFlow.ReceiveInvoice::class.java)
        }

        net.runNetwork()
    }

    @Test
    fun `create trade`() {

        println("Starting creation of trade")

        val invoice = InvoiceProperties(
                invoiceID = "test",
                seller = LocDataStructures.Company("sellerName", "sellerAddress", ""),
                buyer = LocDataStructures.Company("buyerName", "buyerAddress", ""),
                invoiceDate = LocalDate.now(),
                term = 1,
                attachmentHash = SecureHash.randomSHA256(),
                goods = listOf(LocDataStructures.PricedGood("goodsDescription", "goodsPurchaseOrderRef", 1,
                        1.DOLLARS, LocDataStructures.Weight(3.toDouble(),
                        LocDataStructures.WeightUnit.KG))))

        val state = InvoiceState(issuerNode.info.legalIdentities.first(), buyerNode.info.legalIdentities.first(), true, invoice)
        if (state.assigned) println("State created")

        println("Starting flow")
        val future = issuerNode.startFlow(InvoiceFlow.UploadAndSend(buyerNode.info.legalIdentities.first(), state))
                .toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()
        println("Ending flow")
    }

    @Test
    fun `record LOC application`() {

        val application = makeApplication(issuerNode.info.legalIdentities.first())

        val future = buyerNode.startFlow(LOCApplicationFlow.Apply(application)).toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()

        listOf(buyerNode, issuerNode).forEach { node ->
            val locStates = node.transaction {
                node.services.vaultService.queryBy<LOCApplicationState>().states
            }
            assert(locStates.count() > 0)
        }
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
                descriptionGoods = listOf(LocDataStructures.PricedGood(description = "Tiger balm",
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
                status = LOCApplicationStatus.PENDING_ISSUER_REVIEW,
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