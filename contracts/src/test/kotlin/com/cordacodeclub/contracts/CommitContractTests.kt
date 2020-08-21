package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.CommitContract.Commands.*
import com.cordacodeclub.states.*
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
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

class CommitContractTests {

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
    private val basicGame = StaticPointer(
            StateRef(SecureHash.allOnesHash, 1), GameState::class.java)

    fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.issueTwoCommits(
            casinoHash: SecureHash, playerHash: SecureHash) = transaction {
        val casinoId = UniqueIdentifier()
        val playerId = UniqueIdentifier()
        output(CommitContract.id, CommittedState(casinoHash, casino,
                Instant.now().plusSeconds(30), 2, casinoId))
        output(CommitContract.id, CommittedState(playerHash, player,
                Instant.now().plusSeconds(30), 2, playerId))
        output(CommitContract.id, GameState(listOf(casinoId, playerId), UniqueIdentifier(), listOf(casino, player)))
        command(casino.owningKey, Commit(0))
        command(player.owningKey, Commit(1))
        verifies()
    }

    fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.reveal(
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

    @Test
    fun `Reveal command needs a Committed state input`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                input(DummyContract.PROGRAM_ID, DummyState())
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    command(casino.owningKey, Reveal(1, 0))
                    failsWith("The input must be a CommittedState")
                }

                command(casino.owningKey, Reveal(0, 0))
                verifies()
            }
        }
    }

    @Test
    fun `Reveal command needs a Revealed state output`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                output(DummyContract.PROGRAM_ID, DummyState())
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    command(casino.owningKey, Reveal(0, 1))
                    failsWith("The output must be a RevealedState")
                }

                command(casino.owningKey, Reveal(0, 0))
                verifies()
            }
        }
    }

    @Test
    fun `Reveal command needs matching ids`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef, playerRef) = issueTwoCommits(casinoImage.hash, playerImage.hash)
                    .outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                input(playerRef.ref)
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                command(player.owningKey, Reveal(1, 1))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef.getGamePointer(), UniqueIdentifier()))
                    failsWith("The linear ids must match")
                }

                output(CommitContract.id, RevealedState(playerImage, player,
                        playerRef.getGamePointer(), playerRef.state.data.linearId))
                verifies()
            }
        }
    }

    @Test
    fun `Reveal command needs correct image`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    input(CommitContract.id, CommittedState(SecureHash.allOnesHash, casino,
                            casinoRef.state.data.revealDeadline, casinoRef.state.data.gameOutputIndex,
                            casinoRef.state.data.linearId))
                    failsWith("The commit image must match")
                }

                input(casinoRef.ref)
                verifies()
            }
        }
    }

    @Test
    fun `Reveal command leaves creator unchanged`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    input(CommitContract.id, CommittedState(casinoImage.hash, player,
                            casinoRef.state.data.revealDeadline, casinoRef.state.data.gameOutputIndex,
                            casinoRef.state.data.linearId))
                    failsWith("The creator must be unchanged")
                }

                input(casinoRef.ref)
                verifies()
            }
        }
    }

    @Test
    fun `Reveal command leaves game pointer unchanged`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    input(CommitContract.id, CommittedState(casinoImage.hash, casino,
                            casinoRef.state.data.revealDeadline, 1,
                            casinoRef.state.data.linearId))
                    failsWith("The game pointer must be unchanged")
                }

                input(casinoRef.ref)
                verifies()
            }
        }
    }

    @Test
    fun `Reveal game must know the revealed state`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                failsWith("The game must be referenced")

                reference(casinoRef.getGamePointer().pointer)
                verifies()
            }
        }
    }

    @Test
    fun `Reveal command needs a correct TimeWindow`() {
        val casinoImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, SecureHash.allOnesHash)
                    .outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                reference(casinoRef.getGamePointer().pointer)

                tweak {
                    timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline.minusSeconds(1)))
                    verifies()
                }

                tweak {
                    timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline.plusSeconds(1)))
                    failsWith("The reveal deadline must be satisfied")
                }

                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))
                verifies()
            }
        }
    }

    @Test
    fun `Reveal cannot accept unidentified commit states`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef, playerRef) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                command(casino.owningKey, Reveal(0, 0))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))
                verifies()

                tweak {
                    val playerId = UniqueIdentifier()
                    output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                            Instant.now(), 2, playerId))
                    output(CommitContract.id, GameState(listOf(playerId), UniqueIdentifier(), listOf(player)))
                    failsWith("All outputs states which belong to one party must have an associated command")

                    command(player.owningKey, Commit(1))
                    verifies()
                }

                tweak {
                    input(playerRef.ref)
                    failsWith("All inputs states which belong to one party must have an associated command")

                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef.getGamePointer(), playerRef.state.data.linearId))
                    command(player.owningKey, Reveal(1, 1))
                    verifies()
                }

                tweak {
                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef.getGamePointer(), playerRef.state.data.linearId))
                    failsWith("All outputs states which belong to one party must have an associated command")

                    input(playerRef.ref)
                    command(player.owningKey, Reveal(1, 1))
                    verifies()
                }

            }
        }
    }

    @Test
    fun `Reveal command can be signed by anyone`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef) = issueTwoCommits(casinoImage.hash, playerImage.hash)
                    .outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef.ref)
                output(CommitContract.id, RevealedState(casinoImage, casino,
                        casinoRef.getGamePointer(), casinoRef.state.data.linearId))
                reference(casinoRef.getGamePointer().pointer)
                timeWindow(TimeWindow.untilOnly(casinoRef.state.data.revealDeadline))

                tweak {
                    command(player.owningKey, Reveal(0, 0))
                    verifies()
                }

                command(casino.owningKey, Reveal(0, 0))
                verifies()
            }
        }
    }

    @Test
    fun `Use command needs a Revealed state input`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef, playerRef) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                input(casinoRef.getGamePointer().pointer)
                command(player.owningKey, Use(1))

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
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef, playerRef) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(casino.owningKey, Use(0))
                command(player.owningKey, Use(1))

                failsWith("The game must be in input")

                input(casinoRef.getGamePointer().pointer)
                verifies()
            }
        }
    }

    @Test
    fun `Use must use all commits of game`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef, playerRef) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRef.getGamePointer().pointer)
                input(casinoRevealRef.ref)
                command(casino.owningKey, Use(1))

                failsWith("All the game commit ids must be present")

                input(playerRevealRef.ref)
                command(player.owningKey, Use(2))
                verifies()
            }
        }
    }

    @Test
    fun `Use cannot accept unidentified commit states`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef1, playerRef1) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            val (casinoRevealRef1) = reveal(casinoRef1, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef1) = reveal(playerRef1, playerImage).outRefsOfType<RevealedState>()
            val (casinoRef2, playerRef2) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            transaction {
                input(casinoRef1.getGamePointer().pointer)
                input(casinoRevealRef1.ref)
                input(playerRevealRef1.ref)
                command(casino.owningKey, Use(1))
                command(player.owningKey, Use(2))
                verifies()


                tweak {
                    val playerId = UniqueIdentifier()
                    output(CommitContract.id, CommittedState(SecureHash.randomSHA256(), player,
                            Instant.now(), 1, playerId))
                    output(CommitContract.id, GameState(listOf(playerId), UniqueIdentifier(), listOf(player)))
                    failsWith("All outputs states which belong to one party must have an associated command")

                    command(player.owningKey, Commit(0))
                    verifies()
                }

                tweak {
                    input(playerRef2.ref)
                    failsWith("All inputs states which belong to one party must have an associated command")

                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef2.getGamePointer(), playerRef2.state.data.linearId))
                    command(player.owningKey, Reveal(3, 0))
                    reference(playerRef2.getGamePointer().pointer)
                    timeWindow(TimeWindow.untilOnly(Instant.now()))
                    verifies()
                }

                tweak {
                    output(CommitContract.id, RevealedState(playerImage, player,
                            playerRef2.getGamePointer(), playerRef2.state.data.linearId))
                    failsWith("All outputs states which belong to one party must have an associated command")

                    input(playerRef2.ref)
                    command(player.owningKey, Reveal(3, 0))
                    // Put the reference back when the tweak bug is fixed.
//                    reference(playerRef2.getGamePointer().pointer)
                    timeWindow(TimeWindow.untilOnly(Instant.now()))
                    verifies()
                }

            }
        }
    }

    @Test
    fun `Use command needs the creator to be a signer`() {
        val casinoImage = CommitImage.createRandom(random)
        val playerImage = CommitImage.createRandom(random)
        ledgerServices.ledger {
            val (casinoRef, playerRef) = issueTwoCommits(
                    casinoImage.hash, playerImage.hash).outRefsOfType<CommittedState>()
            val (casinoRevealRef) = reveal(casinoRef, casinoImage).outRefsOfType<RevealedState>()
            val (playerRevealRef) = reveal(playerRef, playerImage).outRefsOfType<RevealedState>()
            transaction {
                input(casinoRef.getGamePointer().pointer)
                input(casinoRevealRef.ref)
                input(playerRevealRef.ref)
                command(casino.owningKey, Use(1))

                tweak {
                    command(casino.owningKey, Use(2))
                    failsWith("The creator must sign")
                }

                command(player.owningKey, Use(2))
                verifies()
            }
        }
    }

}