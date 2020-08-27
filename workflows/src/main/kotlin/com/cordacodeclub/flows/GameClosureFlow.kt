package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object GameClosureFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val gameRef: StateAndRef<GameState>,
            val myParty: CordaX500Name
    ): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notary = gameRef.state.notary
            val associatedCommitStates = serviceHub.vaultService.queryBy<CommittedState>().states
            val associatedRevealStates = serviceHub.vaultService.queryBy<RevealedState>().states
            val builder = TransactionBuilder(notary)
                    .addOutputState(gameRef.state)
                    .addCommand(CommitContract.Commands.Resolve())

            associatedCommitStates.map {
                builder.addOutputState(it.state)
                        .addCommand(CommitContract.Commands.Resolve())
            }

            associatedRevealStates.map {
                builder.addOutputState(it.state)
                        .addCommand(CommitContract.Commands.Resolve())
            }

            builder.verify(serviceHub)
            val myParty = gameRef.state.data.participants.singleOrNull() { it.nameOrNull() == myParty }
                    ?: throw FlowException("Identity $myParty not found.")
            val signed = serviceHub.signInitialTransaction(builder, myParty.owningKey)
            subFlow(FinalityFlow(transaction = signed, progressTracker = ProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val playerSession: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            // TODO
            //There is no responder in this flow?
        }
    }
}