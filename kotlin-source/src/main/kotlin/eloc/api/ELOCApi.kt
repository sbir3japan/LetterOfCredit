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
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import org.slf4j.Logger
import java.security.PublicKey
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
     * Displays all invoice states that exist in the node's vault.
     */
    @GET
    @Path("invoices")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvoices() = getAllStatesOfTypeWithHashesAndSigs<InvoiceState>()

    /**
     * Displays all LoC application states that exist in the node's vault.
     */
    @GET
    @Path("all-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocApplications() = getAllStatesOfTypeWithHashesAndSigs<LOCApplicationState>()

    /**
     * Displays all LoC states that exist in the node's vault.
     */
    @GET
    @Path("all")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllLocs() = getAllStatesOfTypeWithHashesAndSigs<LOCState>()

    /**
     * Displays all LoC application states awaiting confirmation that exist in the node's vault.
     */
    @GET
    @Path("awaiting-approval")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAwaitingApprovalLocs() = getFilteredStatesOfTypeWithHashesAndSigs(
            { stateAndRef: StateAndRef<LOCApplicationState> -> stateAndRef.state.data.status == LOCApplicationStatus.PENDING_ISSUER_REVIEW }
    )

    /**
     * Displays all approved LoC application states that exist in the node's vault.
     */
    @GET
    @Path("active")
    @Produces(MediaType.APPLICATION_JSON)
    fun getActiveLocs() = getFilteredStatesOfTypeWithHashesAndSigs(
            { stateAndRef: StateAndRef<LOCApplicationState> -> stateAndRef.state.data.status == LOCApplicationStatus.APPROVED }
    )

    /**
     * Fetches invoice state that matches ref from the node's vault.
     */
    @GET
    @Path("get-invoice")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvoice(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
            { stateAndRef: StateAndRef<InvoiceState> -> stateAndRef.state.data.props.invoiceID == ref }
    )

    /**
     * Fetches LoC application state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc-app")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLocApp(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
            { stateAndRef: StateAndRef<LOCApplicationState> -> stateAndRef.ref.txhash.toString() == ref }
    )

    /**
     * Fetches LoC state that matches ref from the node's vault.
     */
    @GET
    @Path("get-loc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLoc(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
            { stateAndRef: StateAndRef<LOCState> -> stateAndRef.ref.txhash.toString() == ref }
    )

    /**
     * Fetches bill of lading state that matches ref from the node's vault.
     */
    @GET
    @Path("get-bol")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBol(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
            { stateAndRef: StateAndRef<BillOfLadingState> -> stateAndRef.state.data.props.billOfLadingID == ref }
    )

    /**
     * Fetches packing list state that matches ref from the node's vault.
     */
    @GET
    @Path("get-packing-list")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPackingList(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
            { stateAndRef: StateAndRef<PackingListState> -> stateAndRef.state.data.props.orderNumber == ref }
    )

    // TODO: This shouldn't require using a flow. Investigate.
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
    fun locStats(): Map<String, Int> {
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

        return mapOf(
                "awaitingApproval" to awaitingApproval,
                "active" to active,
                "awaitingPayment" to awaitingPayment)
    }

    @POST
    @Path("apply-for-loc")
    fun applyForLoc(loc: LocApplicationData): Response {
        val beneficiary = services.partiesFromName(loc.beneficiary, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.beneficiary} not found.").build()
        val issuing = services.partiesFromName(loc.issuer, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.issuer} not found.").build()
        val advising = services.partiesFromName(loc.advisingBank, exactMatch = false).singleOrNull()
                ?: return Response.status(INTERNAL_SERVER_ERROR).entity("${loc.advisingBank} not found.").build()

        val application = LOCApplicationState(
                owner = me,
                issuer = issuing,
                status = LOCApplicationStatus.PENDING_ISSUER_REVIEW,
                props = loc.toLocApplicationProperties(me, beneficiary, issuing, advising),
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

        val state = BillOfLadingState(me, buyer, advisingBank, issuingBank, Instant.now(), billOfLading.toBillOfLadingProperties(me))

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

        val state = PackingListState(buyer, me, advisingBank, issuingBank, eloc.contract.PackingList.Status.DRAFT, packingList.toPackingListProperties())

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

        val state = InvoiceState(me, buyer, true, invoice.toInvoiceProperties())

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

    /**
     * Displays all states of the given type that exist in the node's vault, along with the hashes and signatures of
     * the transaction that generated them.
     */
    private inline fun <reified T : ContractState> getAllStatesOfTypeWithHashesAndSigs(): Response {
        val states = services.vaultQueryBy<T>().states
        return mapStatesToHashesAndSigs(states)
    }

    /**
     * Displays all states of the given type that meet the filter condition and exist in the node's vault, along with
     * the hashes and signatures of the transaction that generated them.
     */
    private inline fun <reified T : ContractState> getFilteredStatesOfTypeWithHashesAndSigs(filter: (StateAndRef<T>) -> Boolean): Response {
        val states = services.vaultQueryBy<T>().states.filter(filter)
        return mapStatesToHashesAndSigs(states)
    }

    /**
     * Maps the states to the hashes and signatures of the transaction that generated them.
     */
    private fun mapStatesToHashesAndSigs(stateAndRefs: List<StateAndRef<ContractState>>): Response {
        val transactions = services.internalVerifiedTransactionsSnapshot()

        val response = stateAndRefs.map { stateAndRef ->
            val tx = transactions.find { tx -> tx.id == stateAndRef.ref.txhash }
                    ?: return Response.status(BAD_REQUEST).entity("State in vault has no corresponding transaction.").build()

            Triple(tx.id.toString(), tx.sigs.map { it.by.toStringShort() }, stateAndRef.state.data)
        }

        return Response.ok(response, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Fetches the state of the given type that meets the filter condition from the node's vault, along with the hash
     * and signatures of the transaction that generated it.
     */
    private inline fun <reified T : ContractState> getStateOfTypeWithHashAndSigs(ref: String, filter: (StateAndRef<T>) -> Boolean): Response {
        val states = services.vaultQueryBy<T>().states
        val transactions = services.internalVerifiedTransactionsSnapshot()

        val stateAndRef = states.find(filter)
                ?: return Response.status(BAD_REQUEST).entity("State for ref $ref not found.").build()
        val tx = transactions.find { tx -> tx.id == stateAndRef.ref.txhash }
                ?: return Response.status(BAD_REQUEST).entity("State in vault has no corresponding transaction.").build()
        val response = Triple(tx.id.toString(), tx.sigs.map { it.by.toStringShort() }, stateAndRef.state.data)

        return Response.ok(response, MediaType.APPLICATION_JSON).build()
    }
}