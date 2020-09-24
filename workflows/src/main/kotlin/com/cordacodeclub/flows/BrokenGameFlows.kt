package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.*
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.time.Instant
import java.util.*

/**
 * Flows to simulate malicious game flows.
 */
@VisibleForTesting
object BrokenGameFlows {

    object PlayerDoesNotReveal {

        @CordaSerializable
        data class GameTransactions(
                val commitTx: SignedTransaction,
                val casinoRevealTx: SignedTransaction)

        /**
         * Initiated by the player. It implements the same interface as [GameFlows.Initiator] up to a point.
         * Its handler can be [GameFlows.Responder].
         * At the end, there are:
         * - 1 committed state from the player.
         * - 1 revealed state from the casino.
         * And both the casino and the player have them.
         */
        @InitiatingFlow
        class Initiator(private val player: AbstractParty,
                        private val playerWager: Long,
                        private val issuer: AbstractParty,
                        private val casino: AbstractParty,
                        override val progressTracker: ProgressTracker) : FlowLogic<GameTransactions>() {

            constructor(player: AbstractParty, playerWager: Long, issuer: AbstractParty, casino: AbstractParty)
                    : this(player, playerWager, issuer, casino, GameFlows.Initiator.tracker())

            @Suspendable
            override fun call(): GameTransactions {
                progressTracker.currentStep = GameFlows.Initiator.Companion.CreatingPlayerImage
                val playerImage = CommitImage.createRandom(Random())
                val commitDeadline = Instant.now().plus(GameParameters.commitDuration)!!
                val revealDeadline = commitDeadline.plus(GameParameters.revealDuration)!!

                progressTracker.currentStep = GameFlows.Initiator.Companion.ResolvingCasino
                val casinoHost = serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                        ?: throw FlowException("Cannot resolve the casino host")
                if (casinoHost == ourIdentity) throw FlowException("You must play with a remote host, not yourself")
                val casinoSession = initiateFlow(casinoHost)

                // Inform casino of player
                progressTracker.currentStep = GameFlows.Initiator.Companion.SendingPlayer
                subFlow(SyncKeyMappingFlow(casinoSession, listOf(player)))

                // Inform casino of new game
                progressTracker.currentStep = GameFlows.Initiator.Companion.SendingGameSetup
                val notary = serviceHub.networkMapCache.notaryIdentities[0]
                val setup = GameFlows.GameSetup(notary = notary,
                        player = player,
                        playerWager = playerWager,
                        issuer = issuer,
                        casino = casino,
                        commitDeadline = commitDeadline,
                        revealDeadline = revealDeadline)
                casinoSession.send(setup)

                // First notary, not caring much...
                progressTracker.currentStep = GameFlows.Initiator.Companion.FetchingPlayerTokens
                // Player collects enough tokens for the wager.
                val playerTokens = subFlow(LockableTokenFlows.Fetch.Local(player, issuer,
                        playerWager, currentTopLevel?.runId?.uuid ?: throw FlowException("No running id"),
                        GameFlows.Initiator.Companion.FetchingPlayerTokens.childProgressTracker()))

                progressTracker.currentStep = GameFlows.Initiator.Companion.PassingOnToCommit
                // Player asks casino for commit and prepares double commits.
                val commitTx = subFlow(CommitFlows.Initiator(notary, playerImage.hash, setup, playerTokens,
                        casinoSession))

                // Player does not reveal.

                // Player responds to casino's reveal.
                progressTracker.currentStep = GameFlows.Initiator.Companion.PassingOnToCasinoRevealResponder
                val casinoRevealTx = subFlow(RevealFlows.Responder(casinoSession,
                        GameFlows.Initiator.Companion.PassingOnToCasinoRevealResponder.childProgressTracker()))

                return GameTransactions(commitTx, casinoRevealTx)
            }
        }
    }

    object CasinoDoesNotReveal {

        @CordaSerializable
        data class GameTransactions(
                val commitTx: SignedTransaction,
                val playerRevealTx: SignedTransaction)

        /**
         * Initiated by the player. It implements the same interface as [GameFlows.Initiator] up to a point.
         * Its handler can be [Responder].
         * At the end, there are:
         * - 1 committed state from the casino.
         * - 1 revealed state from the player.
         * And only the player have them.
         */
        @InitiatingFlow
        class Initiator(private val player: AbstractParty,
                        private val playerWager: Long,
                        private val issuer: AbstractParty,
                        private val casino: AbstractParty,
                        override val progressTracker: ProgressTracker) : FlowLogic<GameTransactions>() {

            constructor(player: AbstractParty, playerWager: Long, issuer: AbstractParty, casino: AbstractParty)
                    : this(player, playerWager, issuer, casino, GameFlows.Initiator.tracker())

            @Suspendable
            override fun call(): GameTransactions {
                progressTracker.currentStep = GameFlows.Initiator.Companion.CreatingPlayerImage
                val playerImage = CommitImage.createRandom(Random())
                val commitDeadline = Instant.now().plus(GameParameters.commitDuration)!!
                val revealDeadline = commitDeadline.plus(GameParameters.revealDuration)!!

                progressTracker.currentStep = GameFlows.Initiator.Companion.ResolvingCasino
                val casinoHost = serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                        ?: throw FlowException("Cannot resolve the casino host")
                if (casinoHost == ourIdentity) throw FlowException("You must play with a remote host, not yourself")
                val casinoSession = initiateFlow(casinoHost)

                // Inform casino of player
                progressTracker.currentStep = GameFlows.Initiator.Companion.SendingPlayer
                subFlow(SyncKeyMappingFlow(casinoSession, listOf(player)))

                // Inform casino of new game
                progressTracker.currentStep = GameFlows.Initiator.Companion.SendingGameSetup
                val notary = serviceHub.networkMapCache.notaryIdentities[0]
                val setup = GameFlows.GameSetup(notary = notary,
                        player = player,
                        playerWager = playerWager,
                        issuer = issuer,
                        casino = casino,
                        commitDeadline = commitDeadline,
                        revealDeadline = revealDeadline)
                casinoSession.send(setup)

                // First notary, not caring much...
                progressTracker.currentStep = GameFlows.Initiator.Companion.FetchingPlayerTokens
                // Player collects enough tokens for the wager.
                val playerTokens = subFlow(LockableTokenFlows.Fetch.Local(player, issuer,
                        playerWager, currentTopLevel?.runId?.uuid ?: throw FlowException("No running id"),
                        GameFlows.Initiator.Companion.FetchingPlayerTokens.childProgressTracker()))

                progressTracker.currentStep = GameFlows.Initiator.Companion.PassingOnToCommit
                // Player asks casino for commit and prepares double commits.
                val commitTx = subFlow(CommitFlows.Initiator(notary, playerImage.hash, setup, playerTokens,
                        casinoSession))

                progressTracker.currentStep = GameFlows.Initiator.Companion.ExtractingCommitResult
                val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
                val playerCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                        .single { it.state.data.creator == player }

                // Player reveals secretly.
                progressTracker.currentStep = GameFlows.Initiator.Companion.PassingOnToPlayerReveal
                val playerRevealTx = subFlow(RevealFlows.Initiator(
                        playerCommitRef, playerImage, revealDeadline, gameRef, listOf(player), listOf(),
                        GameFlows.Initiator.Companion.PassingOnToPlayerReveal.childProgressTracker()))

                return GameTransactions(commitTx, playerRevealTx)
            }
        }

        /**
         * Its initiator can be [Initiator].
         */
        class Responder(private val playerSession: FlowSession,
                        override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {

            @Suppress("unused")
            constructor(playerSession: FlowSession) : this(playerSession, GameFlows.Responder.tracker())

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = GameFlows.Responder.Companion.CreatingCasinoImage
                val casinoImage = CommitImage.createRandom(Random())

                // Receive player information
                progressTracker.currentStep = GameFlows.Responder.Companion.ReceivingPlayer
                subFlow(SyncKeyMappingFlowHandler(playerSession))

                // Receive new game information
                progressTracker.currentStep = GameFlows.Responder.Companion.ReceivingGameSetup
                val setup = playerSession.receive<GameFlows.GameSetup>().unwrap { it }
                if (GameFlows.Responder.maxPlayerWager < setup.playerWager)
                    throw FlowException("Player wager cannot be more than ${GameFlows.Responder.maxPlayerWager}")
                if (Instant.now().plus(GameParameters.commitDuration) < setup.commitDeadline)
                    throw FlowException("Commit deadline is too far in the future")
                if (setup.commitDeadline.plus(GameParameters.revealDuration) != setup.revealDeadline)
                    throw FlowException("Reveal deadline is incorrect")

                // Casino collects or issues enough tokens for the wager.
                progressTracker.currentStep = GameFlows.Responder.Companion.FetchingCasinoTokens
                val casinoTokens = try {
                    subFlow(LockableTokenFlows.Fetch.Local(
                            setup.casino, setup.issuer, setup.casinoWager,
                            currentTopLevel?.runId?.uuid ?: throw FlowException("No running id"),
                            GameFlows.Responder.Companion.FetchingCasinoTokens.childProgressTracker()))
                } catch (notEnough: LockableTokenFlows.Fetch.NotEnoughTokensException) {
                    if (!GameFlows.Responder.autoIssueWhenPossible || setup.issuer != setup.casino) throw notEnough
                    progressTracker.currentStep = GameFlows.Responder.Companion.SelfIssuingCasinoTokens
                    subFlow(LockableTokenFlows.Issue.Initiator(setup.notary, setup.casino, setup.casinoWager,
                            setup.issuer, GameFlows.Responder.Companion.SelfIssuingCasinoTokens.childProgressTracker()))
                            .tx
                            .outRefsOfType<LockableTokenState>()
                }

                // Casino gives commit info and gets both commits tx.
                progressTracker.currentStep = GameFlows.Responder.Companion.PassingOnToCommitResponder
                val commitTx = subFlow(CommitFlows.Responder(
                        playerSession, setup, casinoImage.hash, casinoTokens,
                        GameFlows.Responder.Companion.PassingOnToCommitResponder.childProgressTracker()))

                // Casino does not reveal
                return commitTx
            }
        }
    }
}