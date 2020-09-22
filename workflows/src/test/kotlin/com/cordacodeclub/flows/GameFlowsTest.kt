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
import net.corda.core.node.NetworkParameters
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class GameFlowsTest {
    private lateinit var network: MockNetwork
    private lateinit var notaryParty: Party
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
                TestCordapp.findCordapp("com.cordacodeclub.flows"))))
        notaryParty = network.defaultNotaryIdentity
        issuerNode = network.createPartyNode()
        issuerNodeParty = issuerNode.info.legalIdentities.first()
        casinoNode = network.createPartyNode(CordaX500Name("Casino", "London", "GB"))
        playerNode = network.createPartyNode(CordaX500Name("Player", "Paris", "FR"))
        playerNodeParty = playerNode.info.legalIdentities.first()
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
        val (casino1, casino2) = listOf("casino1", "casino2")
                .map { casinoNode.startFlow(CreateAccount(it)) }
                .also { network.runNetwork() }
                .map { it.get().state.data }
                .map { casinoNode.services.createKeyForAccount(it) }
                .onEach {
                    casinoNode.startFlow(SyncKeyMappingInitiator(issuerNodeParty, listOf(it)))
                            .also { network.runNetwork() }
                            .get()
                    casinoNode.startFlow(SyncKeyMappingInitiator(playerNodeParty, listOf(it)))
                            .also { network.runNetwork() }
                            .get()
                }
        this.casino1 = casino1
        this.casino2 = casino2
        val (player1, player2) = listOf("player1", "player2")
                .map { playerNode.startFlow(CreateAccount(it)) }
                .also { network.runNetwork() }
                .map { it.get().state.data }
                .map { playerNode.services.createKeyForAccount(it) }
                .onEach {
                    playerNode.startFlow(SyncKeyMappingInitiator(issuerNodeParty, listOf(it)))
                            .also { network.runNetwork() }
                            .get()
                }
        this.player1 = player1
        this.player2 = player2
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
    fun `can launch the game along the happy path without change`() {
        issueToken(issuerNode, casino1, issuer, GameState.maxPayoutRatio * 3L)
        issueToken(issuerNode, player1, issuer, 3L)
        playerNode.startFlow(GameFlows.Initiator(notaryParty, player1, 3L, issuer, casino1))
                .also { network.runNetwork() }
                .get()
    }

    @Test
    fun `can launch the game along the happy path with change`() {
        issueToken(issuerNode, casino1, issuer, GameState.maxPayoutRatio * 4L + 2L)
        issueToken(issuerNode, player1, issuer, 9L)
        playerNode.startFlow(GameFlows.Initiator(notaryParty, player1, 4L, issuer, casino1))
                .also { network.runNetwork() }
                .get()
    }

}