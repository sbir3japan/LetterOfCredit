package eloc.flow

import eloc.LetterOfCreditDataStructures
import eloc.flow.documents.InvoiceFlow
import eloc.state.*
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.contracts.StateRef
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
 * Created by nitesh on 17-05-2018.
 */

class ApproveLocForLegitimateInvoice {
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
    fun `createTradeAndApproveLoc`() {
        val invoice = InvoiceProperties(
                invoiceID = "test",
                seller = LetterOfCreditDataStructures.Company("sellerName", "sellerAddress", ""),
                buyer = LetterOfCreditDataStructures.Company("buyerName", "buyerAddress", ""),
                invoiceDate = LocalDate.now(),
                term = 1,
                attachmentHash = SecureHash.randomSHA256(),
                goods = listOf(LetterOfCreditDataStructures.PricedGood("goodsDescription", "goodsPurchaseOrderRef", 1,
                        1.DOLLARS, LetterOfCreditDataStructures.Weight(3.toDouble(),
                        LetterOfCreditDataStructures.WeightUnit.KG))))

        val state = InvoiceState(issuerNode.info.legalIdentities.first(), buyerNode.info.legalIdentities.first(), true, true, invoice)

        val future = issuerNode.startFlow(InvoiceFlow.UploadAndSend(buyerNode.info.legalIdentities.first(), state))
                .toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()

        val invoiceState = buyerNode.transaction {
            buyerNode.services.vaultService.queryBy<InvoiceState>().states
        }

        val application = makeApplication(issuerNode.info.legalIdentities.first(), invoiceState[0].ref)

        val future1 = buyerNode.startFlow(LOCApplicationFlow.Apply(application)).toCompletableFuture()
        net.runNetwork()
        future1.getOrThrow()

        val locApp = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LetterOfCreditApplicationState>().states
        }

        val invoice_state = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<InvoiceState>().states
        }
        assert(locApp.count() > 0)

        val approvalFuture = issuerNode.startFlow(LOCApprovalFlow.Approve(locApp.first().ref, invoice_state.first().ref)).toCompletableFuture()
        net.runNetwork()
        approvalFuture.getOrThrow()

        val locState = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LetterOfCreditState>().states
        }

        assert(locState.count() > 0)

    }

    private fun makeApplication(issuer: Party, invoiceState: StateRef): LetterOfCreditApplicationState {

        val applicationProps = LetterOfCreditApplicationProperties(
                letterOfCreditApplicationID = "LOC01",
                applicationDate = LocalDate.of(2016, 5, 15),
                typeCredit = LetterOfCreditDataStructures.CreditType.SIGHT,
                amount = 100000.DOLLARS,
                expiryDate = LocalDate.of(2017, 12, 14),
                portLoading = LetterOfCreditDataStructures.Port("SG", "Singapore", null, null, null),
                portDischarge = LetterOfCreditDataStructures.Port("US", "Oakland", null, null, null),
                descriptionGoods = listOf(LetterOfCreditDataStructures.PricedGood(description = "Tiger balm",
                        quantity = 10000,
                        grossWeight = null,
                        unitPrice = Amount(1, Currency.getInstance("USD")),
                        purchaseOrderRef = null
                )),
                placePresentation = LetterOfCreditDataStructures.Location("US", "California", "Oakland"),
                lastShipmentDate = LocalDate.of(2016, 6, 12), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(31),
                beneficiary = buyerNode.info.legalIdentities.first(),
                issuer = issuer,
                applicant = buyerNode.info.legalIdentities.first(),
                advisingBank = advisingBankNode.info.legalIdentities.first(),
                invoiceRef = invoiceState
        )

        val application = LetterOfCreditApplicationState(
                owner = buyerNode.info.legalIdentities.first(),
                issuer = issuer,
                status = LetterOfCreditApplicationStatus.PENDING_ISSUER_REVIEW,
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