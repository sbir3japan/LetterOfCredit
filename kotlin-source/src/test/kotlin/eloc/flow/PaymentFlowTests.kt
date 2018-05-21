package eloc.flow

import eloc.LetterOfCreditDataStructures.Company
import eloc.LetterOfCreditDataStructures.CreditType
import eloc.LetterOfCreditDataStructures.Good
import eloc.LetterOfCreditDataStructures.Location
import eloc.LetterOfCreditDataStructures.Person
import eloc.LetterOfCreditDataStructures.Port
import eloc.LetterOfCreditDataStructures.PricedGood
import eloc.LetterOfCreditDataStructures.Weight
import eloc.LetterOfCreditDataStructures.WeightUnit
import eloc.flow.documents.BillOfLadingFlow
import eloc.flow.loc.AdvisoryPaymentFlow
import eloc.flow.loc.IssuerPaymentFlow
import eloc.flow.loc.SellerPaymentFlow
import eloc.state.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.*

class PaymentFlowTests {
    lateinit var net: MockNetwork
    private lateinit var applicantNode: StartedMockNode
    private lateinit var issuerNode: StartedMockNode
    private lateinit var beneficiaryNode: StartedMockNode
    private lateinit var advisingBankNode: StartedMockNode
    private lateinit var notary: StartedMockNode

    @Before
    fun setup() {
        net = MockNetwork(listOf("eloc.contract", "net.corda.finance.contracts.asset"))

        applicantNode = net.createNode()
        issuerNode = net.createNode()
        beneficiaryNode = net.createNode()
        advisingBankNode = net.createNode()
        notary = net.defaultNotaryNode

        val nodes = listOf(applicantNode, issuerNode, beneficiaryNode, advisingBankNode)

        nodes.forEach {
            it.registerInitiatedFlow(AdvisoryPaymentFlow.ReceivePayment::class.java)
            it.registerInitiatedFlow(IssuerPaymentFlow.ReceivePayment::class.java)
            it.registerInitiatedFlow(SellerPaymentFlow.ReceivePayment::class.java)
        }

        net.runNetwork()
    }

    @Test
    fun `make payment to seller`() {

        val futureApp = applicantNode.startFlow(LOCApplicationFlow.Apply(locApplicationState())).toCompletableFuture()
        net.runNetwork()
        val resultApp = futureApp.getOrThrow()

        // Run approval flow to create LetterOfCreditState from application
        val stateAndRef = resultApp.tx.outRef<LetterOfCreditApplicationState>(0)
        val futureApproval = issuerNode.startFlow(LOCApprovalFlow.Approve(reference = stateAndRef.ref)).toCompletableFuture()
        net.runNetwork()
        futureApproval.getOrThrow()

        val futureBol = beneficiaryNode.startFlow(BillOfLadingFlow.UploadAndSend(billOfLadingState())).toCompletableFuture()
        net.runNetwork()
        futureBol.getOrThrow()

        val futureCash = advisingBankNode.startFlow(SelfIssueCashFlow(1000000000.DOLLARS)).toCompletableFuture()
        net.runNetwork()
        futureCash.getOrThrow()

        val future = advisingBankNode.startFlow(SellerPaymentFlow.MakePayment(locId = stateAndRef.state.data.props.letterOfCreditApplicationID)).toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()

        val issuerState = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LetterOfCreditState>().states.first()
        }

        val advisingState = advisingBankNode.transaction {
            advisingBankNode.services.vaultService.queryBy<LetterOfCreditState>().states.first()
        }

        Assert.assertTrue(issuerState.state.data.status == LetterOfCreditStatus.BENEFICIARY_PAID)
        Assert.assertTrue(advisingState.state.data.status == LetterOfCreditStatus.BENEFICIARY_PAID)
    }

    @Test
    fun `make payment to advisory`() {

        val futureApp = applicantNode.startFlow(LOCApplicationFlow.Apply(locApplicationState())).toCompletableFuture()
        net.runNetwork()
        val resultApp = futureApp.getOrThrow()

        // Run approval flow to create LetterOfCreditState from application
        val stateAndRef = resultApp.tx.outRef<LetterOfCreditApplicationState>(0)
        val futureApproval = issuerNode.startFlow(LOCApprovalFlow.Approve(reference = stateAndRef.ref)).toCompletableFuture()
        net.runNetwork()
        futureApproval.getOrThrow()

        val futureBol = beneficiaryNode.startFlow(BillOfLadingFlow.UploadAndSend(billOfLadingState())).toCompletableFuture()
        net.runNetwork()
        futureBol.getOrThrow()

        val futureCash = issuerNode.startFlow(SelfIssueCashFlow(1000000000.DOLLARS)).toCompletableFuture()
        net.runNetwork()
        futureCash.getOrThrow()

        val future = issuerNode.startFlow(AdvisoryPaymentFlow.MakePayment(locId = stateAndRef.state.data.props.letterOfCreditApplicationID)).toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()

        val issuerState = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LetterOfCreditState>().states.first()
        }

        val advisingState = advisingBankNode.transaction {
            advisingBankNode.services.vaultService.queryBy<LetterOfCreditState>().states.first()
        }

        Assert.assertTrue(issuerState.state.data.status == LetterOfCreditStatus.ADVISORY_PAID)
        Assert.assertTrue(advisingState.state.data.status == LetterOfCreditStatus.ADVISORY_PAID)
    }

    @Test
    fun `make payment to issuer`() {

        val futureApp = applicantNode.startFlow(LOCApplicationFlow.Apply(locApplicationState())).toCompletableFuture()
        net.runNetwork()
        val resultApp = futureApp.getOrThrow()

        // Run approval flow to create LetterOfCreditState from application
        val stateAndRef = resultApp.tx.outRef<LetterOfCreditApplicationState>(0)
        val futureApproval = issuerNode.startFlow(LOCApprovalFlow.Approve(reference = stateAndRef.ref)).toCompletableFuture()
        net.runNetwork()
        futureApproval.getOrThrow()

        val futureBol = beneficiaryNode.startFlow(BillOfLadingFlow.UploadAndSend(billOfLadingState())).toCompletableFuture()
        net.runNetwork()
        futureBol.getOrThrow()

        val futureCash = applicantNode.startFlow(SelfIssueCashFlow(1000000000.DOLLARS)).toCompletableFuture()
        net.runNetwork()
        futureCash.getOrThrow()

        val future = applicantNode.startFlow(IssuerPaymentFlow.MakePayment(locId = stateAndRef.state.data.props.letterOfCreditApplicationID)).toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()

        val issuerState = issuerNode.transaction {
            issuerNode.services.vaultService.queryBy<LetterOfCreditState>().states.first()
        }

        val advisingState = advisingBankNode.transaction {
            advisingBankNode.services.vaultService.queryBy<LetterOfCreditState>().states.first()
        }


        Assert.assertTrue(issuerState.state.data.status == LetterOfCreditStatus.ISSUER_PAID)
        Assert.assertTrue(advisingState.state.data.status == LetterOfCreditStatus.ISSUER_PAID)
    }

    private fun locApplicationState() : LetterOfCreditApplicationState {
        val properties = LetterOfCreditApplicationProperties (
                letterOfCreditApplicationID = "LOC01",
                applicationDate = LocalDate.of(2016,5,15),
                typeCredit = CreditType.SIGHT,
                amount = 100000.DOLLARS,
                expiryDate = LocalDate.of(2017,12,14),
                portLoading = Port("SG","Singapore",null,null,null),
                portDischarge = Port("US","Oakland",null,null,null),
                descriptionGoods = listOf(PricedGood(description="Tiger balm",
                        quantity = 10000,
                        grossWeight = null,
                        unitPrice = Amount(1, Currency.getInstance("USD")),
                        purchaseOrderRef = null
                )),
                placePresentation = Location("US","California","Oakland"),
                lastShipmentDate = LocalDate.of(2016,6,12), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(31),
                beneficiary = beneficiaryNode.info.chooseIdentity(),
                issuer = issuerNode.info.chooseIdentity(),
                applicant = applicantNode.info.chooseIdentity(),
                advisingBank = advisingBankNode.info.chooseIdentity(),
                invoiceRef = StateRef(SecureHash.Companion.randomSHA256(),0)
        )

        return LetterOfCreditApplicationState(properties.applicant, properties.issuer, LetterOfCreditApplicationStatus.PENDING_ISSUER_REVIEW, properties, null)
    }

    private fun billOfLadingState() : BillOfLadingState {
        val properties = BillOfLadingProperties (
                billOfLadingID = "LOC01",
                issueDate = LocalDate.of(2017,12,14),
                carrierOwner = beneficiaryNode.info.chooseIdentity(),
                nameOfVessel = "CordaShip",
                descriptionOfGoods = listOf(Good(description="Crab meet cans",quantity = 10000,grossWeight = null)),
                portOfLoading = Port(country = "Morokko",city = "Larache",address = null,state = null,name=null),
                portOfDischarge = Port(country = "Belgium",city = "Antwerpen",address = null,state = null,name=null),
                grossWeight = Weight(quantity =  2500.0, unit = WeightUnit.KG),
                dateOfShipment = LocalDate.of(2016,5,15),
                shipper = Company(name = "Some company", address = "Some other address", phone = "+11 12345678"),
                notify = Person(name = "Some guy", address = "Some address", phone = "+11 23456789"),
                consignee = Company(name = "Some company", address = "Some other address", phone = "+11 12345678"),
                placeOfReceipt = Location(country = "USA", state ="Iowa", city = "Des Moines")
        )

        return BillOfLadingState(beneficiaryNode.info.chooseIdentity(),applicantNode.info.chooseIdentity(),advisingBankNode.info.chooseIdentity(),issuerNode.info.chooseIdentity(), Instant.now(), properties)
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

}