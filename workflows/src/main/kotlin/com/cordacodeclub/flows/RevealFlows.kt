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
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

/**
 * Flows to execute the reveal of an image corresponding to the committed hash.
 */
object RevealFlows {

    /**
     * In-lined flow initiated by any party.
     * To make it a private reveal, just keep the otherSessions empty and revealParticipants with only yourself.
     * * Its handler is [Responder].
     */
    class Initiator(
            private val committedRef: StateAndRef<CommittedState>,
            private val image: CommitImage,
            private val revealDeadline: Instant,
            private val gameRef: StateAndRef<GameState>,
            private val revealParticipants: List<AbstractParty>,
            private val otherSessions: Collection<FlowSession>,
            override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

        init {
            require(image.hash == committedRef.state.data.hash) {
                "The image does not correspond to the committed ref"
            }
            require(committedRef.getGamePointer().pointer == gameRef.ref) {
                "The game reference does not correspond to the committed ref"
            }
        }

        companion object {
            object GeneratingTransaction : ProgressTracker.Step("Generating transaction.")
            object VerifyingTransaction : ProgressTracker.Step("Verifying contract constraints.")
            object SigningTransaction : ProgressTracker.Step("Signing transaction with our key.")
            object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GeneratingTransaction,
                    VerifyingTransaction,
                    SigningTransaction,
                    FinalisingTransaction)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GeneratingTransaction
            val committed = committedRef.state.data
            val builder = TransactionBuilder(committedRef.state.notary)
                    .addInputState(committedRef)
                    .addOutputState(
                            RevealedState(image, committed.creator, committedRef.getGamePointer(),
                                    committed.linearId, revealParticipants),
                            CommitContract.id)
                    .addCommand(Reveal(0, 0), committed.creator.owningKey)
                    .addReferenceState(ReferencedStateAndRef(gameRef))
                    .setTimeWindow(TimeWindow.untilOnly(revealDeadline))

            progressTracker.currentStep = VerifyingTransaction
            builder.verify(serviceHub)

            progressTracker.currentStep = SigningTransaction
            val signed = serviceHub.signInitialTransaction(builder, committed.creator.owningKey)

            progressTracker.currentStep = FinalisingTransaction
            return subFlow(FinalityFlow(signed, otherSessions))
        }
    }

    /**
     * In-lined flow to potentially receive a revealed commit.
     * Its initiator is [Initiator].
     */
    class Responder(private val revealerSession: FlowSession,
                    override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.")

            fun tracker() = ProgressTracker(FinalisingTransaction)
        }

        @Suspendable
        // All visible so we also record the counterparty's revealed state.
        override fun call(): SignedTransaction {
            progressTracker.currentStep = FinalisingTransaction
            return subFlow(ReceiveFinalityFlow(revealerSession, null, StatesToRecord.ALL_VISIBLE))
        }
    }
}