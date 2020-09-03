package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.contracts.GameContract.Commands.Create
import com.cordacodeclub.contracts.LockableTokenContract.Commands.Lock
import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * Flows to create states with the hashes to which the game participants commit.
 */
object CommitFlows {

    /**
     * In-lined flow initiated by the player.
     * It assumes the casino host is separate.
     * Its handler is [Responder].
     */
    class Initiator(
            val notary: Party,
            val playerHash: SecureHash,
            val setup: GameFlows.GameSetup,
            val playerTokens: List<StateAndRef<LockableTokenState>>,
            val casinoSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val (player, playerWager, issuer, casino,
                    commitDeadline, revealDeadline) = setup
            if (casinoSession.counterparty != serviceHub.identityService.wellKnownPartyFromAnonymous(casino)
                    ?: throw FlowException("Cannot resolve the casino host"))
                throw FlowException("The casinoSession is not for the casino")
            val casinoHash = casinoSession.receive<SecureHash>().unwrap { it }
            val playerCommitId = UniqueIdentifier()
            val casinoCommitId = UniqueIdentifier()
            val builder = TransactionBuilder(notary)
                    .addOutputState(CommittedState(playerHash, setup.player, revealDeadline, 2,
                            playerCommitId, listOf(setup.player)))
                    .addCommand(Command(Commit(0), setup.player.owningKey))
                    .addOutputState(CommittedState(casinoHash, casino, revealDeadline, 2,
                            casinoCommitId, listOf(setup.player, casino)))
                    .addCommand(Command(Commit(1), casino.owningKey))
                    .addOutputState(GameState(setup.casinoCommittedBettor(casinoCommitId),
                            setup.playerCommittedBettor(playerCommitId), 3,
                            UniqueIdentifier(), listOf(setup.player, casino)))
                    .addCommand(Create(2), listOf(setup.casino.owningKey, setup.player.owningKey))
                    .addOutputState(LockableTokenState(issuer, Amount(setup.totalBetted, LockableTokenType),
                            listOf(casino, player)))
                    .setTimeWindow(TimeWindow.untilOnly(commitDeadline))

            // Add player tokens.
            val playerChange = playerTokens
                    .map { it.state.data.amount }
                    .reduce(Amount<LockableTokenType>::plus)
                    .minus(Amount(playerWager, LockableTokenType))
            val playerTokenInputIndices = playerTokens.mapIndexed { index, state ->
                builder.addInputState(state)
                index
            }
            val playerTokenOutputIndices = if (Amount(0L, LockableTokenType) < playerChange) {
                builder.addOutputState(LockableTokenState(player, issuer, playerChange))
                listOf(3, 4)
            } else listOf(3)
            subFlow(SendStateAndRefFlow(casinoSession, playerTokens))
            // Add casino tokens.
            val casinoTokens = subFlow(ReceiveStateAndRefFlow<LockableTokenState>(casinoSession))
            val casinoChange = casinoTokens
                    .map { it.state.data.amount }
                    .reduce(Amount<LockableTokenType>::plus)
                    .minus(Amount(Math.multiplyExact(playerWager, GameParameters.casinoToPlayerRatio), LockableTokenType))
            val currentInputCount = builder.inputStates().size
            val currentOutputCount = builder.outputStates().size
            val casinoTokenInputIndices = casinoTokens.mapIndexed { index, state ->
                builder.addInputState(state)
                currentInputCount + index
            }
            val casinoTokenOutputIndices = if (Amount(0L, LockableTokenType) < casinoChange) {
                builder.addOutputState(LockableTokenState(casino, issuer, casinoChange))
                listOf(currentOutputCount)
            } else listOf()
            builder.addCommand(Lock(playerTokenInputIndices + casinoTokenInputIndices,
                    playerTokenOutputIndices + casinoTokenOutputIndices),
                    listOf(player.owningKey, casino.owningKey))

            builder.verify(serviceHub)
            val partiallySignedTx = serviceHub.signInitialTransaction(builder, setup.player.owningKey)
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx, listOf(casinoSession), listOf(setup.player.owningKey)))
            return subFlow(FinalityFlow(fullySignedTx, casinoSession))
        }
    }

    /**
     * In-lined flow initiated by the casino.
     * Its initiator is [Initiator].
     */
    class Responder(
            val playerSession: FlowSession,
            val setup: GameFlows.GameSetup,
            val casinoHash: SecureHash,
            val casinoTokens: List<StateAndRef<LockableTokenState>>) : FlowLogic<SignedTransaction>() {

        companion object {
            const val minCommits = 2
        }

        @Suspendable
        override fun call(): SignedTransaction {
            if (playerSession.counterparty != serviceHub.identityService.wellKnownPartyFromAnonymous(setup.player)
                    ?: throw FlowException("Cannot resolve the player host"))
                throw FlowException("The playerSession is not for the player")
            playerSession.send(casinoHash)
            subFlow(ReceiveStateAndRefFlow<LockableTokenState>(playerSession))
            subFlow(SendStateAndRefFlow(playerSession, casinoTokens))
            val casinoChange = casinoTokens
                    .map { it.state.data.amount }
                    .reduce(Amount<LockableTokenType>::plus)
                    .minus(Amount(Math.multiplyExact(setup.playerWager, GameParameters.casinoToPlayerRatio),
                            LockableTokenType))
            val fullySignedTx = subFlow(object : SignTransactionFlow(playerSession) {

                override fun checkTransaction(stx: SignedTransaction) {
                    // Only 3 commands with a local key, i.e. to sign by me.
                    val myCommands = stx.tx.commands.filter {
                        it.signers.any(serviceHub::isLocalKey)
                    }
                    if (myCommands.size != 3)
                        throw FlowException("I should have only 3 commands to sign")

                    val myCommitCommand = myCommands
                            .mapNotNull { it.value as? Commit }
                            .singleOrNull()
                            ?: throw FlowException("I should have only 1 Commit command to sign")
                    val myCommitState = stx.tx
                            .outRef<CommittedState>(myCommitCommand.outputIndex)
                            .state.data
                    if (myCommitState.creator != setup.casino)
                        throw FlowException("My commit state should be for casino")
                    if (myCommitState.hash != casinoHash)
                        throw FlowException("My commit state should have the hash I sent")
                    if (stx.tx.outRefsOfType<CommittedState>().size < minCommits)
                        throw FlowException("There should be at least $minCommits commits")
                    if (stx.tx.outputsOfType(CommittedState::class.java).any { it.revealDeadline != setup.revealDeadline })
                        throw FlowException("One CommittedState does not have the correct reveal deadline")
                    if (stx.tx.timeWindow != TimeWindow.untilOnly(setup.commitDeadline))
                        throw FlowException("The time-window is incorrect")

                    val myCreateCommand = myCommands
                            .mapNotNull { it.value as? Create }
                            .singleOrNull()
                            ?: throw FlowException("I should have only 1 Create command to sign")
                    if (stx.tx.outRef<ContractState>(myCreateCommand.outputIndex).state.data !is GameState)
                        throw FlowException("The create output index should have a GameState")
                    val gameStates = stx.tx.outputsOfType<GameState>()
                    if (gameStates.size != 1)
                        throw FlowException("There should be exactly 1 GameState, not ${gameStates.size}")
                    val gameState = gameStates.single()
                    if (gameState.player.committer.holder != setup.player)
                        throw FlowException("The game player should be the expected player")
                    if (gameState.player.issuedAmount.issuer != setup.issuer)
                        throw FlowException("The game player issuer should be the expected issuer")
                    if (gameState.casino.committer.holder != setup.casino)
                        throw FlowException("The game casino should be the expected casino")
                    if (gameState.casino.issuedAmount.issuer != setup.issuer)
                        throw FlowException("The game casino issuer should be the expected issuer")
                    if (gameState.casino.issuedAmount.amount.quantity !=
                            Math.multiplyExact(gameState.player.issuedAmount.amount.quantity, GameParameters.casinoToPlayerRatio))
                        throw FlowException("The casino amount should be the expected ratio with the player amount")

                    val myLockCommand = myCommands
                            .mapNotNull { it.value as? Lock }
                            .singleOrNull()
                            ?: throw FlowException("I should have only 1 Lock command to sign")
                    val myStates = myLockCommand.inputIndices
                            .map {
                                try {
                                    serviceHub.toStateAndRef<LockableTokenState>(stx.tx.inputs[it])
                                } catch (e: ClassCastException) {
                                    throw FlowException(e)
                                }
                            }
                            .filter { ref ->
                                ref.state.data
                                        .also { if (it.isLocked) throw FlowException("No input token should be locked") }
                                        .let { serviceHub.isLocalKey(it.holder!!.owningKey) }
                            }
                            .toSet()
                    if (myStates != casinoTokens.toSet())
                        throw FlowException("Unexpected tokens of casino in inputs")
                    val myChange = myLockCommand.outputIndices
                            .map { stx.tx.outputStates[it] }
                            .filterIsInstance<LockableTokenState>()
                            .filter { it.holder == setup.casino }
                    if (0 < casinoChange.quantity) {
                        if (myChange.size != 1) throw FlowException("Casino expected some change")
                        if (myChange.single().amount != casinoChange)
                            throw FlowException("Casino change is not the expected amount")
                    }

                    // TODO Adds checks on the other commit state, as per the game state?
                }
            })

            // TODO make the casino move on to revealing so that it can protect itself from a player that does not
            // launch a correct FinalityFlow.

            // All visible so we also record the other commit state.
            return subFlow(ReceiveFinalityFlow(playerSession, fullySignedTx.id, StatesToRecord.ALL_VISIBLE))
        }
    }
}