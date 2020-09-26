package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.LeaderboardEntryContract
import com.cordacodeclub.flows.LockableTokenFlows.Information
import com.cordacodeclub.schema.LeaderboardEntrySchemaV1
import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

object LeaderboardFlows {

    object Create {

        @StartableByRPC
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
        @StartableByRPC
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

    object Fetch {
        @StartableByRPC
        class Local(private val tokenIssuer: AbstractParty,
                    override val progressTracker: ProgressTracker = tracker())
            : FlowLogic<List<StateAndRef<LeaderboardEntryState>>>() {

            companion object {
                object PreparingCriteria : ProgressTracker.Step("Preparing criteria.")
                object QueryingVault : ProgressTracker.Step("Querying vault.")
                object ReturningResult : ProgressTracker.Step("Returning result.")

                fun tracker() = ProgressTracker(
                        PreparingCriteria,
                        QueryingVault,
                        ReturningResult)
            }

            @Suspendable
            override fun call(): List<StateAndRef<LeaderboardEntryState>> {
                progressTracker.currentStep = PreparingCriteria
                val criteria = QueryCriteria.VaultQueryCriteria(
                        contractStateTypes = setOf(LeaderboardEntryState::class.java),
                        relevancyStatus = Vault.RelevancyStatus.ALL,
                        status = Vault.StateStatus.UNCONSUMED
                ).and(QueryCriteria.VaultCustomQueryCriteria(builder {
                    LeaderboardEntrySchemaV1.PersistentLeaderboardEntry::tokenIssuer.equal(tokenIssuer.owningKey.encoded)
                }))
                val totalSortAttribute = SortAttribute.Custom(
                        LeaderboardEntrySchemaV1.PersistentLeaderboardEntry::class.java,
                        LeaderboardEntrySchemaV1.COL_TOTAL)
                val creationSortAttribute = SortAttribute.Custom(
                        LeaderboardEntrySchemaV1.PersistentLeaderboardEntry::class.java,
                        LeaderboardEntrySchemaV1.COL_CREATION)
                val sorter = Sort(setOf(
                        Sort.SortColumn(totalSortAttribute, Sort.Direction.DESC),
                        Sort.SortColumn(creationSortAttribute, Sort.Direction.ASC)))

                progressTracker.currentStep = QueryingVault
                var pageNumber = DEFAULT_PAGE_NUM
                val fetched = mutableListOf<StateAndRef<LeaderboardEntryState>>()
                do {
                    val pageSpec = PageSpecification(pageNumber = pageNumber, pageSize = LockableTokenFlows.Fetch.PAGE_SIZE_DEFAULT)
                    val results = serviceHub.vaultService.queryBy(
                            LeaderboardEntryState::class.java, criteria, pageSpec, sorter)
                    for (state in results.states) {
                        // TODO confirm that this should never happen. Worried about ByteArray's length.
                        // https://github.com/xavierlepretre/corda-slot-machine/issues/23
                        if (state.state.data.let { it.tokenIssuer != tokenIssuer })
                            throw FlowException("The query returned a state that had wrong token issuer")
                        fetched += state
                    }
                    pageNumber++
                } while (pageSpec.pageSize * (pageNumber - 1) <= results.totalStatesAvailable)

                progressTracker.currentStep = ReturningResult
                return fetched
            }
        }
    }

}