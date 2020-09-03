package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.contracts.GameContract.Commands.Create
import com.cordacodeclub.contracts.LockableTokenContract.Commands.Lock
import com.cordacodeclub.states.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
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
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Ansterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `Game contract needs a Game command`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 4, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 4, playerId))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(player.owningKey, Lock(listOf(0), listOf(2, 3)))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 2,
                    UniqueIdentifier(), listOf(casino, player)))
            failsWith("The GameContract must find at least 1 command")

            command(listOf(casino.owningKey, player.owningKey), Create(4))
            verifies()
        }
    }

    @Test
    fun `Create command needs a Game state output`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 4, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 4, playerId))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(player.owningKey, Lock(listOf(0), listOf(2, 3)))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 2,
                    UniqueIdentifier(), listOf(casino, player)))

            tweak {
                command(listOf(casino.owningKey, player.owningKey), Create(3))
                failsWith("The output must be a GameState")
            }

            command(listOf(casino.owningKey, player.owningKey), Create(4))
            verifies()
        }
    }

    @Test
    fun `Create must have commits at the id`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 3,
                    gameId1, listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))
            command(player.owningKey, Lock(listOf(0), listOf(3, 4)))
            verifies()

            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo gameId1 with (1L issuedBy issuer), 3,
                    UniqueIdentifier(), listOf(casino, player)))
            command(listOf(casino.owningKey, player.owningKey), Create(5))
            failsWith("The commit ids must all be associated CommittedStates")
        }
    }

    @Test
    fun `Created game must have commits that point to it`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 3,
                    gameId1, listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))
            command(player.owningKey, Lock(listOf(0), listOf(3, 4)))
            verifies()

            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 3,
                    UniqueIdentifier(), listOf(casino, player)))
            command(listOf(casino.owningKey, player.owningKey), Create(5))
            failsWith("The commit ids must all be associated CommittedStates")
        }
    }

    @Test
    fun `Created game commits must have the same reveal deadline`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 1, casinoId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 2,
                    gameId1, listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(listOf(casino.owningKey, player.owningKey), Create(1))
            command(player.owningKey, Lock(listOf(0), listOf(2, 3)))
            command(player.owningKey, Commit(4))

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

    @Test
    fun `Created game bettors must have commits`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 1, casinoId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 2,
                    gameId1, listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(listOf(casino.owningKey, player.owningKey), Create(1))
            command(player.owningKey, Lock(listOf(0), listOf(2, 3)))

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        revealDeadline, 1, playerId))
                command(casino.owningKey, Commit(4))
                failsWith("The game bettors must all have commits")
            }

            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 1, playerId))
            command(player.owningKey, Commit(4))
            verifies()
        }
    }

    @Test
    fun `Created game bettors must be signers`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 3,
                    gameId1, listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(player.owningKey, Lock(listOf(0), listOf(3, 4)))

            tweak {
                command(casino.owningKey, Create(2))
                failsWith("The game bettors must be signers")
            }

            tweak {
                command(player.owningKey, Create(2))
                failsWith("The game bettors must be signers")
            }

            command(listOf(casino.owningKey, player.owningKey), Create(2))
            verifies()
        }
    }

    @Test
    fun `The locked token output index must be possible`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 4, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 4, playerId))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(4))
            command(player.owningKey, Lock(listOf(0), listOf(2, 3)))

            tweak {
                output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                        player commitsTo playerId with (1L issuedBy issuer), 5,
                        gameId1, listOf(casino, player)))
                failsWith("The output locked token index must be possible")
            }

            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 2,
                    gameId1, listOf(casino, player)))
            verifies()
        }
    }

    @Test
    fun `There must be a locked token at the given output index`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(11L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 3,
                    gameId1, listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))

            tweak {
                output(DummyContract.PROGRAM_ID, DummyState())
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                        listOf(casino, player)))
                command(player.owningKey, Lock(listOf(0), listOf(4)))
                failsWith("There must be a LockableTokenState at the output index")
            }

            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            command(player.owningKey, Lock(listOf(0), listOf(3)))
            verifies()
        }
    }

    @Test
    fun `The output locked token must be locked`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 4,
                    gameId1, listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))
            command(player.owningKey, Lock(listOf(0), listOf(3, 4)))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(1L, LockableTokenType),
                        listOf(casino, player)))
                output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(11L, LockableTokenType)))
                failsWith("The output locked token must be locked")
            }

            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            verifies()
        }
    }

    @Test
    fun `The output locked token must have the same issuer as the game`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(12L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 4,
                    gameId1, listOf(casino, player)))
            output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))

            tweak {
                input(LockableTokenContract.id, LockableTokenState(player, player, Amount(11L, LockableTokenType)))
                output(LockableTokenContract.id, LockableTokenState(player, Amount(11L, LockableTokenType),
                        listOf(casino, player)))
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                        listOf(casino, player)))
                command(player.owningKey, Lock(listOf(0), listOf(3, 5)))
                command(player.owningKey, Lock(listOf(1), listOf(4)))
                failsWith("The output locked token must have the same issuer as the game")
            }

            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            command(player.owningKey, Lock(listOf(0), listOf(3, 4)))
            verifies()
        }
    }

    @Test
    fun `The output locked token must have the right amount`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameId1 = UniqueIdentifier()
            val revealDeadline = Instant.now().plusSeconds(60)
            input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(11L, LockableTokenType)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    revealDeadline, 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    revealDeadline, 2, playerId))
            output(GameContract.id, GameState(casino commitsTo casinoId with (10L issuedBy issuer),
                    player commitsTo playerId with (1L issuedBy issuer), 3,
                    gameId1, listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            command(listOf(casino.owningKey, player.owningKey), Create(2))

            tweak {
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(10L, LockableTokenType),
                        listOf(casino, player)))
                output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
                command(player.owningKey, Lock(listOf(0), listOf(3, 4)))
                failsWith("The output locked token must have the right amount")
            }

            tweak {
                input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
                output(LockableTokenContract.id, LockableTokenState(issuer, Amount(12L, LockableTokenType),
                        listOf(casino, player)))
                command(player.owningKey, Lock(listOf(0, 1), listOf(3)))
                failsWith("The output locked token must have the right amount")
            }

            command(player.owningKey, Lock(listOf(0), listOf(3)))
            output(LockableTokenContract.id, LockableTokenState(issuer, Amount(11L, LockableTokenType),
                    listOf(casino, player)))
            verifies()
        }
    }
}