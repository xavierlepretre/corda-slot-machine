package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.CommitImage
import com.cordacodeclub.states.CommittedState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.unwrap
import java.util.*

object GameFlows {

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
            val casinoHost = serviceHub.identityService.requireWellKnownPartyFromAnonymous(casino)
            val casinoSession = initiateFlow(casinoHost)

            // Inform casino of new game
            casinoSession.send(player)
            casinoSession.send(casino)

            // Player asks casino for commit and prepares double commits
            val commitTx = subFlow(CommitFlows.Initiator(
                    playerImage.hash, player, casinoSession, casino))
            val commitStates = commitTx.tx.outRefsOfType<CommittedState>()
            val playerCommit = commitStates.filter { it.state.data.creator == player }
                    .single()

            // Player reveals secretly.
            val playerRevealTx = subFlow(RevealFlows.Initiator(
                    playerCommit, playerImage, listOf(player), listOf()))
            val casinoRevealTx = subFlow(RevealFlows.Responder(casinoSession))

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

            // Casino gives commit info and gets both commits tx.
            val commitTx = subFlow(CommitFlows.Responder(playerSession, casinoImage.hash, casino))
            val commitStates = commitTx.tx.outRefsOfType<CommittedState>()
            val casinoCommit = commitStates.filter { it.state.data.creator == casino }
                    .single()

            // Casino reveals and discloses.
            val casinoRevealTx = subFlow(RevealFlows.Initiator(
                    casinoCommit, casinoImage, listOf(casino, player), listOf(playerSession)))

            TODO("Casino receives outcome transaction from player")

        }

    }

}