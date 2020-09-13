package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Close
import com.cordacodeclub.contracts.GameContract
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

object ForeClosureFlow {

    @StartableByRPC
    class SimpleInitiator(
            val gameRef: StateAndRef<GameState>,
            val myPartyName: CordaX500Name
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notary = gameRef.state.notary
            val associatedRevealStates = serviceHub.vaultService.queryBy<RevealedState>().states
                    .filter { it.state.data.game.pointer == gameRef.ref }
            val associatedCommitStates = serviceHub.vaultService.queryBy<CommittedState>().states
                    .filter { it.state.data.gameOutputIndex == gameRef.ref.index }

            subFlow(Initiator(revealRefs = associatedRevealStates,
                    commitRefs = associatedCommitStates,
                    gameRef = gameRef,
                    myPartyName = myPartyName,
                    notary = notary))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val revealRefs: List<StateAndRef<RevealedState>>,
            val commitRefs: List<StateAndRef<CommittedState>>,
            val gameRef: StateAndRef<GameState>,
            val myPartyName: CordaX500Name,
            val notary: Party?
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notary = notary ?: gameRef.state.notary
            val myParty = serviceHub.networkMapCache.getNodeByLegalName(myPartyName)?.identityFromX500Name(myPartyName)
                    ?: throw FlowException("Identity $myPartyName not found.")
            val builder = TransactionBuilder(notary)
                    .addInputState(gameRef)
                    .addCommand(GameContract.Commands.Close, myParty.owningKey)

            addInputs(revealRefs, builder, myParty.owningKey)
            addInputs(commitRefs, builder, myParty.owningKey)
            builder.verify(serviceHub)

            val signed = serviceHub.signInitialTransaction(builder, myParty.owningKey)
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