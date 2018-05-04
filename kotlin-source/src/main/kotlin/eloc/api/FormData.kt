package eloc.api

import java.io.InputStream

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
        val goodsGrossWeight: Int)

/**
 * The letter-of-credit application form data that is submitted by the
 * front-end to create a letter-of-credit application state.
 */
data class LocAppFormData(
        val applicationId: String,
        val applicationDate: String,
        val typeCredit: String,
        val amount: Int,
        val currency: String,
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
    var txRef: String = ""
}

/**
 * The packing-list form data that is submitted by the front-end to create a
 * packing-list state.
 */
data class PackingListData(
        val issueDate: String,
        val orderNumber: String,
        val sellersOrderNumber: String,

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
        val issuingBank: String)

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

        val attachment: InputStream? = null)