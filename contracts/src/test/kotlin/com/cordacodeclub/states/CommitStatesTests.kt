package com.cordacodeclub.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommitStatesTests {

    private val aliceId = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val alice = aliceId.identity.party
    private val bobId = TestIdentity(CordaX500Name("Bob", "Paris", "FR"))
    private val bob = bobId.identity.party

    @Test
    fun `all bits are in the CommitImage hash`() {
        val leftMostBit = 0x4000000000000000L
        val allHashes = mutableSetOf<SecureHash>()

        for (i in 0..63) {
            allHashes.add(CommitImage(0L, leftMostBit shr i).hash)
            allHashes.add(CommitImage(leftMostBit shr i, 0L).hash)
        }

        // The 0, 0 was duplicated.
        assertEquals(127, allHashes.size)
    }

    @Test
    fun `CommittedState creator must be in the participants`() {
        assertFailsWith<IllegalArgumentException>() {
            CommittedState(SecureHash.allOnesHash, alice, UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException>() {
            CommittedState(SecureHash.allOnesHash, alice, UniqueIdentifier(), listOf(bob))
        }
        CommittedState(SecureHash.allOnesHash, alice, UniqueIdentifier(), listOf(alice))
    }

    @Test
    fun `RevealedState creator must be in the participants`() {
        assertFailsWith<IllegalArgumentException>() {
            RevealedState(CommitImage(0L, 1L), alice, UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException>() {
            RevealedState(CommitImage(0L, 1L), alice, UniqueIdentifier(), listOf(bob))
        }
        RevealedState(CommitImage(0L, 1L), alice, UniqueIdentifier(), listOf(alice))
    }

}