/*package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.BillOfLadingAgreement
import eloc.state.BillOfLadingProperties
import eloc.state.BillofLadingState
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.security.KeyPair

//Carrier protocol, issues Bill of Lading Agreement and sends to the Seller
 class Carrier(val sellerSide: SingleMessageRecipient,
              val carrierKeyPair: KeyPair,
              val sellerSessionID: Long,
              val seller: Party,
              val billOfLadingProperties: BillOfLadingProperties,
              val notaryNode: NodeInfo,
              override val progressTracker: ProgressTracker = Carrier.tracker()) : FlowLogic<SignedTransaction>() {

    companion object {
        object ISSUING : ProgressTracker.Step("Issuing and Timestamping the Bill of Lading")
        object NOTARIZING_BOL : ProgressTracker.Step("Getting Bill Of Lading Notarized")
        object SENDING : ProgressTracker.Step("Sending Bill of Lading to the Seller")
        fun tracker() = ProgressTracker(ISSUING, NOTARIZING_BOL, SENDING)
    }

    @Suspendable
    override fun call() : SignedTransaction {

        //Issue a BoL
        progressTracker.currentStep = ISSUING
        val partialTransaction = BillOfLadingAgreement().generateIssue(
                beneficiary = seller,
                owner = seller.owningKey.singleKey,
                notary = notaryNode.notaryIdentity,
                props = billOfLadingProperties)

        partialTransaction.setTime(serviceHub.clock.instant(), 30.seconds)
        partialTransaction.signWith(carrierKeyPair)

        progressTracker.currentStep = NOTARIZING_BOL
        val signedTransaction = partialTransaction.toSignedTransaction(checkSufficientSignatures = false)

        //record transaction
        serviceHub.recordTransactions(listOf(signedTransaction))

        //Send Bill of Lading to the Seller
        progressTracker.currentStep = SENDING
        val  stateToSend = signedTransaction.tx.outRef(partialTransaction.outputStates().filter { it.data is BillofLadingState }.first());
        send(ElocProtocol.LOC_DOC_PRESENTATION_TOPIC,sellerSide, sellerSessionID, stateToSend)

        return signedTransaction
    }
}*/