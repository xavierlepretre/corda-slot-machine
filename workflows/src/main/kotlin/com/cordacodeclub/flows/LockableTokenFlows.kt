package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.LockableTokenContract
import com.cordacodeclub.schema.LockableTokenSchemaV1
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
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
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.unwrap
import java.util.*

@Suppress("unused")
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
                        private val issuer: AbstractParty,
                        override val progressTracker: ProgressTracker = tracker())
            : FlowLogic<SignedTransaction>() {

            constructor(notary: Party, holder: AbstractParty, amount: Long, issuer: AbstractParty)
                    : this(notary, listOf(holder to amount), issuer)

            constructor(notary: Party, holder: AbstractParty, amount: Long, issuer: AbstractParty,
                        progressTracker: ProgressTracker)
                    : this(notary, listOf(holder to amount), issuer, progressTracker)

            init {
                require(holders.isNotEmpty()) { "The holders cannot be empty" }
            }

            companion object {
                object GeneratingTransaction : ProgressTracker.Step("Generating transaction.")
                object VerifyingTransaction : ProgressTracker.Step("Verifying contract constraints.")
                object SigningTransaction : ProgressTracker.Step("Signing transaction with issuer key.")
                object ResolvingHolders : ProgressTracker.Step("Resolving holders.")
                object SendingIssuerInformation : ProgressTracker.Step("Sending issuer information.")
                object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.") {
                    override fun childProgressTracker() = FinalityFlow.tracker()
                }

                fun tracker() = ProgressTracker(
                        GeneratingTransaction,
                        VerifyingTransaction,
                        SigningTransaction,
                        ResolvingHolders,
                        SendingIssuerInformation,
                        FinalisingTransaction)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = GeneratingTransaction
                val builder = TransactionBuilder(notary)
                        .addCommand(LockableTokenContract.Commands.Issue((holders.indices).toList()), issuer.owningKey)
                holders.forEach {
                    builder.addOutputState(
                            LockableTokenState(it.first, issuer, Amount(it.second, LockableTokenType)),
                            LockableTokenContract.id)
                }

                progressTracker.currentStep = VerifyingTransaction
                builder.verify(serviceHub)

                progressTracker.currentStep = SigningTransaction
                val signed = serviceHub.signInitialTransaction(builder, issuer.owningKey)

                progressTracker.currentStep = ResolvingHolders
                val holderHosts = holders.map {
                    serviceHub.identityService.wellKnownPartyFromAnonymous(it.first)
                            ?: throw FlowException("Could not resolve holder")
                }
                val holderFlows = holderHosts.mapNotNull {
                    if (it == ourIdentity) null
                    else initiateFlow(it)
                }

                progressTracker.currentStep = SendingIssuerInformation
                holderFlows.forEach { subFlow(SyncKeyMappingFlow(it, listOf(issuer))) }

                progressTracker.currentStep = FinalisingTransaction
                return subFlow(FinalityFlow(signed,
                        holderFlows,
                        StatesToRecord.ALL_VISIBLE,
                        FinalisingTransaction.childProgressTracker()))
            }
        }

        /**
         * Receives the issued tokens.
         */
        @InitiatedBy(Initiator::class)
        class Responder(private val issuerSession: FlowSession,
                        override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {

            constructor(issuerSession: FlowSession) : this(issuerSession, tracker())

            companion object {
                object ReceivingIssuerInformation : ProgressTracker.Step("Receiving issuer information.")
                object FinalisingTransaction : ProgressTracker.Step("Finalising transaction.")

                fun tracker() = ProgressTracker(ReceivingIssuerInformation, FinalisingTransaction)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = ReceivingIssuerInformation
                subFlow(SyncKeyMappingFlowHandler(issuerSession))

                progressTracker.currentStep = FinalisingTransaction
                return subFlow(ReceiveFinalityFlow(issuerSession))
            }
        }

        @CordaSerializable
        data class Request(val notary: Party,
                           val holder: AbstractParty,
                           val issuer: AbstractParty)

        @StartableByRPC
        class InitiatorBegSimple(private val notary: Party,
                                 private val holderAccountName: String,
                                 private val issuer: Party,
                                 override val progressTracker: ProgressTracker)
            : FlowLogic<SignedTransaction>() {

            constructor(notary: Party,
                        holderAccountName: String,
                        issuer: Party) : this(notary, holderAccountName, issuer, tracker())

            companion object {
                object ResolvingHolder : ProgressTracker.Step("Resolving holder.")
                object PassingOnToInitiatorBeg : ProgressTracker.Step("Passing on to initiator beg.") {
                    override fun childProgressTracker() = InitiatorBeg.tracker()
                }

                fun tracker() = ProgressTracker(
                        ResolvingHolder,
                        PassingOnToInitiatorBeg)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = ResolvingHolder
                val holder = getParty(holderAccountName)

                progressTracker.currentStep = PassingOnToInitiatorBeg
                return subFlow(InitiatorBeg(Request(notary, holder, issuer),
                        PassingOnToInitiatorBeg.childProgressTracker()))
            }
        }

        /**
         * Asks the issuer to get some tokens.
         * Its handler is [ResponderBeg].
         */
        @InitiatingFlow
        @StartableByRPC
        class InitiatorBeg(private val request: Request,
                           override val progressTracker: ProgressTracker = tracker())
            : FlowLogic<SignedTransaction>() {

            companion object {
                object ResolvingIssuer : ProgressTracker.Step("Resolving issuer.")
                object PassingOnToInitiator : ProgressTracker.Step("Passing on to initiator.") {
                    override fun childProgressTracker() = Initiator.tracker()
                }

                object SendingHolderInformation : ProgressTracker.Step("Sending holder information.")
                object SendingRequestInformation : ProgressTracker.Step("Sending request information.")

                fun tracker() = ProgressTracker(
                        ResolvingIssuer,
                        PassingOnToInitiator,
                        SendingHolderInformation,
                        SendingRequestInformation)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = ResolvingIssuer
                val issuerHost =
                        serviceHub.identityService.wellKnownPartyFromAnonymous(request.issuer)
                                ?: throw FlowException("Could not resolve issuer")
                return if (issuerHost == ourIdentity) {
                    progressTracker.currentStep = PassingOnToInitiator
                    subFlow(Initiator(request.notary, request.holder, automaticAmount, request.issuer,
                            PassingOnToInitiator.childProgressTracker()))
                } else {
                    progressTracker.currentStep = SendingHolderInformation
                    val issuerSession = initiateFlow(issuerHost)
                    subFlow(SyncKeyMappingFlow(issuerSession, listOf(request.holder)))

                    progressTracker.currentStep = SendingRequestInformation
                    issuerSession.sendAndReceive<SignedTransaction>(request).unwrap { it }
                }
            }
        }

        @InitiatedBy(InitiatorBeg::class)
        class ResponderBeg(private val holderSession: FlowSession,
                           override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {

            constructor(holderSession: FlowSession) : this(holderSession, tracker())

            companion object {
                object ReceivingHolderInformation : ProgressTracker.Step("Receiving holder information.")
                object ReceivingRequestInformation : ProgressTracker.Step("Receiving request information.")
                object PassingOnToInitiator : ProgressTracker.Step("Passing on to initiator.")
                object SendingTransactionToHolder : ProgressTracker.Step("Sending transaction to holder.")

                fun tracker() = ProgressTracker(
                        ReceivingHolderInformation,
                        ReceivingRequestInformation,
                        PassingOnToInitiator,
                        SendingTransactionToHolder)
            }

            @Suspendable
            override fun call(): SignedTransaction {
                progressTracker.currentStep = ReceivingHolderInformation
                subFlow(SyncKeyMappingFlowHandler(holderSession))

                progressTracker.currentStep = ReceivingRequestInformation
                val request = holderSession.receive<Request>().unwrap { it }

                progressTracker.currentStep = PassingOnToInitiator
                val tx = subFlow(Initiator(request.notary, request.holder, automaticAmount, request.issuer))

                progressTracker.currentStep = SendingTransactionToHolder
                holderSession.send(tx)
                return tx
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
                    private val softLockId: UUID,
                    override val progressTracker: ProgressTracker = tracker()) :
                FlowLogic<List<StateAndRef<LockableTokenState>>>() {

            init {
                require(0L < requiredAmount) { "The amount must be strictly positive" }
            }

            companion object {
                object PreparingCriteria : ProgressTracker.Step("Preparing criteria.")
                object QueryingVault : ProgressTracker.Step("Querying vault.")
                object SoftLockingTokens : ProgressTracker.Step("Soft locking tokens.")

                fun tracker() = ProgressTracker(
                        PreparingCriteria,
                        QueryingVault,
                        SoftLockingTokens)
            }

            @Suspendable
            override fun call(): List<StateAndRef<LockableTokenState>> {
                progressTracker.currentStep = PreparingCriteria
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

                progressTracker.currentStep = QueryingVault
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

                progressTracker.currentStep = SoftLockingTokens
                serviceHub.vaultService.softLockReserve(softLockId, fetched.map { it.ref }.toNonEmptySet())
                return fetched
            }
        }
    }

    object Balance {

        /**
         * Fetches the current balance of tokens issued by issuer and held by the holder known by its account name.
         */
        @StartableByRPC
        class SimpleLocal(private val holderName: String,
                          private val issuer: AbstractParty,
                          override val progressTracker: ProgressTracker) : FlowLogic<Long>() {

            constructor(holderName: String, issuer: AbstractParty) : this(holderName, issuer, tracker())

            companion object {
                object ResolvingHolder : ProgressTracker.Step("Resolving holder.")
                object PassingOnToLocal : ProgressTracker.Step("Passing on to local.") {
                    override fun childProgressTracker() = Local.tracker()
                }

                fun tracker() = ProgressTracker(ResolvingHolder, PassingOnToLocal)
            }

            @Suspendable
            override fun call(): Long {
                progressTracker.currentStep = ResolvingHolder
                val holder = getParty(holderName)

                progressTracker.currentStep = PassingOnToLocal
                return subFlow(Local(holder, issuer, PassingOnToLocal.childProgressTracker()))
            }
        }

        /**
         * Fetches the current balance of tokens issued by issuer and held by the holder.
         */
        @StartableByRPC
        class Local(private val holder: AbstractParty,
                    private val issuer: AbstractParty,
                    override val progressTracker: ProgressTracker) : FlowLogic<Long>() {

            constructor(holder: AbstractParty, issuer: AbstractParty) : this(holder, issuer, tracker())

            companion object {
                object PreparingCriteria : ProgressTracker.Step("Preparing criteria.")
                object QueryingVault : ProgressTracker.Step("Querying vault.")

                fun tracker() = ProgressTracker(PreparingCriteria, QueryingVault)
            }

            @Suspendable
            override fun call(): Long {
                progressTracker.currentStep = PreparingCriteria
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

                progressTracker.currentStep = QueryingVault
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
