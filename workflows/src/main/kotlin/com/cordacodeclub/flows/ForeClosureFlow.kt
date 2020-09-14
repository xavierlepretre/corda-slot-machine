package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Close
import com.cordacodeclub.contracts.CommitContract.Commands.Use
import com.cordacodeclub.contracts.GameContract
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import com.cordacodeclub.states.getGamePointer
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

object ForeClosureFlow {

    @StartableByRPC
    class SimpleInitiator(
            val gameId: UniqueIdentifier
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val gameStateAndRef = serviceHub.vaultService.queryBy<GameState>().states
                    .singleOrNull { it.state.data.linearId == gameId }
                    ?: throw FlowException("No game with id $gameId found.")
            val notary = gameStateAndRef.state.notary
            val associatedRevealStates = serviceHub.vaultService.queryBy<RevealedState>().states
                    .filter { it.state.data.game.pointer == gameStateAndRef.ref }
            val associatedCommitStates = serviceHub.vaultService.queryBy<CommittedState>().states
                    .filter { it.getGamePointer().pointer == gameStateAndRef.ref }

            return subFlow(Initiator(revealRefs = associatedRevealStates,
                    commitRefs = associatedCommitStates,
                    gameRef = gameStateAndRef,
                    notary = notary))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val revealRefs: List<StateAndRef<RevealedState>>,
            val commitRefs: List<StateAndRef<CommittedState>>,
            val gameRef: StateAndRef<GameState>,
            val notary: Party?
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = notary ?: gameRef.state.notary
            val builder = TransactionBuilder(notary)
                    .addInputState(gameRef)
                    .addCommand(GameContract.Commands.Close, ourIdentity.owningKey)

            gameRef.state.data.participants

            val otherParty = gameRef.state.data.participants
                    .singleOrNull { it.owningKey != ourIdentity.owningKey }
                    ?: throw FlowException("No matching party found. Party starting the flow must be a participant of ${gameRef.state.data.linearId}.")
            val otherSession = initiateFlow(otherParty)

            addInputs(revealRefs, 1, builder, ourIdentity.owningKey)
            addInputs(commitRefs, 1 + revealRefs.size, builder, ourIdentity.owningKey)
            builder.verify(serviceHub)

            val signed = serviceHub.signInitialTransaction(builder, ourIdentity.owningKey)
            return subFlow(FinalityFlow(signed, otherSession))
        }
    }


    class Responder(private val playerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(playerSession))
        }
    }

    private fun addInputs(
            inputRefs: List<StateAndRef<*>>,
            startingIndex: Int,
            builder: TransactionBuilder,
            owningKey: PublicKey) {
        inputRefs.map { builder.addInputState(it) }
        when {
            inputRefs.any { it.state.data is RevealedState } -> {
                for (index in startingIndex..inputRefs.size) {
                    builder.addCommand(Use(index), owningKey)
                }
            }
            inputRefs.any { it.state.data is CommittedState } -> {
                for (index in startingIndex..inputRefs.size) {
                    builder.addCommand(Close(index), owningKey)
                }
            }
        }

    }
}