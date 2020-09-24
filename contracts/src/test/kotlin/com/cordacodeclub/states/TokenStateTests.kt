package com.cordacodeclub.states

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenStateTests {
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "London", "GB"))
    private val issuer = issuerId.identity.party
    private val holderId = TestIdentity(CordaX500Name("Holder", "Paris", "FR"))
    private val holder = holderId.identity.party

    @Test
    fun `can create tokens`() {
        val unlocked = LockableTokenState(holder, issuer, Amount(10, LockableTokenType))
        assertEquals(listOf(holder), unlocked.participants)
        val locked = LockableTokenState(issuer, Amount(11, LockableTokenType), listOf(holder))
        assertEquals(listOf(holder), locked.participants)
    }

    @Test
    fun `plus requires same locked status`() {
        assertFailsWith<IllegalArgumentException> {
            LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                    .plus(LockableTokenState(holder, issuer, Amount(2L, LockableTokenType)))
        }
        assertFailsWith<IllegalArgumentException> {
            LockableTokenState(holder, issuer, Amount(2L, LockableTokenType))
                    .plus(LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer)))
        }
        LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                .plus(LockableTokenState(issuer, Amount(2L, LockableTokenType), listOf(issuer)))
        LockableTokenState(holder, issuer, Amount(1L, LockableTokenType))
                .plus(LockableTokenState(holder, issuer, Amount(2L, LockableTokenType)))
    }

    @Test
    fun `plus requires same issuer`() {
        assertFailsWith<IllegalArgumentException> {
            LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                    .plus(LockableTokenState(holder, Amount(2L, LockableTokenType), listOf(issuer)))
        }
        assertFailsWith<IllegalArgumentException> {
            LockableTokenState(holder, issuer, Amount(2L, LockableTokenType))
                    .plus(LockableTokenState(holder, holder, Amount(1L, LockableTokenType)))
        }
        LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                .plus(LockableTokenState(issuer, Amount(2L, LockableTokenType), listOf(issuer)))
        LockableTokenState(holder, issuer, Amount(1L, LockableTokenType))
                .plus(LockableTokenState(holder, issuer, Amount(2L, LockableTokenType)))
    }

    @Test
    fun `plus requires same holder`() {
        assertFailsWith<IllegalArgumentException> {
            LockableTokenState(holder, issuer, Amount(2L, LockableTokenType))
                    .plus(LockableTokenState(issuer, issuer, Amount(1L, LockableTokenType)))
        }
        LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                .plus(LockableTokenState(issuer, Amount(2L, LockableTokenType), listOf(issuer)))
        LockableTokenState(holder, issuer, Amount(1L, LockableTokenType))
                .plus(LockableTokenState(holder, issuer, Amount(2L, LockableTokenType)))
    }

    @Test
    fun `plus is correct`() {
        assertEquals(
                LockableTokenState(issuer, Amount(3L, LockableTokenType), listOf(issuer)),
                LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                        .plus(LockableTokenState(issuer, Amount(2L, LockableTokenType), listOf(issuer))))
        assertEquals(
                LockableTokenState(issuer, Amount(3L, LockableTokenType), listOf(issuer, holder)),
                LockableTokenState(issuer, Amount(1L, LockableTokenType), listOf(issuer))
                        .plus(LockableTokenState(issuer, Amount(2L, LockableTokenType), listOf(holder))))
        assertEquals(
                LockableTokenState(holder, issuer, Amount(5L, LockableTokenType)),
                LockableTokenState(holder, issuer, Amount(1L, LockableTokenType))
                        .plus(LockableTokenState(holder, issuer, Amount(4L, LockableTokenType))))
    }
}