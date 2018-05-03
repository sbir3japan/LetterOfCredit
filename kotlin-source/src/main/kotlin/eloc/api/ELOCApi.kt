package eloc.api

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
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR

@Path("loc")
class ELOCApi(val services: CordaRPCOps) {
    private val me = services.nodeInfo().legalIdentities.first()
    private val myLegalName = me.name
    private val SERVICE_NODE_NAMES = listOf(CordaX500Name("Notary", "London", "GB"),
            CordaX500Name("NetworkMapService", "London", "GB"))

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
     * Returns all parties registered with the network map.
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
     * Displays all LoC application states that exist in the node's vault.
     */
    @GET
    @Path("all-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocApplications(): List<Pair<String, LocAppDataSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        return listLOCApplications(states)
    }

    /**
     * Displays all LoC application states awaiting confirmation that exist in the node's vault.
     */
    @GET
    @Path("awaiting-approval")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAwaitingApprovalLocs(): List<Pair<String, LocAppDataSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        val statesWithCorrectStatus = states.filter { it.state.data.status == LOCApplicationStatus.PENDING_ISSUER_REVIEW }
        return listLOCApplications(statesWithCorrectStatus)
    }

    /**
     * Displays all approved LoC application states that exist in the node's vault.
     */
    @GET
    @Path("active")
    @Produces(MediaType.APPLICATION_JSON)
    fun getActiveLocs(): List<Pair<String, LocAppDataSummary>> {
        val states = services.vaultQueryBy<LOCApplicationState>().states
        val statesWithCorrectStatus = states.filter { it.state.data.status == LOCApplicationStatus.APPROVED }
        return listLOCApplications(statesWithCorrectStatus)
    }

    /**
     * Fetches LoC application state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLocApp(@QueryParam(value = "ref") ref: String): Response {
        val appState = services.vaultQueryBy<LOCApplicationState>().states.find { it.ref.txhash.toString() == ref }
                ?: return Response.status(BAD_REQUEST).entity("Letter-of-credit application for ref $ref not found.").build()

        val locApplication = locApplicationStateToLocApplicationFormData(appState.state.data)
        locApplication.txRef = ref

        return Response.ok(locApplication, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Displays all LoC states that exist in the node's vault.
     */
    @GET
    @Path("all")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocs(): List<Pair<String, LocDataA>> {
        val states = services.vaultQueryBy<LOCState>().states
        return states.map {
            Pair(it.ref.toString(), locStateToLocDataA(it.state.data))
        }
    }

    /**
     * Fetches LoC state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLoc(@QueryParam(value = "ref") ref: String): Response {
        val locState = services.vaultQueryBy<LOCState>().states.find { it.ref.txhash.toString() == ref }
                ?: return Response.status(BAD_REQUEST).entity("Letter-of-credit for ref $ref not found.").build()

        val loc = locStateToLocDataB(locState.state.data)
        loc.txRef = ref
        return Response.ok(loc, MediaType.APPLICATION_JSON).build()
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
    fun getInvoice(@QueryParam(value = "ref") ref: String): Response {
        val states = services.vaultQueryBy<InvoiceState>().states
        val stateAndRef = states.find { it.state.data.props.invoiceID == ref }
                ?: return Response.status(BAD_REQUEST).entity("Invoice for ref $ref not found.").build()
        return Response.ok(stateAndRef.state.data, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Fetches bill of lading state that matches ref from the node's vault.
     */
    @GET
    @Path("get-bol")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBol(@QueryParam(value = "ref") ref: String): Response {
        val states = services.vaultQueryBy<BillOfLadingState>().states
        val state = states.find { it.state.data.props.billOfLadingID == ref }
                ?: return Response.status(BAD_REQUEST).entity("Invoice for ref $ref not found.").build()

        return Response.ok(state.state.data, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Fetches packing list state that matches ref from the node's vault.
     */
    @GET
    @Path("get-packing-list")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPackingList(@QueryParam(value = "ref") ref: String): Response {
        val states = services.vaultQueryBy<PackingListState>().states
        val state = states.find { it.state.data.props.orderNumber == ref }
                ?: return Response.status(BAD_REQUEST).entity("Invoice for ref $ref not found.").build()

        return Response.ok(state.state.data, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Fetches events concerning bill of lading state that matches ref.
     */
    @GET
    @Path("get-bol-events")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBolEvents(@QueryParam(value = "ref") ref: String): List<Pair<Party, String>> {
        return services.startFlow(::BillOfLadingTimeline, ref).returnValue.getOrThrow()
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
        states.forEach {
            when (it.data.status) {
                LOCApplicationStatus.PENDING_ISSUER_REVIEW -> awaitingApproval++
                LOCApplicationStatus.PENDING_ADVISORY_REVIEW -> awaitingApproval++
                LOCApplicationStatus.APPROVED -> active++
                LOCApplicationStatus.REJECTED -> rejected++
            }
        }

        return LocStats(awaitingApproval, active, awaitingPayment)
    }

    @POST
    @Path("apply-for-loc")
    fun applyForLoc(loc: LocAppFormData): Response {
        val beneficiary = services.partiesFromName(loc.beneficiary, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.beneficiary} not found.").build()
        val issuing = services.partiesFromName(loc.issuer, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.issuer} not found.").build()
        val advising = services.partiesFromName(loc.advisingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.advisingBank} not found.").build()

        val applicationProps = locApplicationFormDataToLocApplicationProperties(loc, me, beneficiary, issuing, advising)

        val application = LOCApplicationState(
                owner = me,
                issuer = issuing,
                status = LOCApplicationStatus.PENDING_ISSUER_REVIEW,
                props = applicationProps,
                purchaseOrder = null)

        val result = services.startFlow(::Apply, application).returnValue.getOrThrow()

        return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
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

        val props = billOfLadingDataToBillOfLadingProperties(billOfLading, me)

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
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${packingList.buyerName} not found.").build()
        val advisingBank = services.partiesFromName(packingList.advisingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${packingList.advisingBank} not found.").build()
        val issuingBank = services.partiesFromName(packingList.issuingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${packingList.issuingBank} not found.").build()

        val plProperties = packingListDataToPackingListProperties(packingList)

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
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${invoice.buyerName} not found.").build()

        val invoiceProperties = invoiceDataToInvoiceProperties(invoice)

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

private fun listLOCApplications(states: List<StateAndRef<LOCApplicationState>>): List<Pair<String, LocAppDataSummary>> {
    return states.map {
        Pair(it.ref.toString(), locApplicationStateToLocApplicationDataSummary(it.state.data))
    }
}