package eloc.state

import eloc.contract.LocDataStructures
import eloc.contract.PackingList
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate

data class PackingListState(
        val seller: Party,
        val buyer: Party,
        val advisory: Party,
        val issuer: Party,
        val status: PackingList.Status,
        val props: PackingListProperties

) : LinearState {

    override val linearId = UniqueIdentifier(props.orderNumber)
    override val participants get() = listOf( seller, buyer, advisory, issuer )
}

@CordaSerializable
data class PackingListProperties (
        val issueDate: LocalDate,
        val orderNumber: String,
        val sellersOrderNumber: String,
        val transportMethod : String,
        val nameOfVessel: String,
        val billOfLadingNumber: String,
        val seller: LocDataStructures.Company?,
        val buyer: LocDataStructures.Company?,
        val descriptionOfGoods: List<LocDataStructures.PricedGood>,
        val attachmentHash: SecureHash
)