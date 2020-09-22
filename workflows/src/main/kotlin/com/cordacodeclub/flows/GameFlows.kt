package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.*
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
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
    data class GameSetup(val notary: Party,
                         val player: AbstractParty,
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
            get() = Math.multiplyExact(playerWager, GameState.maxPayoutRatio)
        val totalBetted
            get() = Math.addExact(playerWager, casinoWager)

        fun playerCommittedBettor(commitId: UniqueIdentifier) =
                player commitsTo commitId with (playerWager issuedBy issuer)

        fun casinoCommittedBettor(commitId: UniqueIdentifier) =
                casino commitsTo commitId with (casinoWager issuedBy issuer)
    }

    @CordaSerializable
    data class GameTransactions(
            val commitTx: SignedTransaction,
            val casinoRevealTx: SignedTransaction,
            val playerRevealTx: SignedTransaction,
            val resolveTx: SignedTransaction) {

        val playerPayout = CommitImage.playerPayoutCalculator(
                casinoRevealTx.tx.outputsOfType<RevealedState>().single().image,
                playerRevealTx.tx.outputsOfType<RevealedState>().single().image)
    }

    @StartableByRPC
    class SimpleInitiator(private val notary: String,
                          private val playerAccountName: String,
                          private val playerWager: Long,
                          private val issuer: AbstractParty,
                          private val casino: AbstractParty,
                          override val progressTracker: ProgressTracker) : FlowLogic<GameResult>() {

        constructor(notary: String,
                    playerAccountName: String,
                    playerWager: Long,
                    issuer: AbstractParty,
                    casino: AbstractParty)
                : this(notary, playerAccountName, playerWager, issuer, casino, tracker())

        companion object {
            object ResolvingPlayer : ProgressTracker.Step("Resolving player.")
            object PassingOnToInitiator : ProgressTracker.Step("Passing on to initiator.") {
                override fun childProgressTracker() = Initiator.tracker()
            }

            object FetchingPlayerBalance : ProgressTracker.Step("Fetching player balance.") {
                override fun childProgressTracker() = LockableTokenFlows.Balance.Local.tracker()
            }

            fun tracker() = ProgressTracker(
                    ResolvingPlayer,
                    PassingOnToInitiator,
                    FetchingPlayerBalance)
        }

        @Suspendable
        override fun call(): GameResult {
            progressTracker.currentStep = ResolvingPlayer
            val player = getParty(playerAccountName)

            progressTracker.currentStep = PassingOnToInitiator
            val playerPayout = subFlow(Initiator(
                    notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(notary))
                            ?: throw FlowException("$notary not found"),
                    player = player,
                    playerWager = playerWager,
                    issuer = issuer,
                    casino = casino,
                    progressTracker = PassingOnToInitiator.childProgressTracker()))
                    .playerPayout

            progressTracker.currentStep = FetchingPlayerBalance
            val playerBalance = subFlow(LockableTokenFlows.Balance.Local(
                    issuer, player, FetchingPlayerBalance.childProgressTracker()))
            return GameResult(playerPayout, playerBalance)
        }
    }

    /**
     * Initiated by the player.
     * Its handler is [Responder].
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val notary: Party,
                    private val player: AbstractParty,
                    private val playerWager: Long,
                    private val issuer: AbstractParty,
                    private val casino: AbstractParty,
                    override val progressTracker: ProgressTracker) : FlowLogic<GameTransactions>() {

        constructor(notary: Party, player: AbstractParty, playerWager: Long,
                    issuer: AbstractParty, casino: AbstractParty)
                : this(notary, player, playerWager, issuer, casino, tracker())

        companion object {
            object CreatingPlayerImage : ProgressTracker.Step("Creating player image.")
            object ResolvingCasino : ProgressTracker.Step("Resolving casino.")
            object SendingPlayer : ProgressTracker.Step("Sending player.")
            object SendingGameSetup : ProgressTracker.Step("Sending game setup.")
            object FetchingPlayerTokens : ProgressTracker.Step("Fetching player tokens.") {
                override fun childProgressTracker() = LockableTokenFlows.Fetch.Local.tracker()
            }

            object PassingOnToCommit : ProgressTracker.Step("Passing on to commit flow.") {
                override fun childProgressTracker() = CommitFlows.Initiator.tracker()
            }

            object ExtractingCommitResult : ProgressTracker.Step("Extracting commit results from transaction.")
            object PassingOnToPlayerReveal : ProgressTracker.Step("Passing on to player reveal flow.") {
                override fun childProgressTracker() = RevealFlows.Initiator.tracker()
            }

            object ExtractingPlayerRevealResult : ProgressTracker.Step("Extracting player reveal results.")
            object PassingOnToCasinoRevealResponder : ProgressTracker.Step("Receiving casino reveal.") {
                override fun childProgressTracker() = RevealFlows.Responder.tracker()
            }

            object ExtractingCasinoRevealResult : ProgressTracker.Step("Extracting casino reveal results.")
            object SendingPlayerReveal : ProgressTracker.Step("Sending player reveal.")
            object PassingOnToGameResolve : ProgressTracker.Step("Passing on to game resolve.") {
                override fun childProgressTracker() = UseFlows.Initiator.tracker()
            }

            fun tracker() = ProgressTracker(
                    CreatingPlayerImage,
                    ResolvingCasino,
                    SendingPlayer,
                    SendingGameSetup,
                    FetchingPlayerTokens,
                    PassingOnToCommit,
                    ExtractingCommitResult,
                    PassingOnToPlayerReveal,
                    ExtractingPlayerRevealResult,
                    PassingOnToCasinoRevealResponder,
                    ExtractingCasinoRevealResult,
                    SendingPlayerReveal,
                    PassingOnToGameResolve)
        }

        @Suspendable
        override fun call(): GameTransactions {
            progressTracker.currentStep = CreatingPlayerImage
            val playerImage = CommitImage.createRandom(Random())
            val commitDeadline = Instant.now().plus(GameParameters.commitDuration)!!
            val revealDeadline = commitDeadline.plus(GameParameters.revealDuration)!!

            progressTracker.currentStep = ResolvingCasino
            val casinoHost = serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host")
            if (casinoHost == ourIdentity) throw FlowException("You must play with a remote host, not yourself")
            val casinoSession = initiateFlow(casinoHost)

            // Inform casino of player
            progressTracker.currentStep = SendingPlayer
            subFlow(SyncKeyMappingFlow(casinoSession, listOf(player)))

            // Inform casino of new game
            progressTracker.currentStep = SendingGameSetup
            val setup = GameSetup(notary = notary,
                    player = player,
                    playerWager = playerWager,
                    issuer = issuer,
                    casino = casino,
                    commitDeadline = commitDeadline,
                    revealDeadline = revealDeadline)
            casinoSession.send(setup)

            // First notary, not caring much...
            progressTracker.currentStep = FetchingPlayerTokens
            // Player collects enough tokens for the wager.
            val playerTokens = subFlow(LockableTokenFlows.Fetch.Local(player, issuer,
                    playerWager, currentTopLevel?.runId?.uuid ?: throw FlowException("No running id"),
                    FetchingPlayerTokens.childProgressTracker()))

            progressTracker.currentStep = PassingOnToCommit
            // Player asks casino for commit and prepares double commits.
            val commitTx = subFlow(CommitFlows.Initiator(notary, playerImage.hash, setup, playerTokens,
                    casinoSession))

            progressTracker.currentStep = ExtractingCommitResult
            val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
            val playerCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                    .single { it.state.data.creator == player }
            val lockedTokenRef = commitTx.tx.outRefsOfType<LockableTokenState>()
                    .single { it.state.data.isLocked }

            // Player reveals secretly.
            progressTracker.currentStep = PassingOnToPlayerReveal
            val playerRevealTx = subFlow(RevealFlows.Initiator(
                    playerCommitRef, playerImage, revealDeadline, gameRef, listOf(player), listOf(),
                    PassingOnToPlayerReveal.childProgressTracker()))

            progressTracker.currentStep = ExtractingPlayerRevealResult
            val playerRevealedRef = playerRevealTx.tx.outRefsOfType<RevealedState>().single()

            // Player responds to casino's reveal.
            progressTracker.currentStep = PassingOnToCasinoRevealResponder
            val casinoRevealTx = subFlow(RevealFlows.Responder(casinoSession,
                    PassingOnToCasinoRevealResponder.childProgressTracker()))

            progressTracker.currentStep = ExtractingCasinoRevealResult
            val casinoRevealed = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            // Player now sends its own reveal transaction.
            progressTracker.currentStep = SendingPlayerReveal
            casinoSession.send(playerRevealTx)

            // Player initiates resolution.
            progressTracker.currentStep = PassingOnToGameResolve
            val useTx = subFlow(UseFlows.Initiator(
                    playerRevealedRef, casinoRevealed, gameRef, lockedTokenRef, casinoSession,
                    PassingOnToGameResolve.childProgressTracker()))
            return GameTransactions(commitTx, casinoRevealTx, playerRevealTx, useTx)
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val playerSession: FlowSession,
                    override val progressTracker: ProgressTracker) : FlowLogic<GameTransactions>() {

        @Suppress("unused")
        constructor(playerSession: FlowSession) : this(playerSession, tracker())

        companion object {
            // Limit to what the casino will accept to play.
            const val maxPlayerWager = 100L

            // Whether to auto issue tokens when not enough tokens can be fetched.
            const val autoIssueWhenPossible = true

            object CreatingCasinoImage : ProgressTracker.Step("Creating casino image.")
            object ReceivingPlayer : ProgressTracker.Step("Receiving player.")
            object ReceivingGameSetup : ProgressTracker.Step("Receiving game setup.")
            object FetchingCasinoTokens : ProgressTracker.Step("Fetching casino tokens.") {
                override fun childProgressTracker() = LockableTokenFlows.Fetch.Local.tracker()
            }

            object SelfIssuingCasinoTokens : ProgressTracker.Step("Self issuing casino tokens.") {
                override fun childProgressTracker() = LockableTokenFlows.Issue.Initiator.tracker()
            }

            object PassingOnToCommitResponder : ProgressTracker.Step("Passing on to commit responder flow.") {
                override fun childProgressTracker() = CommitFlows.Responder.tracker()
            }

            object ExtractingCommitResult : ProgressTracker.Step("Extracting commit results from transaction.")
            object PassingOnToCasinoReveal : ProgressTracker.Step("Receiving casino reveal.") {
                override fun childProgressTracker() = RevealFlows.Initiator.tracker()
            }

            object ExtractingCasinoRevealResult : ProgressTracker.Step("Extracting casino reveal results.")
            object ReceivingPlayerReveal : ProgressTracker.Step("Sending player reveal.")
            object PassingOnToGameResolveResponder : ProgressTracker.Step("Passing on to game resolve responder.") {
                override fun childProgressTracker() = UseFlows.Responder.tracker()
            }

            fun tracker() = ProgressTracker(
                    CreatingCasinoImage,
                    ReceivingPlayer,
                    ReceivingGameSetup,
                    FetchingCasinoTokens,
                    SelfIssuingCasinoTokens,
                    PassingOnToCommitResponder,
                    ExtractingCommitResult,
                    PassingOnToCasinoReveal,
                    ExtractingCasinoRevealResult,
                    ReceivingPlayerReveal,
                    PassingOnToGameResolveResponder)
        }

        @Suspendable
        override fun call(): GameTransactions {
            progressTracker.currentStep = CreatingCasinoImage
            val casinoImage = CommitImage.createRandom(Random())

            // Receive player information
            progressTracker.currentStep = ReceivingPlayer
            subFlow(SyncKeyMappingFlowHandler(playerSession))

            // Receive new game information
            progressTracker.currentStep = ReceivingGameSetup
            val setup = playerSession.receive<GameSetup>().unwrap { it }
            if (maxPlayerWager < setup.playerWager)
                throw FlowException("Player wager cannot be more than $maxPlayerWager")
            if (Instant.now().plus(GameParameters.commitDuration) < setup.commitDeadline)
                throw FlowException("Commit deadline is too far in the future")
            if (setup.commitDeadline.plus(GameParameters.revealDuration) != setup.revealDeadline)
                throw FlowException("Reveal deadline is incorrect")

            // Casino collects or issues enough tokens for the wager.
            progressTracker.currentStep = FetchingCasinoTokens
            val casinoTokens = try {
                subFlow(LockableTokenFlows.Fetch.Local(
                        setup.casino, setup.issuer, setup.casinoWager,
                        currentTopLevel?.runId?.uuid ?: throw FlowException("No running id"),
                        FetchingCasinoTokens.childProgressTracker()))
            } catch (notEnough: LockableTokenFlows.Fetch.NotEnoughTokensException) {
                if (!autoIssueWhenPossible || setup.issuer != setup.casino) throw notEnough
                progressTracker.currentStep = SelfIssuingCasinoTokens
                subFlow(LockableTokenFlows.Issue.Initiator(setup.notary, setup.casino, setup.casinoWager,
                        setup.issuer, SelfIssuingCasinoTokens.childProgressTracker()))
                        .tx
                        .outRefsOfType<LockableTokenState>()
            }

            // Casino gives commit info and gets both commits tx.
            progressTracker.currentStep = PassingOnToCommitResponder
            val commitTx = subFlow(CommitFlows.Responder(
                    playerSession, setup, casinoImage.hash, casinoTokens,
                    PassingOnToCommitResponder.childProgressTracker()))

            progressTracker.currentStep = ExtractingCommitResult
            val gameRef = commitTx.tx.outRefsOfType<GameState>().single()
            val casinoCommitRef = commitTx.tx.outRefsOfType<CommittedState>()
                    .single { it.state.data.creator == setup.casino }

            // Casino does not yet receive any reveal from player, yet reveals and discloses.
            progressTracker.currentStep = PassingOnToCasinoReveal
            val casinoRevealTx = subFlow(RevealFlows.Initiator(casinoCommitRef, casinoImage,
                    setup.revealDeadline, gameRef, listOf(setup.casino, setup.player), listOf(playerSession),
                    PassingOnToCasinoReveal.childProgressTracker()))

            progressTracker.currentStep = ExtractingCasinoRevealResult
            val casinoRevealedRef = casinoRevealTx.tx.outRefsOfType<RevealedState>().single()

            // Casino receives player's reveal
            progressTracker.currentStep = ReceivingPlayerReveal
            val playerRevealTx = playerSession.receive<SignedTransaction>().unwrap { it }

            // Casino receives resolution.
            progressTracker.currentStep = PassingOnToGameResolveResponder
            val useTx = subFlow(UseFlows.Responder(
                    playerSession, PassingOnToGameResolveResponder.childProgressTracker()))

            return GameTransactions(commitTx, casinoRevealTx, playerRevealTx, useTx)
        }
    }
}