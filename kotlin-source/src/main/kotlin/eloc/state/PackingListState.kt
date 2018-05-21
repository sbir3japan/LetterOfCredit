package eloc.state

import eloc.LetterOfCreditDataStructures.Company
import eloc.LetterOfCreditDataStructures.PricedGood
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
        val props: PackingListProperties) : LinearState {

    override val linearId = UniqueIdentifier(props.orderNumber)
    override val participants = listOf(seller, buyer, advisory, issuer)
}

@CordaSerializable
data class PackingListProperties (
        val issueDate: LocalDate,
        val orderNumber: String,
        val transportMethod : String,
        val nameOfVessel: String,
        val billOfLadingNumber: String,
        val seller: Company?,
        val buyer: Company?,
        val descriptionOfGoods: List<PricedGood>,
        val attachmentHash: SecureHash)