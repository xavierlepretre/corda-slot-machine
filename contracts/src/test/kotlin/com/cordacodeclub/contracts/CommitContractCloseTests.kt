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
import net.corda.testing.node.transaction
import org.junit.Test
import java.time.Instant
import java.util.*

class CommitContractCloseTests {

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

    @Test
    fun `Close command passes contract`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val gameStateAndRef = StateAndRef(TransactionState(
                    GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino)), GameContract.id, notaryId.party), StateRef(SecureHash.zeroHash, 1))
            input(GameContract.id, gameStateAndRef.state.data)
            input(CommitContract.id, RevealedState(CommitImage.createRandom(random), player,
                    StaticPointer(gameStateAndRef.ref, GameState::class.java), UniqueIdentifier(), listOf(player, casino)))
            command(casino.owningKey, CommitContract.Commands.Close)
            command(casino.owningKey, GameContract.Commands.Resolve(0))
            verifies()
        }
    }
}