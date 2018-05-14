package eloc.state

import eloc.LetterOfCreditDataStructures.Port
import eloc.LetterOfCreditDataStructures.CreditType
import eloc.LetterOfCreditDataStructures.PricedGood
import eloc.LetterOfCreditDataStructures.Location
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.time.Period
import java.util.*

data class LetterOfCreditState(
        val beneficiaryPaid: Boolean,
        val advisoryPaid: Boolean,
        val issuerPaid: Boolean,
        val issued: Boolean,
        val shipped: Boolean,
        val terminated: Boolean,
        val props: LetterOfCreditProperties) : LinearState {

    override val linearId = UniqueIdentifier(props.letterOfCreditID)

    override val participants get() = listOf(props.beneficiary, props.advisingBank, props.issuingBank, props.applicant)

    fun beneficiaryPaid(): LetterOfCreditState = copy(beneficiaryPaid = true)
    fun issuerPaid(): LetterOfCreditState = copy(issuerPaid = true)
    fun advisoryPaid(): LetterOfCreditState = copy(advisoryPaid = true)
    fun shipped(): LetterOfCreditState = copy(shipped = true)

    val status: String get() {
        return when {
            terminated -> "Terminated"
            issuerPaid -> "Issuer Paid"
            advisoryPaid -> "Advisory Paid"
            beneficiaryPaid -> "Seller Paid"
            shipped -> "Shipped"
            else -> "Active"
        }
    }
}

@CordaSerializable
data class LetterOfCreditProperties (
        val letterOfCreditID: String,
        val applicationDate: LocalDate,
        val issueDate: LocalDate,
        val typeCredit: CreditType,
        val amount: Amount<Currency>,
        val invoiceRef: StateRef,
        val expiryDate: LocalDate,
        val portLoading: Port,
        val portDischarge: Port,
        val descriptionGoods: List<PricedGood>,
        val placePresentation: Location,
        val latestShip: LocalDate,
        val periodPresentation: Period,
        val beneficiary: Party,
        val issuingBank: Party,
        val applicant: Party,
        val advisingBank: Party) {

    constructor(applicationProps: LetterOfCreditApplicationProperties, issueDate: LocalDate) : this(
            letterOfCreditID = applicationProps.letterOfCreditApplicationID,
            applicationDate = applicationProps.applicationDate,
            issueDate = issueDate,
            typeCredit = applicationProps.typeCredit,
            amount = applicationProps.amount,
            invoiceRef = applicationProps.invoiceRef,
            expiryDate = applicationProps.expiryDate,
            portLoading = applicationProps.portLoading,
            portDischarge = applicationProps.portDischarge,
            descriptionGoods = applicationProps.descriptionGoods,
            placePresentation = applicationProps.placePresentation,
            latestShip = applicationProps.lastShipmentDate,
            periodPresentation = applicationProps.periodPresentation,
            beneficiary = applicationProps.beneficiary,
            issuingBank = applicationProps.issuer,
            applicant = applicationProps.applicant,
            advisingBank = applicationProps.advisingBank
    )
}