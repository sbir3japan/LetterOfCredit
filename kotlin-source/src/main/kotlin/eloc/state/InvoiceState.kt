package eloc.state

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import eloc.contract.Invoice
import eloc.contract.LocDataStructures
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import java.time.LocalDate
import java.util.*

@JsonIgnoreProperties(ignoreUnknown=true)
data class InvoiceState(
        val owner: Party,
        val buyer: Party,
        val assigned: Boolean,
        val props: InvoiceProperties

) : LinearState {
    override val linearId: UniqueIdentifier get() = UniqueIdentifier()
    override val participants get() = listOf( owner, buyer )
    fun generateInvoice( notary: Party ) : TransactionBuilder = Invoice().generateInvoice(props, owner, buyer, notary)
}

// Invoice Statement
@CordaSerializable
data class InvoiceProperties (
        val invoiceID: String,
        val seller: LocDataStructures.Company,
        val buyer: LocDataStructures.Company,
        val invoiceDate: LocalDate,
        val attachmentHash: SecureHash,
        val term: Long,
        val goods: List<LocDataStructures.PricedGood> = ArrayList()
) {
    init {
        require(term > 0) { "the term must be a positive number" }
        require(goods.isNotEmpty()) { "there must be goods assigned to the invoice"}
    }

    // returns the single currency used by the goods list
    val goodCurrency: Currency get() = goods.map { it.unitPrice.token }.distinct().single()

    // add term to invoice date to determine the payDate
    val payDate: LocalDate get() {
        return invoiceDate.plusDays(term)
    }
}