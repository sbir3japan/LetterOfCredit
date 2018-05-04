package eloc.api

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