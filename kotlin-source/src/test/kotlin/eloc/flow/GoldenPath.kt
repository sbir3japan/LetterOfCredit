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
import net.corda.core.identity.CordaX500Name
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

    @Before
    fun setup() {
        network = MockNetwork(listOf("eloc.contract", "net.corda.finance.contracts.asset"))
        seller = network.createPartyNode(CordaX500Name.parse("O=Lok Ma Exporters,L=Shenzhen,C=CN"))
        buyer = network.createPartyNode(CordaX500Name.parse("O=Analog Importers,L=Liverpool,C=GB"))
        issuingBank = network.createPartyNode(CordaX500Name.parse("O=First Bank of London,L=London,C=GB"))
        advisingBank = network.createPartyNode(CordaX500Name.parse("O=Shenzhen State Bank,L=Shenzhen,C=CN"))
        network.runNetwork()
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

    private val invoiceProperties = InvoiceProperties(
            invoiceID = "123",
            seller = Company("Lok Ma Exporters", "123 Main St. Shenzhen, China", ""),
            buyer = Company("Analog Importers", "123 Street. Iowa, US", ""),
            invoiceDate = LocalDate.now(),
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

    private val letterOfCreditApplicationProperties = LetterOfCreditApplicationProperties(
            letterOfCreditApplicationID = invoiceProperties.invoiceID,
            applicationDate = LocalDate.now(),
            typeCredit = SIGHT,
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

    @Test
    fun `travel golden path`() {
        // Creating the invoice.
        val flow = CreateInvoiceFlow(buyer.party.name.organisation, invoiceProperties)
        seller.runFlow(flow)

        // Applying for the letter of credit.
        val applicationState = LetterOfCreditApplicationState(
                buyer.party,
                issuingBank.party,
                seller.party,
                advisingBank.party,
                letterOfCreditApplicationProperties)
        val flow2 = ApplyForLoCFlow(applicationState)
        buyer.runFlow(flow2)

        // Approving the letter of credit.
        val flow3 = ApproveLoCFlow(letterOfCreditApplicationProperties.letterOfCreditApplicationID)
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