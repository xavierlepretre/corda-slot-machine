package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.*
import com.cordacodeclub.contracts.GameContract.Commands.Create
import com.cordacodeclub.contracts.GameContract.Commands.Resolve
import com.cordacodeclub.states.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StaticPointer
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.LedgerDSL
import net.corda.testing.dsl.TestLedgerDSLInterpreter
import net.corda.testing.dsl.TestTransactionDSLInterpreter
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant
import java.util.*

class GameContractResolveTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party
    private val random = Random()

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.issueTwoCommits(
            casinoHash: SecureHash, playerHash: SecureHash) = transaction {
        val casinoId = UniqueIdentifier()
        val playerId = UniqueIdentifier()
        output(CommitContract.id, CommittedState(casinoHash, casino,
                Instant.now().plusSeconds(30), 2, casinoId))
        output(CommitContract.id, CommittedState(playerHash, player,
                Instant.now().plusSeconds(30), 2, playerId))
        output(GameContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))
        command(casino.owningKey, Commit(0))
        command(player.owningKey, Commit(1))
        command(player.owningKey, Create(2))
        verifies()
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.reveal(
            committedRef: StateAndRef<CommittedState>, image: CommitImage) = transaction {
        val committed = committedRef.state.data
        input(committedRef.ref)
        output(CommitContract.id, RevealedState(image, committed.creator, committedRef.getGamePointer(),
                committed.linearId))
        command(committed.creator.owningKey, Reveal(0, 0))
        reference(committedRef.getGamePointer().pointer)
        timeWindow(TimeWindow.untilOnly(committed.revealDeadline))
        verifies()
    }

    @Test
    fun `Resolve command needs a Game state input`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(player.owningKey, Use(0))
                command(player.owningKey, Use(1))
                input(gameRef.ref)

                tweak {
                    command(player.owningKey, Resolve(1))
                    failsWith("The input must be a GameState")
                }

                command(player.owningKey, Resolve(2))
                verifies()
            }
        }
    }

    @Test
    fun `Resolve must have reveals at the id`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage).outRefsOfType<RevealedState>()
            transaction {
                command(player.owningKey, Resolve(0))
                command(player.owningKey, Use(1))
                command(player.owningKey, Use(2))

                tweak {
                    input(GameContract.id, gameRef.state.data.copy(commitIds = listOf(
                            casinoRef.state.data.linearId, UniqueIdentifier())))
                    input(casinoRevealRef.ref)
                    input(playerRevealRef.ref)
                    failsWith("The commit ids must all be associated RevealedStates")
                }

                input(gameRef.ref)
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                verifies()
            }
        }
    }

    @Test
    fun `Resolved game must have reveals that point to it`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef1, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(gameRef.ref)
                input(casinoRevealRef.ref)
                command(player.owningKey, Resolve(0))
                command(player.owningKey, Use(1))
                command(player.owningKey, Use(2))

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
}