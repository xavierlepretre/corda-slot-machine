package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.*
import com.cordacodeclub.contracts.GameContract.Commands.Resolve
import com.cordacodeclub.contracts.LockableTokenContract.Commands.Release
import com.cordacodeclub.states.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
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
import java.math.BigInteger
import java.time.Instant
import java.util.*

class CommitContractUseTests {

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

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.issueTwoCommits(
            casinoHash: SecureHash, playerHash: SecureHash) = transaction {
        val casinoId = UniqueIdentifier()
        val playerId = UniqueIdentifier()
        val revealDeadline = Instant.now().plusSeconds(30)
        input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(201L, LockableTokenType)))
        output(CommitContract.id, CommittedState(casinoHash, casino,
                revealDeadline, 2, casinoId))
        output(CommitContract.id, CommittedState(playerHash, player,
                revealDeadline, 2, playerId))
        output(GameContract.id, 3, GameState(casino commitsTo casinoId with (199L issuedBy issuer),
                player commitsTo playerId with (1L issuedBy issuer), 3,
                UniqueIdentifier(), listOf(casino, player)))
        output(LockableTokenContract.id, 2, LockableTokenState(issuer, Amount(200L, LockableTokenType),
                listOf(casino, player)))
        output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
        command(casino.owningKey, Commit(0))
        command(player.owningKey, Commit(1))
        command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))
        command(player.owningKey, LockableTokenContract.Commands.Lock(listOf(0), listOf(3, 4)))
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
    fun `Use command needs a Revealed state input`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                input(casinoRef.getGamePointer().pointer)
                input(lockedRef.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(200L, LockableTokenType)))

                command(player.owningKey, Use(1))
                command(player.owningKey, Resolve(2))
                command(player.owningKey, Release(listOf(3), listOf(0)))

                tweak {
                    command(casino.owningKey, Use(2))
                    failsWith("The input must be a RevealedState")
                }

                command(casino.owningKey, Use(0))
                verifies()
            }
        }
    }

    @Test
    fun `Use must have game in input`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                input(lockedRef.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(200L, LockableTokenType)))
                command(casino.owningKey, Use(0))
                command(player.owningKey, Use(1))
                command(player.owningKey, Release(listOf(2), listOf(0)))
                command(player.owningKey, Resolve(3))

                failsWith("Missing required encumbrance 2")

                input(casinoRef.getGamePointer().pointer)
                verifies()
            }
        }
    }

    @Test
    fun `Use cannot accept unidentified commit states`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef1, playerRef1) = issueTx.outRefsOfType<CommittedState>()
            val (lockedRef1) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef1) = reveal(casinoRef1, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef1) = reveal(playerRef1, playerImage).outRefsOfType<RevealedState>()
            val (_, playerRef2) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef1.getGamePointer().pointer)
                input(casinoRevealRef1.ref)
                input(playerRevealRef1.ref)
                input(lockedRef1.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(200L, LockableTokenType)))
                command(player.owningKey, Resolve(0))
                command(casino.owningKey, Use(1))
                command(player.owningKey, Use(2))
                command(player.owningKey, Release(listOf(3), listOf(0)))
                verifies()

                tweak {
                    input(playerRef2.ref)
                    failsWith("All inputs states which belong to one party must have an associated command")

                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef2.getGamePointer(), playerRef2.state.data.linearId))
                    command(player.owningKey, Reveal(4, 1))
                    reference(playerRef2.getGamePointer().pointer)
                    timeWindow(TimeWindow.untilOnly(Instant.now()))
                    verifies()
                }

                tweak {
                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef2.getGamePointer(), playerRef2.state.data.linearId))
                    failsWith("All outputs states which belong to one party must have an associated command")

                    input(playerRef2.ref)
                    command(player.owningKey, Reveal(4, 1))
                    reference(playerRef2.getGamePointer().pointer)
                    timeWindow(TimeWindow.untilOnly(Instant.now()))
                    verifies()
                }

            }
        }
    }

    @Test
    fun `Use command can be signed by anyone`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L)) // -> 0 payout
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRef.getGamePointer().pointer)
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                input(lockedRef.ref)
                output(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(200L, LockableTokenType)))
                command(player.owningKey, Resolve(0))
                command(casino.owningKey, Use(1))
                command(player.owningKey, Release(listOf(3), listOf(0)))

                tweak {
                    command(casino.owningKey, Use(2))
                    verifies()
                }

                command(player.owningKey, Use(2))
                verifies()
            }
        }
    }

}