package eloc.state

import eloc.contract.LocDataStructures
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate

data class BillOfLadingState(
        val owner: Party,
        val buyer: Party,
        val advisory: Party,
        val issuer: Party,
        val timestamp: Instant,
        val props: BillOfLadingProperties) : LinearState {

    override val linearId = UniqueIdentifier(props.billOfLadingID)

    override val participants get() = listOf(owner, buyer, advisory, issuer)
}

@CordaSerializable
data class BillOfLadingProperties(
        val billOfLadingID: String,
        val issueDate: LocalDate,
        val carrierOwner: net.corda.core.identity.Party,
        val nameOfVessel: String,
        val descriptionOfGoods: List<LocDataStructures.Good>,
        val portOfLoading: LocDataStructures.Port,
        val portOfDischarge: LocDataStructures.Port,
        val grossWeight: LocDataStructures.Weight,
        val dateOfShipment: LocalDate?,
        val shipper: LocDataStructures.Company?,
        val notify: LocDataStructures.Person?,
        val consignee: LocDataStructures.Company?,
        val placeOfReceipt: LocDataStructures.Location?,
        val attachment: InputStream? = null)