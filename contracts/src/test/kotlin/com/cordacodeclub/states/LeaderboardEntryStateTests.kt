package com.cordacodeclub.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class LeaderboardEntryStateTests {

    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Amsterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val aliceId = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val alice = aliceId.identity.party
    private val bobId = TestIdentity(CordaX500Name("Bob", "Paris", "FR"))
    private val bob = bobId.identity.party

    @Test
    fun `LeaderboardEntryState player must be in participants`() {
        assertFailsWith<IllegalArgumentException> {
            LeaderboardEntryState(alice, 40, bob, Instant.now(), UniqueIdentifier(), listOf())
        }
        assertFailsWith<IllegalArgumentException> {
            LeaderboardEntryState(alice, 40, bob, Instant.now(), UniqueIdentifier(), listOf(bob))
        }
        LeaderboardEntryState(alice, 40, bob, Instant.now())
        LeaderboardEntryState(alice, 40, bob, Instant.now(), UniqueIdentifier(), listOf(alice))
        LeaderboardEntryState(alice, 40, bob, Instant.now(), UniqueIdentifier(), listOf(alice, bob))
        LeaderboardEntryState(alice, 40, bob, Instant.now(), UniqueIdentifier(), listOf(alice, issuer))
    }
}