package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
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
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party

    @Test
    fun `Commit command needs a Committed state output`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 1, casinoId))
            output(CommitContract.id, GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino)))

            tweak {
                command(casino.owningKey, Commit(1))
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
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 1, casinoId))
            output(CommitContract.id, GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino)))

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
            output(CommitContract.id, GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino, player)))
            command(casino.owningKey, Commit(1))

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        Instant.now(), -1, casinoId))
                failsWith("The game output index must be possible")
            }

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        Instant.now(), 2, casinoId))
                failsWith("The game output index must be possible")
            }

            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 0, casinoId))
            verifies()
        }
    }

    @Test
    fun `Commit needs the Game state at the right index`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            output(CommitContract.id, GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino, player)))
            command(casino.owningKey, Commit(1))

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        Instant.now(), 1, casinoId))
                failsWith("The game output must be at the right index")
            }

            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 0, casinoId))
            verifies()
        }
    }

    @Test
    fun `Commit cannot accept duplicate ids in outputs`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            output(CommitContract.id, GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino, player)))
            command(casino.owningKey, Commit(1))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 0, casinoId))
            verifies()

            tweak {
                output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                        Instant.now(), 0, casinoId))
                failsWith("There is more than 1 output state with a given id")
            }

        }
    }

    @Test
    fun `Commit cannot accept unidentified commit states`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            output(CommitContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 0, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    Instant.now(), 0, playerId))
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
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 1, casinoId))
            command(casino.owningKey, Commit(0))

            tweak {
                output(CommitContract.id, GameState(listOf(UniqueIdentifier()), UniqueIdentifier(), listOf(casino, player)))
                failsWith("The game commit ids must all loop back")
            }

            output(CommitContract.id, GameState(listOf(casinoId), UniqueIdentifier(), listOf(casino, player)))
            verifies()

        }
    }

    @Test
    fun `Commit command works`() {
        ledgerServices.transaction {
            val casinoId = UniqueIdentifier()
            val playerId = UniqueIdentifier()
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino,
                    Instant.now(), 2, casinoId))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                    Instant.now(), 2, playerId))
            output(CommitContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            verifies()
        }
    }

}