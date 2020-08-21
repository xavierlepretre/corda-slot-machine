package com.cordacodeclub.states

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.StaticPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith

class CommitStatesTests {

    private val aliceId = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val alice = aliceId.identity.party
    private val bobId = TestIdentity(CordaX500Name("Bob", "Paris", "FR"))
    private val bob = bobId.identity.party
    private val random = Random()
    private val basicGame = StaticPointer(
            StateRef(SecureHash.allOnesHash, 1), GameState::class.java)

    @Test
    fun `CommittedState creator must be in the participants`() {
        assertFailsWith<IllegalArgumentException>() {
            CommittedState(SecureHash.allOnesHash, alice, Instant.now(), 1, UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException>() {
            CommittedState(SecureHash.allOnesHash, alice, Instant.now(), 1, UniqueIdentifier(), listOf(bob))
        }
        CommittedState(SecureHash.allOnesHash, alice, Instant.now(), 1, UniqueIdentifier(), listOf(alice))
    }

    @Test
    fun `RevealedState creator must be in the participants`() {
        assertFailsWith<IllegalArgumentException>() {
            RevealedState(CommitImage.createRandom(random), alice, basicGame, UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException>() {
            RevealedState(CommitImage.createRandom(random), alice, basicGame, UniqueIdentifier(), listOf(bob))
        }
        RevealedState(CommitImage.createRandom(random), alice, basicGame, UniqueIdentifier(), listOf(alice))
    }

}