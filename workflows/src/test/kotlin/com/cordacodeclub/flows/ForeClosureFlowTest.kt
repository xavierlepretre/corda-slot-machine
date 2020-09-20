package com.cordacodeclub.flows

import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.LockableTokenState
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class ForeClosureFlowTest {
    private lateinit var network: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var issuerNode: StartedMockNode
    private lateinit var issuerNodeParty: Party
    private lateinit var issuer: AbstractParty
    private lateinit var casinoNode: StartedMockNode
    private lateinit var casino1: AbstractParty
    private lateinit var casino2: AbstractParty
    private lateinit var playerNode: StartedMockNode
    private lateinit var playerNodeParty: Party
    private lateinit var player1: AbstractParty
    private lateinit var player2: AbstractParty

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                networkParameters = testNetworkParameters(listOf(), 4),
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows"),
                        TestCordapp.findCordapp("com.cordacodeclub.contracts"),
                        TestCordapp.findCordapp("com.cordacodeclub.flows")))
                // To accommodate the schedulable events.
                .withThreadPerNode(true))
        notaryNode = network.defaultNotaryNode
        issuerNode = network.createPartyNode()
        issuerNodeParty = issuerNode.info.legalIdentities.first()
        casinoNode = network.createPartyNode(CordaX500Name("Casino", "London", "GB"))
        playerNode = network.createPartyNode(CordaX500Name("Player", "Paris", "FR"))
        playerNodeParty = playerNode.info.legalIdentities.first()
        network.waitQuiescent()
        prepareIssuerAndHolders()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    private fun prepareIssuerAndHolders() {
        issuer = issuerNode.startFlow(CreateAccount("issuer"))
                .also { network.waitQuiescent() }
                .get().state.data
                .let { issuerNode.services.createKeyForAccount(it) }
        val (casino1, casino2) = listOf("casino1", "casino2")
                .map { casinoNode.startFlow(CreateAccount(it)) }
                .also { network.waitQuiescent() }
                .map { it.get().state.data }
                .map { casinoNode.services.createKeyForAccount(it) }
                .onEach {
                    casinoNode.startFlow(SyncKeyMappingInitiator(issuerNodeParty, listOf(it)))
                            .also { network.waitQuiescent() }
                            .get()
                    casinoNode.startFlow(SyncKeyMappingInitiator(playerNodeParty, listOf(it)))
                            .also { network.waitQuiescent() }
                            .get()
                }
        this.casino1 = casino1
        this.casino2 = casino2
        val (player1, player2) = listOf("player1", "player2")
                .map { playerNode.startFlow(CreateAccount(it)) }
                .also { network.waitQuiescent() }
                .map { it.get().state.data }
                .map { playerNode.services.createKeyForAccount(it) }
                .onEach {
                    playerNode.startFlow(SyncKeyMappingInitiator(issuerNodeParty, listOf(it)))
                            .also { network.waitQuiescent() }
                            .get()
                }
        this.player1 = player1
        this.player2 = player2
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
                .also { network.waitQuiescent() }
                .get()
                .tx
                .outRefsOfType()
    }

    @Test
    fun `scheduled task has correct flow name`() {
        assertEquals(ForeClosureFlow.SimpleInitiator::class.java.name,
                GameState.foreClosureFlowName)
    }

    @Test
    fun `can run foreclosure flow when the player did not reveal`() {
        casinoNode.registerInitiatedFlow(
                BrokenGameFlows.PlayerDoesNotReveal.Initiator::class.java,
                GameFlows.Responder::class.java)
        issueToken(issuerNode, casino1, issuer, GameState.maxPayoutRatio * 3L)
        issueToken(issuerNode, player1, issuer, 3L)
        playerNode.startFlow(
                BrokenGameFlows.PlayerDoesNotReveal.Initiator(player1, 3L, issuer, casino1))
                .also { network.waitQuiescent() }
                .get()

        // Advance time beyond the reveal deadline.
        val onwards = GameParameters.commitDuration + GameParameters.revealDuration +
                Duration.ofMinutes(1)
        listOf(notaryNode, playerNode, casinoNode)
                .forEach { (it.services.clock as TestClock).advanceBy(onwards) }

        // The foreclosure flow will start itself thanks to schedulable state.
        network.waitQuiescent()

        val playerBalance = playerNode.startFlow(LockableTokenFlows.Balance.Local(player1, issuer))
                .also { network.waitQuiescent() }
                .get()
        assertEquals(3L, playerBalance)
        val casinoBalance = casinoNode.startFlow(LockableTokenFlows.Balance.Local(casino1, issuer))
                .also { network.waitQuiescent() }
                .get()
        assertEquals(GameState.maxPayoutRatio * 3L, casinoBalance)
    }
}