package com.cordacodeclub.services

import net.corda.core.contracts.UniqueIdentifier
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeaderboardNicknamesDatabaseTest {
    private lateinit var network: MockNetwork
    private lateinit var issuerNode: StartedMockNode
    private lateinit var issuerNodeParty: Party
    private lateinit var casinoNode: StartedMockNode
    private lateinit var playerNode: StartedMockNode
    private lateinit var playerNodeParty: Party

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
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `not all nicknames are valid`() {
        val nickService = playerNode.services.leaderboardNicknamesDatabaseService
        assertFalse(nickService.isValidNickname("hello"))
        assertFalse(nickService.isValidNickname(" hello"))
        assertFalse(nickService.isValidNickname("hel lo"))
        assertFalse(nickService.isValidNickname("hellO"))
        assertFalse(nickService.isValidNickname("hello-world"))
        assertFalse(nickService.isValidNickname("1234567890123456789012345"))

        assertTrue(nickService.isValidNickname("hello_world"))
        assertTrue(nickService.isValidNickname("helloWorld123"))
        assertTrue(nickService.isValidNickname("123456789012345678901234"))
    }

    @Test
    fun `can add a leaderboard nickname`() {
        val id = UniqueIdentifier()
        val nickService = playerNode.services.leaderboardNicknamesDatabaseService
        val nickname = playerNode.transaction {
            nickService.addLeaderboardNickname(id.id, "player1")
            nickService.getNickname(id.id)
        }
        assertEquals("player1", nickname)
        assertTrue(playerNode.transaction { nickService.hasNickname(id.id) })
    }

    @Test
    fun `can delete a leaderboard nickname`() {
        val id = UniqueIdentifier()
        val nickService = playerNode.services.leaderboardNicknamesDatabaseService
        val hasNow = playerNode.transaction {
            nickService.addLeaderboardNickname(id.id, "player1")
            nickService.deleteNickname(id.id)
            nickService.hasNickname(id.id)
        }
        assertFalse(hasNow)
    }

}