package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.LeaderboardEntryContract
import com.cordacodeclub.flows.LockableTokenFlows.Information
import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

object LeaderboardFlows {

    object Create {

        class SimpleInitiator(private val playerAccountName: String,
                              private val tokenIssuer: AbstractParty,
                              private val observers: List<Party> = listOf(),
                              override val progressTracker: ProgressTracker = Initiator.tracker())
            : FlowLogic<SignedTransaction>() {

            companion object {
                object ResolvingPlayer : ProgressTracker.Step("Resolving player.")
                object PassingOnToInitiator : ProgressTracker.Step("Passing on to initiator.") {
                    override fun childProgressTracker() = Initiator.tracker()
                }

                fun tracker() = ProgressTracker(
                        ResolvingPlayer,
                        PassingOnToInitiator)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = ResolvingPlayer
                val player = getParty(playerAccountName)

                progressTracker.currentStep = PassingOnToInitiator
                return subFlow(Initiator(player = player,
                        tokenIssuer = tokenIssuer,
                        observers = observers,
                        progressTracker = PassingOnToInitiator.childProgressTracker()))
            }
        }

        @InitiatingFlow
        class Initiator(private val player: AbstractParty,
                        private val tokenIssuer: AbstractParty,
                        private val observers: List<Party> = listOf(),
                        override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

            companion object {
                object FetchingTokens : ProgressTracker.Step("Fetching tokens.")
                object GeneratingTransaction : ProgressTracker.Step("Generating transaction.")
                object VerifyingTransaction : ProgressTracker.Step("Verifying transaction.")
                object SigningTransaction : ProgressTracker.Step("Signing transaction.")
                object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.") {
                    override fun childProgressTracker() = FinalityFlow.tracker()
                }

                fun tracker() = ProgressTracker(
                        FetchingTokens,
                        GeneratingTransaction,
                        VerifyingTransaction,
                        SigningTransaction,
                        FinalisingTransaction)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = FetchingTokens
                val tokenStates = subFlow(Information.Local(player, tokenIssuer))
                val total = tokenStates.map { it.state.data }
                        .reduce(LockableTokenState::plus)
                        .amount

                progressTracker.currentStep = GeneratingTransaction
                val notary = tokenStates.map { it.state.notary }
                        .singleOrNull()
                        ?: throw FlowException("Tokens are not controlled by a single notary")
                val now = Instant.now()
                val participants = observers.plus(player).distinct()
                val builder = TransactionBuilder(notary)
                        .addCommand(LeaderboardEntryContract.Commands.Create(0), player.owningKey)
                        .addOutputState(LeaderboardEntryState(player, total,
                                tokenIssuer, now, UniqueIdentifier(), participants))
                        .setTimeWindow(TimeWindow.between(now.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                                now.plus(LeaderboardEntryContract.maxTimeWindowRadius)))
                tokenStates.forEach {
                    builder.addReferenceState(ReferencedStateAndRef(it))
                }

                progressTracker.currentStep = VerifyingTransaction
                builder.verify(serviceHub)

                progressTracker.currentStep = SigningTransaction
                val signed = serviceHub.signInitialTransaction(builder, player.owningKey)

                val observerHosts = participants
                        .map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
                        .distinct()
                        .filter { it != ourIdentity }
                val observerSessions = observerHosts.map(this@Initiator::initiateFlow)

                progressTracker.currentStep = FinalisingTransaction
                return subFlow(FinalityFlow(signed, observerSessions, FinalisingTransaction.childProgressTracker()))
            }
        }

        /**
         * Receives the created leaderboard entry.
         */
        @InitiatedBy(Initiator::class)
        class Responder(private val issuerSession: FlowSession,
                        override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {

            constructor(issuerSession: FlowSession) : this(issuerSession, tracker())

            companion object {
                object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.")

                fun tracker() = ProgressTracker(FinalisingTransaction)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = FinalisingTransaction
                return subFlow(ReceiveFinalityFlow(issuerSession))
            }
        }
    }

}