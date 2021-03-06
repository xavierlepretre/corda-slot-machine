package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.LeaderboardEntryContract.Commands.Overtake
import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import java.time.Instant

class LeaderboardEntryContractOvertakeTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Amsterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `Overtake command needs a leaderboard entry state input`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            input(DummyContract.PROGRAM_ID, DummyState())
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(21, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))

            tweak {
                command(player.owningKey, Overtake(1, 0))
                failsWith("The input must be a LeaderboardEntryState")
            }

            command(player.owningKey, Overtake(0, 0))
            verifies()
        }
    }

    @Test
    fun `Overtake command needs a leaderboard entry state output`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))
            output(DummyContract.PROGRAM_ID, DummyState())
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(21, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))

            tweak {
                command(player.owningKey, Overtake(0, 1))
                failsWith("The output must be a LeaderboardEntryState")
            }

            command(player.owningKey, Overtake(0, 0))
            verifies()
        }
    }

    @Test
    fun `Overtake must have correct referenced lockable tokens`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Overtake(0, 0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(29, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))
            failsWith("The referenced tokens sum must match the entry total")

            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(1, LockableTokenType)))
            verifies()

            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(1, LockableTokenType)))
            failsWith("The referenced tokens sum must match the entry total")
        }
    }

    @Test
    fun `Overtake must have a time window`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Overtake(0, 0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(30, LockableTokenType)))
            failsWith("There must be a time-window")

            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))
            verifies()
        }
    }

    @Test
    fun `Overtake must have a time window with 2 bounds`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Overtake(0, 0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(30, LockableTokenType)))
            timeWindow(TimeWindow.fromOnly(creationDate.plusSeconds(60)))
            failsWith("There must be a time-window with 2 bounds")

            timeWindow(TimeWindow.untilOnly(creationDate.minusSeconds(60)))
            failsWith("There must be a time-window with 2 bounds")

            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))
            verifies()
        }
    }

    @Test
    fun `Overtake must have happen in a narrow enough time window`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Overtake(0, 0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(30, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius.plusSeconds(1)),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius.minusSeconds(1))))
            failsWith("The time-window bounds must not be far from the creation date")

            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius.minusSeconds(1)),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius.plusSeconds(1))))
            failsWith("The time-window bounds must not be far from the creation date")

            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))
            verifies()

            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius.minusSeconds(1)),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius.minusSeconds(1))))
            verifies()
        }
    }

    @Test
    fun `On overtake created player must sign`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id,
                    LeaderboardEntryState(casino, 20, issuer, creationDate.minusSeconds(999)))
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(21, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                    creationDate.plus(LeaderboardEntryContract.maxTimeWindowRadius)))

            tweak {
                command(issuer.owningKey, Overtake(0, 0))
                failsWith("The created entry player must be a signer")
            }

            command(player.owningKey, Overtake(0, 0))
            verifies()
        }
    }
}