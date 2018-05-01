package eloc.api

import eloc.contract.LOCApplication
import eloc.contract.LocDataStructures
import eloc.flow.LOCApplicationFlow.Apply
import eloc.flow.LOCApprovalFlow
import eloc.flow.documents.BillOfLadingFlow
import eloc.flow.documents.BillOfLadingTimeline
import eloc.flow.documents.InvoiceFlow
import eloc.flow.documents.PackingListFlow
import eloc.flow.loc.AdvisoryPaymentFlow
import eloc.flow.loc.IssuerPaymentFlow
import eloc.flow.loc.SellerPaymentFlow
import eloc.flow.loc.ShippingFlow
import eloc.state.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
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
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR

val SERVICE_NODE_NAMES = listOf(CordaX500Name("Notary", "London", "GB"),
        CordaX500Name("NetworkMapService", "London", "GB"))

@Path("loc")
class ELOCApi(val services: CordaRPCOps) {
    private val me = services.nodeInfo().legalIdentities.first()
    private val myLegalName = me.name

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
     * Returns all parties registered with the network map. These names can be used to look up identities
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
     * Get all states from the node's vault.
     */
    @GET
    @Path("vault")
    @Produces(MediaType.APPLICATION_JSON)
    fun getVault(): Pair<List<StateAndRef<ContractState>>, List<StateAndRef<ContractState>>> {
        val unconsumedStates = services.vaultQueryBy<ContractState>(QueryCriteria.VaultQueryCriteria()).states
        val consumedStates = services.vaultQueryBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)).states
        return Pair(unconsumedStates, consumedStates)
    }

    /**
     * Until WildFire is integrated, we can self-issue cash
     */
    @GET
    @Path("issue-cash")
    @Produces(MediaType.APPLICATION_JSON)
    fun issueCash(): Response {
        val notary = services.notaryIdentities().firstOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("Could not find a notary.").type(MediaType.APPLICATION_JSON).build()
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(10000000.DOLLARS, issueRef, notary)

        val flowHandle = services.startFlowDynamic(CashIssueFlow::class.java, issueRequest)
        val result = flowHandle.use { it.returnValue.getOrThrow() }
        return Response.ok(result.stx.tx.outputs.single().data).build()
    }

    /**
     * Get the node's current cash balances.
     */
    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCashBalances(): Map<Currency, Amount<Currency>> {
        val balance = services.getCashBalances()
        return if (balance.isEmpty()) {
            mapOf(Pair(Currency.getInstance("USD"), 0.DOLLARS))
        } else {
            balance
        }
    }

    /**
     * Displays all letter-of-credit application states that exist in the node's vault.
     */
    @GET
    @Path("all-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocApplications(): List<Pair<String, LocSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        return listLOCApplications(states)
    }

    /**
     * Displays all active LoC states that exist in the node's vault.
     */
    @GET
    @Path("all")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocs(): List<Pair<String, LocStateProperties>> {
        val states = services.vaultQueryBy<LOCState>().states
        return listLOC(states)
    }

    /**
     * Fetches LoC application state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLocApp(@QueryParam(value = "ref") ref: String): Response {
        val appState = services.vaultQueryBy<LOCApplicationState>().states.find { it.ref.txhash.toString() == ref }
                ?: return Response.status(BAD_REQUEST).entity("Letter-of-credit application state not found for this reference.").type(MediaType.APPLICATION_JSON).build()

        val locApplication = letterOfCreditApplicationStateToLetterOfCreditApplication(appState.state.data)
        locApplication.txRef = ref

        return Response.ok(locApplication, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Fetches LoC state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLoc(@QueryParam(value = "ref") ref: String): Response {
        val locState = services.vaultQueryBy<LOCState>().states.find { it.ref.txhash.toString() == ref }
                ?: return Response.status(BAD_REQUEST).entity("Letter-of-credit state not found for this reference.").type(MediaType.APPLICATION_JSON).build()

        val loc = letterOfCreditStateToLetterOfCredit(locState.state.data)
        loc.txRef = ref
        return Response.ok(loc, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Displays all LoC states awaiting confirmation that exist in the node's vault.
     */
    @GET
    @Path("awaiting-approval")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAwaitingApprovalLocs(): List<Pair<String, LocSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        val statesWithCorrectStatus = states.filter { it.state.data.status == LOCApplication.Status.PENDING_ISSUER_REVIEW }
        return listLOCApplications(statesWithCorrectStatus)
    }

    /**
     * Displays all active LoC states that exist in the node's vault.
     */
    @GET
    @Path("active")
    @Produces(MediaType.APPLICATION_JSON)
    fun getActiveLocs(): List<Pair<String, LocSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        val statesWithCorrectStatus = states.filter { it.state.data.status == LOCApplication.Status.APPROVED }
        return listLOCApplications(statesWithCorrectStatus)
    }

    /**
     * Displays all invoice states that exist in the node's vault.
     */
    @GET
    @Path("invoices")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvoices(): List<InvoiceState> {
        return services.vaultQueryBy<InvoiceState>().states.map { it.state.data }
    }

    /**
     * Fetches invoice state that matches ref from the node's vault.
     */
    @GET
    @Path("get-invoice")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvoice(@QueryParam(value = "ref") ref: String): InvoiceState? {
        return services.vaultQueryBy<InvoiceState>().states
                .filter { it.state.data.props.invoiceID == ref }.map { it.state.data }.firstOrNull()
    }

    /**
     * Fetches bill of lading state that matches ref from the node's vault.
     */
    @GET
    @Path("get-bol")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBol(@QueryParam(value = "ref") ref: String): BillOfLadingState? {
        return services.vaultQueryBy<BillOfLadingState>().states
                .filter { it.state.data.props.billOfLadingID == ref }.map { it.state.data }.firstOrNull()
    }

    /**
     * Fetches bill of lading states that matches ref from the node's vault.
     */
    @GET
    @Path("get-bol-events")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBolEvents(@QueryParam(value = "ref") ref: String): List<Pair<Party, String>> {
        return services.startFlow(::BillOfLadingTimeline, ref)
                .returnValue
                .getOrThrow()
    }

    /**
     * Fetches packing list state that matches ref from the node's vault.
     */
    @GET
    @Path("get-packing-list")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPackingList(@QueryParam(value = "ref") ref: String): PackingListState? {
        return services.vaultQueryBy<PackingListState>().states
                .filter { it.state.data.props.orderNumber == ref }.map { it.state.data }.firstOrNull()
    }

    @POST
    @Path("apply-for-loc")
    fun applyForLoc(loc: LocApp): Response {
        val beneficiary = services.partiesFromName(loc.beneficiary, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.beneficiary} not found.").build()
        val issuing = services.partiesFromName(loc.issuer, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.issuer} not found.").build()
        val advising = services.partiesFromName(loc.advisingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.advisingBank} not found.").build()

        val applicationProps = extractLocApplicationProperties(loc, me, beneficiary, issuing, advising)

        val application = LOCApplicationState(
                owner = me,
                issuer = issuing,
                status = LOCApplication.Status.PENDING_ISSUER_REVIEW,
                props = applicationProps,
                purchaseOrder = null)

        val result = services.startFlow(::Apply, application).returnValue.getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("loc-stats")
    @Produces(MediaType.APPLICATION_JSON)
    fun locStats(): LocStats {
        var awaitingApproval = 0
        var active = 0
        var awaitingPayment = 0
        var rejected = 0

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
    fun submitBol(billOfLading: BillOfLadingData): Response {
        // Check bill of lading hasn't already been added.
        if (services.vaultQueryBy<BillOfLadingState>().states.filter { it.state.data.props.billOfLadingID == billOfLading.billOfLadingId }.count() > 0) {
            return Response.accepted().entity("Bill of Lading already added").build()
        }

        val buyer = services.partiesFromName(billOfLading.buyer, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${billOfLading.buyer} not found.").build()
        val advisingBank = services.partiesFromName(billOfLading.advisingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${billOfLading.advisingBank} not found.").build()
        val issuingBank = services.partiesFromName(billOfLading.issuingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${billOfLading.issuingBank} not found.").build()

        val props = extractBillOfLadingProperties(billOfLading, me)

        val state = BillOfLadingState(me, buyer, advisingBank, issuingBank, Instant.now(), props)

        val result = services.startFlow(BillOfLadingFlow::UploadAndSend, state).returnValue.getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @POST
    @Path("submit-pl")
    fun submitPackingList(packingList: PackingListData): Response {

        // Check bill of lading hasn't already been added.
        if (services.vaultQueryBy<PackingListState>().states.filter { it.state.data.props.billOfLadingNumber == packingList.billOfLadingNumber }.count() > 0) {
            return Response.accepted().entity("Packing List already added").build()
        }

        val buyer = services.partiesFromName(packingList.buyerName, exactMatch = false).singleOrNull()
                ?: throw RuntimeException("${packingList.buyerName} not found.")
        val advisingBank = services.partiesFromName(packingList.advisingBank, exactMatch = false).singleOrNull()
                ?: throw RuntimeException("${packingList.advisingBank} not found.")
        val issuingBank = services.partiesFromName(packingList.issuingBank, exactMatch = false).singleOrNull()
                ?: throw RuntimeException("${packingList.issuingBank} not found.")

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

        val state = PackingListState(buyer, me, advisingBank, issuingBank, eloc.contract.PackingList.Status.DRAFT, plProperties)

        val result = services.startFlow(PackingListFlow::UploadAndSend, state)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("approve-loc")
    fun approveLOCApplication(@QueryParam(value = "ref") ref: String): Response {

        val stateAndRef = services.vaultQueryBy<LOCApplicationState>().states.filter {
            it.ref.txhash.toString() == ref
        }.first()

        val result = services.startFlow(LOCApprovalFlow::Approve, stateAndRef.ref)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @POST
    @Path("create-trade")
    fun createTrade(invoice: InvoiceData): Response {
        val buyer = services.partiesFromName(invoice.buyerName, exactMatch = false).firstOrNull()

        if (buyer == null) {
            throw RuntimeException("$invoice.buyer not found.")
        }

        val invoiceProperties = InvoiceProperties(
                invoiceID = invoice.invoiceId,
                seller = LocDataStructures.Company(invoice.sellerName, invoice.sellerAddress, ""),
                buyer = LocDataStructures.Company(invoice.buyerName, invoice.buyerAddress, ""),
                invoiceDate = LocalDate.parse(invoice.invoiceDate.substringBefore('T')),
                term = invoice.term.toLong(),
                attachmentHash = SecureHash.randomSHA256(),
                goods = listOf(LocDataStructures.PricedGood(invoice.goodsDescription, invoice.goodsPurchaseOrderRef, invoice.goodsQuantity,
                        invoice.goodsUnitPrice.DOLLARS, LocDataStructures.Weight(invoice.goodsGrossWeight.toDouble(),
                        LocDataStructures.WeightUnit.KG))))

        val state = InvoiceState(me, buyer, true, invoiceProperties)

        val result = services.startFlow(InvoiceFlow::UploadAndSend, buyer, state)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("ship")
    fun ship(@QueryParam(value = "ref") ref: String): Response {
        val result = services.startFlow(ShippingFlow::Ship, ref)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("pay-seller")
    fun paySeller(@QueryParam(value = "locId") locId: String): Response {
        val result = services.startFlow(SellerPaymentFlow::MakePayment, locId)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("pay-adviser")
    fun payAdviser(@QueryParam(value = "locId") locId: String): Response {
        val result = services.startFlow(AdvisoryPaymentFlow::MakePayment, locId)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }

    @GET
    @Path("pay-issuer")
    fun payIssuer(@QueryParam(value = "locId") locId: String): Response {
        val result = services.startFlow(IssuerPaymentFlow::MakePayment, locId)
                .returnValue
                .getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
    }
}

private fun letterOfCreditApplicationStateToLetterOfCreditApplication(state: LOCApplicationState): LocApp {
    val props = state.props

    return LocApp(
            props.letterOfCreditApplicationID,
            props.applicationDate.toString(),
            props.typeCredit.toString(),
            props.amount.quantity.toInt(),
            props.amount.token.currencyCode,
            props.expiryDate.toString(),
            props.portLoading.country,
            props.portLoading.city,
            props.portLoading.address ?: "na",
            props.portDischarge.country,
            props.portDischarge.city,
            props.portDischarge.address ?: "na",
            props.goods.first().description,
            props.goods.first().quantity,
            props.goods.first().grossWeight!!.quantity.toInt(),
            props.goods.first().grossWeight!!.unit.toString(),
            props.goods.first().unitPrice.quantity.toInt(),
            props.goods.first().purchaseOrderRef ?: "na",
            props.placePresentation.country,
            props.placePresentation.state ?: "na",
            props.placePresentation.city,
            props.lastShipmentDate.toString(),
            props.periodPresentation.days,
            props.beneficiary.name.organisation,
            props.issuer.name.organisation,
            props.applicant.name.organisation,
            props.advisingBank.name.organisation)
}

private fun letterOfCreditStateToLetterOfCredit(state: LOCState): LetterOfCredit {
    val props = state.props

    return LetterOfCredit(
            props.letterOfCreditID,
            props.applicationDate.toString(),
            props.issueDate.toString(),
            props.typeCredit.toString(),
            props.amount.quantity.toInt(),
            props.amount.token.currencyCode,
            props.expiryDate.toString(),
            props.portLoading.country,
            props.portLoading.city,
            props.portLoading.address ?: "na",
            props.portDischarge.country,
            props.portDischarge.city,
            props.portDischarge.address ?: "na",
            props.descriptionGoods.first().description,
            props.descriptionGoods.first().quantity,
            props.descriptionGoods.first().grossWeight!!.quantity.toInt(),
            props.descriptionGoods.first().grossWeight!!.unit.toString(),
            props.descriptionGoods.first().unitPrice.quantity.toInt(),
            props.descriptionGoods.first().purchaseOrderRef ?: "na",
            props.placePresentation.country,
            props.placePresentation.state ?: "na",
            props.placePresentation.city,
            props.latestShip.toString(),
            props.periodPresentation.days,
            props.beneficiary.name,
            props.issuingBank.name,
            props.applicant.name,
            props.advisingBank.name,
            state.beneficiaryPaid,
            state.advisoryPaid,
            state.issuerPaid,
            state.issued,
            state.terminated)
}

private fun listLOCApplications(states: List<StateAndRef<LOCApplicationState>>): List<Pair<String, LocSummary>> {
    return states.map {
        Pair(it.ref.toString(), extractLocApplicationStateProperties(it.state.data))
    }
}

private fun generateStatus(loc: LOCState): String {
    if (loc.terminated) return "Terminated"
    if (loc.issuerPaid) return "Issuer Paid"
    if (loc.advisoryPaid) return "Advisory Paid"
    if (loc.beneficiaryPaid) return "Seller Paid"
    if (loc.shipped) return "Shipped"
    return "Active"
}

private fun listLOC(states: List<StateAndRef<LOCState>>): List<Pair<String, LocStateProperties>> {
    return states.map {
        Pair(it.ref.toString(), extractLocStateProperties(it.state.data))
    }
}

private fun extractLocApplicationStateProperties(state: LOCApplicationState): LocSummary {
    return LocSummary(state.props.beneficiary.name.organisation,
            state.props.applicant.name.organisation,
            state.props.amount.quantity.toInt(),
            state.props.amount.token.currencyCode,
            state.props.goods.first().description,
            state.props.goods.first().purchaseOrderRef,
            state.status.toString())
}

private fun extractLocStateProperties(state: LOCState): LocStateProperties {
    return LocStateProperties(
            state.beneficiaryPaid,
            state.advisoryPaid,
            state.issuerPaid,
            state.issued,
            state.terminated,
            state.props.beneficiary.name.organisation,
            state.props.applicant.name.organisation,
            state.props.advisingBank.name.organisation,
            state.props.issuingBank.name.organisation,
            state.props.amount.quantity.toInt(),
            state.props.amount.token.currencyCode,
            state.props.descriptionGoods.first().quantity,
            state.props.descriptionGoods.first().purchaseOrderRef,
            state.props.descriptionGoods.first().description,
            generateStatus(state))
}

private fun extractLocApplicationProperties(loc: LocApp, applicant: Party, beneficiary: Party, issuing: Party, advising: Party): LOCApplicationProperties {
    return LOCApplicationProperties(
            loc.applicationId,
            LocalDate.parse(loc.applicationDate.substringBefore('T')),
            LocDataStructures.CreditType.valueOf(loc.typeCredit),
            issuing,
            beneficiary,
            applicant,
            advising,
            LocalDate.parse(loc.expiryDate.substringBefore('T')),
            LocDataStructures.Port(loc.portLoadingCountry, loc.portLoadingCity, loc.portLoadingAddress, null, null),
            LocDataStructures.Port(loc.portDischargeCountry, loc.portDischargeCity, loc.portDischargeAddress, null, null),
            LocDataStructures.Location(loc.placePresentationCountry, loc.placePresentationState, loc.placePresentationCity),
            LocalDate.parse(loc.lastShipmentDate.substringBefore('T')), // TODO does it make sense to include shipment date?
            Period.ofDays(loc.periodPresentation),
            listOf(LocDataStructures.PricedGood(
                    loc.goodsDescription,
                    loc.goodsPurchaseOrderRef,
                    loc.goodsQuantity,
                    Amount(loc.goodsUnitPrice.toLong(), Currency.getInstance(loc.currency)),
                    LocDataStructures.Weight(loc.goodsWeight.toDouble(), LocDataStructures.WeightUnit.valueOf(loc.goodsWeightUnit)))),
            ArrayList(),
            StateRef(SecureHash.randomSHA256(), 0),
            Amount(loc.amount.toLong(), Currency.getInstance(loc.currency))
    )
}

private fun extractBillOfLadingProperties(billOfLading: BillOfLadingData, seller: Party): BillOfLadingProperties {
    return BillOfLadingProperties(
            billOfLading.billOfLadingId,
            LocalDate.parse(billOfLading.issueDate.substringBefore('T')),
            // For now we'll just set the carrier owner as the seller, in future we should have a node representing the carrier
            seller,
            billOfLading.nameOfVessel,
            listOf(LocDataStructures.Good(description = billOfLading.goodsDescription, quantity = billOfLading.goodsQuantity, grossWeight = null)),
            LocDataStructures.Port(country = billOfLading.portOfLoadingCountry, city = billOfLading.portOfLoadingCity, address = billOfLading.portOfLoadingAddress, state = null, name = null),
            LocDataStructures.Port(country = billOfLading.portOfDischargeCountry, city = billOfLading.portOfDischargeCity, address = billOfLading.portOfDischargeAddress, state = null, name = null),
            LocDataStructures.Weight(
                    billOfLading.grossWeight.toDouble(),
                    LocDataStructures.WeightUnit.valueOf(billOfLading.grossWeightUnit)),
            LocalDate.parse(billOfLading.dateOfShipment.substringBefore('T')),
            null,
            LocDataStructures.Person(
                    billOfLading.notifyName,
                    billOfLading.notifyAddress,
                    billOfLading.notifyPhone),
            LocDataStructures.Company(
                    billOfLading.consigneeName,
                    billOfLading.consigneeAddress,
                    billOfLading.consigneePhone),
            LocDataStructures.Location(
                    billOfLading.placeOfReceiptCountry,
                    null,
                    billOfLading.placeOfReceiptCity),
            billOfLading.attachment
    )
}