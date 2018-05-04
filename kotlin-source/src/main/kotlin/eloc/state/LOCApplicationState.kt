package eloc.state

import eloc.contract.LocDataStructures
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.time.Period
import java.util.*

@CordaSerializable
data class LOCApplicationProperties(
        val letterOfCreditApplicationID: String,
        val applicationDate: LocalDate,
        val typeCredit: LocDataStructures.CreditType,
        val issuer: Party,
        val beneficiary: Party,
        val applicant: Party,
        val advisingBank: Party,
        val expiryDate: LocalDate,
        val portLoading: LocDataStructures.Port,
        val portDischarge: LocDataStructures.Port,
        val placePresentation: LocDataStructures.Location,
        val lastShipmentDate: LocalDate,
        val periodPresentation: Period,
        val goods: List<LocDataStructures.PricedGood> = ArrayList(),
        val documentsRequired: List<String> = ArrayList(),
        val invoiceRef: StateRef,
        val amount: Amount<Currency>)

@CordaSerializable
enum class LOCApplicationStatus {
    PENDING_ISSUER_REVIEW,
    PENDING_ADVISORY_REVIEW,
    APPROVED,
    REJECTED,
}

data class LOCApplicationState(
        val owner: Party,
        val issuer: Party,
        val status: LOCApplicationStatus,
        val props: LOCApplicationProperties,
        val purchaseOrder: Attachment?) : LinearState {

    override val participants get() = listOf( owner, issuer )
    override val linearId: UniqueIdentifier = UniqueIdentifier(props.letterOfCreditApplicationID)

}