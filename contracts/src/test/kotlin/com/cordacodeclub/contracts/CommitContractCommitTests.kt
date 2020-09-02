package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.states.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import java.time.Instant

class CommitContractCommitTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Ansterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `CommitContract with a Commit needs a command`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino)))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))
            failsWith("The CommitContract must find at least 1 command")

            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            verifies()
        }
    }

    @Test
    fun `Commit command needs a Committed state output`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino)))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))

            tweak {
                command(casino.owningKey, Commit(2))
                failsWith("The output must be a CommitState")
            }

            command(casino.owningKey, Commit(0))
            verifies()
        }
    }

    @Test
    fun `Commit command needs the creator to be a signer`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino)))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))

            tweak {
                command(player.owningKey, Commit(0))
                failsWith("The creator must sign")
            }

            tweak {
                command(listOf(casino.owningKey, player.owningKey), Commit(0))
                verifies()
            }

            command(casino.owningKey, Commit(0))
            verifies()
        }
    }

    @Test
    fun `Commit needs the Committed state with a correct game index`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        revealDeadline, -1, casinoId))
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                        revealDeadline, 2, playerId))
                output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                        player commitsTo playerId with (1L issuedBy issuer),
                        UniqueIdentifier(), listOf(casino, player)))
                failsWith("The game output index must be possible")
            }

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        revealDeadline, 3, casinoId))
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                        revealDeadline, 2, playerId))
                output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                        player commitsTo playerId with (1L issuedBy issuer),
                        UniqueIdentifier(), listOf(casino, player)))
                failsWith("The game output index must be possible")
            }

            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino, player)))
            verifies()
        }
    }

    @Test
    fun `Commit needs the Game state at the right index`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        revealDeadline, 1, casinoId))
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                        revealDeadline, 2, playerId))
                output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                        player commitsTo playerId with (1L issuedBy issuer),
                        UniqueIdentifier(), listOf(casino, player)))
                failsWith("The game output must be at the right index")
            }

            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino, player)))
            verifies()
        }
    }

    @Test
    fun `Commit cannot accept duplicate ids in outputs`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino, player)))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(0))
            command(casino.owningKey, Commit(1))
            command(player.owningKey, Commit(2))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 0, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 0, playerId))
            verifies()

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        revealDeadline, 0, casinoId))
                failsWith("There is more than 1 output state with a given id")
            }

        }
    }

    @Test
    fun `Commit cannot accept unidentified commit states`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino, player)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 0, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 0, playerId))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(0))
            command(casino.owningKey, Commit(1))
            failsWith("All outputs states which belong to one party must have an associated command")

            command(player.owningKey, Commit(2))
            verifies()
        }
    }

    @Test
    fun `Commit game needs to have correct commit ids`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))

            tweak {
                output(GameContract.id, GameState(casino commitsTo UniqueIdentifier() with (10L issuedBy issuer),
                        player commitsTo playerId with (1L issuedBy issuer),
                        UniqueIdentifier(), listOf(casino, player)))
                failsWith("The game commit ids must all loop back")
            }

            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino, player)))
            verifies()

        }
    }

    @Test
    fun `Commit command works`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer),
                    UniqueIdentifier(), listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))
            verifies()
        }
    }

}