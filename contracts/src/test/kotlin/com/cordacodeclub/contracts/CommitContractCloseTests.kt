package com.cordacodeclub.contracts

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
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.util.*

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
    private val random = Random()

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.issueTwoCommits(
            casinoHash: SecureHash, playerHash: SecureHash) = transaction {
        val casinoId = UniqueIdentifier()
        val playerId = UniqueIdentifier()
        val revealDeadline = Instant.now().plusSeconds(60)
        input(LockableTokenContract.id, LockableTokenState(casino, issuer, Amount(398L, LockableTokenType)))
        input(LockableTokenContract.id, LockableTokenState(player, issuer, Amount(3L, LockableTokenType)))
        output(CommitContract.id, CommittedState(casinoHash, casino,
                revealDeadline, 2, casinoId))
        output(CommitContract.id, CommittedState(playerHash, player,
                revealDeadline, 2, playerId))
        output(GameContract.id, 3, GameState(casino commitsTo casinoId with (398L issuedBy issuer),
                player commitsTo playerId with (2L issuedBy issuer), 3,
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

    @Test
    fun `Close command passes contract`() {
        val casinoImage = CommitImage(BigInteger.valueOf(11L))
        val playerImage = CommitImage(BigInteger.valueOf(12L))
        ledgerServices.ledger {
            val issueTx = issueTwoCommits(casinoImage.hash, playerImage.hash)
            val (casinoRef, playerRef) = issueTx.outRefsOfType<CommittedState>()
            val (lockedRef) = issueTx.outRefsOfType<LockableTokenState>()
            transaction {
                input(casinoRef.ref)
                input(playerRef.ref)
                reference(playerRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                command(player.owningKey, CommitContract.Commands.Close(0))
                command(player.owningKey, CommitContract.Commands.Close(1))
                command(listOf(casino.owningKey, player.owningKey), GameContract.Commands.Close(2))
                verifies()
            }
        }
    }
}