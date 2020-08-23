package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.LockableTokenContract
import com.cordacodeclub.schema.LockableTokenSchemaV1
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
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
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.toNonEmptySet
import java.util.*

object LockableTokenFlows {

    object Issue {
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
                return subFlow(FinalityFlow(signed, holderFlows, StatesToRecord.ALL_VISIBLE))
            }
        }

        /**
         * Receives the issued tokens.
         */
        @InitiatedBy(Initiator::class)
        class Responder(private val issuerSession: FlowSession) : FlowLogic<SignedTransaction>() {

            @Suspendable
            override fun call() = subFlow(ReceiveFinalityFlow(issuerSession))
        }
    }
}
