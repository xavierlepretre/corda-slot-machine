package com.cordacodeclub.contracts

import com.cordacodeclub.states.*
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
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

    @Test
    fun `Close command passes contract`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            val gameStateAndRef = StateAndRef(
                    TransactionState(
                            GameState(
                                    casino commitsTo casinoId with (199L issuedBy issuer),
                                    player commitsTo playerId with (1L issuedBy issuer),
                                    3,
                                    UniqueIdentifier(),
                                    listOf(casino)),
                            GameContract.id,
                            notaryId.party),
                    StateRef(SecureHash.zeroHash, 1))
            input(GameContract.id, gameStateAndRef.state.data)
            input(CommitContract.id, RevealedState(
                    CommitImage.createRandom(random),
                    casino,
                    StaticPointer(gameStateAndRef.ref, GameState::class.java),
                    UniqueIdentifier(),
                    listOf(casino)))
            command(casino.owningKey, CommitContract.Commands.Close(0))
            command(casino.owningKey, GameContract.Commands.Close)
            verifies()
        }
    }
}