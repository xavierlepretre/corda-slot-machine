package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Use
import com.cordacodeclub.contracts.GameContract.Commands.Resolve
import com.cordacodeclub.contracts.LockableTokenContract.Commands.Release
import com.cordacodeclub.states.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Flows to determine the outcome of the game.
 */
object UseFlows {

    /**
     * In-lined flow started by the player.
     * Its handler is [Responder].
     */
    class Initiator(
            private val playerRef: StateAndRef<RevealedState>,
            private val casinoRef: StateAndRef<RevealedState>,
            private val gameRef: StateAndRef<GameState>,
            private val lockedTokenRef: StateAndRef<LockableTokenState>,
            private val casinoSession: FlowSession,
            override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object ResolvingCasino : ProgressTracker.Step("Resolving casino.")
            object GeneratingTransaction : ProgressTracker.Step("Generating transaction.")
            object PreparingPayoutTokens : ProgressTracker.Step("Preparing payout tokens.")
            object VerifyingTransaction : ProgressTracker.Step("Verifying contract constraints.")
            object SigningTransaction : ProgressTracker.Step("Signing transaction with our key.")
            object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    ResolvingCasino,
                    GeneratingTransaction,
                    PreparingPayoutTokens,
                    VerifyingTransaction,
                    SigningTransaction,
                    FinalisingTransaction)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = ResolvingCasino
            val player = playerRef.state.data.creator
            val casino = casinoRef.state.data.creator
            if (casinoSession.counterparty != serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host"))
                throw FlowException("The casinoSession is not for the casino")

            progressTracker.currentStep = GeneratingTransaction
            val builder = TransactionBuilder(playerRef.state.notary)
                    .addInputState(playerRef)
                    .addCommand(Use(0), player.owningKey)
                    .addInputState(casinoRef)
                    .addCommand(Use(1), player.owningKey)
                    .addInputState(gameRef)
                    .addCommand(Resolve(2), player.owningKey)
                    .addInputState(lockedTokenRef)

            progressTracker.currentStep = PreparingPayoutTokens
            val playerPayout = Math.multiplyExact(
                    CommitImage.playerPayoutCalculator(
                            casinoRef.state.data.image, playerRef.state.data.image),
                    gameRef.state.data.player.issuedAmount.amount.quantity)
            val casinoPayout = gameRef.state.data.bettedAmount.quantity - playerPayout
            val playerOutIndex = if (0L < playerPayout) {
                builder.addOutputState(LockableTokenState(player, gameRef.state.data.tokenIssuer,
                        Amount(playerPayout, LockableTokenType)))
                builder.outputStates().size - 1
            } else null
            val casinoOutIndex = if (0L < casinoPayout) {
                builder.addOutputState(LockableTokenState(casino, gameRef.state.data.tokenIssuer,
                        Amount(casinoPayout, LockableTokenType)))
                builder.outputStates().size - 1
            } else null
            builder.addCommand(Release(listOf(3), listOfNotNull(playerOutIndex, casinoOutIndex)), player.owningKey)

            progressTracker.currentStep = VerifyingTransaction
            builder.verify(serviceHub)

            progressTracker.currentStep = SigningTransaction
            val signedTx = serviceHub.signInitialTransaction(builder, player.owningKey)

            progressTracker.currentStep = FinalisingTransaction
            return subFlow(FinalityFlow(signedTx, casinoSession))
        }
    }

    /**
     * In-lined flow initiated by the casino. Casino signatures are not required on resolution.
     * Its initiator is [Initiator].
     */
    class Responder(private val playerSession: FlowSession,
                    override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

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