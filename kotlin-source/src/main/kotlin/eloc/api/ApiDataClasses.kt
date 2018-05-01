package eloc.api

import net.corda.core.identity.CordaX500Name
import java.io.InputStream

data class LocStats(val awaitingApproval: Int, val active: Int, val awaitingPayment: Int)

/**
 * How a packing list is represented by the front-end.
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
        val goodsUnitPrice: Int,
        val goodsGrossWeight: Int,

        val attachmentHash: String?,
        val advisingBank: String,
        val issuingBank: String)

/**
 * How an invoice is represented by the front-end.
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
 * How a bill of lading is represented by the front-end.
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

/**
 * How a letter-of-credit application is represented by the front-end.
 */
data class LocAppData(
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
 * How a letter-of-credit is represented by the front-end.
 */
data class LocData(
        val beneficiaryPaid: Boolean,
        val advisoryPaid: Boolean,
        val issuerPaid: Boolean,
        val issued: Boolean,
        val terminated: Boolean,
        val beneficiary: String,
        val applicant: String,
        val advisoryBank: String,
        val issuingBank: String,
        val amount: Int,
        val currency: String,
        val quantity: Int,
        val purchaseOrderRef: String?,
        val description: String,
        val status: String)

/**
 * How a letter-of-credit or letter-of-credit application is represented by
 * the front-end in summarised form.
 */
data class LocSummary(
        val beneficiary: String,
        val applicant: String,
        val amount: Int,
        val currency: String,
        val description: String,
        val orderRef: String?,
        val status: String)

data class LetterOfCredit(
        val letterOfCreditId: String,
        val applicationDate: String,
        val issueDate: String,
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
        val beneficiary: CordaX500Name,
        val issuer: CordaX500Name,
        val applicant: CordaX500Name,
        val advisingBank: CordaX500Name,
        val beneficiaryPaid: Boolean,
        val advisoryPaid: Boolean,
        val issuerPaid: Boolean,
        val issued: Boolean,
        val terminated: Boolean) {
    var txRef: String = ""
}