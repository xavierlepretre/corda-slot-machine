package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.CommitImage
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.accounts.workflows.services.AccountService
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Flows to orchestrate the game from start to finish.
 */
object GameFlows {

    val commitDuration = Duration.ofMinutes(2)!!
    val revealDuration = Duration.ofMinutes(2)!!

    /**
     * Data transport class to inform the remote note in 1 send.
     */
    @CordaSerializable
    data class GameSetup(val player: AbstractParty,
                         val casino: AbstractParty,
                         val commitDeadline: Instant,
                         val revealDeadline: Instant) {
        init {
            require(commitDeadline < revealDeadline) { "The commit deadline must come before the reveal one" }
        }
    }

    data class GameResultDetail(val setup: GameSetup,
                                val commitTx: SignedTransaction,
                                val playerRevealTx: SignedTransaction,
                                val casinoRevealTx: SignedTransaction,
                                val useTx: SignedTransaction,
                                val images: List<CommitImage>)

    @StartableByRPC
    class SimpleInitiator(val playerName: String, val casinoName: CordaX500Name) : FlowLogic<GameResultDetail>() {

        @Suspendable
        override fun call(): GameResultDetail {
            val casino = serviceHub.identityService.wellKnownPartyFromX500Name(casinoName)
                    ?: throw FlowException("Unknown casino name $casinoName")
            val accountService = serviceHub.cordaService(AccountService::class.java)
            val playerAccount = accountService
                    .accountInfo(playerName)
                    .let {
                        if (it.size == 0)
                            throw FlowException("No player with this name $playerName")
                        else if (1 < it.size)
                            throw FlowException("More than 1 player found with this name $playerName")
                        it.single()
                    }
                    .state.data
            if (playerAccount.host != ourIdentity)
                throw FlowException("This player is not hosted here $playerName")
            val player = serviceHub.identityService.publicKeysForExternalId(playerAccount.identifier.id)
                    .toList()
                    .let {
                        if (1 <= it.size) AnonymousParty(it.first())
                        else serviceHub.createKeyForAccount(playerAccount)
                    }
            return subFlow(Initiator(player, casino))
        }
    }

    /**
     * Initiated by the player.
     * Its handler is [Responder].
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val player: AbstractParty, val casino: AbstractParty) : FlowLogic<GameResultDetail>() {

        @Suspendable
        override fun call(): GameResultDetail {
            val playerImage = CommitImage.createRandom(Random())
            val commitDeadline = Instant.now().plus(commitDuration)!!
            val revealDeadline = commitDeadline.plus(revealDuration)!!
            val casinoHost = serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host")
            if (casinoHost == ourIdentity) throw FlowException("You must play with a remote host, not yourself")
            val casinoSession = initiateFlow(casinoHost)
            // Inform casino of new game
            val setup = GameSetup(player, casino, commitDeadline, revealDeadline)
            casinoSession.send(setup)
            // First notary, not caring much...
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            // Player asks casino for commit and prepares double commits
            val commitTx = subFlow(CommitFlows.Initiator(notary, playerImage.hash, setup, casinoSession))
            val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
            val playerCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                    .single { it.state.data.creator == player }

            // Player reveals secretly.
            val playerRevealTx = subFlow(RevealFlows.Initiator(
                    playerCommitRef, playerImage, revealDeadline, gameRef, listOf(player), listOf()))
            val playerRevealedRef = playerRevealTx.tx.outRefsOfType<RevealedState>().single()
            // Player responds to casino's reveal.
            val casinoRevealTx = subFlow(RevealFlows.Responder(casinoSession))
            val casinoRevealedRef = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()
            val casinoImage = casinoRevealedRef.state.data.image

            // Player initiates resolution.
            val useTx = subFlow(UseFlows.Initiator(
                    playerRevealedRef, casinoRevealedRef, gameRef, casinoSession))

            // Player lets the casino know which tx hash is actually the reveal. The casino node already has the tx
            // because it is part of the history for useTx to be verified.
            casinoSession.send(playerRevealTx.id)

            return GameResultDetail(setup, commitTx, playerRevealTx, casinoRevealTx, useTx,
                    listOf(playerImage, casinoImage))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val playerSession: FlowSession) : FlowLogic<GameResultDetail>() {

        @Suspendable
        override fun call(): GameResultDetail {
            val casinoImage = CommitImage.createRandom(Random())
            // Receive new game information
            val setup = playerSession.receive<GameSetup>().unwrap { it }
            val (player, casino, commitDeadline, revealDeadline) = setup
            if (Instant.now().plus(revealDuration) < commitDeadline.plus(Duration.ofMinutes(1)))
                throw FlowException("Commit deadline is too far in the future")
            if (commitDeadline.plus(revealDuration) != revealDeadline)
                throw FlowException("Reveal deadline is incorrect")
            // Casino gives commit info and gets both commits tx.
            val commitTx = subFlow(CommitFlows.Responder(
                    playerSession, commitDeadline, revealDeadline, casinoImage.hash, casino))
            val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
            val casinoCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                    .single { it.state.data.creator == casino }

            // Casino does not receive any reveal from player, yet reveals and discloses.
            val casinoRevealTx = subFlow(RevealFlows.Initiator(casinoCommitRef, casinoImage,
                    revealDeadline, gameRef, listOf(casino, player), listOf(playerSession)))
            val casinoRevealedRef = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            // Casino receives resolution.
            val useTx = subFlow(UseFlows.Responder(playerSession))

            // Casino finally receives the hash of playerRevealTx.
            val playerRevealTx = serviceHub.validatedTransactions.getTransaction(
                    playerSession.receive<SecureHash>().unwrap { it })
                    ?: throw FlowException("The player reveal tx id sent is unknown")
            val playerRevealedRef = playerRevealTx.tx.outRefsOfType<RevealedState>().single()
            val playerImage = playerRevealedRef.state.data.image

            return GameResultDetail(setup, commitTx, playerRevealTx, casinoRevealTx, useTx,
                    listOf(playerImage, casinoImage))
        }
    }
}