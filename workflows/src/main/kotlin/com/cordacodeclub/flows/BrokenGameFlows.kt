package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.CommitImage
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
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
}