package eloc.flow

import eloc.LetterOfCreditDataStructures.Company
import eloc.LetterOfCreditDataStructures.CreditType.SIGHT
import eloc.LetterOfCreditDataStructures.Location
import eloc.LetterOfCreditDataStructures.Port
import eloc.LetterOfCreditDataStructures.PricedGood
import eloc.LetterOfCreditDataStructures.Weight
import eloc.LetterOfCreditDataStructures.WeightUnit.KG
import eloc.state.InvoiceProperties
import eloc.state.LetterOfCreditApplicationProperties
import eloc.state.LetterOfCreditApplicationState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Period

class GoldenPath {
    private lateinit var network: MockNetwork
    private lateinit var seller: StartedMockNode
    private lateinit var buyer: StartedMockNode
    private lateinit var issuingBank: StartedMockNode
    private lateinit var advisingBank: StartedMockNode
    private lateinit var letterOfCreditApplicationProperties: LetterOfCreditApplicationProperties

    @Before
    fun setup() {
        network = MockNetwork(listOf("eloc.contract", "net.corda.finance.contracts.asset"))
        seller = network.createPartyNode()
        buyer = network.createPartyNode()
        issuingBank = network.createPartyNode()
        advisingBank = network.createPartyNode()
        network.runNetwork()

        letterOfCreditApplicationProperties = LetterOfCreditApplicationProperties(
                letterOfCreditApplicationID = invoiceProperties.invoiceID,
                applicationDate = LocalDate.now(),
                typeCredit = SIGHT,
                issuer = issuingBank.party,
                beneficiary = seller.party,
                applicant = buyer.party,
                advisingBank = advisingBank.party,
                expiryDate = LocalDate.MAX,
                portLoading = Port("CH", "Shenzhen", "The Port", null, null),
                portDischarge = Port("US", "Des Moines", "3 Sea Way", null, null),
                placePresentation = Location("US", "Des Moines", "Des Moines"),
                lastShipmentDate = LocalDate.MAX,
                periodPresentation = Period.ofDays(1),
                descriptionGoods = listOf(
                        PricedGood(
                                "OLED 6\" Screens",
                                invoiceProperties.invoiceID,
                                10000,
                                400.DOLLARS,
                                Weight(30.0, KG)
                        )
                ),
                documentsRequired = listOf(),
                amount = 30000.DOLLARS
        )
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    private val StartedMockNode.party: Party
        get() = info.legalIdentities.first()

    private fun StartedMockNode.runFlow(flow: FlowLogic<SignedTransaction>): SignedTransaction {
        val future = startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }

    // TODO: Update these to reflect latest front-end autocomplete.
    private val invoiceProperties = InvoiceProperties(
            invoiceID = "123",
            seller = Company("Lok Ma Exporters", "123 Main St. Shenzhen, China", ""),
            buyer = Company("Analog Importers", "123 Street. Iowa, US", ""),
            invoiceDate = LocalDate.now(),
            attachmentHash = SecureHash.randomSHA256(),
            term = 5,
            goods = listOf(
                    PricedGood(
                            "OLED 6\" Screens",
                            "Mock1",
                            10000,
                            3.DOLLARS,
                            Weight(30.0, KG)
                    )
            )
    )

    @Test
    fun `travel golden path`() {
        // Creating the invoice.
        val flow = CreateInvoiceFlow(buyer.party.name.toString(), invoiceProperties)
        seller.runFlow(flow)

        // Applying for the letter of credit.
        val applicationState = LetterOfCreditApplicationState(
                buyer.party,
                issuingBank.party,
                letterOfCreditApplicationProperties)
        val flow2 = ApplyForLoCFlow(applicationState)
        val applicationTx = buyer.runFlow(flow2)

        // Approving the letter of credit.
        // TODO: Too brittle. Retrieve the state ref from the vault.
        val applicationStateRef = StateRef(applicationTx.id, 0)
        val flow3 = ApproveLoCFlow(applicationStateRef, TODO())
        issuingBank.runFlow(flow3)

        // Adding the bill of lading.
        TODO()

        // Shipping the order.
        TODO()

        // Paying the seller.
        TODO()

        // Paying the advising bank.
        TODO()

        // Paying the issuing bank.
        TODO()
    }
}