package eloc.api

import net.corda.core.identity.CordaX500Name
import java.io.InputStream

/** These classes capture the data to be passed back to the front-end as JSON. */

data class LocStats(val awaitingApproval: Int, val active: Int, val awaitingPayment: Int)

/**
 * How a letter-of-credit application is represented by the front-end in summarised form.
 */
data class LocAppDataSummary(
        val beneficiary: String,
        val applicant: String,
        val amount: Int,
        val currency: String,
        val description: String,
        val orderRef: String?,
        val status: String)

/**
 * The first way a letter-of-credit is represented by the front-end.
 */
data class LocDataA(
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
 * The second way a letter-of-credit is represented by the front-end.
 */
data class LocDataB(
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