package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Close
import com.cordacodeclub.contracts.CommitContract.Commands.Use
import com.cordacodeclub.contracts.GameContract
import com.cordacodeclub.contracts.LockableTokenContract
import com.cordacodeclub.contracts.LockableTokenContract.Commands.Release
import com.cordacodeclub.states.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Flows to close an incomplete game.
 */
@Suppress("unused")
object ForeClosureFlow {

    @StartableByRPC
    @SchedulableFlow
    class SimpleInitiator(
            private val gameId: UniqueIdentifier,
            override val progressTracker: ProgressTracker
    ) : FlowLogic<SignedTransaction>() {

        @Suppress("unused")
        constructor(gameId: UniqueIdentifier) : this(gameId, tracker())

        companion object {
            object ResolvingGame : ProgressTracker.Step("Resolving game.")
            object ResolvingCommits : ProgressTracker.Step("Resolving commits.")
            object ResolvingReveals : ProgressTracker.Step("Resolving reveals.")
            object ResolvingLockedTokens : ProgressTracker.Step("Resolving locked tokens.")
            object PassingOnToInitiator : ProgressTracker.Step("Passing on to initiator.") {
                override fun childProgressTracker() = Initiator.tracker()
            }

            fun tracker() = ProgressTracker(
                    ResolvingGame,
                    ResolvingCommits,
                    ResolvingReveals,
                    ResolvingLockedTokens,
                    PassingOnToInitiator)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = ResolvingGame
            val gameStateAndRef = serviceHub.vaultService
                    .queryBy<GameState>(QueryCriteria.LinearStateQueryCriteria().withUuid(listOf(gameId.id)))
                    .states.singleOrNull { it.state.data.linearId == gameId }
                    ?: throw FlowException("0 or 2 games with $gameId found.")

            progressTracker.currentStep = ResolvingCommits
            val eitherCommitStates = serviceHub.vaultService.queryBy<CommitState>(
                    QueryCriteria.LinearStateQueryCriteria()
                            .withUuid(gameStateAndRef.state.data.commitIds.map { it.id }))
                    .states

            @Suppress("UNCHECKED_CAST")
            val associatedCommitStates = eitherCommitStates
                    .filter { it.state.data is CommittedState }
                    .mapNotNull { it as? StateAndRef<CommittedState> }
                    .filter { it.getGamePointer().pointer == gameStateAndRef.ref }

            progressTracker.currentStep = ResolvingReveals
            @Suppress("UNCHECKED_CAST")
            val associatedRevealStates = eitherCommitStates
                    .filter { it.state.data is RevealedState }
                    .mapNotNull { it as? StateAndRef<RevealedState> }
                    .filter { it.state.data.game.pointer == gameStateAndRef.ref }

            progressTracker.currentStep = ResolvingLockedTokens
            val lockedTokensRef = serviceHub
                    .toStateAndRef<LockableTokenState>(gameStateAndRef.getLockedWagersRef())

            progressTracker.currentStep = PassingOnToInitiator
            return subFlow(Initiator(revealRefs = associatedRevealStates,
                    commitRefs = associatedCommitStates,
                    gameRef = gameStateAndRef,
                    lockedTokensRef = lockedTokensRef,
                    progressTracker = PassingOnToInitiator.childProgressTracker()))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val revealRefs: List<StateAndRef<RevealedState>>,
            val commitRefs: List<StateAndRef<CommittedState>>,
            val gameRef: StateAndRef<GameState>,
            val lockedTokensRef: StateAndRef<LockableTokenState>,
            override val progressTracker: ProgressTracker
    ) : FlowLogic<SignedTransaction>() {

        constructor(revealRefs: List<StateAndRef<RevealedState>>,
                    commitRefs: List<StateAndRef<CommittedState>>,
                    gameRef: StateAndRef<GameState>,
                    lockedTokensRef: StateAndRef<LockableTokenState>)
                : this(revealRefs, commitRefs, gameRef, lockedTokensRef, tracker())

        init {
            require(commitRefs.isNotEmpty()) { "There must be unrevealed commit states" }
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
            val game = gameRef.state.data
            val builder = TransactionBuilder(gameRef.state.notary)
                    .setTimeWindow(TimeWindow.fromOnly(game.revealDeadline.plusSeconds(1)))
                    .addInputState(gameRef)
                    .addCommand(GameContract.Commands.Close(0), ourIdentity.owningKey)

            commitRefs.forEach {
                builder.addCommand(Close(builder.inputStates().size), ourIdentity.owningKey)
                builder.addInputState(it)
            }

            revealRefs.forEach {
                builder.addCommand(Use(builder.inputStates().size), ourIdentity.owningKey)
                builder.addInputState(it)
            }

            // Reversing plain and simple. TODO Add penalty.
            builder.addCommand(Release(listOf(builder.inputStates().size), listOf(0, 1)),
                    ourIdentity.owningKey)
                    .addInputState(lockedTokensRef)
                    .addOutputState(
                            LockableTokenState(game.player.committer.holder, game.tokenIssuer, game.player.issuedAmount.amount),
                            LockableTokenContract.id)
                    .addOutputState(
                            LockableTokenState(game.casino.committer.holder, game.tokenIssuer, game.casino.issuedAmount.amount),
                            LockableTokenContract.id)

            progressTracker.currentStep = VerifyingTransaction
            builder.verify(serviceHub)

            val otherParties = (commitRefs + revealRefs + gameRef + lockedTokensRef)
                    .flatMap { it.state.data.participants }
                    .map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)
                            ?: throw FlowException("A party cannot be resolved")}
                    .filter { it != ourIdentity }
                    .distinct()
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