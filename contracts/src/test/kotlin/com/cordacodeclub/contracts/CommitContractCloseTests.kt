package com.cordacodeclub.contracts

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

class CommitContractCloseTests {

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
        val revealDeadline = Instant.now().plusSeconds(60)
        input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(398L, LockableTokenType)))
        input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3L, LockableTokenType)))
        output(CommitContract.id, CommittedState(casinoHash, casino,
                2, casinoId))
        output(CommitContract.id, CommittedState(playerHash, player,
                2, playerId))
        output(GameContract.id, 3, GameState(casino commitsTo casinoId with (398L issuedBy issuer),
                player commitsTo playerId with (2L issuedBy issuer), revealDeadline, 3,
                UniqueIdentifier(), listOf(casino, player)))
        output(LockableTokenContract.id, 2, LockableTokenState(issuer, Amount(400L, LockableTokenType),
                listOf(casino, player)))
        output(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(1L, LockableTokenType)))
        command(casino.owningKey, CommitContract.Commands.Commit(0))
        command(player.owningKey, CommitContract.Commands.Commit(1))
        command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Create(2))
        command(listOf(casino.owningKey, player.owningKey),
                LockableTokenContract.Commands.Lock(listOf(0, 1), listOf(3, 4)))
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
        command(committed.creator.owningKey, CommitContract.Commands.Reveal(0, 0))
        reference(gameRef.ref)
        timeWindow(TimeWindow.untilOnly(gameRef.state.data.revealDeadline))
        verifies()
    }

    //TODO fix failing test
//    @Test
//    fun `Close command needs a CommittedState state input`() {
//        ledgerServices.transaction {
//            val casinoId = UniqueIdentifier()
//            val revealDeadline = Instant.now().plusSeconds(60)
//            input(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
//                    revealDeadline, 2, casinoId))
//            input(DummyContract.PROGRAM_ID, DummyState())
//            command(player.owningKey, DummyCommandData)
//            command(player.owningKey, CommitContract.Commands.Close(1))
//            failsWith("The input must be a CommitState")
//        }
//    }

    @Test
    fun `Close command passes contract with 1 commit`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L))
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (gameRef) = issueTx.outRefsOfType<GameState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            val (playerRevealRef) = reveal(playerRef, playerImage, gameRef).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRef.ref)
                command(player.owningKey, CommitContract.Commands.Close(0))
                input(playerRevealRef.ref)
                command(player.owningKey, CommitContract.Commands.Use(1))
                input(playerRef.getGamePointer().pointer)
                command(player.owningKey, GameContract.Commands.Close(2))
                input(lockedRef.ref)
                output(LockableTokenContract.id,
                        LockableTokenState(player, issuer, Amount(2L, LockableTokenType)))
                output(LockableTokenContract.id,
                        LockableTokenState(casino, issuer, Amount(398L, LockableTokenType)))
                command(player.owningKey, LockableTokenContract.Commands.Release(listOf(3), listOf(0, 1)))

                timeWindow(TimeWindow.fromOnly(gameRef.state.data.revealDeadline.plusSeconds(1)))
                verifies()
            }
        }
    }
}