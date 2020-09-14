package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfoFlow
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfoHandlerFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*

object UserAccountFlows {

    object Create {
        /**
         * Its handler is [Responder].
         */
        @InitiatingFlow
        @StartableByRPC
        class Initiator(private val accountName: String,
                        private val observers: List<Party>,
                        override val progressTracker: ProgressTracker)
            : FlowLogic<Pair<StateAndRef<AccountInfo>, AnonymousParty>>() {

            constructor(accountName: String, observers: List<Party>) : this(accountName, observers, tracker())
            constructor(accountName: String, observer: Party) : this(accountName, listOf(observer))
            constructor(accountName: String) : this(accountName, listOf())

            companion object {
                object VerifyingNameUnicity : ProgressTracker.Step("Verifying account name unicity.")
                object CreatingAccount : ProgressTracker.Step("Creating account.")
                object SendingAccountToObservers : ProgressTracker.Step("Sending account to observers.")
                object CreatingPublicKey : ProgressTracker.Step("Creating public key for account.")
                object SendingPublicKeyToObervers : ProgressTracker.Step("Send public key to observer.")

                fun tracker() = ProgressTracker(
                        VerifyingNameUnicity,
                        CreatingAccount,
                        SendingAccountToObservers,
                        CreatingPublicKey,
                        SendingPublicKeyToObervers)
            }

            @Suspendable
            override fun call(): Pair<StateAndRef<AccountInfo>, AnonymousParty> {
                progressTracker.currentStep = VerifyingNameUnicity
                if (accountService.accountInfo(accountName).any { it.state.data.host == ourIdentity }) {
                    throw FlowException("$accountName account already exists")
                }

                progressTracker.currentStep = CreatingAccount
                val accountRef = subFlow(CreateAccount(accountName))

                progressTracker.currentStep = SendingAccountToObservers
                val observerSessions = observers.filter { it != ourIdentity }
                        .distinct()
                        .map { initiateFlow(it) }
                subFlow(ShareAccountInfoFlow(accountRef, observerSessions))

                progressTracker.currentStep = CreatingPublicKey
                val accountParty = getParty(accountName)

                progressTracker.currentStep = SendingPublicKeyToObervers
                subFlow(Sync.Initiator(
                        Sync.ToSync(accountRef.state.data.identifier.id, accountParty.owningKey),
                        observerSessions))

                return accountRef to accountParty
            }
        }

        @InitiatedBy(Initiator::class)
        class Responder(private val creatorSession: FlowSession) : FlowLogic<Unit>() {

            @Suspendable
            override fun call() {
                subFlow(ShareAccountInfoHandlerFlow(creatorSession))
                subFlow(Sync.Responder(creatorSession))
            }
        }
    }

    object Sync {

        @CordaSerializable
        data class ToSync(val id: UUID, val key: PublicKey)

        /**
         * Inline flow to send a public key associated with an id, whose handler is [Responder].
         */
        class Initiator(private val toSync: ToSync,
                        private val recipients: List<FlowSession>) : FlowLogic<Unit>() {

            constructor(toSync: ToSync, recipient: FlowSession) : this(toSync, listOf(recipient))

            @Suspendable
            override fun call() {
                recipients.distinct()
                        .forEach {
                            it.send(toSync)
                        }
            }
        }

        /**
         * Inline flow whose initiator is [Initiator].
         */
        class Responder(private val senderSession: FlowSession) : FlowLogic<ToSync>() {

            @Suspendable
            override fun call() = senderSession.receive<ToSync>()
                    .unwrap { it }
                    .also { serviceHub.identityService.registerKey(it.key, senderSession.counterparty, it.id) }
        }
    }
}