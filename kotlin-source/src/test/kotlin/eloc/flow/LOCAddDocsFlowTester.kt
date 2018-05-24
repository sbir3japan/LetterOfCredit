package eloc.flow

import eloc.flow.documents.BillOfLadingFlow
import eloc.helpers.bolProperties
import eloc.state.BillOfLadingState
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Created by msreich on 4/13/17.
 */

class LOCAddDocsFlowTester {
    private lateinit var net: MockNetwork
    private lateinit var issuerNode: StartedMockNode
    private lateinit var beneficiaryNode: StartedMockNode
    private lateinit var advisingBankNode: StartedMockNode
    private lateinit var buyerNode: StartedMockNode

    @Before
    fun setup() {
        net = MockNetwork(listOf("eloc.contract", "net.corda.finance.contracts.asset"))
        advisingBankNode = net.createNode()
        issuerNode = net.createNode()
        beneficiaryNode = net.createNode()
        buyerNode = net.createNode()
        net.runNetwork()
    }

    @Test
    fun `record bill of lading`() {
        val beneficiary = beneficiaryNode.info.legalIdentities.first()
        val initialState = BillOfLadingState(beneficiary, beneficiary, buyerNode.info.legalIdentities.first(), advisingBankNode.info.legalIdentities.first(), issuerNode.info.legalIdentities.first(), Instant.now(), bolProperties)

        // kick off flow
        val sellerFlow = BillOfLadingFlow.UploadAndSend(initialState)
        val future = beneficiaryNode.startFlow(sellerFlow).toCompletableFuture()
        net.runNetwork()
        future.getOrThrow()

        listOf(beneficiaryNode, advisingBankNode).forEach { node ->
            val bolStates = node.transaction {
                node.services.vaultService.queryBy<BillOfLadingState>().states
            }
            assert(bolStates.count() > 0)
        }
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

}