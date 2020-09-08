package com.cordacodeclub.states

import com.google.common.collect.testing.Helpers.assertContains
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
        assertFailsWith<IllegalArgumentException> {
            CommittedState(SecureHash.allOnesHash, alice, Instant.now(), 1, UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException> {
            CommittedState(SecureHash.allOnesHash, alice, Instant.now(), 1, UniqueIdentifier(), listOf(bob))
        }
        CommittedState(SecureHash.allOnesHash, alice, Instant.now(), 1, UniqueIdentifier(), listOf(alice))
    }

    @Test
    fun `RevealedState creator must be in the participants`() {
        assertFailsWith<IllegalArgumentException> {
            RevealedState(CommitImage.createRandom(random), alice, basicGame, UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException> {
            RevealedState(CommitImage.createRandom(random), alice, basicGame, UniqueIdentifier(), listOf(bob))
        }
        RevealedState(CommitImage.createRandom(random), alice, basicGame, UniqueIdentifier(), listOf(alice))
    }

    @Test
    fun `Payout calculates expected distribution`() {
        val random = Random()

        val distribution = (1..1_000_000)
                .map {
                    CommitImage.playerPayoutCalculator(
                            CommitImage.createRandom(random), CommitImage.createRandom(random))
                }
                .groupBy { it }
                .mapValues { it.value.size }
        println(distribution)
        assertContains(IntRange(250, 350), distribution[200L])
        assertContains(IntRange(1_400, 1_600), distribution[50L])
        assertContains(IntRange(3_000, 4_000), distribution[20L])
        assertContains(IntRange(4_000, 5_000), distribution[15L])
        assertContains(IntRange(5_000, 6_000), distribution[13L])
        assertContains(IntRange(7_500, 8_500), distribution[12L])
        assertContains(IntRange(9_500, 10_500), distribution[10L])
        assertContains(IntRange(85_000, 95_000), distribution[4L])
        assertContains(IntRange(850_000, 900_000), distribution[0L])
    }
}