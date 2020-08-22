package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.LockableTokenContract.Commands.Redeem
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

class LockableTokenContractRedeemTests {

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

    @Test
    fun `Command must have inputs`() {
        Redeem(listOf(1), listOf(1))
        assertThrows<IllegalArgumentException> { Redeem(listOf(), listOf(1)) }
    }

    @Test
    fun `Command can have no outputs`() {
        Redeem(listOf(1), listOf(1))
        Redeem(listOf(1), listOf())
    }

    @Test
    fun `Inputs must be lockable tokens`() {
        ledgerServices.transaction {
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))

            tweak {
                input(DummyContract.PROGRAM_ID, DummyState())
                failsWith("The inputs must be lockable token states")
            }

            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must be lockable tokens`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))

            tweak {
                output(DummyContract.PROGRAM_ID, DummyState())
                failsWith("The outputs must be lockable token states")
            }

            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must have strictly positive amounts`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(0, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0, 1), listOf(0)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(0, LockableTokenType)))
                failsWith("The outputs must have positive amounts")
            }

            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Inputs must have a single issuer`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0, 1), listOf(0)))

            tweak {
                input(LockableTokenContract.id, LockableTokenState(casino, player, Amount(4, LockableTokenType)))
                failsWith("The inputs must have a single issuer")
            }

            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must have at most one issuer`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(5, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0, 1)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(casino, player, Amount(3, LockableTokenType)))
                failsWith("The outputs must have at most one issuer")
            }

            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Inputs and outputs must have the same issuer`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(casino, player, Amount(3, LockableTokenType)))
                failsWith("The input and output issuers must be the same")
            }

            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `The sums must decrease or be zero`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(0, LockableTokenType)))

            tweak {
                command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf()))
                verifies()
            }

            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0, 1), listOf(0)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
                failsWith("The sums must decrease or be zero")
            }

            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Inputs must be unlocked`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0, 1), listOf(0)))

            tweak {
                input(LockableTokenContract.id, LockableTokenState(issuer, Amount(4, LockableTokenType)))
                failsWith("The inputs must be unlocked")
            }

            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Outputs must be unlocked`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(4, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0, 1)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(2, LockableTokenType)))
                failsWith("The outputs must be unlocked")
            }

            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            verifies()
        }
    }

    @Test
    fun `Input holders and issuers must sign`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(5, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(7, LockableTokenType)))

            tweak {
                command(issuer.owningKey, Redeem(listOf(0, 1), listOf(0)))
                failsWith("The input holders must sign")
            }

            tweak {
                command(casino.owningKey, Redeem(listOf(0, 1), listOf(0)))
                failsWith("The input issuer must sign")
            }

            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0, 1), listOf(0)))
            verifies()

        }
    }

    @Test
    fun `All covered lockable token states must not overlap`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))
            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))
            verifies()

            tweak {
                command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))
                failsWith("All covered token inputs must have no overlap")
            }

            tweak {
                input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
                command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(1), listOf(0)))
                failsWith("All covered token outputs must have no overlap")
            }
        }
    }

    @Test
    fun `All lockable token states must be covered by a command`() {
        ledgerServices.transaction {
            input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(3, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2, LockableTokenType)))

            tweak {
                input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
                command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))
                failsWith("All lockable token inputs must be covered by a command")
            }

            tweak {
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(1, LockableTokenType)))
                command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))
                failsWith("All lockable token outputs must be covered by a command")
            }

            command(listOf(casino.owningKey, issuer.owningKey), Redeem(listOf(0), listOf(0)))
            verifies()
        }
    }

}