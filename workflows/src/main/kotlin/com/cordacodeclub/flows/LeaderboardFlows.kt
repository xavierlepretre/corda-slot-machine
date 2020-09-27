package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.LeaderboardEntryContract
import com.cordacodeclub.flows.LockableTokenFlows.Information
import com.cordacodeclub.schema.LeaderboardEntrySchemaV1
import com.cordacodeclub.services.leaderboardNicknamesDatabaseService
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

    const val maxLeaderboardLength = 20

    data class LeaderboardNamedEntryState(
            val state: StateAndRef<LeaderboardEntryState>,
            val nickname: String)

    object Create {

        @StartableByRPC
        class SimpleInitiator(private val playerAccountName: String,
                              private val playerNickname: String,
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
                        playerNickname = playerNickname,
                        tokenIssuer = tokenIssuer,
                        observers = observers,
                        progressTracker = PassingOnToInitiator.childProgressTracker()))
            }
        }

        @InitiatingFlow
        @StartableByRPC
        class Initiator(private val player: AbstractParty,
                        private val playerNickname: String,
                        private val tokenIssuer: AbstractParty,
                        private val observers: List<Party> = listOf(),
                        override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

            companion object {
                object FetchingTokens : ProgressTracker.Step("Fetching tokens.")
                object FetchingCurrentLeaderboard : ProgressTracker.Step("Fetching current leaderboard.")
                object GeneratingTransaction : ProgressTracker.Step("Generating transaction.")
                object VerifyingTransaction : ProgressTracker.Step("Verifying transaction.")
                object AddingNickname : ProgressTracker.Step("Adding player nickname.")
                object SigningTransaction : ProgressTracker.Step("Signing transaction.")
                object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.") {
                    override fun childProgressTracker() = FinalityFlow.tracker()
                }

                object RemovingNicknames : ProgressTracker.Step("Removing nicknames.")

                fun tracker() = ProgressTracker(
                        FetchingTokens,
                        FetchingCurrentLeaderboard,
                        GeneratingTransaction,
                        VerifyingTransaction,
                        AddingNickname,
                        SigningTransaction,
                        FinalisingTransaction,
                        RemovingNicknames)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = FetchingTokens
                val tokenStates = subFlow(Information.Local(player, tokenIssuer))
                val playerTotal = tokenStates.map { it.state.data }
                        .reduce(LockableTokenState::plus)
                        .amount

                progressTracker.currentStep = FetchingCurrentLeaderboard
                val leaderboard = subFlow(Fetch.Local(tokenIssuer))
                        .map { it.state }
                val entriesToKeep = leaderboard.take(maxLeaderboardLength)
                val entriesToRetire = leaderboard.drop(maxLeaderboardLength)
                val entryToOvertake =
                        if (entriesToKeep.size == maxLeaderboardLength
                                && leaderboard.last().state.data.total < playerTotal) entriesToKeep.last()
                        else if (entriesToKeep.size == maxLeaderboardLength)
                            throw FlowException("The player does not qualify to enter the leaderboard")
                        else null
                if (entriesToKeep.map { it.state.data }.any { it.player == player && it.total == playerTotal })
                    throw FlowException("Same player cannot enter the leaderboard with identical total")

                progressTracker.currentStep = GeneratingTransaction
                val notary = tokenStates.map { it.state.notary }
                        .distinct()
                        .singleOrNull()
                        ?: throw FlowException("Tokens are not controlled by a single notary")
                val now = Instant.now()
                val newId = UniqueIdentifier()
                val participants = observers.plus(player).distinct()
                val builder = TransactionBuilder(notary)
                if (entryToOvertake == null) {
                    builder.addCommand(LeaderboardEntryContract.Commands.Create(0), player.owningKey)
                } else {
                    builder.addCommand(LeaderboardEntryContract.Commands.Overtake(0, 0), player.owningKey)
                            .addInputState(entryToOvertake)
                }
                builder.addOutputState(LeaderboardEntryState(player, playerTotal, tokenIssuer, now, newId, participants))
                entriesToRetire.forEach {
                    builder.addCommand(
                            LeaderboardEntryContract.Commands.Retire(builder.inputStates().size),
                            it.state.data.player.owningKey)
                            .addInputState(it)
                }
                builder.setTimeWindow(TimeWindow.between(now.minus(LeaderboardEntryContract.maxTimeWindowRadius),
                        now.plus(LeaderboardEntryContract.maxTimeWindowRadius)))
                tokenStates.forEach {
                    builder.addReferenceState(ReferencedStateAndRef(it))
                }

                progressTracker.currentStep = VerifyingTransaction
                builder.verify(serviceHub)

                progressTracker.currentStep = AddingNickname
                try {
                    serviceHub.leaderboardNicknamesDatabaseService.addLeaderboardNickname(newId.id, playerNickname)
                } catch (e: RuntimeException) {
                    throw FlowException(e)
                }

                progressTracker.currentStep = SigningTransaction
                val signed = serviceHub.signInitialTransaction(builder, player.owningKey)

                val observerHosts = participants
                        .map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) }
                        .distinct()
                        .filter { it != ourIdentity }
                val observerSessions = observerHosts.map(this@Initiator::initiateFlow)

                progressTracker.currentStep = FinalisingTransaction
                val finalised = subFlow(FinalityFlow(signed, observerSessions, FinalisingTransaction.childProgressTracker()))

                progressTracker.currentStep = RemovingNicknames
                entriesToRetire.plus(entryToOvertake)
                        .filterNotNull()
                        .onEach {
                            try {
                                serviceHub.leaderboardNicknamesDatabaseService.deleteNickname(it.state.data.linearId.id)
                            } catch (e: RuntimeException) {
                                // Swallow it
                            }
                        }
                return finalised
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
            : FlowLogic<List<LeaderboardNamedEntryState>>() {

            companion object {
                object PreparingCriteria : ProgressTracker.Step("Preparing criteria.")
                object QueryingVault : ProgressTracker.Step("Querying vault.")
                object AssigningNames : ProgressTracker.Step("Assigning names to entries.")
                object ReturningResult : ProgressTracker.Step("Returning result.")

                fun tracker() = ProgressTracker(
                        PreparingCriteria,
                        QueryingVault,
                        AssigningNames,
                        ReturningResult)
            }

            @Suspendable
            override fun call(): List<LeaderboardNamedEntryState> {
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

                progressTracker.currentStep = AssigningNames
                val namedEntries = fetched.map {
                    LeaderboardNamedEntryState(it, try {
                        serviceHub.leaderboardNicknamesDatabaseService.getNickname(it.state.data.linearId.id)
                    } catch (e: RuntimeException) {
                        "unnamed"
                    })
                }

                progressTracker.currentStep = ReturningResult
                return namedEntries
            }
        }
    }

}