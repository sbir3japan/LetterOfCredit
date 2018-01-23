/*package eloc.flow

import eloc.api.LocApp
import eloc.contract.LOCApplication
import eloc.contract.LocDataStructures
import eloc.flow.documents.InvoiceFlow.ReceiveInvoice
import eloc.flow.LOCApplicationFlow.Apply
import eloc.flow.documents.InvoiceFlow
import eloc.state.InvoiceProperties
import eloc.state.InvoiceState
import eloc.state.LOCApplicationProperties
import eloc.state.LOCApplicationState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash.Companion.randomSHA256
import net.corda.core.getOrThrow
import net.corda.core.node.services.unconsumedStates
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Period
import java.util.*
import kotlin.test.assertEquals

/**
 * Started creating this flow based of the original eloc flow - originally the buyer & buyer had a nodes and negotiated an invoice.
 * For now it is here for reference but may be deleted and was only partly implemented and tested - only the invoice creation is completed.
 **/
class SellerFlowTest {

    lateinit var net: MockNetwork
    lateinit var issuingBank : MockNetwork.MockNode
    lateinit var confirmingBank: MockNetwork.MockNode
    lateinit var applicant: MockNetwork.MockNode
    lateinit var seller: MockNetwork.MockNode
    lateinit var carrier: MockNetwork.MockNode
    lateinit var notary: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork()
        val nodes = net.createSomeNodes(5)
        issuingBank = nodes.partyNodes[0]
        confirmingBank = nodes.partyNodes[1]
        carrier = nodes.partyNodes[2]
        applicant = nodes.partyNodes[3]
        seller = nodes.partyNodes[4]
        notary = nodes.notaryNode
        net.runNetwork()
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

    val invoiceProperties =
            InvoiceProperties(
                    invoiceID = "123",
                    seller = LocDataStructures.Company(
                            name = "Mega Corp LTD.",
                            address = "123 Main St. Awesome Town, ZZ 11111",
                            phone = null
                    ),
                    buyer = LocDataStructures.Company(
                            name = "Sandworm Imports",
                            address = "555 Elm St. Little Town, VV, 22222",
                            phone = null
                    ),
                    invoiceDate = LocalDate.now(),
                    term = 60,
                    goods = arrayListOf(
                            LocDataStructures.PricedGood(
                                    description = "Salt",
                                    purchaseOrderRef = null,
                                    quantity = 10,
                                    unitPrice = 3.DOLLARS,
                                    grossWeight = null
                            ),
                            LocDataStructures.PricedGood(
                                    description = "Pepper",
                                    purchaseOrderRef = null,
                                    quantity = 20,
                                    unitPrice = 4.DOLLARS,
                                    grossWeight = null
                            )
                    ),
                    attachmentHash = randomSHA256()
            )

    @Test
    fun `lets trade`() {
        // invoice to start with - both the buyer and buyer apparently have a copy they negotiated - here we just use the same one
        val initialState = InvoiceState(seller.info.legalIdentity, applicant.info.legalIdentity, false, invoiceProperties)

        val sellerFlow = Seller.sellerGatheringDocs( issuingBank.info.legalIdentity, carrier.info.legalIdentity, issuingBank.info,
                                                applicant.info, notary.info, seller.services.legalIdentityKey, seller.info, initialState)

        //applicant needs to register the flow we are getting from the buyer (the applicant flow is triggered once the buyer sends)

        //val applicantFlow = Applicant.ApplicantFlow( applicant.services.legalIdentityKey, buyer.info.legalIdentity, initialState, notary.info.legalIdentity )

        //buyer fires off and sends the invoice to buyer
        val future = seller.services.startFlow(sellerFlow).resultFuture
        net.runNetwork()
        val result = future.getOrThrow()

        assert( true )
    }

    @Test
    fun `send invoice`() {
        // register response flow for applicant
        applicant.services.registerFlowInitiator(InvoiceFlow.SendInvoice::class.java, ::ReceiveInvoice)

        // invoice to start with - both the buyer and buyer apparently have a copy they negotiated - here we just use the same one
        val initialState = InvoiceState(seller.info.legalIdentity, applicant.info.legalIdentity, false, invoiceProperties)

        // kick off flow
        val sellerFlow = InvoiceFlow.SendInvoice(applicant.info.legalIdentity, initialState)

        val future = seller.services.startFlow(sellerFlow).resultFuture
        net.runNetwork()
        val result = future.getOrThrow()

        val sellerId = seller.services.vaultService.unconsumedStates<InvoiceState>().first().state.data.props.invoiceID
        val applicantId = applicant.services.vaultService.unconsumedStates<InvoiceState>().first().state.data.props.invoiceID

        assertEquals("123", sellerId)
        assertEquals("123", applicantId)
    }

    @Test
    fun applyForLocTest()
    {
        val loc = LocApp("test", "12-12-2017", "SIGHT", 100,"USD","12-12-2018", "USA", "City",
                "Address","USA","City","Address","Description",100,100,
                "G",50,"Test","USA","State","City","12-12-2018",
                1,"Mock Company 6","Mock Company 2","Mock Company 5","Mock Company 3")
        val invoiceRef = StateRef(randomSHA256(),0)
        // buyer's bank
        val issuerIdentity = issuingBank.services.myInfo.legalIdentity
        // buyer
        val beneficiaryIdentity = seller.services.identityService.partyFromName(loc.beneficiary) ?: throw RuntimeException("$loc.beneficiary not found.")
        // buyer
        val applicantIdentity = applicant.services.identityService.partyFromName(loc.applicant) ?: throw RuntimeException("$loc.applicant not found.")
        // buyer's bank
        val advisingBankIdentity = confirmingBank.services.identityService.partyFromName(loc.advisingBank) ?: throw RuntimeException("$loc.advisingBank not found.")

        val applicationProps = LOCApplicationProperties (
                letterOfCreditApplicationID = "LOC01",
                applicationDate = LocalDate.now(),
                typeCredit = LocDataStructures.CreditType.valueOf(loc.typeCredit),
                amount = Amount(loc.amount.toLong(), Currency.getInstance(loc.currency)),
                expiryDate = LocalDate.now(),
                portLoading = LocDataStructures.Port(loc.portLoadingCountry,loc.portLoadingCity,loc.portLoadingAddress,null,null),
                portDischarge = LocDataStructures.Port(loc.portDischargeCountry,loc.portDischargeCity,loc.portDischargeAddress,null,null),
                goods = listOf(LocDataStructures.PricedGood(description=loc.goodsDescription,
                        quantity = loc.goodsQuantity,
                        grossWeight = LocDataStructures.Weight(loc.goodsWeight.toDouble(),LocDataStructures.WeightUnit.valueOf(loc.goodsWeightUnit)),
                        unitPrice = Amount(loc.goodsUnitPrice.toLong(), Currency.getInstance(loc.currency)),
                        purchaseOrderRef = null
                )),
                placePresentation = LocDataStructures.Location(loc.placePresentationCountry,loc.placePresentationState,loc.placePresentationCity),
                lastShipmentDate = LocalDate.now(), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(loc.periodPresentation),
                beneficiary = beneficiaryIdentity,
                issuer = issuerIdentity,
                applicant = applicantIdentity,
                advisingBank = advisingBankIdentity,
                invoiceRef = StateRef(randomSHA256(),0)
        )

        val application = LOCApplicationState(
                owner = issuingBank.services.myInfo.legalIdentity,
                status = LOCApplication.Status.PENDING_ISSUER_REVIEW,
                props = applicationProps,
                purchaseOrder = null
        )

        println("Starting flow")
        val future = seller.services.startFlow(Apply(application)).resultFuture
        net.runNetwork()
        val result = future.getOrThrow()
        net.runNetwork()
    }
}
*/