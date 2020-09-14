package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Close
import com.cordacodeclub.contracts.GameContract
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import com.cordacodeclub.states.getGamePointer
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

object ForeClosureFlow {

    @StartableByRPC
    class SimpleInitiator(
            val gameId: String
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val gameStateAndRef = serviceHub.vaultService.queryBy<GameState>().states
                    .singleOrNull { it.state.data.linearId.toString() == gameId } ?: throw FlowException("No game with id $gameId found.")
            val notary = gameStateAndRef.state.notary
            val associatedRevealStates = serviceHub.vaultService.queryBy<RevealedState>().states
                    .filter { it.state.data.game.pointer == gameStateAndRef.ref }
            val associatedCommitStates = serviceHub.vaultService.queryBy<CommittedState>().states
                    .filter { it.getGamePointer().pointer == gameStateAndRef.ref }

            subFlow(Initiator(revealRefs = associatedRevealStates,
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
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notary = notary ?: gameRef.state.notary
            val builder = TransactionBuilder(notary)
                    .addInputState(gameRef)
                    .addCommand(GameContract.Commands.Close, ourIdentity.owningKey)

            addInputs(revealRefs, builder, ourIdentity.owningKey)
            addInputs(commitRefs, builder, ourIdentity.owningKey)
            builder.verify(serviceHub)

            val signed = serviceHub.signInitialTransaction(builder, ourIdentity.owningKey)
            subFlow(FinalityFlow(transaction = signed))
        }
    }

    private fun addInputs(inputRefs: List<StateAndRef<*>>, builder: TransactionBuilder, owningKey: PublicKey) {
        for (index in 0..inputRefs.size) {
            builder.addInputState(inputRefs[index])
                    .addCommand(Close(index), owningKey)
        }
    }
}