package eloc.state

import eloc.contract.LocDataStructures
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.time.Period
import java.util.*

data class LOCState(
        val beneficiaryPaid: Boolean,
        val advisoryPaid: Boolean,
        val issuerPaid: Boolean,
        val issued: Boolean,
        val terminated: Boolean,
        val props: LOCProperties

) : LinearState {

    override val linearId = UniqueIdentifier(props.letterOfCreditID)

    override val participants get() = listOf(props.beneficiary, props.advisingBank, props.issuingBank, props.applicant)

    fun beneficiaryPaid(): LOCState = copy(beneficiaryPaid = true)
    fun issuerPaid(): LOCState = copy(issuerPaid = true)
    fun advisoryPaid(): LOCState = copy(advisoryPaid = true)
}

@CordaSerializable
data class LOCProperties (
        val letterOfCreditID: String,
        val applicationDate: LocalDate,
        val issueDate: LocalDate,
        val typeCredit: LocDataStructures.CreditType,
        val amount: Amount<Currency>,
        val invoiceRef: StateRef,
        val expiryDate: LocalDate,
        val portLoading: LocDataStructures.Port,
        val portDischarge: LocDataStructures.Port,
        val descriptionGoods: List<LocDataStructures.PricedGood>,
        val placePresentation: LocDataStructures.Location,
        val latestShip: LocalDate,
        val periodPresentation: Period,
        val beneficiary: Party,
        val issuingBank: Party,
        val applicant: Party,
        val advisingBank: Party
) {
    constructor(applicationProps: LOCApplicationProperties, issueDate: LocalDate) : this(
            letterOfCreditID = applicationProps.letterOfCreditApplicationID,
            applicationDate = applicationProps.applicationDate,
            issueDate = issueDate,
            typeCredit = applicationProps.typeCredit,
            amount = applicationProps.amount,
            invoiceRef = applicationProps.invoiceRef,
            //TODO this probably should not be in the application
            expiryDate = applicationProps.expiryDate,
            portLoading = applicationProps.portLoading,
            portDischarge = applicationProps.portDischarge,
            descriptionGoods = applicationProps.goods,
            placePresentation = applicationProps.placePresentation,
            latestShip = applicationProps.lastShipmentDate,
            periodPresentation = applicationProps.periodPresentation,
            beneficiary = applicationProps.beneficiary,
            issuingBank = applicationProps.issuer,
            applicant = applicationProps.applicant,
            advisingBank = applicationProps.advisingBank
    )
}