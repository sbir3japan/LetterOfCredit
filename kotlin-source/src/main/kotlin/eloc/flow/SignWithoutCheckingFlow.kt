package eloc.flow

import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction

/**
 * A response flow that doesn't check the transaction before signing.
 * For demo purposes only!
 */
class SignWithoutCheckingFlow(counterpartySession: FlowSession) : SignTransactionFlow(counterpartySession) {
    override fun checkTransaction(stx: SignedTransaction) {
        // For demo purposes, we don't do any checking here.
    }
}