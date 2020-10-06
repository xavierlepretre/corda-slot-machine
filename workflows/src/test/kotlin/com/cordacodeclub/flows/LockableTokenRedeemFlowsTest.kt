package com.cordacodeclub.flows

import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LockableTokenRedeemFlowsTest {
    private lateinit var network: MockNetwork
    private lateinit var issuerNode: StartedMockNode
    private lateinit var issuerNodeParty: Party
    private lateinit var issuer: AbstractParty
    private lateinit var holderNode: StartedMockNode
    private lateinit var holder1: AbstractParty
    private lateinit var holder2: AbstractParty

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters()
                .withCordappsForAllNodes(listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows"),
                        TestCordapp.findCordapp("com.cordacodeclub.contracts"),
                        TestCordapp.findCordapp("com.cordacodeclub.flows"))))
        issuerNode = network.createPartyNode()
        issuerNodeParty = issuerNode.info.legalIdentities.first()
        holderNode = network.createPartyNode()
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

    private fun issueToken(issuerNode: StartedMockNode,
                           holder: AbstractParty,
                           issuer: AbstractParty,
                           amount: Long) =
            issueToken(issuerNode, listOf(holder to amount), issuer)[0]

    private fun issueToken(issuerNode: StartedMockNode,
                           holders: List<Pair<AbstractParty, Long>>,
                           issuer: AbstractParty): List<StateAndRef<LockableTokenState>> {
        return issuerNode.startFlow(LockableTokenFlows.Issue.Initiator(
                network.defaultNotaryIdentity, holders, issuer))
                .also { network.runNetwork() }
                .get()
                .tx
                .outRefsOfType()
    }

    @Test
    fun `can redeem without change`() {
        val issuedToken = issueToken(issuerNode, holder1, issuer, 100L)
        val redeemTx = holderNode.startFlow(LockableTokenFlows.Redeem.Initiator(issuedToken, 0L))
                .also { network.runNetwork() }
                .get()
        assertEquals(issuedToken.ref, redeemTx.inputs.single())
        assertTrue(redeemTx.tx.outputs.isEmpty())
    }

    @Test
    fun `can redeem multiple without change`() {
        val issuedToken1 = issueToken(issuerNode, holder1, issuer, 100L)
        val issuedToken2 = issueToken(issuerNode, holder1, issuer, 50L)
        val redeemTx = holderNode.startFlow(LockableTokenFlows.Redeem.Initiator(
                listOf(issuedToken1, issuedToken2), 0L))
                .also { network.runNetwork() }
                .get()
        assertEquals(listOf(issuedToken1.ref, issuedToken2.ref), redeemTx.inputs)
        assertTrue(redeemTx.tx.outputs.isEmpty())
    }

    @Test
    fun `can redeem with change`() {
        val issuedToken = issueToken(issuerNode, holder1, issuer, 100L)
        val redeemTx = holderNode.startFlow(LockableTokenFlows.Redeem.Initiator(issuedToken, 20L))
                .also { network.runNetwork() }
                .get()
        assertEquals(issuedToken.ref, redeemTx.inputs.single())
        assertEquals(1, redeemTx.tx.outputs.size)
        val output = redeemTx.tx.outputsOfType<LockableTokenState>().single()
        assertEquals(LockableTokenState(holder1, issuer, Amount(20L, LockableTokenType)),
                output)
    }

    @Test
    fun `can redeem multiple with change`() {
        val issuedToken1 = issueToken(issuerNode, holder1, issuer, 100L)
        val issuedToken2 = issueToken(issuerNode, holder1, issuer, 25L)
        val redeemTx = holderNode.startFlow(LockableTokenFlows.Redeem.Initiator(
                listOf(issuedToken1, issuedToken2), 20L))
                .also { network.runNetwork() }
                .get()
        assertEquals(listOf(issuedToken1.ref, issuedToken2.ref), redeemTx.inputs)
        assertEquals(1, redeemTx.tx.outputs.size)
        val output = redeemTx.tx.outputsOfType<LockableTokenState>().single()
        assertEquals(LockableTokenState(holder1, issuer, Amount(20L, LockableTokenType)),
                output)
    }

}