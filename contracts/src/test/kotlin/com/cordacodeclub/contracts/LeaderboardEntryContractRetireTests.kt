package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.LeaderboardEntryContract.Commands.Retire
import com.cordacodeclub.states.LeaderboardEntryState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import java.time.Instant

class LeaderboardEntryContractRetireTests {

    private val notaryId = TestIdentity(CordaX500Name("Notary", "Washington D.C.", "US"))
    private val ledgerServices = MockServices(
            cordappPackages = listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"),
            firstIdentity = notaryId,
            networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4))
    private val issuerId = TestIdentity(CordaX500Name("Issuer", "Amsterdam", "NL"))
    private val issuer = issuerId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `Retire command needs a leaderboard entry state input`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))
            input(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(player.owningKey, Retire(1))
                failsWith("The input must be a LeaderboardEntryState")
            }

            command(player.owningKey, Retire(0))
            verifies()
        }
    }

    @Test
    fun `On retire player must sign`() {
        ledgerServices.transaction {
            val creationDate = Instant.now()
            input(LeaderboardEntryContract.id, LeaderboardEntryState(player, 21, issuer, creationDate))

            tweak {
                command(issuer.owningKey, Retire(0))
                failsWith("The retired entry player must be a signer")
            }

            command(player.owningKey, Retire(0))
            verifies()
        }
    }
}