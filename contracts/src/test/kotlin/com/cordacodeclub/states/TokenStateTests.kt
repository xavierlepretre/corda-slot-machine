package com.cordacodeclub.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.StaticPointer
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class TokenStateTests {
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "London", "GB"))
    private val issuer = issuerId.identity.party
    private val holderId = TestIdentity(CordaX500Name("Holder", "Paris", "FR"))
    private val holder = holderId.identity.party

    @Test
    fun `can create tokens`() {
        val unlocked = LockableTokenState(holder, issuer, Amount(10, LockableTokenType))
        assertEquals(listOf(holder), unlocked.participants)
        val locked = LockableTokenState(issuer, Amount(11, LockableTokenType))
        assertEquals(listOf(issuer), locked.participants)
    }

}