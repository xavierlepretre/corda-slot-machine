package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.CommitImage
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.RevealedState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.unwrap
import java.time.Duration
import java.time.Instant
import java.util.*

object GameFlows {

    val commitDuration =  Duration.ofMinutes(2)
    val revealDuration = Duration.ofMinutes(2)

    /**
     * Initiated by the player.
     * Its handler is [Responder].
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val player: AbstractParty,
                    val casino: AbstractParty) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            val playerImage = CommitImage.createRandom(Random())
            val commitDeadline = Instant.now().plus(commitDuration)
            val revealDeadline = commitDeadline.plus(revealDuration)
            val casinoHost = serviceHub.identityService.requireWellKnownPartyFromAnonymous(casino)
            val casinoSession = initiateFlow(casinoHost)

            // Inform casino of new game
            casinoSession.send(player)
            casinoSession.send(casino)
            casinoSession.send(commitDeadline)
            casinoSession.send(revealDeadline)

            // Player asks casino for commit and prepares double commits
            val commitTx = subFlow(CommitFlows.Initiator(
                    playerImage.hash, player, commitDeadline, revealDeadline, casinoSession, casino))
            val commitStates = commitTx.tx.outRefsOfType<CommittedState>()
            val playerCommit = commitStates.single { it.state.data.creator == player }

            // Player reveals secretly.
            val playerRevealTx = subFlow(RevealFlows.Initiator(
                    playerCommit, playerImage, revealDeadline, listOf(player), listOf()))
            val playerRevealed = playerRevealTx.tx.outRefsOfType<RevealedState>().single()
            val casinoRevealTx = subFlow(RevealFlows.Responder(casinoSession))
            val casinoRevealed = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            TODO("Player creates outcome transaction")

        }
        
    }

    @InitiatedBy(Initiator::class)
    class Responder(val playerSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            val casinoImage = CommitImage.createRandom(Random())

            // Receive new game information
            val player = playerSession.receive<AbstractParty>().unwrap { it }
            val casino = playerSession.receive<AbstractParty>().unwrap { it }
            val commitDeadline = playerSession.receive<Instant>().unwrap { it }
            if (Instant.now().plus(revealDuration) < commitDeadline.plus(Duration.ofMinutes(1)))
                throw FlowException("Commit deadline is too far in the future")
            val revealDeadline = playerSession.receive<Instant>().unwrap { it }
            if (commitDeadline.plus(revealDuration) != revealDeadline)
                throw FlowException("Reveal deadline is incorrect")

            // Casino gives commit info and gets both commits tx.
            val commitTx = subFlow(CommitFlows.Responder(
                    playerSession, commitDeadline, revealDeadline, casinoImage.hash, casino))
            val commitStates = commitTx.tx.outRefsOfType<CommittedState>()
            val casinoCommit = commitStates.single { it.state.data.creator == casino }

            // Casino reveals and discloses.
            val casinoRevealTx = subFlow(RevealFlows.Initiator(
                    casinoCommit, casinoImage, revealDeadline, listOf(casino, player), listOf(playerSession)))
            val casinoRevealed = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            TODO("Casino receives outcome transaction from player")

        }

    }

}