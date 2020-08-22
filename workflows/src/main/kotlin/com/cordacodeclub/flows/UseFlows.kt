package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Use
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
                    .addInputState(casinoRef)
                    .addInputState(gameRef)
                    .addCommand(Use(0), player.owningKey)
                    .addCommand(Use(1), player.owningKey)
            builder.verify(serviceHub)
            val partiallySignedTx = serviceHub.signInitialTransaction(
                    builder, player.owningKey)
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx, listOf(casinoSession), listOf(player.owningKey)))
            return subFlow(FinalityFlow(fullySignedTx, casinoSession))
        }
    }

    /**
     * In-lined flow initiated by the casino.
     * Its initiator is [Initiator].
     */
    class Responder(
            val playerSession: FlowSession,
            val casinoRef: StateAndRef<RevealedState>) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            val fullySignedTx = subFlow(object : SignTransactionFlow(playerSession) {

                override fun checkTransaction(stx: SignedTransaction) {

                    // Only 1 command with a local key, i.e. to sign by me.
                    val myCommands = stx.tx.commands.filter {
                        it.signers.any(serviceHub::isLocalKey)
                    }
                    if (myCommands.size != 1)
                        throw FlowException("I should have only 1 command to sign")
                    val myCommand = myCommands.single().value
                    if (myCommand !is CommitContract.Commands.Use)
                        throw FlowException("I should only sign a Use command")
                    val myRevealedState = stx.inputs[myCommand.inputIndex]
                    if (myRevealedState != casinoRef.ref)
                        throw FlowException("It is not my revealed ref")

                }

            })

            // TODO make the casino move on to revealing so that it can protect itself from a player that does not
            // launch a correct FinalityFlow.

            // All visible so we also get the other commit state.
            return subFlow(ReceiveFinalityFlow(playerSession, fullySignedTx.id, StatesToRecord.ALL_VISIBLE))

        }
    }
}