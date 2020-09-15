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
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.node.services.vault.QueryCriteria

object ForeClosureFlow {

    @StartableByRPC
    class SimpleInitiator(
            val gameId: UniqueIdentifier
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val gameStateAndRef = serviceHub.vaultService
                    .queryBy(GameState::class.java, QueryCriteria.LinearStateQueryCriteria().withUuid(listOf(gameId.id)))
                    .states.singleOrNull { it.state.data.linearId == gameId }
                    ?: throw FlowException("No game with id $gameId found.")
            val notary = gameStateAndRef.state.notary
            // TODO improve the query to be more specific to deal with potential pagination issues
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
                    .addCommand(GameContract.Commands.Close(0), ourIdentity.owningKey)

            val otherParties = gameRef.state.data.participants
                    .filter { it.owningKey != ourIdentity.owningKey }

            val otherSessions = otherParties.map { initiateFlow(it) }

            // We always have 2 commits by this stage
            if (commitRefs.isNotEmpty()) {
                builder.addInputState(commitRefs.first())
                builder.addCommand(Close(1), ourIdentity.owningKey)
                builder.addInputState(commitRefs.last())
                builder.addCommand(Close(2), ourIdentity.owningKey)
            }

            // We might have 1 or 2 commits by this stage
            if (revealRefs.isNotEmpty()) {
                when (revealRefs.size) {
                    1 -> {
                        builder.addInputState(revealRefs.first())
                        builder.addCommand(Use(3), ourIdentity.owningKey)
                    }
                    2 -> {
                        builder.addInputState(revealRefs.first())
                        builder.addCommand(Use(3), ourIdentity.owningKey)
                        builder.addInputState(revealRefs.last())
                        builder.addCommand(Use(4), ourIdentity.owningKey)
                    }
                    else -> throw FlowException("There can only two be reveals for each game, but ${revealRefs.size} found.")
                }
            }
            builder.verify(serviceHub)

            val signed = serviceHub.signInitialTransaction(builder, ourIdentity.owningKey)
            return subFlow(FinalityFlow(signed, otherSessions))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val playerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(playerSession))
        }
    }
}