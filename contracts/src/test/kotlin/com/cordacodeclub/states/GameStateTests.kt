package com.cordacodeclub.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class GameStateTests {

    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Amsterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val aliceId = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val alice = aliceId.identity.party
    private val bobId = TestIdentity(CordaX500Name("Bob", "Paris", "FR"))
    private val bob = bobId.identity.party

    @Test
    fun `GameState must have at least 1 participant`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                    bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                    Instant.now().plusSeconds(60), 0,
                    UniqueIdentifier(), listOf())
        }
        GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                Instant.now().plusSeconds(60), 0,
                UniqueIdentifier(), listOf(alice))
    }

    @Test
    fun `GameState must has same issuer in bettors`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy alice),
                    bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                    Instant.now().plusSeconds(60), 1,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                Instant.now().plusSeconds(60), 1,
                UniqueIdentifier(), listOf(alice, bob))
    }

    @Test
    fun `GameState must have different holders in bettors`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                    alice commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                    Instant.now().plusSeconds(60), 2,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                Instant.now().plusSeconds(60), 2,
                UniqueIdentifier(), listOf(alice, bob))
    }

    @Test
    fun `GameState locked wagers output index must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                    bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                    Instant.now().plusSeconds(60), -1,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                Instant.now().plusSeconds(60), 0,
                UniqueIdentifier(), listOf(alice, bob))
    }

    @Test
    fun `GameState wagers need to be in proportion`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio + 1L issuedBy issuer),
                    bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                    Instant.now().plusSeconds(60), -1,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio - 1L issuedBy issuer),
                    bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                    Instant.now().plusSeconds(60), -1,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (3 * GameState.maxPayoutRatio + 1L issuedBy issuer),
                    bob commitsTo UniqueIdentifier() with (3L issuedBy issuer),
                    Instant.now().plusSeconds(60), 0,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        assertFailsWith<IllegalArgumentException> {
            GameState(alice commitsTo UniqueIdentifier() with (3 * GameState.maxPayoutRatio - 1L issuedBy issuer),
                    bob commitsTo UniqueIdentifier() with (3L issuedBy issuer),
                    Instant.now().plusSeconds(60), 0,
                    UniqueIdentifier(), listOf(alice, bob))
        }
        GameState(alice commitsTo UniqueIdentifier() with (GameState.maxPayoutRatio issuedBy issuer),
                bob commitsTo UniqueIdentifier() with (1L issuedBy issuer),
                Instant.now().plusSeconds(60), 0,
                UniqueIdentifier(), listOf(alice, bob))
        GameState(alice commitsTo UniqueIdentifier() with (3 * GameState.maxPayoutRatio issuedBy issuer),
                bob commitsTo UniqueIdentifier() with (3L issuedBy issuer),
                Instant.now().plusSeconds(60), 0,
                UniqueIdentifier(), listOf(alice, bob))
    }
}