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
            letterOfCreditApplicationID = loc.applicationId,
            applicationDate = LocalDate.parse(loc.applicationDate.substringBefore('T')),
            typeCredit = LocDataStructures.CreditType.valueOf(loc.typeCredit),
            issuer = issuing,
            beneficiary = beneficiary,
            applicant = applicant,
            advisingBank = advising,
            expiryDate = LocalDate.parse(loc.expiryDate),
            portLoading = LocDataStructures.Port(loc.portLoadingCountry, loc.portLoadingCity, loc.portLoadingAddress, null, null),
            portDischarge = LocDataStructures.Port(loc.portDischargeCountry, loc.portDischargeCity, loc.portDischargeAddress, null, null),
            placePresentation = LocDataStructures.Location(loc.placePresentationCountry, loc.placePresentationState, loc.placePresentationCity),
            lastShipmentDate = LocalDate.parse(loc.lastShipmentDate.substringBefore('T')), // TODO does it make sense to include shipment date?
            periodPresentation = Period.ofDays(loc.periodPresentation),
            descriptionGoods = listOf(LocDataStructures.PricedGood(
                    loc.goodsDescription,
                    loc.goodsPurchaseOrderRef,
                    loc.goodsQuantity,
                    Amount(loc.goodsUnitPrice.toLong(), Amount.parseCurrency(loc.amount).token),
                    LocDataStructures.Weight(loc.goodsWeight.toDouble(), LocDataStructures.WeightUnit.valueOf(loc.goodsWeightUnit)))),
            documentsRequired = ArrayList(),
            invoiceRef = StateRef(SecureHash.randomSHA256(), 0),
            amount = Amount.parseCurrency(loc.amount))
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
                        unitPrice = Amount.parseCurrency(packingListData.goodsUnitPrice),
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