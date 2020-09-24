package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.*
import com.cordacodeclub.contracts.GameContract.Commands.Create
import com.cordacodeclub.contracts.GameContract.Commands.Resolve
import com.cordacodeclub.contracts.LockableTokenContract.Commands.Release
import com.cordacodeclub.states.*
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.LedgerDSL
import net.corda.testing.dsl.TestLedgerDSLInterpreter
import net.corda.testing.dsl.TestTransactionDSLInterpreter
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Ignore
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.util.*

class GameContractResolveTests {

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
    private val random = Random()

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.issueTwoCommits(
            casinoHash: SecureHash, playerHash: SecureHash) = transaction {
        val casinoId = UniqueIdentifier()
        val playerId = UniqueIdentifier()
        val commitDeadline = Instant.now().plusSeconds(30)
        val revealDeadline = commitDeadline.plusSeconds(30)
        input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(398L, LockableTokenType)))
        input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3L, LockableTokenType)))
        output(CommitContract.id, CommittedState(casinoHash, casino,
                2, casinoId))
        output(CommitContract.id, CommittedState(playerHash, player,
                2, playerId))
        output(GameContract.id, 3, GameState(casino commitsTo casinoId with (398L issuedBy issuer),
                player commitsTo playerId with (2L issuedBy issuer), commitDeadline, revealDeadline, 3,
                UniqueIdentifier(), listOf(casino, player)))
        output(LockableTokenContract.id, 2, LockableTokenState(issuer, Amount(400L, LockableTokenType),
                listOf(casino, player)))
        output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
        command(casino.owningKey, Commit(0))
        command(player.owningKey, Commit(1))
        command(listOf(casino.owningKey, player.owningKey), Create(2))
        command(listOf(casino.owningKey, player.owningKey),
                LockableTokenContract.Commands.Lock(listOf(0, 1), listOf(3, 4)))
        timeWindow(TimeWindow.untilOnly(commitDeadline))
        verifies()
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.reveal(
            committedRef: StateAndRef<CommittedState>, image: CommitImage,
            gameRef: StateAndRef<GameState>) = transaction {
        require(gameRef.ref == committedRef.getGamePointer().pointer) { "Wrong gameRef" }
        val committed = committedRef.state.data
        input(committedRef.ref)
        output(CommitContract.id, RevealedState(image, committed.creator, committedRef.getGamePointer(),
                committed.linearId))
        command(committed.creator.owningKey, Reveal(0, 0))
        reference(gameRef.ref)
        timeWindow(TimeWindow.untilOnly(gameRef.state.data.revealDeadline))
        verifies()
    }

    @Test
    fun `Resolve command needs a Game state input`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(player.owningKey, Use(0))
                command(player.owningKey, Use(1))
                input(lockedRef.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(400L, LockableTokenType)))
                command(player.owningKey, Release(listOf(2), listOf(0)))
                input(gameRef.ref)

                tweak {
                    command(player.owningKey, Resolve(1))
                    failsWith("The input must be a GameState")
                }

                command(player.owningKey, Resolve(3))
                verifies()
            }
        }
    }

    @Test
    @Ignore("How to circumvent the encumbrance check?")
    fun `Resolve must have reveals at the id`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(400L, LockableTokenType)))
                command(player.owningKey, Release(listOf(1), listOf(0)))
                command(player.owningKey, Resolve(0))
                command(player.owningKey, Use(2))
                command(player.owningKey, Use(3))

                tweak {
                    input(GameContract.id, gameRef.state.data.let { state ->
                        state.copy(casino = state.casino
                                .let { bettor ->
                                    bettor.copy(committer = bettor.committer.copy(linearId = UniqueIdentifier()))
                                })
                    })
                    input(lockedRef.ref)
                    input(casinoRevealRef.ref)
                    input(playerRevealRef.ref)
                    failsWith("The commit ids must all be associated RevealedStates")
                }

                input(gameRef.ref)
                input(lockedRef.ref)
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                verifies()
            }
        }
    }

    @Test
    fun `Resolved game must have reveals that point to it`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(gameRef.ref)
                command(player.owningKey, Resolve(0))
                input(lockedRef.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(400L, LockableTokenType)))
                command(player.owningKey, Release(listOf(1), listOf(0)))
                input(casinoRevealRef.ref)
                command(player.owningKey, Use(2))
                command(player.owningKey, Use(3))

                tweak {
                    input(CommitContract.id, playerRevealRef.state.data
                            .copy(game = StaticPointer(casinoRevealRef.ref, GameState::class.java)))
                    failsWith("The commit ids must all be associated RevealedStates")
                }

                input(playerRevealRef.ref)
                verifies()
            }
        }
    }

    @Test
    fun `Resolved game must have the correct payout - intermediate`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(34L)) // -> 10 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(gameRef.ref)
                command(player.owningKey, Resolve(0))
                input(lockedRef.ref)
                command(player.owningKey, Release(listOf(1), listOf(0, 1)))
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(player.owningKey, Use(2))
                command(player.owningKey, Use(3))

                tweak {
                    output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(382L, LockableTokenType)))
                    output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(18L, LockableTokenType)))
                    failsWith("The player payout should be correct")
                }

                tweak {
                    output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(378L, LockableTokenType)))
                    output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(22L, LockableTokenType)))
                    failsWith("The player payout should be correct")
                }

                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(380L, LockableTokenType)))
                output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(20L, LockableTokenType)))
                verifies()
            }
        }
    }

    @Test
    fun `Resolved game must have the correct payout - player loses`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 50 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(gameRef.ref)
                command(player.owningKey, Resolve(0))
                input(lockedRef.ref)
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(player.owningKey, Use(2))
                command(player.owningKey, Use(3))

                tweak {
                    output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(398L, LockableTokenType)))
                    output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(2L, LockableTokenType)))
                    command(player.owningKey, Release(listOf(1), listOf(0, 1)))
                    failsWith("The player payout should be correct")
                }

                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(400L, LockableTokenType)))
                command(player.owningKey, Release(listOf(1), listOf(0)))
                verifies()
            }
        }
    }

    @Test
    fun `Resolved game must have the correct payout - player wins all`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(3_071L)) // -> 200 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(gameRef.ref)
                command(player.owningKey, Resolve(0))
                input(lockedRef.ref)
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(player.owningKey, Use(2))
                command(player.owningKey, Use(3))

                tweak {
                    output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(2L, LockableTokenType)))
                    output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(398L, LockableTokenType)))
                    command(player.owningKey, Release(listOf(1), listOf(0, 1)))
                    failsWith("The player payout should be correct")
                }

                output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(400L, LockableTokenType)))
                command(player.owningKey, Release(listOf(1), listOf(0)))
                verifies()
            }
        }
    }

    @Test
    fun `All input game states must be covered by a command`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val issueTx2 = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (gameRef2) = issueTx2.outRefsOfType<GameState>()
            val (lockedRef2) = issueTx2.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage, gameRef).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(gameRef.ref)
                command(player.owningKey, Resolve(0))
                input(lockedRef.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(400L, LockableTokenType)))
                command(player.owningKey, Release(listOf(1), listOf(0)))
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(player.owningKey, Use(2))
                command(player.owningKey, Use(3))
                verifies()

                input(gameRef2.ref)
                input(lockedRef2.ref)
                failsWith("All input game states must have an associated command")
            }
        }
    }
}