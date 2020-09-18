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
import net.corda.core.utilities.ProgressTracker

/**
 * Flows to close an incomplete game.
 */
object ForeClosureFlow {

    @StartableByRPC
    class SimpleInitiator(
            private val gameId: UniqueIdentifier,
            override val progressTracker: ProgressTracker
    ) : FlowLogic<SignedTransaction>() {

        constructor(gameId: UniqueIdentifier) : this(gameId, tracker())

        companion object {
            object ResolvingGame : ProgressTracker.Step("Resolving game.")
            object ResolvingCommits : ProgressTracker.Step("Resolving commits.")
            object ResolvingReveals : ProgressTracker.Step("Resolving reveals.")
            object PassingOnToInitiator : ProgressTracker.Step("Passing on to initiator.") {
                override fun childProgressTracker() = Initiator.tracker()
            }

            fun tracker() = ProgressTracker(
                    ResolvingGame,
                    ResolvingCommits,
                    ResolvingReveals,
                    PassingOnToInitiator)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = ResolvingGame
            val gameStateAndRef = serviceHub.vaultService
                    .queryBy(GameState::class.java, QueryCriteria.LinearStateQueryCriteria().withUuid(listOf(gameId.id)))
                    .states.singleOrNull { it.state.data.linearId == gameId }
                    ?: throw FlowException("No game with id $gameId found.")
            val notary = gameStateAndRef.state.notary

            progressTracker.currentStep = ResolvingCommits
            val associatedRevealStates = serviceHub.vaultService.queryBy<RevealedState>().states
                    .filter { it.state.data.game.pointer == gameStateAndRef.ref }

            progressTracker.currentStep = ResolvingReveals
            val associatedCommitStates = serviceHub.vaultService.queryBy<CommittedState>().states
                    .filter { it.getGamePointer().pointer == gameStateAndRef.ref }

            progressTracker.currentStep = PassingOnToInitiator
            return subFlow(Initiator(revealRefs = associatedRevealStates,
                    commitRefs = associatedCommitStates,
                    gameRef = gameStateAndRef,
                    notary = notary,
                    progressTracker = PassingOnToInitiator.childProgressTracker()))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val revealRefs: List<StateAndRef<RevealedState>>,
            val commitRefs: List<StateAndRef<CommittedState>>,
            val gameRef: StateAndRef<GameState>,
            val notary: Party?,
            override val progressTracker: ProgressTracker
    ) : FlowLogic<SignedTransaction>() {

        constructor(revealRefs: List<StateAndRef<RevealedState>>,
                    commitRefs: List<StateAndRef<CommittedState>>,
                    gameRef: StateAndRef<GameState>,
                    notary: Party?) : this(revealRefs, commitRefs, gameRef, notary, tracker())

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
            val notary = notary ?: gameRef.state.notary
            val builder = TransactionBuilder(notary)
                    .addInputState(gameRef)
                    .addCommand(GameContract.Commands.Close(0), ourIdentity.owningKey)

            if (commitRefs.isNotEmpty()) {
                commitRefs.forEach {
                    builder.addCommand(Close(builder.inputStates().size), ourIdentity.owningKey)
                    builder.addInputState(it)
                }
            }

            if (revealRefs.isNotEmpty()) {
                revealRefs.forEach {
                    builder.addCommand(Use(builder.inputStates().size), ourIdentity.owningKey)
                    builder.addInputState(it)
                }
            }

            progressTracker.currentStep = VerifyingTransaction
            builder.verify(serviceHub)

            val otherParties = gameRef.state.data.participants
                    .filter { it.owningKey != ourIdentity.owningKey }.distinct()
            val otherSessions = otherParties.map { initiateFlow(it) }

            progressTracker.currentStep = SigningTransaction
            val signed = serviceHub.signInitialTransaction(builder, ourIdentity.owningKey)

            progressTracker.currentStep = FinalisingTransaction
            return subFlow(FinalityFlow(signed, otherSessions))
        }
    }

    /**
     * In-lined flow to potentially receive some commits, reveals and a game.
     * Its initiator is [Initiator].
     */
    @InitiatedBy(Initiator::class)
    class Responder(
            private val playerSession: FlowSession,
            override val progressTracker: ProgressTracker
    ) : FlowLogic<SignedTransaction>() {

        @Suppress("unused")
        constructor(playerSession: FlowSession) : this(playerSession, tracker())

        companion object {
            object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.")

            fun tracker() = ProgressTracker(FinalisingTransaction)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = FinalisingTransaction
            return subFlow(ReceiveFinalityFlow(playerSession))
        }
    }
}