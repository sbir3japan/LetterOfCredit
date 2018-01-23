package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.flows.CashIssueFlow
import java.util.*

/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/option purposes!
 */

@CordaSerializable
@StartableByRPC
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Unit>() {

    override val progressTracker: ProgressTracker = SelfIssueCashFlow.tracker()

    companion object {
        object PREPARING : ProgressTracker.Step("Preparing to self issue cash.")
        object ISSUING : ProgressTracker.Step("Issuing cash")

        fun tracker() = ProgressTracker(PREPARING, ISSUING)
    }

    @Suspendable
    override fun call() {
        /** Create the cash issue command. */
        val issueRef = OpaqueBytes.of(0)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        /** Create the cash issuance transaction. */
        progressTracker.currentStep = PREPARING
        subFlow(CashIssueFlow(amount, issueRef, notary))
    }
}