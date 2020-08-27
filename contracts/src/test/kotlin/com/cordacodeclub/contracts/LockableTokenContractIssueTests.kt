package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.LockableTokenContract.Commands.Issue
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class LockableTokenContractIssueTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Hamburg", "DE"))
    private val issuer = issuerId.identity.party
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party
    private val random = Random()

    @Test
    fun `Command must have outputs`() {
        Issue(listOf(1))
        assertThrows<IllegalArgumentException> { Issue(listOf()) }
    }

    @Test
    fun `Outputs must be lockable tokens`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(issuer.owningKey, Issue(listOf(0, 1)))

            tweak {
                output(DummyContract.PROGRAM_ID, DummyState())
                failsWith("The outputs must be lockable token states")
            }

            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must have strictly positive amounts`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(issuer.owningKey, Issue(listOf(0, 1)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(0, LockableTokenType)))
                failsWith("The outputs must have positive amounts")
            }

            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must be unlocked`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(issuer.owningKey, Issue(listOf(0, 1)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(3, LockableTokenType)))
                failsWith("The outputs must be unlocked")
            }

            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must have a single issuer`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(issuer.owningKey, Issue(listOf(0, 1)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(casino, player, Amount(3, LockableTokenType)))
                failsWith("The outputs must have a single issuer")
            }

            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Issuer must sign`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(2, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3, LockableTokenType)))

            tweak {
                command(player.owningKey, Issue(listOf(0, 1)))
                failsWith("The issuer must sign")
            }

            command(issuer.owningKey, Issue(listOf(0, 1)))
            verifies()
        }
    }

    @Test
    fun `All covered lockable token states must not overlap`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(issuer.owningKey, Issue(listOf(0)))
            verifies()

            tweak {
                command(issuer.owningKey, Issue(listOf(0)))
                failsWith("All covered token outputs must have no overlap")
            }
        }
    }

    @Test
    fun `All lockable token states must be covered by a command`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1, LockableTokenType)))

            tweak {
                command(issuer.owningKey, Issue(listOf(0)))
                failsWith("All lockable token outputs must be covered by a command")
            }

            tweak {
                input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
                command(issuer.owningKey, Issue(listOf(0, 1)))
                failsWith("All lockable token inputs must be covered by a command")
            }

            command(issuer.owningKey, Issue(listOf(0, 1)))
            verifies()
        }
    }

}