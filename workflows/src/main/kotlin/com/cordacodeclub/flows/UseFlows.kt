package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Use
import com.cordacodeclub.contracts.GameContract.Commands.Resolve
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Flows to determine the outcome of the game.
 */
object UseFlows {

    /**
     * In-lined flow started by the player.
     * Its handler is [Responder].
     */
    class Initiator(
            val playerRef: StateAndRef<RevealedState>,
            val casinoRef: StateAndRef<RevealedState>,
            val gameRef: StateAndRef<GameState>,
            val casinoSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val player = playerRef.state.data.creator
            val casino = casinoRef.state.data.creator
            if (casinoSession.counterparty != serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host"))
                throw FlowException("The casinoSession is not for the casino")
            val builder = TransactionBuilder(playerRef.state.notary)
                    .addInputState(playerRef)
                    .addCommand(Use(0), player.owningKey)
                    .addInputState(casinoRef)
                    .addCommand(Use(1), player.owningKey)
                    .addInputState(gameRef)
                    .addCommand(Resolve(2), player.owningKey)
            builder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(builder, player.owningKey)
            return subFlow(FinalityFlow(signedTx, casinoSession))
        }
    }

    /**
     * In-lined flow initiated by the casino. Casino signatures are not required on resolution.
     * Its initiator is [Initiator].
     */
    class Responder(val playerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() = subFlow(ReceiveFinalityFlow(playerSession))
    }
}