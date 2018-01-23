package eloc.flow

/*
import co.paralleluniverse.fibers.Suspendable
import com.r3corda.contracts.BillOfLadingAgreement
import com.r3corda.contracts.Invoice
import com.r3corda.contracts.LOC
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.utilities.Emoji
import com.r3corda.core.utilities.ProgressTracker

/**
 * Created by N992551 on 14.07.2016.
 */
/*
val DEMO_TOPIC = "initiate.demo.eloc"

data class HandShake(val sendingNode: NodeInfo, val sessionId: Long, val designatedNotary: NodeInfo)
data class InfoToSeller(val issuerNode: NodeInfo, val issuerSessionId: Long, val applicantSessionId: Long)
data class InfoToIssuer(val sellerNode: NodeInfo, val sellerSessionId: Long, val applicantNode:NodeInfo, val applicantSessionId: Long, val designatedNotary: NodeInfo)
data class InfoToCarrier(val sellerNode: NodeInfo, val sellerSessionId: Long, val designatedNotary: NodeInfo)

class eLocDemoProtocolSeller(
        val buyerName: String,
        val carrierName: String,
        val notaryName: String,
        val invoiceProperties: Invoice.InvoiceProperties,
        val certificateOfOriginHash: SecureHash,
        override val progressTracker: ProgressTracker = eLocDemoProtocolSeller.tracker()) : ProtocolLogic<Unit>() {


    companion object {
        object ANNOUNCING_APPLICANT : ProgressTracker.Step("Announcing to the buyer")
        object ANNOUNCING_CARRIER : ProgressTracker.Step("Announcing to the carrier")
        object FINANCING_TRADE : ProgressTracker.Step("Initiating the buyer protocol for letter of credit")

        fun tracker() = ProgressTracker(ANNOUNCING_APPLICANT, ANNOUNCING_CARRIER, FINANCING_TRADE).apply {
            childrenFor[FINANCING_TRADE] = Seller.tracker()
        }
    }

    @Suspendable
    override fun call() {
        val myNode = serviceHub.networkMapCache.getNodeByPublicKey(serviceHub.storageService.myLegalIdentity.owningKey) ?: throw Exception("Cannot find my Node information")

        val notaryNode = serviceHub.networkMapCache.getNodeByLegalName(notaryName) ?: throw Exception("Unable to find notary node with name $notaryName")

        progressTracker.currentStep = ANNOUNCING_APPLICANT

        //Uses the network map to retrieve the buyer
        val buyerNode = serviceHub.networkMapCache.getNodeByLegalName(buyerName) ?: throw Exception("Unable to find buyer node with name $buyerName")

        val sellerSessionId = random63BitValue()

        val sellerKickOffId = "${myNode.identity.name.toLowerCase().trim()}.${invoiceProperties.invoiceID}".hashCode().toLong()

        val infoFromApplicant = sendAndReceive<InfoToSeller>(DEMO_TOPIC, buyerNode.address,sellerKickOffId, sellerSessionId, HandShake(myNode, sellerSessionId, notaryNode)).validate { it }

        progressTracker.currentStep = ANNOUNCING_CARRIER

        val carrierNode = serviceHub.networkMapCache.getNodeByLegalName(carrierName) ?: throw Exception("Unable to find carrier node with name $carrierName")
        send(DEMO_TOPIC, carrierNode.address, sellerKickOffId, InfoToCarrier(myNode,sellerSessionId,notaryNode))

        progressTracker.currentStep = FINANCING_TRADE

        val invoiceState = Invoice.State(notaryNode.identity,
                serviceHub.storageService.myLegalIdentity,
                buyerNode.identity,
                false,
                invoiceProperties)

        val buyer = Seller.sellerGatheringDocs(
                buyerNode.address,
                infoFromApplicant.issuerNode.address,
                carrierNode.address,
                infoFromApplicant.issuerNode,
                buyerNode,
                notaryNode,
                serviceHub.storageService.myLegalIdentityKey,
                myNode,
                sellerSessionId,
                infoFromApplicant.applicantSessionId,
                invoiceState,
                serviceHub.storageService.attachments.openAttachment(certificateOfOriginHash) ?: throw Exception("Cannot find attachment with hash $certificateOfOriginHash"),
                infoFromApplicant.issuerSessionId
        )

        val paymentTx: SignedTransaction = subProtocol(buyer)
        logger.info("Sale completed - We got payment from the issuer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(paymentTx.tx)}")
    }

}


class eLocDemoProtocolBuyer(
        val sellerName: String,
        val issuerName: String,
        val expectedInvoiceProperties: Invoice.InvoiceProperties,
        val applicationDetails: Applicant.ApplicationInput,
        val purchaseOrderHash: SecureHash,
        val fees: Double,
        override val progressTracker: ProgressTracker = eLocDemoProtocolBuyer.tracker()
) : ProtocolLogic<Unit>() {

    companion object {
        object WAITING_SELLER_HANDSHAKE : ProgressTracker.Step("Waiting connection from buyer")
        object ANNOUNCING_ISSUER : ProgressTracker.Step("Announcing to the issuer")
        object ANNOUNCING_SELLER : ProgressTracker.Step("Announcing to the buyer")

        object FINANCING_TRADE : ProgressTracker.Step("Initiating the buyer protocol for letter of credit")

        fun tracker() = ProgressTracker(WAITING_SELLER_HANDSHAKE, ANNOUNCING_ISSUER, ANNOUNCING_SELLER, FINANCING_TRADE).apply {
            childrenFor[FINANCING_TRADE] = Applicant.Applicant.tracker()
        }
    }

    @Suspendable
    override fun call() {
        val myNode = serviceHub.networkMapCache.getNodeByPublicKey(serviceHub.storageService.myLegalIdentity.owningKey) ?: throw Exception("Cannot find my Node information")

        progressTracker.currentStep = WAITING_SELLER_HANDSHAKE

        val sellerKickOffID = "${sellerName.toLowerCase().trim()}.${expectedInvoiceProperties.invoiceID}".hashCode().toLong()
        val applicantKickOffID = myNode.identity.name.toLowerCase().trim().hashCode().toLong()

        val sellerInfo = receive<HandShake>(DEMO_TOPIC, sellerKickOffID).validate {
            if (!it.sendingNode.identity.name.equals(sellerName, true)) {
                throw Exception("Unidentified buyer: ${it.sendingNode.identity.name}")
            }
            it
        }

        val sellerSessionId = sellerInfo.sessionId
        val applicantSessionId = random63BitValue()
        val notaryNode = sellerInfo.designatedNotary

        val issuerNode = serviceHub.networkMapCache.getNodeByLegalName(issuerName) ?: throw Exception("Unable to find issuer node with name $issuerName")

        progressTracker.currentStep = ANNOUNCING_ISSUER
        val issuerSessionId = sendAndReceive<Long>(DEMO_TOPIC, issuerNode.address, applicantKickOffID, applicantSessionId, InfoToIssuer(sellerInfo.sendingNode, sellerSessionId, myNode, applicantSessionId, notaryNode)).validate { it }

        progressTracker.currentStep = ANNOUNCING_SELLER
        send(DEMO_TOPIC, sellerInfo.sendingNode.address, sellerSessionId, InfoToSeller(issuerNode, issuerSessionId, applicantSessionId))

        progressTracker.currentStep = FINANCING_TRADE

        val expectedInvoice = Invoice.State(notaryNode.identity,
                sellerInfo.sendingNode.identity,
                serviceHub.storageService.myLegalIdentity,
                false,
                expectedInvoiceProperties)

        val applicantProtocol = Applicant.Applicant(
                issuerNode.address,
                sellerInfo.sendingNode.address,
                serviceHub.storageService.myLegalIdentityKey,
                applicantSessionId,
                issuerSessionId,
                fees,
                expectedInvoice,
                applicationDetails,
                notaryNode.identity,
                purchaseOrderHash
        )

        val paymentTx: SignedTransaction = subProtocol(applicantProtocol)
        logger.info("Financing completed - We reimbursed the issuer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(paymentTx.tx)}")
    }
}


class eLocDemoProtocolIssuer(
        val buyerName: String,
        val expectedFees: Double,
        override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {

    companion object {
        object WAITING_BUYER_HANDSHAKE : ProgressTracker.Step("Waiting connection from buyer")
        object LETTER_OF_CREDIT_APPLICATION : ProgressTracker.Step("Initiating the issuer protocol for letter of credit application")
        object DOCUMENT_PRESENTATION : ProgressTracker.Step("Initiating the issuer protocol for document presentation")
        object APPLICANT_REPAYMENT : ProgressTracker.Step("Initiating the issuer protocol for applicant payment")

        fun tracker() = ProgressTracker(WAITING_BUYER_HANDSHAKE, LETTER_OF_CREDIT_APPLICATION, DOCUMENT_PRESENTATION, APPLICANT_REPAYMENT).apply {
            childrenFor[LETTER_OF_CREDIT_APPLICATION] = Issuer.elocApplicationProtocol.tracker()
            childrenFor[DOCUMENT_PRESENTATION] = Issuer.elocDocumentPresentationProtocol.tracker()
            childrenFor[APPLICANT_REPAYMENT] = Issuer.elocDocumentPresentationProtocol.tracker()
        }
    }


    @Suspendable
    override fun call() {
        progressTracker.currentStep = WAITING_BUYER_HANDSHAKE
        val applicantKickOffId = buyerName.toLowerCase().trim().hashCode().toLong()
        val buyerInfo = receive<InfoToIssuer>(DEMO_TOPIC, applicantKickOffId).validate {
            if (!it.applicantNode.identity.name.equals(buyerName,false)){
                throw Exception("Unidentified buyer: ${it.applicantNode.identity.name}")
            }
            it
        }

        val issuerSessionId = random63BitValue()
        send(DEMO_TOPIC, buyerInfo.applicantNode.address,buyerInfo.applicantSessionId,issuerSessionId)

        progressTracker.currentStep = LETTER_OF_CREDIT_APPLICATION
        val applicationProtocol = Issuer.elocApplicationProtocol(issuerSessionId,buyerInfo.applicantSessionId,buyerInfo.applicantNode.address,buyerInfo.designatedNotary.identity,expectedFees)
        val locIssueTx = subProtocol(applicationProtocol)
        logger.info("Issued loc\n\nTransaction is:\n\n${Emoji.renderIfSupported(locIssueTx.tx)}")


        progressTracker.currentStep = DOCUMENT_PRESENTATION
        val issuerDocPresentationProtocol = Issuer.elocDocumentPresentationProtocol(
                issuerSessionId,
                buyerInfo.sellerSessionId,
                buyerInfo.sellerNode.address,
                buyerInfo.designatedNotary.identity,
                locIssueTx.tx.outRef(locIssueTx.tx.outputs.filterIsInstance<LOC.State>().single())
        )
        val docApplicationTx = subProtocol(issuerDocPresentationProtocol)
        logger.info("Received document presentation\n\nTransaction is:\n\n${Emoji.renderIfSupported(docApplicationTx.tx)}")

        progressTracker.currentStep = APPLICANT_REPAYMENT
        val issuerPaymentProtocol = Issuer.elocReceivePaymentProtocol(
                issuerSessionId,
                buyerInfo.applicantSessionId,
                buyerInfo.applicantNode.address,
                docApplicationTx.tx.outRef<LOC.State>(docApplicationTx.tx.outputs.filterIsInstance<LOC.State>().single()).ref
        )
        val finalPaymentTx = subProtocol(issuerPaymentProtocol)
        logger.info("Received repayment from applicant\n\nFinal Transaction is:\n\n${Emoji.renderIfSupported(finalPaymentTx.tx)}")

    }

}

class eLocDemoProtocolCarrier(
        val relatedInvoiceId: String,
        val sellerName: String,
        val bolProperties: BillOfLadingAgreement.BillOfLadingProperties,
        override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {
    companion object {
        object WAITING_SELLER_HANDSHAKE : ProgressTracker.Step("Waiting connection from buyer")
        object ISSUING_BILL_OF_LADING : ProgressTracker.Step("Issuing BoL to the issuer")

        fun tracker() = ProgressTracker(WAITING_SELLER_HANDSHAKE, ISSUING_BILL_OF_LADING).apply {
            childrenFor[ISSUING_BILL_OF_LADING] = Carrier.tracker()
        }
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = WAITING_SELLER_HANDSHAKE
        val sellerKickOffID = "${sellerName.toLowerCase().trim()}.$relatedInvoiceId".hashCode().toLong()

        val sellerInfo = receive<InfoToCarrier>(DEMO_TOPIC,sellerKickOffID).validate {
            if (!it.sellerNode.identity.name.equals(sellerName,false)){
                throw Exception("Unidentified buyer: ${it.sellerNode.identity.name}")
            }
            it
        }

        progressTracker.currentStep=ISSUING_BILL_OF_LADING
        val carrierProtocol = Carrier(
                sellerInfo.sellerNode.address,
                serviceHub.storageService.myLegalIdentityKey,
                sellerInfo.sellerSessionId,
                sellerInfo.sellerNode.identity,
                bolProperties,
                sellerInfo.designatedNotary
        )
        val billOfLadingIssueTx = subProtocol(carrierProtocol)
        logger.info("Issued Bill Of Lading\n\nTransaction is:\n\n${Emoji.renderIfSupported(billOfLadingIssueTx.tx)}")

    }

}*/