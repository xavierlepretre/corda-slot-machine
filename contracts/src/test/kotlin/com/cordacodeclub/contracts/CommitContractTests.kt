package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.*
import com.cordacodeclub.states.CommitImage
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.RevealedState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import java.util.*

class CommitContractTests {

    private val ledgerServices = MockServices(listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"))
    private val casinoId = TestIdentity(CordaX500Name("Casino", "London", "GB"))
    private val casino = casinoId.identity.party
    private val playerId = TestIdentity(CordaX500Name("Player", "Paris", "FR"))
    private val player = playerId.identity.party
    private val random = Random()

    @Test
    fun `Commit command needs a Committed state output`() {
        ledgerServices.transaction {
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino, UniqueIdentifier()))
            output(DummyContract.PROGRAM_ID, DummyState())

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
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino, UniqueIdentifier()))

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
    fun `Commit command works`() {
        ledgerServices.transaction {
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), casino, UniqueIdentifier()))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player, UniqueIdentifier()))
            command(casino.owningKey, Commit(0))
            command(player.owningKey, Commit(1))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs a Committed state input`() {
        val image = CommitImage.createRandom(random)
        val id = UniqueIdentifier()
        ledgerServices.transaction {
            output(CommitContract.id, RevealedState(image, casino, id))
            input(CommitContract.id, CommittedState(image.hash, casino, id))
            input(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(casino.owningKey, Reveal(1, 0))
                failsWith("The input must be a CommittedState")
            }

            command(casino.owningKey, Reveal(0, 0))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs a Revealed state output`() {
        val image = CommitImage.createRandom(random)
        val id = UniqueIdentifier()
        ledgerServices.transaction {
            input(CommitContract.id, CommittedState(image.hash, casino, id))
            output(CommitContract.id, RevealedState(image, casino, id))
            output(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(casino.owningKey, Reveal(0, 1))
                failsWith("The output must be a RevealedState")
            }

            command(casino.owningKey, Reveal(0, 0))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs matching ids`() {
        val image = CommitImage.createRandom(random)
        val id1 = UniqueIdentifier()
        val id2 = UniqueIdentifier()
        ledgerServices.transaction {
            input(CommitContract.id, CommittedState(image.hash, casino, id1))
            input(CommitContract.id, CommittedState(image.hash, casino, id2))
            output(CommitContract.id, RevealedState(image, casino, id1))
            command(casino.owningKey, Reveal(0, 0))
            command(casino.owningKey, Reveal(1, 1))

            tweak {
                output(CommitContract.id, RevealedState(image, casino, id1))
                failsWith("The linear ids must match")
            }

            output(CommitContract.id, RevealedState(image, casino, id2))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs correct image`() {
        val image = CommitImage.createRandom(random)
        val id1 = UniqueIdentifier()
        ledgerServices.transaction {
            output(CommitContract.id, RevealedState(image, casino, id1))
            command(casino.owningKey, Reveal(0, 0))

            tweak {
                input(CommitContract.id, CommittedState(SecureHash.allOnesHash, casino, id1))
                failsWith("The commit image must match")
            }

            input(CommitContract.id, CommittedState(image.hash, casino, id1))
            verifies()
        }
    }

    @Test
    fun `Reveal command leaves creator unchanged`() {
        val image = CommitImage.createRandom(random)
        val id1 = UniqueIdentifier()
        ledgerServices.transaction {
            output(CommitContract.id, RevealedState(image, casino, id1))
            command(casino.owningKey, Reveal(0, 0))

            tweak {
                input(CommitContract.id, CommittedState(image.hash, player, id1))
                failsWith("The creator must be unchanged")
            }

            input(CommitContract.id, CommittedState(image.hash, casino, id1))
            verifies()
        }
    }

    @Test
    fun `Reveal command can be signed by anyone`() {
        val image = CommitImage.createRandom(random)
        val id1 = UniqueIdentifier()
        ledgerServices.transaction {
            input(CommitContract.id, CommittedState(image.hash, casino, id1))
            output(CommitContract.id, RevealedState(image, casino, id1))

            tweak {
                command(player.owningKey, Reveal(0, 0))
                verifies()
            }

            command(casino.owningKey, Reveal(0, 0))
            verifies()
        }
    }

    @Test
    fun `Use command needs a Revealed state input`() {
        val image = CommitImage.createRandom(random)
        ledgerServices.transaction {
            input(CommitContract.id, RevealedState(image, casino, UniqueIdentifier()))
            input(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(casino.owningKey, Use(1))
                failsWith("The input must be a RevealedState")
            }

            command(casino.owningKey, Use(0))
            verifies()
        }
    }

    @Test
    fun `Use command needs the creator to be a signer`() {
        val image = CommitImage.createRandom(random)
        ledgerServices.transaction {
            input(CommitContract.id, RevealedState(image, casino, UniqueIdentifier()))

            tweak {
                command(player.owningKey, Use(0))
                failsWith("The creator must sign")
            }

            command(casino.owningKey, Use(0))
            verifies()
        }
    }

}