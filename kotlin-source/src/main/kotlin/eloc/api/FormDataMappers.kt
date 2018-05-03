package eloc.api

import eloc.contract.LocDataStructures
import eloc.state.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.finance.DOLLARS
import java.time.LocalDate
import java.time.Period
import java.util.*

/** These functions map the form data into state properties. */

/**
 * Converts the [InvoiceData] submitted from the front-end into the
 * properties for an [InvoiceState].
 */
fun invoiceDataToInvoiceProperties(invoiceData: InvoiceData) = InvoiceProperties(
        invoiceID = invoiceData.invoiceId,
        seller = LocDataStructures.Company(invoiceData.sellerName, invoiceData.sellerAddress, ""),
        buyer = LocDataStructures.Company(invoiceData.buyerName, invoiceData.buyerAddress, ""),
        invoiceDate = LocalDate.parse(invoiceData.invoiceDate.substringBefore('T')),
        term = invoiceData.term.toLong(),
        attachmentHash = SecureHash.randomSHA256(),
        goods = listOf(LocDataStructures.PricedGood(invoiceData.goodsDescription, invoiceData.goodsPurchaseOrderRef, invoiceData.goodsQuantity,
                invoiceData.goodsUnitPrice.DOLLARS, LocDataStructures.Weight(invoiceData.goodsGrossWeight.toDouble(),
                LocDataStructures.WeightUnit.KG))))

/**
 * Converts the [LocAppData] submitted from the front-end into the
 * properties for a [LOCApplicationState].
 */
fun locApplicationFormDataToLocApplicationProperties(loc: LocAppFormData, applicant: Party, beneficiary: Party, issuing: Party, advising: Party): LOCApplicationProperties {
    return LOCApplicationProperties(
            loc.applicationId,
            LocalDate.parse(loc.applicationDate.substringBefore('T')),
            LocDataStructures.CreditType.valueOf(loc.typeCredit),
            issuing,
            beneficiary,
            applicant,
            advising,
            LocalDate.parse(loc.expiryDate.substringBefore('T')),
            LocDataStructures.Port(loc.portLoadingCountry, loc.portLoadingCity, loc.portLoadingAddress, null, null),
            LocDataStructures.Port(loc.portDischargeCountry, loc.portDischargeCity, loc.portDischargeAddress, null, null),
            LocDataStructures.Location(loc.placePresentationCountry, loc.placePresentationState, loc.placePresentationCity),
            LocalDate.parse(loc.lastShipmentDate.substringBefore('T')), // TODO does it make sense to include shipment date?
            Period.ofDays(loc.periodPresentation),
            listOf(LocDataStructures.PricedGood(
                    loc.goodsDescription,
                    loc.goodsPurchaseOrderRef,
                    loc.goodsQuantity,
                    Amount(loc.goodsUnitPrice.toLong(), Currency.getInstance(loc.currency)),
                    LocDataStructures.Weight(loc.goodsWeight.toDouble(), LocDataStructures.WeightUnit.valueOf(loc.goodsWeightUnit)))),
            ArrayList(),
            StateRef(SecureHash.randomSHA256(), 0),
            Amount(loc.amount.toLong(), Currency.getInstance(loc.currency)))
}

/**
 * Converts the [PackingListData] submitted from the front-end into the
 * properties for a [PackingListState].
 */
fun packingListDataToPackingListProperties(packingListData: PackingListData) = PackingListProperties(
        issueDate = LocalDate.parse(packingListData.issueDate.substringBefore('T')),
        orderNumber = packingListData.orderNumber,
        sellersOrderNumber = packingListData.sellersOrderNumber,
        transportMethod = packingListData.transportMethod,
        nameOfVessel = packingListData.nameOfVessel,
        billOfLadingNumber = packingListData.billOfLadingNumber,
        seller = LocDataStructures.Company(
                name = packingListData.sellerName,
                address = packingListData.sellerAddress,
                phone = ""),
        buyer = LocDataStructures.Company(
                name = packingListData.buyerName,
                address = packingListData.buyerAddress,
                phone = ""),
        descriptionOfGoods = arrayListOf(
                LocDataStructures.PricedGood(
                        description = packingListData.goodsDescription,
                        purchaseOrderRef = packingListData.goodsPurchaseOrderRef,
                        quantity = packingListData.goodsQuantity,
                        unitPrice = packingListData.goodsUnitPrice.DOLLARS,
                        grossWeight = LocDataStructures.Weight(packingListData.goodsGrossWeight.toDouble(), LocDataStructures.WeightUnit.KG)
                )),
        attachmentHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() }))
)

/**
 * Converts the [BillOfLadingData] submitted from the front-end into the
 * properties for a [BillOfLadingState].
 */
fun billOfLadingDataToBillOfLadingProperties(billOfLading: BillOfLadingData, seller: Party): BillOfLadingProperties {
    return BillOfLadingProperties(
            billOfLading.billOfLadingId,
            LocalDate.parse(billOfLading.issueDate.substringBefore('T')),
            // For now we'll just set the carrier owner as the seller, in future we should have a node representing the carrier
            seller,
            billOfLading.nameOfVessel,
            listOf(LocDataStructures.Good(description = billOfLading.goodsDescription, quantity = billOfLading.goodsQuantity, grossWeight = null)),
            LocDataStructures.Port(country = billOfLading.portOfLoadingCountry, city = billOfLading.portOfLoadingCity, address = billOfLading.portOfLoadingAddress, state = null, name = null),
            LocDataStructures.Port(country = billOfLading.portOfDischargeCountry, city = billOfLading.portOfDischargeCity, address = billOfLading.portOfDischargeAddress, state = null, name = null),
            LocDataStructures.Weight(
                    billOfLading.grossWeight.toDouble(),
                    LocDataStructures.WeightUnit.valueOf(billOfLading.grossWeightUnit)),
            LocalDate.parse(billOfLading.dateOfShipment.substringBefore('T')),
            null,
            LocDataStructures.Person(
                    billOfLading.notifyName,
                    billOfLading.notifyAddress,
                    billOfLading.notifyPhone),
            LocDataStructures.Company(
                    billOfLading.consigneeName,
                    billOfLading.consigneeAddress,
                    billOfLading.consigneePhone),
            LocDataStructures.Location(
                    billOfLading.placeOfReceiptCountry,
                    null,
                    billOfLading.placeOfReceiptCity),
            billOfLading.attachment)
}