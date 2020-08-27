package com.cordacodeclub.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertFailsWith

class GameStateTests {

    private val aliceId = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val alice = aliceId.identity.party
    private val bobId = TestIdentity(CordaX500Name("Bob", "Paris", "FR"))

    @Test
    fun `GameState must have at least 1 participant`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(listOf(UniqueIdentifier(), UniqueIdentifier()), UniqueIdentifier(), listOf())
        }
        GameState(listOf(UniqueIdentifier(), UniqueIdentifier()), UniqueIdentifier(), listOf(alice))
    }
}