package com.cordacodeclub.flows

import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaderboardCreateFlowTest {
    private lateinit var network: MockNetwork
    private lateinit var issuerNode: StartedMockNode
    private lateinit var issuerNodeParty: Party
    private lateinit var issuer: AbstractParty
    private lateinit var casinoNode: StartedMockNode
    private lateinit var playerNode: StartedMockNode
    private lateinit var playerNodeParty: Party
    private lateinit var player1: AbstractParty
    private lateinit var player2: AbstractParty
    private lateinit var player3: AbstractParty

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
        val (player1, player2, player3) = listOf("player1", "player2", "player3")
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
        this.player3 = player3
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

    private fun createEntry(playerNode: StartedMockNode, player: AbstractParty) = createEntryRef(playerNode, player)
            .state.data

    private fun createEntryRef(playerNode: StartedMockNode, player: AbstractParty) = playerNode
            .startFlow(LeaderboardFlows.Create.Initiator(player, issuer))
            .also { network.runNetwork() }
            .get()
            .tx
            .outRefsOfType<LeaderboardEntryState>()
            .single()

    private fun fetchEntryRefs(playerNode: StartedMockNode) = playerNode
            .startFlow(LeaderboardFlows.Fetch.Local(issuer))
            .also { network.runNetwork() }
            .get()

    @Test
    fun `can create leaderboard entry`() {
        issueToken(issuerNode, player1, issuer, 100L)
        val createTx = playerNode.startFlow(LeaderboardFlows.Create.Initiator(player1, issuer))
                .also { network.runNetwork() }
                .get()
        val created = createTx.tx.outputsOfType<LeaderboardEntryState>().single()
        assertEquals(player1, created.player)
        assertEquals(issuer, created.tokenIssuer)
        assertEquals(Amount(100L, LockableTokenType), created.total)
    }

    @Test
    fun `can create 3 leaderboard entries`() {
        issueToken(issuerNode, player1, issuer, 100L)
        val created1 = createEntry(playerNode, player1)
        issueToken(issuerNode, player2, issuer, 110L)
        val created2 = createEntry(playerNode, player2)
        assertEquals(player2, created2.player)
        assertEquals(issuer, created2.tokenIssuer)
        assertEquals(Amount(110L, LockableTokenType), created2.total)
        issueToken(issuerNode, player1, issuer, 20L)
        val created3 = createEntry(playerNode, player1)
        assertEquals(player1, created3.player)
        assertEquals(issuer, created3.tokenIssuer)
        assertEquals(Amount(120L, LockableTokenType), created3.total)
    }

    @Test
    fun `cannot create somewhat duplicate leaderboard entries`() {
        issueToken(issuerNode, player1, issuer, 100L)
        playerNode.startFlow(LeaderboardFlows.Create.Initiator(player1, issuer))
                .also { network.runNetwork() }
                .get()
        val error = assertThrows<FlowException> {
            playerNode.startFlow(LeaderboardFlows.Create.Initiator(player1, issuer))
                    .also { network.runNetwork() }
                    .getOrThrow()
        }
        assertEquals("Same player cannot enter the leaderboard with identical total", error.message)
    }

    @Test
    fun `can create same leaderboard entry total if different player`() {
        issueToken(issuerNode, player1, issuer, 100L)
        val created1 = createEntryRef(playerNode, player1)
        issueToken(issuerNode, player2, issuer, 100L)
        val created2 = createEntryRef(playerNode, player2)
        val fetched = fetchEntryRefs(playerNode)

        assertEquals(listOf(created1, created2), fetched)
    }

    @Test
    fun `can create 20 leaderboard entries of same player`() {
        val created = (1..LeaderboardFlows.maxLeaderboardLength).map {
            issueToken(issuerNode, player1, issuer, 10L)
            createEntryRef(playerNode, player1)
        }
        val fetched = fetchEntryRefs(playerNode)

        assertEquals(created.sortedByDescending { it.state.data.total }, fetched)
    }

    @Test
    fun `can create 21 leaderboard entries of same player and keep 20`() {
        val created = (1..LeaderboardFlows.maxLeaderboardLength).map {
            issueToken(issuerNode, player1, issuer, 10L)
            createEntryRef(playerNode, player1)
        }
        issueToken(issuerNode, player1, issuer, 10L)
        val createTx = playerNode.startFlow(LeaderboardFlows.Create.Initiator(player1, issuer))
                .also { network.runNetwork() }
                .get()
        assertEquals(created.first(), playerNode.services.toStateAndRef(createTx.tx.inputs.first()))
        val fetched = fetchEntryRefs(playerNode)
        assertEquals(
                created.drop(1).plus(createTx.tx.outRefsOfType()).sortedByDescending { it.state.data.total },
                fetched)
    }

    @Test
    fun `can create 21 leaderboard entries of different players and keep 20`() {
        val created = (1..(LeaderboardFlows.maxLeaderboardLength / 2)).flatMap {
            issueToken(issuerNode, player1, issuer, 10L)
            val first = createEntryRef(playerNode, player1)
            issueToken(issuerNode, player2, issuer, 10L)
            val second = createEntryRef(playerNode, player2)
            listOf(first, second)
        }
        issueToken(issuerNode, player1, issuer, 10L)
        val createTx = playerNode.startFlow(LeaderboardFlows.Create.Initiator(player1, issuer))
                .also { network.runNetwork() }
                .get()
        // When overtaking, with entries that have the same total, it is the newest one that is picked.
        assertEquals(created[1], playerNode.services.toStateAndRef(createTx.tx.inputs.first()))
        val fetched = fetchEntryRefs(playerNode)
        val expected = created.drop(2)
                .plus(created.first())
                .plus(createTx.tx.outRefsOfType())
                .sortedByDescending { it.state.data.total }
        assertEquals(expected, fetched)
    }

    @Test
    fun `cannot create leaderboard entry if do not qualify`() {
        val created = (1..(LeaderboardFlows.maxLeaderboardLength / 2)).flatMap {
            issueToken(issuerNode, player1, issuer, 10L)
            val first = createEntryRef(playerNode, player1)
            issueToken(issuerNode, player2, issuer, 10L)
            val second = createEntryRef(playerNode, player2)
            listOf(first, second)
        }
        issueToken(issuerNode, player3, issuer, 10L)
        val error = assertThrows<FlowException> {
            playerNode.startFlow(LeaderboardFlows.Create.Initiator(player3, issuer))
                    .also { network.runNetwork() }
                    .getOrThrow()
        }
        assertEquals("The player does not qualify to enter the leaderboard", error.message)
    }

}