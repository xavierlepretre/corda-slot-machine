package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract
import com.cordacodeclub.contracts.CommitContract.Commands.Reveal
import com.cordacodeclub.states.*
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.Instant

object RevealFlows {

    /**
     * In-lined flow initiated by any party.
     * To make it a private reveal, just keep the otherSessions empty.
     * * Its handler is [Responder].
     */
    class Initiator(
            val committedRef: StateAndRef<CommittedState>,
            val image: CommitImage,
            val revealDeadline: Instant,
            val gameRef: StateAndRef<GameState>,
            val otherParticipants: List<AbstractParty>,
            val otherSessions: Collection<FlowSession>) : FlowLogic<SignedTransaction>() {

        init {
            require(image.hash == committedRef.state.data.hash) {
                "The image does not correspond to the committed ref"
            }
            require(committedRef.getGamePointer().pointer == gameRef.ref) {
                "The game reference does not correspond to the committed ref"
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val committed = committedRef.state.data
            val builder = TransactionBuilder(committedRef.state.notary)
                    .addInputState(committedRef)
                    .addOutputState(
                            RevealedState(image, committed.creator, committedRef.getGamePointer(),
                                    committed.linearId, otherParticipants),
                            CommitContract.id)
                    .addCommand(Reveal(0, 0))
                    .addReferenceState(ReferencedStateAndRef(gameRef))
                    .setTimeWindow(TimeWindow.untilOnly(revealDeadline))

            builder.verify(serviceHub)

            val signed = serviceHub.signInitialTransaction(builder, committed.creator.owningKey)

            return subFlow(FinalityFlow(signed, otherSessions))
        }
    }

    /**
     * In-lined flow to potentially receive a revealed commit.
     * Its initiator is [Initiator].
     */
    class Responder(val revealerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(revealerSession, null, StatesToRecord.ALL_VISIBLE))
        }

    }

}