package com.cordacodeclub.flows

import com.cordacodeclub.flows.LockableTokenFlows.Fetch.NotEnoughTokensException
import com.cordacodeclub.states.LockableTokenState
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
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
import java.util.*
import kotlin.test.assertEquals

class LockableTokenFetchFlowsTest {
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
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
//        listOf(issuerNode, holderNode).forEach { it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java) }
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
    fun `can fetch issued token of holder just equal`() {
        val issuedToken = issueToken(issuerNode, holder1, issuer, 100L)
        val fetched = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 100L, UUID.randomUUID()))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched.size)
        assertEquals(issuedToken, fetched.single())
    }

    @Test
    fun `can fetch issued token of holder just below`() {
        val issuedToken = issueToken(issuerNode, holder1, issuer, 100L)
        val fetched = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 99L, UUID.randomUUID()))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched.size)
        assertEquals(issuedToken, fetched.single())
    }

    @Test
    fun `cannot fetch issued token when not enough`() {
        issueToken(issuerNode, holder1, issuer, 200L)
        val fetched = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 201L, UUID.randomUUID()))
                .also { network.runNetwork() }
        val exception = assertThrows<NotEnoughTokensException> { fetched.getOrThrow() }
        assertEquals("Not enough tokens", exception.message)
    }

    @Test
    fun `can fetch issued tokens of holder just below`() {
        val issuedTokens = issueToken(issuerNode, listOf(holder1 to 100L, holder1 to 50L), issuer)
        val fetched = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 110L, UUID.randomUUID()))
                .also { network.runNetwork() }
                .get()
        assertEquals(2, fetched.size)
        assertEquals(issuedTokens, fetched)
    }

    @Test
    fun `cannot fetch issued tokens of holder just below`() {
        issueToken(issuerNode, listOf(holder1 to 100L, holder2 to 50L), issuer)
        val fetched = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 110L, UUID.randomUUID()))
                .also { network.runNetwork() }
        val exception = assertThrows<NotEnoughTokensException> { fetched.getOrThrow() }
        assertEquals("Not enough tokens", exception.message)
    }

    @Test
    fun `can fetch issued tokens of 2 holders just equal`() {
        val issuedTokens = issueToken(issuerNode, listOf(holder1 to 100L, holder2 to 50L), issuer)
        val fetched2 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder2, issuer, 50L, UUID.randomUUID()))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched2.size)
        assertEquals(issuedTokens[1], fetched2[0])
        val fetched1 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 100L, UUID.randomUUID()))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched1.size)
        assertEquals(issuedTokens[0], fetched1[0])
    }

    @Test
    fun `can fetch soft locked tokens if give right uuid`() {
        val issuedTokens = issueToken(issuerNode, listOf(holder1 to 100L, holder1 to 50L), issuer)
        val fetchId1 = UUID.randomUUID()
        val fetched1 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 100L, fetchId1))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched1.size)
        assertEquals(issuedTokens[0], fetched1[0])
        val fetched2 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 101L, fetchId1))
                .also { network.runNetwork() }
                .get()
        assertEquals(2, fetched2.size)
        assertEquals(issuedTokens, fetched2)
    }

    @Test
    fun `because of soft lock will fetch other token of holder just equal`() {
        val issuedTokens = issueToken(issuerNode, listOf(holder1 to 100L, holder1 to 50L), issuer)
        val fetchId1 = UUID.randomUUID()
        val fetched1 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 100L, fetchId1))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched1.size)
        assertEquals(issuedTokens[0], fetched1[0])
        val fetchId2 = UUID.randomUUID()
        val fetched2 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 50L, fetchId2))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched2.size)
        assertEquals(issuedTokens[1], fetched2[0])
        val fetched3 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 1L, UUID.randomUUID()))
                .also { network.runNetwork() }
        val exception = assertThrows<NotEnoughTokensException> { fetched3.getOrThrow() }
        assertEquals("Not enough tokens", exception.message)
        holderNode.transaction { holderNode.services.vaultService.softLockRelease(fetchId1) }
        val fetched4 = holderNode.startFlow(LockableTokenFlows.Fetch.Local(
                holder1, issuer, 1L, UUID.randomUUID()))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched4.size)
        assertEquals(issuedTokens[0], fetched4[0])
    }

}