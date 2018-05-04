package eloc.api

/** These classes capture the data to be passed back to the front-end as JSON. */

data class LocStats(val awaitingApproval: Int, val active: Int, val awaitingPayment: Int)