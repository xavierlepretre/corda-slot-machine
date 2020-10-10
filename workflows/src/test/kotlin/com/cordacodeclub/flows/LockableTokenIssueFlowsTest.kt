package com.cordacodeclub.flows

import com.cordacodeclub.flows.GetNotaryAndCasino.Companion.configPlayerHostKey
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class LockableTokenIssueFlowsTest {
    private lateinit var network: MockNetwork
    private lateinit var issuerNode: StartedMockNode
    private lateinit var issuerNodeParty: Party
    private lateinit var issuer: AbstractParty
    private lateinit var holderNode: StartedMockNode
    private lateinit var holderNodeParty: Party
    private lateinit var holder1: AbstractParty
    private lateinit var holder2: AbstractParty
    private lateinit var otherNode: StartedMockNode
    private lateinit var otherNodeParty: Party

    @Before
    fun setup() {
        val holderHostName = "O=Holder, L=Paris, C=FR"
        network = MockNetwork(MockNetworkParameters()
                .withCordappsForAllNodes(listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows"),
                        TestCordapp.findCordapp("com.cordacodeclub.contracts"),
                        TestCordapp.findCordapp("com.cordacodeclub.flows")
                                .withConfig(mapOf(configPlayerHostKey to holderHostName)))))
        issuerNode = network.createPartyNode(CordaX500Name.parse("O=Issuer, L=London, C=GB"))
        issuerNodeParty = issuerNode.info.legalIdentities.first()
        holderNode = network.createPartyNode(CordaX500Name.parse(holderHostName))
        holderNodeParty = holderNode.info.legalIdentities.first()
        otherNode = network.createPartyNode()
        otherNodeParty = otherNode.info.legalIdentities.first()
        network.runNetwork()
        prepareIssuerAndHolders()
    }

    private fun prepareIssuerAndHolders() {
        issuer = issuerNode.startFlow(CreateAccount("issuer"))
                .also { network.runNetwork() }
                .get().state.data
                .let { issuerNode.services.createKeyForAccount(it) }
        val (holder1, holder2) = listOf("holder1", "holder2")
                .map { holderNode.startFlow(CreateAccount(it)) }
                .also { network.runNetwork() }
                .map { it.get().state.data }
                .map { holderNode.services.createKeyForAccount(it) }
                .onEach {
                    holderNode.startFlow(SyncKeyMappingInitiator(issuerNodeParty, listOf(it)))
                            .also { network.runNetwork() }
                            .get()
                }
        this.holder1 = holder1
        this.holder2 = holder2
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `can create token issue tx`() {
        val issueTx = issuerNode.startFlow(LockableTokenFlows.Issue.Initiator(
                network.defaultNotaryIdentity, holder1, 100L, issuer))
                .also { network.runNetwork() }
                .get()
        val outputs = issueTx.coreTransaction.outputStates
        assertEquals(1, outputs.size)
        assertEquals(LockableTokenState(holder1, issuer, Amount(100L, LockableTokenType)), outputs.single())
    }

    @Test
    fun `can create several token holders issue tx`() {
        val issueTx = issuerNode.startFlow(LockableTokenFlows.Issue.Initiator(
                network.defaultNotaryIdentity,
                listOf(holder1 to 100L, holder2 to 200L),
                issuer))
                .also { network.runNetwork() }
                .get()
        val outputs = issueTx.coreTransaction.outputStates
        assertEquals(2, outputs.size)
        assertEquals(LockableTokenState(holder1, issuer, Amount(100L, LockableTokenType)), outputs[0])
        assertEquals(LockableTokenState(holder2, issuer, Amount(200L, LockableTokenType)), outputs[1])
    }

    @Test
    fun `can create several tokens issue tx`() {
        val issueTx = issuerNode.startFlow(LockableTokenFlows.Issue.Initiator(
                network.defaultNotaryIdentity,
                listOf(holder1 to 100L, holder1 to 200L),
                issuer))
                .also { network.runNetwork() }
                .get()
        val outputs = issueTx.coreTransaction.outputStates
        assertEquals(2, outputs.size)
        assertEquals(LockableTokenState(holder1, issuer, Amount(100L, LockableTokenType)), outputs[0])
        assertEquals(LockableTokenState(holder1, issuer, Amount(200L, LockableTokenType)), outputs[1])
    }

    @Test
    fun `can find tx and tokens in holder node`() {
        val issueTx = issuerNode.startFlow(LockableTokenFlows.Issue.Initiator(
                network.defaultNotaryIdentity, holder1, 100L, issuer))
                .also { network.runNetwork() }
                .get()
        val output = issueTx.coreTransaction.outRef<LockableTokenState>(0)
        for (node in listOf(issuerNode, holderNode)) {
            assertEquals(issueTx, node.services.validatedTransactions.getTransaction(issueTx.id))
            val vaultTokens = node.transaction {
                node.services.vaultService.queryBy(LockableTokenState::class.java).states
            }
            assertEquals(1, vaultTokens.size)
            assertEquals(output, vaultTokens.single())
        }
    }

    @Test
    fun `can beg for tokens and find tokens in vaults`() {
        issuerNode.startFlow(SyncKeyMappingInitiator(holderNodeParty, listOf(issuer)))
                .also { network.runNetwork() }
                .get()
        val issueTx = holderNode.startFlow(LockableTokenFlows.Beg.Initiator(
                LockableTokenFlows.Beg.Request(network.defaultNotaryIdentity, holder1, issuer)))
                .also { network.runNetwork() }
                .get()
        val output = issueTx.coreTransaction.outRef<LockableTokenState>(0)
        assertEquals(LockableTokenFlows.Issue.automaticAmount, output.state.data.amount.quantity)
        for (node in listOf(issuerNode, holderNode)) {
            assertEquals(issueTx, node.services.validatedTransactions.getTransaction(issueTx.id))
            val vaultTokens = node.transaction {
                node.services.vaultService.queryBy(LockableTokenState::class.java).states
            }
            assertEquals(1, vaultTokens.size)
            assertEquals(output, vaultTokens.single())
        }
    }

    @Test
    fun `cannot beg from wrong host`() {
        issuerNode.startFlow(SyncKeyMappingInitiator(otherNodeParty, listOf(issuer)))
                .also { network.runNetwork() }
                .get()
        val error = assertThrows<FlowException> {
            otherNode.startFlow(LockableTokenFlows.Beg.Initiator(
                    LockableTokenFlows.Beg.Request(network.defaultNotaryIdentity, otherNodeParty, issuer)))
                    .also { network.runNetwork() }
                    .getOrThrow()
        }
        assertEquals("Your host is not allowed to beg for tokens", error.message)
    }

    @Test
    fun `when beg for tokens instead moves and find tokens in vaults`() {
        issuerNode.startFlow(SyncKeyMappingInitiator(holderNodeParty, listOf(issuer)))
                .also { network.runNetwork() }
                .get()
        val issueTx = issuerNode.startFlow(LockableTokenFlows.Issue.Initiator(network.defaultNotaryIdentity,
                issuer, LockableTokenFlows.Issue.automaticAmount, issuer))
                .also { network.runNetwork() }
                .get()
        val moveTx = holderNode.startFlow(LockableTokenFlows.Beg.Initiator(
                LockableTokenFlows.Beg.Request(network.defaultNotaryIdentity, holder1, issuer)))
                .also { network.runNetwork() }
                .get()

        val issued = issueTx.tx.outRef<LockableTokenState>(0)

        assertEquals(1, moveTx.tx.inputs.size)
        assertEquals(issued.ref, moveTx.tx.inputs.single())

        val output = moveTx.coreTransaction.outRef<LockableTokenState>(0)
        assertEquals(LockableTokenFlows.Issue.automaticAmount, output.state.data.amount.quantity)
        for (node in listOf(issuerNode, holderNode)) {
            assertEquals(moveTx, node.services.validatedTransactions.getTransaction(moveTx.id))
            val vaultTokens = node.transaction {
                node.services.vaultService.queryBy(LockableTokenState::class.java).states
            }
            assertEquals(1, vaultTokens.size)
            assertEquals(output, vaultTokens.single())
        }
    }

}