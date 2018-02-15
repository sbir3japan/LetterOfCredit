package eloc.flow

/*
import co.paralleluniverse.fibers.Suspendable
import eloc.contract.BillOfLadingAgreement
import eloc.contract.LOCApplication
import eloc.contracts.LOC
import eloc.state.BillOfLadingState
import eloc.state.InvoiceState
import eloc.state.LOCProperties
import eloc.state.LOCState
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import java.security.SignatureException
import java.time.ZoneId

/**
 * Created by N992551 on 30.06.2016.
 */
object Issuer {


    class UnacceptableApplicationException(override val message: String) : Exception(message)
    class UnacceptableDocPresentationException(override val message: String) : Exception(message)
    class UnacceptablePaymentException(override val message: String) : Exception(message)

    object RECEIVING_LOC_APPLICATION : ProgressTracker.Step("Waiting for letter of credit application")
    object VERIFYING_APPLICATION : ProgressTracker.Step("Verifying for letter of credit application")
    object APPROVING_APPLICATION : ProgressTracker.Step("Approving letter of credit application")
    object NOTARIZING_APPLICATION : ProgressTracker.Step("Sending letter of credit application approval to Notary")
    object SENDING_APPROVAL_TO_APPLICANT : ProgressTracker.Step("Sending letter of credit application approval to Applicant")

    object SENDING_LOC_TO_BENEFICIARY : ProgressTracker.Step("Sending letter of credit to beneficiary")
    object WAITING_DOC_PRESENTATION : ProgressTracker.Step("Waiting for document presentation from buyer")
    object VALIDATING_DOC_PRESENTATION : ProgressTracker.Step("Validating document presentation from buyer")
    object NOTARIZING_DOC_PRESENTATION : ProgressTracker.Step("Sending letter of credit doc presentation to Notary")
    object WAITING_FOR_DOC_PRESENTATION_SIGNATURE : ProgressTracker.Step("Waiting for letter of credit doc presentation's sig. from buyer")
    object FINALIZING_DOC_PRESENTATION : ProgressTracker.Step("Finalizing letter of credit doc presentation")

    object WAITING_FOR_PAYMENT : ProgressTracker.Step("Waiting from loc from applicant")
    object VERIFYING_PAYMENT : ProgressTracker.Step("Verifying loc from applicant")
    object SENDING_PAYMENT_SIG : ProgressTracker.Step("Sending loc tx signature to applicant")



    class elocApplicationProtocol(val sessionID: Long,
                                  val buyerSessionID: Long,
                                  val buyer: SingleMessageRecipient,
                                  val notary: Party,
                                  val minFees: Double,
                                  override val progressTracker: ProgressTracker = tracker() ) : ProtocolLogic<SignedTransaction>() {

        companion object {
            fun tracker() = ProgressTracker(
                    RECEIVING_LOC_APPLICATION,
                    VERIFYING_APPLICATION,
                    APPROVING_APPLICATION,
                    NOTARIZING_APPLICATION,
                    SENDING_APPROVAL_TO_APPLICANT)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val applicationState = receiveAndValidateLocApplication();
            progressTracker.currentStep = APPROVING_APPLICATION;

            val properties = LOCProperties(
                    applicationState.state.data.props.letterOfCreditApplicationID,
                    applicationState.state.data.props.applicationDate,
                    serviceHub.clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
                    applicationState.state.data.props.typeCredit,
                    applicationState.state.data.props.amount,
                    applicationState.state.data.props.invoiceRef,
                    applicationState.state.data.props.expiryDate,
                    applicationState.state.data.props.portLoading,
                    applicationState.state.data.props.portDischarge,
                    applicationState.state.data.props.goods,
                    applicationState.state.data.props.placePresentation,
                    applicationState.state.data.props.lastShipmentDate,
                    applicationState.state.data.props.periodPresentation,
                    applicationState.state.data.props.beneficiary,
                    serviceHub.storageService.myLegalIdentity,
                    applicationState.state.data.props.applicant
            )
            val txBuilder = LOC().generateIssue(notary, false, true, false, properties)

            LOCApplication().generateApprove(txBuilder, applicationState);
            val currentTime = serviceHub.clock.instant()
            txBuilder.setTime(currentTime, notary, 30.seconds)
            txBuilder.signWith(serviceHub.storageService.myLegalIdentityKey)

            serviceHub.verifyTransaction(txBuilder.toWireTransaction().toLedgerTransaction(serviceHub.identityService, serviceHub.storageService.attachments))


            progressTracker.currentStep = NOTARIZING_APPLICATION;
            val notarySignature = subProtocol(NotaryProtocol(txBuilder.toSignedTransaction(checkSufficientSignatures = false).tx));
            val fullySigned = txBuilder.toSignedTransaction(checkSufficientSignatures = false) + notarySignature;

            fullySigned.verify()
            serviceHub.recordTransactions(listOf(fullySigned));

            progressTracker.currentStep = SENDING_APPROVAL_TO_APPLICANT;
            val stateToSend = fullySigned.tx.outRef<LOCApplicationState>(txBuilder.outputStates().filter { it is LOCApplicationState }.first());
            send(ElocProtocol.LOC_APPLICATION_TOPIC, buyer, buyerSessionID, stateToSend);


            return fullySigned;

        }

        @Suspendable
        private fun receiveAndValidateLocApplication(): StateAndRef<LOCApplicationState> {
            progressTracker.currentStep = RECEIVING_LOC_APPLICATION;
            // Wait for a trade request to come in on our pre-provided session ID.
            val maybeApplication = receive<StateAndRef<LOCApplication.State>>(ElocProtocol.LOC_APPLICATION_TOPIC, sessionID)

            progressTracker.currentStep = VERIFYING_APPLICATION
            maybeApplication.validate {
                if (it.state.status != LOCApplication.Status.PENDING_ISSUER_REVIEW) {
                    throw UnacceptableApplicationException("Application status must be " + LOCApplication.Status.PENDING_ISSUER_REVIEW)
                }
                if (!serviceHub.storageService.myLegalIdentity.equals(it.state.props.issuer)) {
                    throw UnacceptableApplicationException("This node does not manage the owner key");
                }
                if (it.state.owner != it.state.props.issuer.owningKey) {
                    throw UnacceptableApplicationException("Issuer in properties is different than LoC application owner");
                }

                //TODO other checks, e.g. dates, documentsRequired, etc...

                // Check the transaction that contains the state which is being resolved.
                // We only have a hash here, so if we don't know it already, we have to ask for it.
                subProtocol(ResolveTransactionsProtocol(setOf(it.ref.txhash), buyer))
                val applicationTx = serviceHub.storageService.validatedTransactions.get(it.ref.txhash);
                if (applicationTx == null) {
                    throw UnacceptableApplicationException("Could not find application transaction")
                } else if (applicationTx.tx.attachments.isEmpty()) {
                    throw UnacceptableApplicationException("Application transaction does not contain any attachment")
                }

                //Retrieve Invoice
                subProtocol(ResolveTransactionsProtocol(setOf(it.state.props.invoiceRef.txhash), buyer))
                val invoiceIssuingTx = serviceHub.storageService.validatedTransactions.get(it.state.props.invoiceRef.txhash) ?:
                        throw UnacceptableApplicationException("Could not find invoice issuing transaction");
                val invoice = invoiceIssuingTx.tx.outputs[it.state.props.invoiceRef.index]
                if (invoice !is Invoice.State) {
                    throw UnacceptableApplicationException("Could not find invoice output state");
                }

                //Verify that the beneficiary in the application is the same as Issuer of the Invoice
                if (invoice.owner != it.state.props.beneficiary) {
                    throw UnacceptableApplicationException("The issuer of the invoice is not the beneficiary of the LoC")
                }

                //Verify invoice amount and fees
                if (invoice.amount.currency != it.state.props.amount.currency) {
                    throw UnacceptableApplicationException("Invoice and LoC application are in different currencies")
                }
                val fees = it.state.props.amount.pennies.toDouble() / invoice.amount.pennies.toDouble() - 1.0;
                if (fees < minFees) {
                    throw UnacceptableApplicationException("Fees implied by application and invoice are too low")
                }

                //Verify that goods in invoice and Loc application are consistent
                for (goodDescription in invoice.props.goods.map { it.description.trim() }.toHashSet()) {
                    val locApplicationGood = it.state.props.goods.filter { it.description.trim().equals(goodDescription, true) }
                    if (locApplicationGood.isEmpty()) {
                        throw UnacceptableDocPresentationException("Good $goodDescription is in invoice but not in LoC application")
                    }
                }

                return it;
            }
        }

    }

    class elocDocumentPresentationProtocol(val sessionID: Long,
                                           val sellerSessionID: Long,
                                           val buyer: SingleMessageRecipient,
                                           val notary: Party,
                                           val locStateAndRef: StateAndRef<LOC.State>,
                    /adrscked(notarySig)

            //GET SIGNATURE FROM SELLER
            val partiallySignedTx = tx.toSignedTransaction(checkSufficientSignatures = false)
            progressTracker.currentStep = WAITING_FOR_DOC_PRESENTATION_SIGNATURE
            val untrustedData = sendAndReceive<DigitalSignature.WithKey>(ElocProtocol.LOC_DOC_PRESENTATION_TOPIC, buyer, sellerSessionID, sessionID, partiallySignedTx);
            val signatureFromSeller = untrustedData.validate { it }
            tx.addSignatureUnchecked(signatureFromSeller)

            progressTracker.currentStep = FINALIZING_DOC_PRESENTATION
            //SIGN
            tx.signWith(serviceHub.storageService.myLegalIdentityKey)
            cashSigningPubKey.minusElement(serviceHub.storageService.myLegalIdentity)
            for (publicKey in cashSigningPubKey) {
                tx.signWith(serviceHub.keyManagementService.toKeyPair(publicKey))
            }

            val verifySignatures = true
            val fullySignedTx = tx.toSignedTransaction(checkSufficientSignatures = verifySignatures)
            fullySignedTx.verify(throwIfSignaturesAreMissing = verifySignatures)
            serviceHub.recordTransactions(listOf(fullySignedTx));

            //SEND ALL SIGNATURES TO SELLER
            send(ElocProtocol.LOC_DOC_PRESENTATION_TOPIC, buyer, sellerSessionID, fullySignedTx.sigs)

            return fullySignedTx

        }

        @Suspendable
        private fun receiveAndValidateDocPresentation(loc: LOC.State): Pair<Amount, WireTransaction> {
            val docPresentationPartial = receive<WireTransaction>(ElocProtocol.LOC_DOC_PRESENTATION_TOPIC, sessionID);
            progressTracker.currentStep = VALIDATING_DOC_PRESENTATION

            docPresentationPartial.validate {
                //verify inputs            val partialDocPresentation = createDocumentPresentation(invoiceTx.outRef(0),issueBoLtx.outRef(0), ALICE,attachment)

                if (it.inputs.size != 2) {
                    throw UnacceptableDocPresentationException("We are expecting two documents");
                }
                subProtocol(ResolveTransactionsProtocol(it.inputs.map { it.txhash }.toHashSet(), buyer))
                //Verify invoice
                var invoice: Invoice.State? = null;
                for (input in it.inputs) {
                    val originatingTx = serviceHub.storageService.validatedTransactions.get(input.txhash);
                    val state = originatingTx?.tx?.outputs?.get(input.index);
                    if (state is Invoice.State) {
                        if (!input.equals(loc.props.invoiceRef)) {
                            throw UnacceptableDocPresentationException("Invoice does not correspond to reference in the loc");
                        }
                        invoice = state;
                    }
                }
                if (invoice == null) {
                    throw UnacceptableDocPresentationException("Could not find invoice");
                }

                //Verify Bill of lading
                val output = it.outputs.single();
                if (output !is BillOfLadingAgreement.State) {
                    throw UnacceptableDocPresentationException("There isn't a BoL document in the presentation");
                } else {
                    /*if (!output.owner.equals(output.beneficiary.owningKey)){
                        throw UnacceptableDocPresentationException("BoL agreement output state is not assigned to beneficiary");
                    }*/
                    if (!output.beneficiary.equals(loc.props.appplicant)) {
                        throw UnacceptableDocPresentationException("BoL beneficiary is not the loc applicant")
                    }
                    for (goodDescription in loc.props.descriptionGoods.map { it.description.trim() }.toHashSet()) {
                        val bolGood = output.props.descriptionOfGoods.filter { it.description.trim().equals(goodDescription, true) }
                        if (bolGood.isEmpty()) {
                            throw UnacceptableDocPresentationException("Good $goodDescription is in LoC but not in BoL")
                        }
                    }
                }

                //Retrieve attachment
                subProtocol(FetchAttachmentsProtocol(setOf(it.attachments.single()), buyer));

                //TODO: Check consistency of dates: demand presentation, ecc.
                return Pair(invoice.amount, it);
            }
        }

    }

    class elocReceivePaymentProtocol(val sessionID: Long,
                                     val buyerSessionID: Long,
                                     val applicant: SingleMessageRecipient,
                                     val paidLocRef: StateRef,
                                     override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<SignedTransaction>() {
        companion object {
            fun tracker() = ProgressTracker(
                    WAITING_FOR_PAYMENT,
                    VERIFYING_PAYMENT,
                    SENDING_PAYMENT_SIG
            )

        }

        @Suspendable
        override fun call(): SignedTransaction {
            val partiallySignedTx = receiveAndValidatePayment()
            val keyPair = serviceHub.storageService.myLegalIdentityKey
            val ourSig = keyPair.signWithECDSA(partiallySignedTx.txBits)
            val fullySignedTx = partiallySignedTx.withAdditionalSignature(ourSig)
            fullySignedTx.verify()
            serviceHub.recordTransactions(listOf(fullySignedTx))

            progressTracker.currentStep = SENDING_PAYMENT_SIG
            send(ElocProtocol.LOC_PAYMENT_TOPIC, applicant, buyerSessionID, ourSig)

            return fullySignedTx
        }

        @Suspendable
        fun receiveAndValidatePayment(): SignedTransaction {
            progressTracker.currentStep = WAITING_FOR_PAYMENT
            val paymentTx = sendAndReceive<SignedTransaction>(ElocProtocol.LOC_PAYMENT_TOPIC,
                    applicant,
                    buyerSessionID,
                    sessionID,
                    serviceHub.storageService.validatedTransactions.get(paidLocRef.txhash)!!)

            paymentTx.validate {

                progressTracker.currentStep = VERIFYING_PAYMENT

                val missingSigs = it.verify(throwIfSignaturesAreMissing = false)

                val locOutput = it.tx.outputs.filter { it is LOC.State }.single() as LOC.State
                if (missingSigs != setOf(locOutput.props.issuingbank.owningKey)) {
                    throw SignatureException("The set of missing signatures is not as expected: $missingSigs")
                }

                if (!it.tx.inputs.contains(paidLocRef)) {
                    throw UnacceptablePaymentException("The loc transaction does not reference the paid BoL (ref ${paidLocRef}) in its inputs")
                }

                subProtocol(ResolveTransactionsProtocol(it.tx.inputs.map { it.txhash }.toHashSet(), applicant))

                serviceHub.verifyTransaction(it.tx.toLedgerTransaction(serviceHub.identityService, serviceHub.storageService.attachments))

                val expectedAmount = locOutput.props.amount
                val receivedAmount = it.tx.outputs.filterIsInstance<Cash.State>().filter { serviceHub.storageService.myLegalIdentity.owningKey.equals(it.owner) }.map { it.amount }.sumOrThrow()

                if (!expectedAmount.currency.equals(receivedAmount.currency)) {
                    throw UnacceptablePaymentException("Different currency. Expected: ${expectedAmount.currency}, Received: ${receivedAmount.currency}")
                }

                if (expectedAmount.pennies > receivedAmount.pennies) {
                    throw UnacceptablePaymentException("Payment too low. Expected: ${expectedAmount.pennies}, Received: ${receivedAmount.pennies}")
                }

                return it

            }
        }
    }

}*/