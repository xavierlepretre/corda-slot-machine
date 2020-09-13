package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.LockableTokenContract
import com.cordacodeclub.schema.LockableTokenSchemaV1
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import com.r3.corda.lib.accounts.workflows.services.AccountService
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.unwrap
import java.util.*

object LockableTokenFlows {

    object Issue {

        const val automaticAmount = 100L

        /**
         * Issues the amount of tokens to the given holders.
         * Its handler is [Responder].
         */
        @InitiatingFlow
        @StartableByRPC
        class Initiator(private val notary: Party,
                        private val holders: List<Pair<AbstractParty, Long>>,
                        private val issuer: AbstractParty)
            : FlowLogic<SignedTransaction>() {

            constructor(notary: Party, holder: AbstractParty, amount: Long, issuer: AbstractParty)
                    : this(notary, listOf(holder to amount), issuer)

            init {
                require(holders.isNotEmpty()) { "The holders cannot be empty" }
            }

            @Suspendable
            override fun call(): SignedTransaction {
                val builder = TransactionBuilder(notary)
                        .addCommand(LockableTokenContract.Commands.Issue((holders.indices).toList()), issuer.owningKey)
                holders.forEach {
                    builder.addOutputState(
                            LockableTokenState(it.first, issuer, Amount(it.second, LockableTokenType)),
                            LockableTokenContract.id)
                }
                builder.verify(serviceHub)
                val signed = serviceHub.signInitialTransaction(builder, issuer.owningKey)
                val holderHosts = holders.map {
                    serviceHub.identityService.wellKnownPartyFromAnonymous(it.first)
                            ?: throw FlowException("Could not resolve holder")
                }
                val holderFlows = holderHosts.mapNotNull {
                    if (it == ourIdentity) null
                    else initiateFlow(it)
                }
                holderFlows.forEach { subFlow(SyncKeyMappingFlow(it, listOf(issuer))) }
                return subFlow(FinalityFlow(signed, holderFlows, StatesToRecord.ALL_VISIBLE))
            }
        }

        /**
         * Receives the issued tokens.
         */
        @InitiatedBy(Initiator::class)
        class Responder(private val issuerSession: FlowSession) : FlowLogic<SignedTransaction>() {

            @Suspendable
            override fun call(): SignedTransaction {
                subFlow(SyncKeyMappingFlowHandler(issuerSession))
                return subFlow(ReceiveFinalityFlow(issuerSession))
            }
        }

        @CordaSerializable
        data class Request(val notary: Party,
                           val holder: AbstractParty,
                           val issuer: AbstractParty)

        /**
         * Asks the issuer to get some tokens.
         * Its handler is [ResponderBeg].
         */
        @InitiatingFlow
        @StartableByRPC
        class InitiatorBeg(private val request: Request) : FlowLogic<SignedTransaction>() {

            @Suspendable
            override fun call(): SignedTransaction {
                val issuerHost =
                        serviceHub.identityService.wellKnownPartyFromAnonymous(request.issuer)
                                ?: throw FlowException("Could not resolve issuer")
                return if (issuerHost == ourIdentity)
                    subFlow(Initiator(request.notary, request.holder, automaticAmount, request.issuer))
                else {
                    val issuerSession = initiateFlow(issuerHost)
                    issuerSession.sendAndReceive<SignedTransaction>(request).unwrap { it }
                }
            }
        }

        @InitiatedBy(InitiatorBeg::class)
        class ResponderBeg(private val holderSession: FlowSession) : FlowLogic<SignedTransaction>() {

            @Suspendable
            override fun call(): SignedTransaction = holderSession.receive<Request>()
                    .unwrap {
                        subFlow(Initiator(it.notary, it.holder, automaticAmount, it.issuer))
                    }
                    .also {
                        holderSession.send(it)
                    }
        }
    }

    object Fetch {
        const val PAGE_SIZE_DEFAULT = 200

        /**
         * Fetches enough tokens issued by issuer and held by holder to cover the required amount.
         * The soft lock id it typically FlowLogic.currentTopLevel?.runId?.uuid ?: throw FlowException("No running id")
         * so that they can be automatically locked until the flow is ended or killed.
         * This code was inspired by the similar function found in the Token SDK here:
         * https://github.com/corda/token-sdk/blob/22e18e6/modules/selection/src/main/kotlin/com.r3.corda.lib.tokens.selection/database/selector/DatabaseTokenSelection.kt#L57-L110
         */
        class Local(private val holder: AbstractParty,
                    private val issuer: AbstractParty,
                    private val requiredAmount: Long,
                    private val softLockId: UUID) :
                FlowLogic<List<StateAndRef<LockableTokenState>>>() {

            init {
                require(0L < requiredAmount) { "The amount must be strictly positive" }
            }

            @Suspendable
            override fun call(): List<StateAndRef<LockableTokenState>> {
                val criteria = QueryCriteria.VaultQueryCriteria(
                        contractStateTypes = setOf(LockableTokenState::class.java),
                        softLockingCondition = QueryCriteria.SoftLockingCondition(
                                QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED,
                                listOf(softLockId)),
                        relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                        status = Vault.StateStatus.UNCONSUMED
                ).and(QueryCriteria.VaultCustomQueryCriteria(builder {
                    LockableTokenSchemaV1.PersistentLockableToken::issuer.equal(issuer.owningKey.encoded)
                })
                ).and(QueryCriteria.VaultCustomQueryCriteria(builder {
                    LockableTokenSchemaV1.PersistentLockableToken::holder.equal(holder.owningKey.encoded)
                }))

                var pageNumber = DEFAULT_PAGE_NUM
                var claimedAmount = 0L
                val fetched = mutableListOf<StateAndRef<LockableTokenState>>()
                do {
                    val pageSpec = PageSpecification(pageNumber = pageNumber, pageSize = PAGE_SIZE_DEFAULT)
                    val results: Vault.Page<LockableTokenState> = serviceHub.vaultService.queryBy(
                            LockableTokenState::class.java, criteria, pageSpec)
                    for (state in results.states) {
                        // TODO confirm that this should never happen. Worried about ByteArray's length.
                        // https://github.com/xavierlepretre/corda-slot-machine/issues/23
                        if (state.state.data.let { it.holder != holder || it.issuer != issuer })
                            throw FlowException("The query returned a state that had wrong holder or issuer")
                        fetched += state
                        claimedAmount = Math.addExact(claimedAmount, state.state.data.amount.quantity)
                        if (requiredAmount <= claimedAmount) break
                    }
                    pageNumber++
                } while (claimedAmount < requiredAmount && (pageSpec.pageSize * (pageNumber - 1)) <= results.totalStatesAvailable)
                if (claimedAmount < requiredAmount) throw FlowException("Not enough tokens")
                serviceHub.vaultService.softLockReserve(softLockId, fetched.map { it.ref }.toNonEmptySet())
                return fetched
            }
        }
    }

    object Balance {

        class SimpleLocal(private val holderName: String,
                          private val issuerName: CordaX500Name) : FlowLogic<Long>() {

            @Suspendable
            override fun call(): Long {
                val issuer = serviceHub.identityService.wellKnownPartyFromX500Name(issuerName)
                        ?: throw FlowException("Unknown issuer name $issuerName")
                val accountService = serviceHub.cordaService(AccountService::class.java)
                val holderAccount = accountService
                        .accountInfo(holderName)
                        .let {
                            if (it.isEmpty())
                                throw FlowException("No holder with this name $holderName")
                            else if (1 < it.size)
                                throw FlowException("More than 1 holder found with this name $holderName")
                            it.single()
                        }
                        .state.data
                if (holderAccount.host != ourIdentity)
                    throw FlowException("This holder is not hosted here $holderName")
                val holder = serviceHub.identityService.publicKeysForExternalId(holderAccount.identifier.id)
                        .iterator()
                        .next()
                        .let { AnonymousParty(it)}
                return subFlow(Local(holder, issuer))
            }
        }

        /**
         * Fetches the current balance of tokens issued by issuer and held by the holder.
         */
        class Local(private val holder: AbstractParty,
                    private val issuer: AbstractParty) :
                FlowLogic<Long>() {

            @Suspendable
            override fun call(): Long {
                val criteria = QueryCriteria.VaultQueryCriteria(
                        contractStateTypes = setOf(LockableTokenState::class.java),
                        relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                        status = Vault.StateStatus.UNCONSUMED
                ).and(QueryCriteria.VaultCustomQueryCriteria(builder {
                    LockableTokenSchemaV1.PersistentLockableToken::issuer.equal(issuer.owningKey.encoded)
                })
                ).and(QueryCriteria.VaultCustomQueryCriteria(builder {
                    LockableTokenSchemaV1.PersistentLockableToken::holder.equal(holder.owningKey.encoded)
                }))

                var pageNumber = DEFAULT_PAGE_NUM
                var balanceSoFar = 0L
                do {
                    val pageSpec = PageSpecification(pageNumber = pageNumber, pageSize = Fetch.PAGE_SIZE_DEFAULT)
                    val results: Vault.Page<LockableTokenState> = serviceHub.vaultService.queryBy(
                            LockableTokenState::class.java, criteria, pageSpec)
                    for (state in results.states) {
                        // TODO confirm that this should never happen. Worried about ByteArray's length.
                        // https://github.com/xavierlepretre/corda-slot-machine/issues/23
                        if (state.state.data.let { it.holder != holder || it.issuer != issuer })
                            throw FlowException("The query returned a state that had wrong holder or issuer")
                        balanceSoFar = Math.addExact(balanceSoFar, state.state.data.amount.quantity)
                    }
                    pageNumber++
                } while ((pageSpec.pageSize * (pageNumber - 1)) <= results.totalStatesAvailable)
                return balanceSoFar
            }
        }
    }
}
