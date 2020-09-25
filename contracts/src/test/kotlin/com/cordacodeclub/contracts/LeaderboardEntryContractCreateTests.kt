package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.LeaderboardEntryContract.Commands.Create
import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TimeWindow
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import java.time.Instant

class LeaderboardEntryContractCreateTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Amsterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `Leaderboard entry contract needs a leaderboard entry command`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 20, issuer, creationDate))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(20, LockableTokenType)))
            command(player.owningKey, DummyContract.Commands.Create())
            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))
            failsWith("The LeaderboardEntryContract must find at least 1 command")

            command(player.owningKey, Create(0))
            verifies()
        }
    }

    @Test
    fun `Create command needs a leaderboard entry state output`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))
            output(DummyContract.PROGRAM_ID, DummyState())
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(21, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))

            tweak {
                command(player.owningKey, Create(1))
                failsWith("The output must be a LeaderboardEntryState")
            }

            command(player.owningKey, Create(0))
            verifies()
        }
    }

    @Test
    fun `Create must have correct referenced lockable tokens`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Create(0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(29, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))
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
    fun `Create must have a time window`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Create(0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(30, LockableTokenType)))
            failsWith("There must be a time-window")

            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))
            verifies()
        }
    }

    @Test
    fun `Create must have a time window with 2 bounds`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Create(0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(30, LockableTokenType)))
            timeWindow(TimeWindow.fromOnly(creationDate.plusSeconds(60)))
            failsWith("There must be a time-window with 2 bounds")

            timeWindow(TimeWindow.untilOnly(creationDate.minusSeconds(60)))
            failsWith("There must be a time-window with 2 bounds")

            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))
            verifies()
        }
    }

    @Test
    fun `Create must have happen in a narrow enough time window`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 30, issuer, creationDate))
            command(player.owningKey, Create(0))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(30, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(61),
                    creationDate.plusSeconds(59)))
            failsWith("The time-window bounds must not be far from the creation date")

            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(59),
                    creationDate.plusSeconds(61)))
            failsWith("The time-window bounds must not be far from the creation date")

            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))
            verifies()

            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(59),
                    creationDate.plusSeconds(59)))
            verifies()
        }
    }

    @Test
    fun `On create player must sign`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            output(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))
            reference(LockableTokenContract.id,
                    LockableTokenState(player, issuer, Amount(21, LockableTokenType)))
            timeWindow(TimeWindow.between(
                    creationDate.minusSeconds(60),
                    creationDate.plusSeconds(60)))

            tweak {
                command(issuer.owningKey, Create(0))
                failsWith("The created entry player must be a signer")
            }

            command(player.owningKey, Create(0))
            verifies()
        }
    }
}