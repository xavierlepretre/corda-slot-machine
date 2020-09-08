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

/**
 * Flows to determine the outcome of the game.
 */
object UseFlows {

    /**
     * In-lined flow started by the player.
     * Its handler is [Responder].
     */
    class Initiator(
            val playerRef: StateAndRef<RevealedState>,
            val casinoRef: StateAndRef<RevealedState>,
            val gameRef: StateAndRef<GameState>,
            val lockedTokenRef: StateAndRef<LockableTokenState>,
            val casinoSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val player = playerRef.state.data.creator
            val casino = casinoRef.state.data.creator
            if (casinoSession.counterparty != serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host"))
                throw FlowException("The casinoSession is not for the casino")
            val builder = TransactionBuilder(playerRef.state.notary)
                    .addInputState(playerRef)
                    .addCommand(Use(0), player.owningKey)
                    .addInputState(casinoRef)
                    .addCommand(Use(1), player.owningKey)
                    .addInputState(gameRef)
                    .addCommand(Resolve(2), player.owningKey)
                    .addInputState(lockedTokenRef)
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
            builder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(builder, player.owningKey)
            return subFlow(FinalityFlow(signedTx, casinoSession))
        }
    }

    /**
     * In-lined flow initiated by the casino. Casino signatures are not required on resolution.
     * Its initiator is [Initiator].
     */
    class Responder(val playerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() = subFlow(ReceiveFinalityFlow(playerSession))
    }
}