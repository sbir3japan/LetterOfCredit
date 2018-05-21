package eloc.api

import eloc.LetterOfCreditDataStructures.Company
import eloc.LetterOfCreditDataStructures.CreditType
import eloc.LetterOfCreditDataStructures.Good
import eloc.LetterOfCreditDataStructures.Location
import eloc.LetterOfCreditDataStructures.Person
import eloc.LetterOfCreditDataStructures.Port
import eloc.LetterOfCreditDataStructures.PricedGood
import eloc.LetterOfCreditDataStructures.Weight
import eloc.LetterOfCreditDataStructures.WeightUnit
import eloc.state.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.finance.DOLLARS
import java.io.InputStream
import java.time.LocalDate
import java.time.Period
import java.util.*

/** These classes capture the JSON form data passed from the front-end. */

/**
 * The invoice form data that is submitted by the front-end to create an
 * invoice state.
 */
data class InvoiceData(
        val invoiceId: String,
        val sellerName: String,
        val sellerAddress: String,
        val buyerName: String,
        val buyerAddress: String,
        val invoiceDate: String,
        val term: Int,
        val goodsDescription: String,
        val goodsPurchaseOrderRef: String,
        val goodsQuantity: Int,
        val goodsUnitPrice: Int,
        val goodsGrossWeight: Int) {

    /**
     * Converts the [InvoiceData] submitted from the front-end into the
     * properties for an [InvoiceState].
     */
    fun toInvoiceProperties() = InvoiceProperties(
            invoiceID = invoiceId,
            seller =Company(sellerName, sellerAddress, ""),
            buyer = Company(buyerName, buyerAddress, ""),
            invoiceDate = LocalDate.parse(invoiceDate.substringBefore('T')),
            term = term.toLong(),
            attachmentHash = SecureHash.randomSHA256(),
            goods = listOf(PricedGood(goodsDescription, goodsPurchaseOrderRef, goodsQuantity,
                    goodsUnitPrice.DOLLARS, Weight(goodsGrossWeight.toDouble(),
                    WeightUnit.KG))))
}

/**
 * The letter-of-credit application form data that is submitted by the
 * front-end to create a letter-of-credit application state.
 */
data class LocApplicationData(
        val applicationId: String,
        val applicationDate: String,
        val typeCredit: String,
        val amount: String,
        val expiryDate: String,
        val portLoadingCountry: String,
        val portLoadingCity: String,
        val portLoadingAddress: String,
        val portDischargeCountry: String,
        val portDischargeCity: String,
        val portDischargeAddress: String,
        val goodsDescription: String,
        val goodsQuantity: Int,
        val goodsWeight: Int,
        val goodsWeightUnit: String,
        val goodsUnitPrice: Int,
        val goodsPurchaseOrderRef: String,
        val placePresentationCountry: String,
        val placePresentationState: String,
        val placePresentationCity: String,
        val lastShipmentDate: String,
        val periodPresentation: Int,
        val beneficiary: String,
        val issuer: String,
        val applicant: String,
        val advisingBank: String) {

    /**
     * Converts the [LocApplicationData] submitted from the front-end into the
     * properties for a [LetterOfCreditApplicationState].
     */
    fun toLocApplicationProperties(applicant: Party, beneficiary: Party, issuing: Party, advising: Party): LetterOfCreditApplicationProperties {
        return LetterOfCreditApplicationProperties(
                letterOfCreditApplicationID = applicationId,
                applicationDate = LocalDate.parse(applicationDate.substringBefore('T')),
                typeCredit = CreditType.valueOf(typeCredit),
                issuer = issuing,
                beneficiary = beneficiary,
                applicant = applicant,
                advisingBank = advising,
                expiryDate = LocalDate.parse(expiryDate),
                portLoading = Port(portLoadingCountry, portLoadingCity, portLoadingAddress, null, null),
                portDischarge = Port(portDischargeCountry, portDischargeCity, portDischargeAddress, null, null),
                placePresentation = Location(placePresentationCountry, placePresentationState, placePresentationCity),
                lastShipmentDate = LocalDate.parse(lastShipmentDate.substringBefore('T')), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(periodPresentation),
                descriptionGoods = listOf(PricedGood(
                        goodsDescription,
                        goodsPurchaseOrderRef,
                        goodsQuantity,
                        Amount(goodsUnitPrice.toLong(), Amount.parseCurrency(amount).token),
                        Weight(goodsWeight.toDouble(), WeightUnit.valueOf(goodsWeightUnit)))),
                documentsRequired = ArrayList(),
                invoiceRef = StateRef(SecureHash.randomSHA256(), 0),
                amount = Amount.parseCurrency(amount))
    }
}

/**
 * The packing-list form data that is submitted by the front-end to create a
 * packing-list state.
 */
data class PackingListData(
        val issueDate: String,
        val orderNumber: String,

        val transportMethod: String,
        val nameOfVessel: String,
        val billOfLadingNumber: String,

        val sellerName: String,
        val sellerAddress: String,

        val buyerName: String,
        val buyerAddress: String,

        val goodsDescription: String,
        val goodsPurchaseOrderRef: String,
        val goodsQuantity: Int,
        val goodsUnitPrice: String,
        val goodsGrossWeight: Int,

        val attachmentHash: String?,
        val advisingBank: String,
        val issuingBank: String) {

    /**
     * Converts the [PackingListData] submitted from the front-end into the
     * properties for a [PackingListState].
     */
    fun toPackingListProperties() = PackingListProperties(
            issueDate = LocalDate.parse(issueDate.substringBefore('T')),
            orderNumber = orderNumber,
            transportMethod = transportMethod,
            nameOfVessel = nameOfVessel,
            billOfLadingNumber = billOfLadingNumber,
            seller = Company(
                    name = sellerName,
                    address = sellerAddress,
                    phone = ""),
            buyer = Company(
                    name = buyerName,
                    address = buyerAddress,
                    phone = ""),
            descriptionOfGoods = arrayListOf(
                    PricedGood(
                            description = goodsDescription,
                            purchaseOrderRef = goodsPurchaseOrderRef,
                            quantity = goodsQuantity,
                            unitPrice = Amount.parseCurrency(goodsUnitPrice),
                            grossWeight = Weight(goodsGrossWeight.toDouble(), WeightUnit.KG)
                    )),
            attachmentHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() }))
    )
}

/**
 * The bill-of-lading form data that is submitted by the front-end to create a
 * bill-of-lading state.
 */
data class BillOfLadingData(
        val billOfLadingId: String,
        val issueDate: String,
        val carrierOwner: String,

        val nameOfVessel: String,
        val goodsDescription: String,
        val goodsQuantity: Int,
        val dateOfShipment: String,

        val portOfLoadingCountry: String,
        val portOfLoadingCity: String,
        val portOfLoadingAddress: String,

        val portOfDischargeCountry: String,
        val portOfDischargeCity: String,
        val portOfDischargeAddress: String,

        val shipper: String,
        val notifyName: String,
        val notifyAddress: String,
        val notifyPhone: String,

        val consigneeName: String,
        val consigneeAddress: String,
        val consigneePhone: String,

        val grossWeight: Int,
        val grossWeightUnit: String,

        val placeOfReceiptCountry: String,
        val placeOfReceiptCity: String,
        val buyer: String,
        val advisingBank: String,
        val issuingBank: String,

        val attachment: InputStream? = null) {

    /**
     * Converts the [BillOfLadingData] submitted from the front-end into the
     * properties for a [BillOfLadingState].
     */
    fun toBillOfLadingProperties(seller: Party): BillOfLadingProperties {
        return BillOfLadingProperties(
                billOfLadingId,
                LocalDate.parse(issueDate.substringBefore('T')),
                // For now we'll just set the carrier owner as the seller, in future we should have a node representing the carrier
                seller,
                nameOfVessel,
                listOf(Good(description = goodsDescription, quantity = goodsQuantity, grossWeight = null)),
                Port(country = portOfLoadingCountry, city = portOfLoadingCity, address = portOfLoadingAddress, state = null, name = null),
                Port(country = portOfDischargeCountry, city = portOfDischargeCity, address = portOfDischargeAddress, state = null, name = null),
                Weight(
                        grossWeight.toDouble(),
                        WeightUnit.valueOf(grossWeightUnit)),
                LocalDate.parse(dateOfShipment.substringBefore('T')),
                null,
                Person(
                        notifyName,
                        notifyAddress,
                        notifyPhone),
                Company(
                        consigneeName,
                        consigneeAddress,
                        consigneePhone),
                Location(
                        placeOfReceiptCountry,
                        null,
                        placeOfReceiptCity),
                attachment)
    }
}