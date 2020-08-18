package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.states.CommittedState
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
    fun `Commit command needs a Commit state output`() {
        ledgerServices.transaction {
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), alice, UniqueIdentifier(), listOf(alice)))
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
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), alice, UniqueIdentifier(), listOf(alice)))

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
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), alice, UniqueIdentifier(), listOf(alice)))
            output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), bob, UniqueIdentifier(), listOf(bob)))
            command(alice.owningKey, Commit(0))
            command(bob.owningKey, Commit(1))
            verifies()
        }
    }

}