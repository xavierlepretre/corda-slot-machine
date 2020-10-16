package com.cordacodeclub.flows

import com.cordacodeclub.states.LockableTokenState
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
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
import kotlin.test.assertNotNull

class LockableTokenInformationFlowsTest {
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
    fun `can fetch sum of issued tokens`() {
        issueToken(issuerNode, issuer, issuer, 80L)
        issueToken(issuerNode, holder1, issuer, 100L)
        issueToken(issuerNode, holder2, issuer, 150L)
        issueToken(holderNode, holder2, holder2, 180L)
        val fetched1 = holderNode.startFlow(LockableTokenFlows.Information.LocalAll())
                .also { network.runNetwork() }
                .get()
                .all
        assertEquals(2, fetched1.size)
        val summary1 = fetched1[issuer]
        assertNotNull(summary1)
        assertEquals(2, summary1!!.unlocked.size)
        assertEquals(100L, summary1.unlocked[holder1]!!.quantity)
        assertEquals(150L, summary1.unlocked[holder2]!!.quantity)
        assertEquals(0L, summary1.locked.quantity)
        val summary2 = fetched1[holder2]
        assertNotNull(summary2)
        assertEquals(1, summary2!!.unlocked.size)
        assertEquals(180L, summary2.unlocked[holder2]!!.quantity)

        val fetched2 = issuerNode.startFlow(LockableTokenFlows.Information.LocalAll())
                .also { network.runNetwork() }
                .get()
                .all
        assertEquals(1, fetched2.size)
        val summary3 = fetched2[issuer]
        assertNotNull(summary3)
        assertEquals(1, summary3!!.unlocked.size)
        assertEquals(80L, summary3.unlocked[issuer]!!.quantity)
    }

}