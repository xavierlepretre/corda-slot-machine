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

class CommitContractTests {

    private val ledgerServices = MockServices(listOf("com.cordacodeclub.contracts", "net.corda.testing.contracts"))
    private val aliceId = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val alice = aliceId.identity.party
    private val bobId = TestIdentity(CordaX500Name("Bob", "Paris", "FR"))
    private val bob = bobId.identity.party
    private val carolId = TestIdentity(CordaX500Name("Carol", "Rotterdam", "NL"))
    private val carol = carolId.identity.party

    @Test
    fun `Commit command needs a Committed state output`() {
        ledgerServices.transaction {
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), alice, UniqueIdentifier()))
            output(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(alice.owningKey, Commit(1))
                failsWith("The output must be a CommitState")
            }

            command(alice.owningKey, Commit(0))
            verifies()
        }
    }

    @Test
    fun `Commit command needs the creator to be a signer`() {
        ledgerServices.transaction {
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), alice, UniqueIdentifier()))

            tweak {
                command(bob.owningKey, Commit(0))
                failsWith("The creator must sign")
            }

            tweak {
                command(listOf(alice.owningKey, bob.owningKey), Commit(0))
                verifies()
            }

            command(alice.owningKey, Commit(0))
            verifies()
        }
    }

    @Test
    fun `Commit command works`() {
        ledgerServices.transaction {
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), alice, UniqueIdentifier()))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), bob, UniqueIdentifier()))
            command(alice.owningKey, Commit(0))
            command(bob.owningKey, Commit(1))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs a Committed state input`() {
        val image = CommitImage(0L, 1L)
        val id = UniqueIdentifier()
        ledgerServices.transaction {
            output(CommitContract.id, RevealedState(image, alice, id))
            input(CommitContract.id, CommittedState(image.hash, alice, id))
            input(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(alice.owningKey, Reveal(1, 0))
                failsWith("The input must be a CommittedState")
            }

            command(alice.owningKey, Reveal(0, 0))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs a Revealed state output`() {
        val image = CommitImage(0L, 1L)
        val id = UniqueIdentifier()
        ledgerServices.transaction {
            input(CommitContract.id, CommittedState(image.hash, alice, id))
            output(CommitContract.id, RevealedState(image, alice, id))
            output(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(alice.owningKey, Reveal(0, 1))
                failsWith("The output must be a RevealedState")
            }

            command(alice.owningKey, Reveal(0, 0))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs matching ids`() {
        val image = CommitImage(0L, 1L)
        val id1 = UniqueIdentifier()
        val id2 = UniqueIdentifier()
        ledgerServices.transaction {
            input(CommitContract.id, CommittedState(image.hash, alice, id1))
            input(CommitContract.id, CommittedState(image.hash, alice, id2))
            output(CommitContract.id, RevealedState(image, alice, id1))
            command(alice.owningKey, Reveal(0, 0))
            command(alice.owningKey, Reveal(1, 1))

            tweak {
                output(CommitContract.id, RevealedState(image, alice, id1))
                failsWith("The linear ids must match")
            }

            output(CommitContract.id, RevealedState(image, alice, id2))
            verifies()
        }
    }

    @Test
    fun `Reveal command needs correct image`() {
        val image = CommitImage(0L, 1L)
        val id1 = UniqueIdentifier()
        ledgerServices.transaction {
            output(CommitContract.id, RevealedState(image, alice, id1))
            command(alice.owningKey, Reveal(0, 0))

            tweak {
                input(CommitContract.id, CommittedState(SecureHash.allOnesHash, alice, id1))
                failsWith("The commit image must match")
            }

            input(CommitContract.id, CommittedState(image.hash, alice, id1))
            verifies()
        }
    }

    @Test
    fun `Reveal command leaves creator unchanged`() {
        val image = CommitImage(0L, 1L)
        val id1 = UniqueIdentifier()
        ledgerServices.transaction {
            output(CommitContract.id, RevealedState(image, alice, id1))
            command(alice.owningKey, Reveal(0, 0))

            tweak {
                input(CommitContract.id, CommittedState(image.hash, bob, id1))
                failsWith("The creator must be unchanged")
            }

            input(CommitContract.id, CommittedState(image.hash, alice, id1))
            verifies()
        }
    }

    @Test
    fun `Reveal command can be signed by anyone`() {
        val image = CommitImage(0L, 1L)
        val id1 = UniqueIdentifier()
        ledgerServices.transaction {
            input(CommitContract.id, CommittedState(image.hash, alice, id1))
            output(CommitContract.id, RevealedState(image, alice, id1))

            tweak {
                command(bob.owningKey, Reveal(0, 0))
                verifies()
            }

            command(alice.owningKey, Reveal(0, 0))
            verifies()
        }
    }

    @Test
    fun `Use command needs a Revealed state input`() {
        val image = CommitImage(0L, 1L)
        ledgerServices.transaction {
            input(CommitContract.id, RevealedState(image, alice, UniqueIdentifier()))
            input(DummyContract.PROGRAM_ID, DummyState())

            tweak {
                command(alice.owningKey, Use(1))
                failsWith("The input must be a RevealedState")
            }

            command(alice.owningKey, Use( 0))
            verifies()
        }
    }

    @Test
    fun `Use command needs the creator to be a signer`() {
        val image = CommitImage(0L, 1L)
        ledgerServices.transaction {
            input(CommitContract.id, RevealedState(image, alice, UniqueIdentifier()))

            tweak {
                command(bob.owningKey, Use(0))
                failsWith("The creator must sign")
            }

            command(alice.owningKey, Use( 0))
            verifies()
        }
    }

}