package com.cordacodeclub.flows

import com.cordacodeclub.flows.LeaderboardFlows.LeaderboardNamedEntryState
import com.cordacodeclub.services.leaderboardNicknamesDatabaseService
import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaderboardRetireFlowTest {
    private lateinit var network: MockNetwork
    private lateinit var issuerNode: StartedMockNode
    private lateinit var issuerNodeParty: Party
    private lateinit var issuer: AbstractParty
    private lateinit var casinoNode: StartedMockNode
    private lateinit var casino: Party
    private lateinit var playerNode: StartedMockNode
    private lateinit var playerNodeParty: Party
    private lateinit var player1: AbstractParty
    private lateinit var player2: AbstractParty

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters()
                .withNetworkParameters(testNetworkParameters(listOf(), 4))
                .withCordappsForAllNodes(listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows"),
                        TestCordapp.findCordapp("com.cordacodeclub.contracts"),
                        TestCordapp.findCordapp("com.cordacodeclub.flows"))))
        issuerNode = network.createPartyNode()
        issuerNodeParty = issuerNode.info.legalIdentities.first()
        casinoNode = network.createPartyNode(CordaX500Name("Casino", "London", "GB"))
        casino = casinoNode.info.legalIdentities.first()
        playerNode = network.createPartyNode(CordaX500Name("Player", "Paris", "FR"))
        playerNodeParty = playerNode.info.legalIdentities.first()
        network.runNetwork()
        prepareIssuerAndHolders()
    }

    private fun prepareIssuerAndHolders() {
        issuer = issuerNode.startFlow(CreateAccount("issuer"))
                .also { network.runNetwork() }
                .get().state.data
                .let { issuerNode.services.createKeyForAccount(it) }
        val (player1, player2) = listOf("player1", "player2")
                .map { playerNode.startFlow(CreateAccount(it)) }
                .also { network.runNetwork() }
                .map { it.get().state.data }
                .map { playerNode.services.createKeyForAccount(it) }
                .onEach {
                    playerNode.startFlow(SyncKeyMappingInitiator(issuerNodeParty, listOf(it)))
                            .also { network.runNetwork() }
                            .get()
                    playerNode.startFlow(SyncKeyMappingInitiator(casino, listOf(it)))
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

    private fun createEntry(playerNode: StartedMockNode,
                            player: AbstractParty,
                            nickname: String,
                            issuer: AbstractParty = this.issuer) = playerNode
            .startFlow(LeaderboardFlows.Create.Initiator(player, nickname, issuer))
            .also { network.runNetwork() }
            .get()
            .tx
            .outRefsOfType<LeaderboardEntryState>()
            .single()
            .let { LeaderboardNamedEntryState(it, nickname) }

    @Test
    fun `can retire a single entry`() {
        issueToken(issuerNode, player1, issuer, 100L)
        createEntry(playerNode, player1, "nickname1")
        issueToken(casinoNode, player1, casino, 50L)
        createEntry(playerNode, player1, "nickname1", casino)
        issueToken(issuerNode, player2, issuer, 125L)
        val entry3 = createEntry(playerNode, player2, "nickname2")
        playerNode.startFlow(LeaderboardFlows.Retire.Initiator(player1))
                .also { network.runNetwork() }
                .get()
        val fetched = playerNode.startFlow(LeaderboardFlows.Fetch.Local(issuer))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched.size)
        assertEquals(entry3, fetched.single())
    }

    @Test
    fun `can retire 3 entries`() {
        issueToken(issuerNode, player1, issuer, 100L)
        createEntry(playerNode, player1, "nickname1")
        issueToken(issuerNode, player1, issuer, 100L)
        createEntry(playerNode, player1, "nickname1")
        issueToken(casinoNode, player1, casino, 50L)
        createEntry(playerNode, player1, "nickname1", casino)
        issueToken(issuerNode, player2, issuer, 125L)
        val entry3 = createEntry(playerNode, player2, "nickname2")
        playerNode.startFlow(LeaderboardFlows.Retire.Initiator(player1))
                .also { network.runNetwork() }
                .get()
        val fetched = playerNode.startFlow(LeaderboardFlows.Fetch.Local(issuer))
                .also { network.runNetwork() }
                .get()
        assertEquals(1, fetched.size)
        assertEquals(entry3, fetched.single())
    }

}