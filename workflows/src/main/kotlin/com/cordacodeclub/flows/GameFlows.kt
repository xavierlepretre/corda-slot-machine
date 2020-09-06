package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.*
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.accounts.workflows.services.AccountService
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import java.time.Instant
import java.util.*

/**
 * Flows to orchestrate the game from start to finish.
 */
object GameFlows {

    /**
     * Data transport class to inform the remote note in 1 send.
     */
    @CordaSerializable
    data class GameSetup(val player: AbstractParty,
                         val playerWager: Long,
                         val issuer: AbstractParty,
                         val casino: AbstractParty,
                         val commitDeadline: Instant,
                         val revealDeadline: Instant) {
        init {
            require(commitDeadline < revealDeadline) { "The commit deadline must come before the reveal one" }
        }

        val playerBettor
            get() = Bettor(player, issuer, playerWager)
        val casinoBettor
            get() = Bettor(casino, issuer, casinoWager)
        val casinoWager
            get() = Math.multiplyExact(playerWager, GameParameters.casinoToPlayerRatio)
        val totalBetted
            get() = Math.addExact(playerWager, casinoWager)

        fun playerCommittedBettor(commitId: UniqueIdentifier) =
                player commitsTo commitId with (playerWager issuedBy issuer)

        fun casinoCommittedBettor(commitId: UniqueIdentifier) =
                casino commitsTo commitId with (Math.multiplyExact(playerWager, GameParameters.casinoToPlayerRatio) issuedBy issuer)
    }

    @StartableByRPC
    class SimpleInitiator(val playerName: String,
                          val playerWager: Long,
                          val issuerName: CordaX500Name,
                          val casinoName: CordaX500Name) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val issuer = serviceHub.identityService.wellKnownPartyFromX500Name(issuerName)
                    ?: throw FlowException("Unknown issuer name $issuerName")
            val casino = serviceHub.identityService.wellKnownPartyFromX500Name(casinoName)
                    ?: throw FlowException("Unknown casino name $casinoName")
            val accountService = serviceHub.cordaService(AccountService::class.java)
            val playerAccount = accountService
                    .accountInfo(playerName)
                    .let {
                        if (it.isEmpty())
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
                        if (it.isNotEmpty()) AnonymousParty(it.first())
                        else serviceHub.createKeyForAccount(playerAccount)
                    }
            subFlow(Initiator(player = player,
                    playerWager = playerWager,
                    issuer = issuer,
                    casino = casino))
        }
    }

    /**
     * Initiated by the player.
     * Its handler is [Responder].
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val player: AbstractParty,
                    val playerWager: Long,
                    val issuer: AbstractParty,
                    val casino: AbstractParty) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val playerImage = CommitImage.createRandom(Random())
            val commitDeadline = Instant.now().plus(GameParameters.commitDuration)!!
            val revealDeadline = commitDeadline.plus(GameParameters.revealDuration)!!
            val casinoHost = serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host")
            if (casinoHost == ourIdentity) throw FlowException("You must play with a remote host, not yourself")
            val casinoSession = initiateFlow(casinoHost)
            // Inform casino of player
            subFlow(SyncKeyMappingFlow(casinoSession, listOf(player)))
            // Inform casino of new game
            val setup = GameSetup(player = player,
                    playerWager = playerWager,
                    issuer = issuer,
                    casino = casino,
                    commitDeadline = commitDeadline,
                    revealDeadline = revealDeadline)
            casinoSession.send(setup)
            // First notary, not caring much...
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            // Player collects enough tokens for the wager.
            val playerTokens = subFlow(LockableTokenFlows.Fetch.Local(player, issuer,
                    playerWager, currentTopLevel?.runId?.uuid ?: throw FlowException("No running id")))
            // Player asks casino for commit and prepares double commits.
            val commitTx = subFlow(CommitFlows.Initiator(notary, playerImage.hash, setup, playerTokens,
                    casinoSession))
            val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
            val playerCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                    .single { it.state.data.creator == player }
            val lockedTokenRef = commitTx.tx.outRefsOfType<LockableTokenState>()
                    .single { it.state.data.isLocked }

            // Player reveals secretly.
            val playerRevealTx = subFlow(RevealFlows.Initiator(
                    playerCommitRef, playerImage, revealDeadline, gameRef, listOf(player), listOf()))
            val playerRevealedRef = playerRevealTx.tx.outRefsOfType<RevealedState>().single()
            // Player responds to casino's reveal.
            val casinoRevealTx = subFlow(RevealFlows.Responder(casinoSession))
            val casinoRevealed = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            // Player initiates resolution.
            val useTx = subFlow(UseFlows.Initiator(
                    playerRevealedRef, casinoRevealed, gameRef, lockedTokenRef, casinoSession))

            //TODO("Return the outcome for the RPC")
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val playerSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val casinoImage = CommitImage.createRandom(Random())
            // Receive player information
            subFlow(SyncKeyMappingFlowHandler(playerSession))
            // Receive new game information
            val setup = playerSession.receive<GameSetup>().unwrap { it }
            val (player, playerWager, issuer, casino, commitDeadline,
                    revealDeadline) = setup
            if (Instant.now().plus(GameParameters.commitDuration) < commitDeadline)
                throw FlowException("Commit deadline is too far in the future")
            if (commitDeadline.plus(GameParameters.revealDuration) != revealDeadline)
                throw FlowException("Reveal deadline is incorrect")
            // Casino collects enough tokens for the wager.
            val casinoTokens = subFlow(LockableTokenFlows.Fetch.Local(casino, issuer,
                    setup.casinoWager, currentTopLevel?.runId?.uuid ?: throw FlowException("No running id")))
            // Casino gives commit info and gets both commits tx.
            val commitTx = subFlow(CommitFlows.Responder(
                    playerSession, setup, casinoImage.hash, casinoTokens))
            val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
            val casinoCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                    .single { it.state.data.creator == casino }

            // Casino does not receive any reveal from player, yet reveals and discloses.
            val casinoRevealTx = subFlow(RevealFlows.Initiator(casinoCommitRef, casinoImage,
                    revealDeadline, gameRef, listOf(casino, player), listOf(playerSession)))
            val casinoRevealedRef = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            // Casino receives resolution.
            val useTx = subFlow(UseFlows.Responder(playerSession))
        }
    }
}