package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract
import com.cordacodeclub.contracts.GameContract
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder

object ForeClosureFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val gameRef: StateAndRef<GameState>,
            val myParty: CordaX500Name
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notary = gameRef.state.notary
            val myParty = serviceHub.networkMapCache.getNodeByLegalName(myParty)?.identityFromX500Name(myParty)
                    ?: throw FlowException("Identity $myParty not found.")
            val associatedRevealStates = serviceHub.vaultService.queryBy<RevealedState>().states
                    .filter { it.state.data.game.pointer == gameRef.ref }
            val builder = TransactionBuilder(notary)
                    .addInputState(gameRef)
                    .addCommand(GameContract.Commands.Close, myParty.owningKey)

            associatedRevealStates.map {
                builder.addInputState(it)
                        .addCommand(CommitContract.Commands.Close, myParty.owningKey)
            }

            builder.verify(serviceHub)

            val signed = serviceHub.signInitialTransaction(builder, myParty.owningKey)
            subFlow(FinalityFlow(transaction = signed))
        }
    }
}