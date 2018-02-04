package eloc.api

import eloc.flow.documents.BillOfLadingTimeline
import eloc.contract.LOCApplication
import eloc.contract.LocDataStructures
import eloc.flow.*
import eloc.flow.LOCApplicationFlow.Apply
import eloc.flow.documents.BillOfLadingFlow
import eloc.flow.documents.InvoiceFlow
import eloc.flow.documents.PackingListFlow
import eloc.flow.payment.AdvisoryPaymentFlow
import eloc.flow.payment.IssuerPaymentFlow
import eloc.flow.payment.SellerPaymentFlow
import eloc.state.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import org.slf4j.Logger
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val SERVICE_NODE_NAMES = listOf(CordaX500Name("Controller",  "London", "GB"),
        CordaX500Name("NetworkMapService",  "London", "GB"))

@Path("loc")
class ELOCApi(val services: CordaRPCOps) {
    private val myLegalName = services.nodeInfo().legalIdentities.first().name

    companion object {
        val logger: Logger = loggerFor<ELOCApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName.organisation)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = services.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                .filter { it != myLegalName && it !in SERVICE_NODE_NAMES })
    }

    /**
     * Until WildFire is integrated, we can self issue cash
     */
    @GET
    @Path("issue-cash")
    @Produces(MediaType.APPLICATION_JSON)
    fun issueCash() {
        println("starting self issuance of cash")

        val notary = services.notaryIdentities().firstOrNull() ?: throw IllegalStateException("Could not find a notary.")
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(10000000.DOLLARS, issueRef, notary)

        // 2. Start flow and wait for response.
        val flowHandle = services.startFlowDynamic(CashIssueFlow::class.java, issueRequest)
        val result = flowHandle.use { it.returnValue.getOrThrow() }
        Response.Status.CREATED to result.stx.tx.outputs.single().data
    }

    /**
     * Displays all cash states that exist in the node's vault.
     */
    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCashBalances(): Map<Currency, Amount<Currency>> {
        val balance = services.getCashBalances()
        if (balance.isEmpty()) {
            return mapOf(Pair(Currency.getInstance("USD"), 0.DOLLARS))
        }

        return balance
    }

    /**
     * Displays all loc application states that exist in the node's vault.
     */
    @GET
    @Path("all-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocApplications(): List<Pair<String, LocSummary>> = listLOCApplications(null)

    /**
     * Displays all active loc states that exist in the node's vault.
     */
    @GET
    @Path("all")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocs(): List<Pair<String, LocStateSummary>> = listLOC()

    /**
     * Fetches loc application state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLocApp(@QueryParam(value = "ref") ref: String): LocApp? {
        println(ref)

        val appState = services.vaultQueryBy<LOCApplicationState>().states
                .filter { it.ref.txhash.toString().equals(ref) }.firstOrNull()

        if (appState != null) {
            println("State found")
            val props = appState.state.data.props
            val loc = LocApp(props.letterOfCreditApplicationID, props.applicationDate.toString(), props.typeCredit.toString(), props.amount.quantity.toInt(),
                    props.amount.token.currencyCode, props.expiryDate.toString(), props.portLoading.country, props.portLoading.city, props.portLoading.address ?: "na",
                    props.portDischarge.country, props.portDischarge.city, props.portDischarge.address ?: "na", props.goods.first().description, props.goods.first().quantity,
                    props.goods.first().grossWeight!!.quantity.toInt(), props.goods.first().grossWeight!!.unit.toString(), props.goods.first().unitPrice.quantity.toInt(),
                    props.goods.first().purchaseOrderRef ?: "na", props.placePresentation.country, props.placePresentation.state ?: "na",
                    props.placePresentation.city, props.lastShipmentDate.toString(), props.periodPresentation.days, props.beneficiary.name.organisation, props.issuer.name.organisation, props.applicant.name.organisation,
                    props.advisingBank.name.organisation)
            loc.txRef = ref

            return loc
        }

        println("State not found")
        return null
    }

    /**
     * Fetches loc application state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLoc(@QueryParam(value = "ref") ref: String): Loc? {
        println(ref)
        val locState = services.vaultQueryBy<LOCState>().states
                .filter { it.ref.txhash.toString().equals(ref) }.firstOrNull()

        if (locState != null) {
            println("State found")
            val state = locState.state.data
            val props = state.props
            val loc = Loc(props.letterOfCreditID, props.applicationDate.toString(), props.issueDate.toString(), props.typeCredit.toString(), props.amount.quantity.toInt(),
                    props.amount.token.currencyCode, props.expiryDate.toString(), props.portLoading.country, props.portLoading.city, props.portLoading.address ?: "na",
                    props.portDischarge.country, props.portDischarge.city, props.portDischarge.address ?: "na", props.descriptionGoods.first().description, props.descriptionGoods.first().quantity,
                    props.descriptionGoods.first().grossWeight!!.quantity.toInt(), props.descriptionGoods.first().grossWeight!!.unit.toString(), props.descriptionGoods.first().unitPrice.quantity.toInt(),
                    props.descriptionGoods.first().purchaseOrderRef ?: "na", props.placePresentation.country, props.placePresentation.state ?: "na",
                    props.placePresentation.city, props.latestShip.toString(), props.periodPresentation.days, props.beneficiary.name, props.issuingBank.name, props.applicant.name,
                    props.advisingBank.name, state.beneficiaryPaid, state.advisoryPaid, state.issuerPaid, state.issued, state.terminated)
            loc.txRef = ref

            return loc
        }

        println("State not found")
        return null
    }

    /**
     * Displays all loc states awaiting confirmation that exist in the node's vault.
     */
    @GET
    @Path("awaiting-approval")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAwaitingApprovalLocs(): List<Pair<String, LocSummary>> = listLOCApplications(LOCApplication.Status.PENDING_ISSUER_REVIEW)

    /**
     * Displays all active loc states that exist in the node's vault.
     */
    @GET
    @Path("active-applications")
    @Produces(MediaType.APPLICATION_JSON)
    fun getActiveLocsApps(): List<Pair<String, LocSummary>> = listLOCApplications(LOCApplication.Status.APPROVED)

    /**
     * Displays all active loc states that exist in the node's vault.
     */
    @GET
    @Path("active")
    @Produces(MediaType.APPLICATION_JSON)
    fun getActiveLocs(): List<Pair<String, LocSummary>> = listLOCApplications(LOCApplication.Status.APPROVED)

    /**
     * Displays all loc states awaiting payment that exist in the node's vault.
     */
    @GET
    @Path("awaiting-payment")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAwaitingPaymentLocs(): List<Pair<String, LocSummary>> = listLOCApplications(LOCApplication.Status.APPROVED)

    /**
     * Displays all invoice states that exist in the node's vault.
     */
    @GET
    @Path("invoices")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvoices(): List<InvoiceState> {
        println("fetching invoices")
        return services.vaultQueryBy<InvoiceState>().states.map { it.state.data }
    }

    /**
     * Fetches invoice state that matches ref from the node's vault.
     */
    @GET
    @Path("get-invoice")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvoice(@QueryParam(value = "ref") ref: String): InvoiceState? {
        println("fetching invoice with ref $ref")

        return services.vaultQueryBy<InvoiceState>().states
                .filter { it.state.data.props.invoiceID == ref }.map { it.state.data }.firstOrNull()
    }

    /**
     * Fetches bill of lading state that matches ref from the node's vault.
     */
    @GET
    @Path("get-bol")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBol(@QueryParam(value = "ref") ref: String): BillofLadingState? {
        println("fetching bol with ref $ref")
        return services.vaultQueryBy<BillofLadingState>().states
                .filter { it.state.data.props.billOfLadingID == ref }.map { it.state.data }.firstOrNull()
    }

    /**
     * Fetches invoice state that matches ref from the node's vault.
     */
    @GET
    @Path("get-bol-events")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBolEvents(@QueryParam(value = "ref") ref: String): List<Pair<Party, String>> {
        println("fetching bol states with ref $ref")

        val result: List<Pair<Party, String>> = services.startFlow(::BillOfLadingTimeline, ref)
                .returnValue
                .getOrThrow()

        println("Ending flow")

        return result
    }

    /**
     * Fetches packing list state that matches ref from the node's vault.
     */
    @GET
    @Path("get-packing-list")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPackingList(@QueryParam(value = "ref") ref: String): PackingListState? {
        println("fetching packing list with ref $ref")
        return services.vaultQueryBy<PackingListState>().states
                .filter { it.state.data.props.orderNumber == ref }.map { it.state.data }.firstOrNull()
    }

    private fun listLOCApplications(status: LOCApplication.Status?): List<Pair<String, LocSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        if (status == null) {
            return states.map {
                Pair(it.ref.toString(),
                        LocSummary(it.state.data.props.beneficiary.name.organisation,
                                it.state.data.props.applicant.name.organisation,
                                it.state.data.props.amount.quantity.toInt(),
                                it.state.data.props.amount.token.currencyCode,
                                it.state.data.props.goods.first().description,
                                it.state.data.props.goods.first().purchaseOrderRef,
                                it.state.data.status.toString()))
            }
        }
        return states.filter {
            it.state.data.status == status
        }.map {
            Pair(it.ref.toString(),
                    LocSummary(it.state.data.props.beneficiary.name.organisation,
                            it.state.data.props.applicant.name.organisation,
                            it.state.data.props.amount.quantity.toInt(),
                            it.state.data.props.amount.token.currencyCode,
                            it.state.data.props.goods.first().description,
                            it.state.data.props.goods.first().purchaseOrderRef,
                            it.state.data.status.toString()))
        }
    }

    private fun listLOC(): List<Pair<String, LocStateSummary>> {
        val states = services.vaultQueryBy<LOCState>().states
        return states.map {
            Pair(it.ref.toString(),
                    LocStateSummary(
                            it.state.data.beneficiaryPaid,
                            it.state.data.advisoryPaid,
                            it.state.data.issuerPaid,
                            it.state.data.issued,
                            it.state.data.terminated,
                            it.state.data.props.beneficiary.name.organisation,
                            it.state.data.props.applicant.name.organisation,
                            it.state.data.props.advisingBank.name.organisation,
                            it.state.data.props.issuingBank.name.organisation,
                            it.state.data.props.amount.quantity.toInt(),
                            it.state.data.props.amount.token.currencyCode,
                            it.state.data.props.descriptionGoods.first().quantity,
                            it.state.data.props.descriptionGoods.first().purchaseOrderRef,
                            it.state.data.props.descriptionGoods.first().description,
                            generateStatus(it.state.data)))
        }
    }

    @POST
    @Path("apply-for-loc")
    @Produces(MediaType.APPLICATION_JSON)
    fun applyForLoc(loc: LocApp): SignedTransaction {
        val applicantIdentity = services.nodeInfo().legalIdentities.first()
        val beneficiaryIdentity = services.partiesFromName(loc.beneficiary, exactMatch = false).singleOrNull() ?: throw RuntimeException("${loc.beneficiary} not found.")
        val issuerIdentity = services.partiesFromName(loc.issuer, exactMatch = false).singleOrNull() ?: throw RuntimeException("${loc.issuer} not found.")
        val advisingBankIdentity = services.partiesFromName(loc.advisingBank, exactMatch = false).singleOrNull() ?: throw RuntimeException("${loc.advisingBank} not found.")

        val applicationProps = LOCApplicationProperties(
                letterOfCreditApplicationID = loc.applicationId,
                applicationDate = LocalDate.parse(loc.applicationDate.substringBefore('T')),
                typeCredit = LocDataStructures.CreditType.valueOf(loc.typeCredit),
                amount = Amount(loc.amount.toLong(), Currency.getInstance(loc.currency)),
                expiryDate = LocalDate.parse(loc.expiryDate.substringBefore('T')),
                portLoading = LocDataStructures.Port(loc.portLoadingCountry, loc.portLoadingCity, loc.portLoadingAddress, null, null),
                portDischarge = LocDataStructures.Port(loc.portDischargeCountry, loc.portDischargeCity, loc.portDischargeAddress, null, null),
                goods = listOf(LocDataStructures.PricedGood(description = loc.goodsDescription,
                        quantity = loc.goodsQuantity,
                        grossWeight = LocDataStructures.Weight(loc.goodsWeight.toDouble(), LocDataStructures.WeightUnit.valueOf(loc.goodsWeightUnit)),
                        unitPrice = Amount(loc.goodsUnitPrice.toLong(), Currency.getInstance(loc.currency)),
                        purchaseOrderRef = loc.goodsPurchaseOrderRef
                )),
                placePresentation = LocDataStructures.Location(loc.placePresentationCountry, loc.placePresentationState, loc.placePresentationCity),
                lastShipmentDate = LocalDate.parse(loc.lastShipmentDate.substringBefore('T')), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(loc.periodPresentation),
                beneficiary = beneficiaryIdentity,
                issuer = issuerIdentity,
                applicant = applicantIdentity,
                advisingBank = advisingBankIdentity,
                invoiceRef = StateRef(SecureHash.randomSHA256(), 0)
        )

        val application = LOCApplicationState(
                owner = applicantIdentity,
                issuer = issuerIdentity,
                status = LOCApplication.Status.PENDING_ISSUER_REVIEW,
                props = applicationProps,
                purchaseOrder = null
        )

        println("Starting flow")
        val result = services.startFlow(::Apply, application)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return result
    }

    @GET
    @Path("loc-stats")
    @Produces(MediaType.APPLICATION_JSON)
    fun locStats(): LocStats {

        var awaitingApproval: Int = 0
        var active: Int = 0
        var awaitingPayment: Int = 0
        var rejected: Int = 0

        val states = services.vaultQueryBy<LOCApplicationState>().states.map { it.state }
        states.forEach({
            when (it.data.status) {
                LOCApplication.Status.PENDING_ISSUER_REVIEW -> awaitingApproval++
                LOCApplication.Status.PENDING_ADVISORY_REVIEW -> awaitingApproval++
                LOCApplication.Status.APPROVED -> active++
                LOCApplication.Status.REJECTED -> rejected++
            }
        })

        return LocStats(awaitingApproval, active, awaitingPayment)
    }

    @POST
    @Path("submit-bol")
    @Produces(MediaType.APPLICATION_JSON)
    fun submitBol(bol: Bol): Response {

        println("Starting Bill of Lading submission")
        val seller = services.nodeInfo().legalIdentities.first()
        val buyer = services.partiesFromName(bol.buyer, exactMatch = false).singleOrNull() ?: throw RuntimeException("${bol.buyer} not found.")
        val advisingBank = services.partiesFromName(bol.advisingBank, exactMatch = false).singleOrNull() ?: throw RuntimeException("${bol.advisingBank} not found.")
        val issuingBank = services.partiesFromName(bol.issuingBank, exactMatch = false).singleOrNull() ?: throw RuntimeException("${bol.issuingBank} not found.")

        val pros = BillOfLadingProperties(
                billOfLadingID = bol.billOfLadingId,
                issueDate = LocalDate.parse(bol.issueDate.substringBefore('T')),
                // For now we'll just set the carrier owner as the seller, in future we should have a node representing the carrier
                carrierOwner = seller,
                nameOfVessel = bol.nameOfVessel,
                descriptionOfGoods = listOf(LocDataStructures.Good(description = bol.goodsDescription, quantity = bol.goodsQuantity, grossWeight = null)),
                dateOfShipment = LocalDate.parse(bol.dateOfShipment.substringBefore('T')),
                portOfLoading = LocDataStructures.Port(country = bol.portOfLoadingCountry, city = bol.portOfLoadingCity, address = bol.portOfLoadingAddress, state = null, name = null),
                portOfDischarge = LocDataStructures.Port(country = bol.portOfDischargeCountry, city = bol.portOfDischargeCity, address = bol.portOfDischargeAddress, state = null, name = null),
                shipper = null,
                notify = LocDataStructures.Person(
                        name = bol.notifyName,
                        address = bol.notifyAddress,
                        phone = bol.notifyPhone
                ),
                consignee = LocDataStructures.Company(
                        name = bol.consigneeName,
                        address = bol.consigneeAddress,
                        phone = bol.consigneePhone
                ),
                grossWeight = LocDataStructures.Weight(
                        quantity = bol.grossWeight.toDouble(),
                        unit = LocDataStructures.WeightUnit.valueOf(bol.grossWeightUnit)
                ),
                placeOfReceipt = LocDataStructures.Location(
                        country = bol.placeOfReceiptCountry,
                        state = null,
                        city = bol.placeOfReceiptCity
                ))

        val state = BillofLadingState(seller, buyer, advisingBank, issuingBank, Instant.now(), pros)

        println("Starting flow")
        val result = services.startFlow(BillOfLadingFlow::UploadAndSend, state)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @POST
    @Path("submit-pl")
    @Produces(MediaType.APPLICATION_JSON)
    fun submitPackingList(packingList: PackingList): Response {

        val buyer = services.partiesFromName(packingList.buyerName, exactMatch = false).singleOrNull() ?: throw RuntimeException("${packingList.buyerName} not found.")
        val seller = services.nodeInfo().legalIdentities.first()
        val advisingBank = services.partiesFromName(packingList.advisingBank, exactMatch = false).singleOrNull() ?: throw RuntimeException("${packingList.advisingBank} not found.")
        val issuingBank = services.partiesFromName(packingList.issuingBank, exactMatch = false).singleOrNull() ?: throw RuntimeException("${packingList.issuingBank} not found.")

        val plProperties = PackingListProperties(
                issueDate = LocalDate.parse(packingList.issueDate.substringBefore('T')),
                orderNumber = packingList.orderNumber,
                sellersOrderNumber = packingList.sellersOrderNumber,
                transportMethod = packingList.transportMethod,
                nameOfVessel = packingList.nameOfVessel,
                billOfLadingNumber = packingList.billOfLadingNumber,
                seller = LocDataStructures.Company(
                        name = packingList.sellerName,
                        address = packingList.sellerAddress,
                        phone = ""
                ),
                buyer = LocDataStructures.Company(
                        name = packingList.buyerName,
                        address = packingList.buyerAddress,
                        phone = ""
                ),
                descriptionOfGoods = arrayListOf(
                        LocDataStructures.PricedGood(
                                description = packingList.goodsDescription,
                                purchaseOrderRef = packingList.goodsPurchaseOrderRef,
                                quantity = packingList.goodsQuantity,
                                unitPrice = packingList.goodsUnitPrice.DOLLARS,
                                grossWeight = LocDataStructures.Weight(packingList.goodsGrossWeight.toDouble(), LocDataStructures.WeightUnit.KG)
                        )
                ),
                attachmentHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() })
                )
        )

        val state = PackingListState(buyer, seller, advisingBank, issuingBank, eloc.contract.PackingList.Status.DRAFT, plProperties)

        println("Status:" + state.status)

        println("Starting flow")
        val result = services.startFlow(PackingListFlow::UploadAndSend, state)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("approve-loc")
    @Produces(MediaType.APPLICATION_JSON)
    fun approveLOCApplication(@QueryParam(value = "ref") ref: String): Response {

        val stateAndRef = services.vaultQueryBy<LOCApplicationState>().states.filter {
            it.ref.txhash.toString().equals(ref)
        }.first()

        println("Starting flow")
        val result = services.startFlow(LOCApprovalFlow::Approve, stateAndRef.ref)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @POST
    @Path("create-trade")
    @Produces(MediaType.APPLICATION_JSON)
    fun createTrade(inv: Inv): Response {

        println("Starting creation of trade")

        val seller = services.nodeInfo().legalIdentities.first()
        val buyer = services.partiesFromName(inv.buyerName, exactMatch = false).firstOrNull()

        if(buyer == null) {
            println("Could not find buyer")
            throw RuntimeException("$inv.buyer not found.")
        }

        val invoice = InvoiceProperties(
                invoiceID = inv.invoiceId,
                seller = LocDataStructures.Company(inv.sellerName, inv.sellerAddress, ""),
                buyer = LocDataStructures.Company(inv.buyerName, inv.buyerAddress, ""),
                invoiceDate = LocalDate.parse(inv.invoiceDate.substringBefore('T')),
                term = inv.term.toLong(),
                attachmentHash = SecureHash.randomSHA256(),
                goods = listOf(LocDataStructures.PricedGood(inv.goodsDescription, inv.goodsPurchaseOrderRef, inv.goodsQuantity,
                        inv.goodsUnitPrice.DOLLARS, LocDataStructures.Weight(inv.goodsGrossWeight.toDouble(),
                        LocDataStructures.WeightUnit.KG))))

        val state = InvoiceState(seller, buyer, true, invoice)
        if (state.assigned) println("State created")

        println("Starting flow")
        val result = services.startFlow(InvoiceFlow::UploadAndSend, buyer, state)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("pay-seller")
    @Produces(MediaType.APPLICATION_JSON)
    fun paySeller(@QueryParam(value = "locId") locId: String): Response {

        println("Paying seller")

        val result = services.startFlow(SellerPaymentFlow::MakePayment, locId)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("pay-adviser")
    @Produces(MediaType.APPLICATION_JSON)
    fun payAdviser(@QueryParam(value = "locId") locId: String): Response {

        println("Paying adviser")

        val result = services.startFlow(AdvisoryPaymentFlow::MakePayment, locId)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("pay-issuer")
    @Produces(MediaType.APPLICATION_JSON)
    fun payIssuer(@QueryParam(value = "locId") locId: String): Response {

        println("Paying issuer")

        val result = services.startFlow(IssuerPaymentFlow::MakePayment, locId)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    /*@POST
    @Path("claim-funds")
    @Produces(MediaType.APPLICATION_JSON)
    fun claimFunds(claim: ClaimFundsParams): String {

        val issuingX500 = CordaX500Name(claim.party,"London", "GB")

        val issuingBank = services.wellKnownPartyFromX500Name(issuingX500) ?: throw RuntimeException("${claim.party} not found.")

        println("Sending docs to issuer")
        val result = services.startFlow(LOCDemandPresentationFlow::DemandInitiator, claim.ref, claim.ref, issuingBank)
                .returnValue
                .getOrThrow()
        println("Ending flow")

        return result.message
    }*/

    private fun generateStatus(loc: LOCState): String {
        if (loc.terminated) return "Terminated"
        if (loc.issuerPaid) return "Issuer Paid"
        if (loc.advisoryPaid) return "Advisory Paid"
        if (loc.beneficiaryPaid) return "Seller Paid"

        return "Active"
    }
}

data class Inv(val invoiceId: String,
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

data class LocStats(val awaitingApproval: Int,
                    val active: Int,
                    val awaitingPayment: Int)

data class LocSummary(val beneficiary: String,
                      val applicant: String,
                      val amount: Int,
                      val currency: String,
                      val description: String,
                      val orderRef: String?,
                      val status: String)

data class LocStateSummary(val beneficiaryPaid: Boolean,
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

data class Loc(val letterOfCreditId: String,
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

data class LocApp(val applicationId: String,
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

data class Bol(val billOfLadingId: String,
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
               val issuingBank: String)

data class PackingList(val issueDate: String,
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

data class ClaimFundsParams(val ref: String,
                            val party: String)