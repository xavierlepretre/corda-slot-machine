package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.contracts.GameContract.Commands.Create
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import java.time.Instant

class GameContractCreateTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `Game contract needs a Game command`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    Instant.now(), 2, playerId))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            output(GameContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))
            failsWith("The GameContract must find at least 1 command")

            command(listOf(casino.owningKey, player.owningKey), Create(2))
            verifies()
        }
    }

    @Test
    fun `Create command needs a Game state output`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    Instant.now(), 2, playerId))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            output(GameContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))

            tweak {
                command(listOf(casino.owningKey, player.owningKey), Create(1))
                failsWith("The output must be a GameState")
            }

            command(listOf(casino.owningKey, player.owningKey), Create(2))
            verifies()
        }
    }

    @Test
    fun `Create must have commits at the id`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    Instant.now(), 2, playerId))
            output(GameContract.id, GameState(listOf(casinoId, playerId), gameId1, listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))
            verifies()

            output(GameContract.id, GameState(listOf(casinoId, gameId1), UniqueIdentifier(), listOf(casino, player)))
            command(listOf(casino.owningKey, player.owningKey), Create(3))
            failsWith("The commit ids must all be associated CommittedStates")
        }
    }

    @Test
    fun `Created game must have commits that point to it`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    Instant.now(), 2, playerId))
            output(GameContract.id, GameState(listOf(casinoId, playerId), gameId1, listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))
            verifies()

            output(GameContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))
            command(listOf(casino.owningKey, player.owningKey), Create(3))
            failsWith("The commit ids must all be associated CommittedStates")
        }
    }

    @Test
    fun `Created game commits must have the same reveal deadline`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 1, casinoId))
            output(GameContract.id, GameState(listOf(casinoId, playerId), gameId1, listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(2))
            command(listOf(casino.owningKey, player.owningKey), Create(1))

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                        revealDeadline.plusSeconds(1), 1, playerId))
                failsWith("The commits must all have the same reveal deadline")
            }

            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 1, playerId))
            verifies()
        }
    }

}