package com.cordacodeclub.contracts

import com.cordacodeclub.states.*
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class GameContract : Contract {

    companion object {
        val id = GameContract::class.java.canonicalName!!
    }

    override fun verify(tx: LedgerTransaction) {
        // Collect linear ids indices. Is it too restrictive as it may affect states that it does not care about.
        val inputIds = tx.inputs
                .mapIndexedNotNull { index, state ->
                    (state.state.data as? LinearState)?.let { index to it }
                }
                .groupBy { (_, state) -> state.linearId }
                .mapValues {
                    if (it.value.size != 1)
                        throw IllegalArgumentException("There is more than 1 input state with a given id")
                    it.value.single()
                }
        val outputIds = tx.outputStates
                .mapIndexedNotNull { index, state ->
                    (state as? LinearState)?.let { index to it }
                }
                .groupBy { (_, state) -> state.linearId }
                .mapValues {
                    if (it.value.size != 1)
                        throw IllegalArgumentException("There is more than 1 output state with a given id")
                    it.value.single()
                }

        val commands = tx.commandsOfType<Commands>().map { command ->
            when (command.value) {
                is Commands.Create -> verifyCreate(tx, command.value as Commands.Create, command.signers, outputIds)
                is Commands.Resolve -> verifyResolve(tx, command.value as Commands.Resolve, inputIds)
                is Commands.Close -> verifyClose(tx, command.value as Commands.Close)
            }
            command.value
        }
        "The GameContract must find at least 1 command" using commands.isNotEmpty()
        val coveredInputs = commands.filterIsInstance<HasInput>()
                .map { it.inputIndex }
        val coveredOutputs = commands.filterIsInstance<HasOutput>()
                .map { it.outputIndex }
        "All covered inputs must have no overlap" using (coveredInputs.distinct().size == coveredInputs.size)
        "All covered outputs must have no overlap" using (coveredOutputs.distinct().size == coveredOutputs.size)
        val allGameInputs = tx.inputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is GameState }
                .map { it.first }
        "All input game states must have an associated command" using (
                allGameInputs.size == coveredInputs.size
                        && allGameInputs.all { coveredInputs.contains(it) })
        val allGameOutputs = tx.outputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is GameState }
                .map { it.first }
        "All output game states must have an associated command" using (
                allGameOutputs.size == coveredOutputs.size
                        && allGameOutputs.all { coveredOutputs.contains(it) })
    }

    private fun verifyCreate(tx: LedgerTransaction, create: Commands.Create, signers: List<PublicKey>,
                             outputIds: Map<UniqueIdentifier, Pair<Int, LinearState>>) {
        "The output must be a GameState" using (tx.outputStates[create.outputIndex] is GameState)
        val gameRef = tx.outRef<GameState>(create.outputIndex)
        val gameState = gameRef.state.data
        val associatedCommits = listOf(gameState.casino, gameState.player)
                .map { outputIds[it.committer.linearId] }
        "The commit deadline must be satisfied" using (tx.timeWindow
                ?.untilTime
                ?.let { gameState.commitDeadline <= it }
                ?: false)
        "The commit ids must all be associated CommittedStates" using associatedCommits.all { pair ->
            pair?.let { (commitIndex, linearState) ->
                linearState is CommittedState
                        && linearState.gameOutputIndex == create.outputIndex
                        && tx.outputs[commitIndex].contract == CommitContract.id
            } ?: false
        }
        "The game bettors must all have commits" using (associatedCommits
                .mapNotNull { (it?.second as? CommittedState)?.creator }
                .distinct()
                .size == 2)
        "The game bettors must be signers" using (listOf(gameState.casino, gameState.player)
                .map { it.committer.holder.owningKey }
                .toSet() == signers.toSet())
        "The output locked token index must be possible" using (gameState.lockedWagersOutputIndex < tx.outputs.size)
        val lockedToken = tx.getOutput(gameState.lockedWagersOutputIndex)
                .also { "There must be a LockableTokenState at the output index" using (it is LockableTokenState) }
                .let { it as LockableTokenState }
        "The output locked token must be locked" using lockedToken.isLocked
        "The output locked token must have the same issuer as the game" using (lockedToken.issuer == gameState.tokenIssuer)
        "The output locked token must have the right amount" using (lockedToken.amount == gameState.bettedAmount)
        val lockedTokenRef = tx.outRef<LockableTokenState>(gameState.lockedWagersOutputIndex)
        "The output locked token and the game must be mutually encumbered" using
                (gameRef.state.encumbrance == gameState.lockedWagersOutputIndex
                        && lockedTokenRef.state.encumbrance == create.outputIndex)
    }

    private fun verifyResolve(tx: LedgerTransaction, resolve: Commands.Resolve,
                              inputIds: Map<UniqueIdentifier, Pair<Int, LinearState>>) {
        val gameRef = tx.inputs[resolve.inputIndex]
        "The input must be a GameState" using (gameRef.state.data is GameState)
        val gameState = gameRef.state.data as GameState
        "The commit ids must all be associated RevealedStates" using listOf(gameState.casino, gameState.player)
                .map { it.committer.linearId }
                .all { revealId ->
                    inputIds[revealId]?.let { (revealIndex, linearState) ->
                        linearState is RevealedState
                                && linearState.game.pointer == gameRef.ref
                                && tx.inRef<RevealedState>(revealIndex).state.contract == CommitContract.id
                    } ?: false
                }
        val (casinoImage, playerImage) = listOf(gameState.casino, gameState.player)
                .map { it.committer.linearId }
                .mapNotNull { inputIds[it]?.second as? RevealedState }
                .map { it.image }
                .also { "There should be 2 images " using (it.size == 2) }
        val expectedPlayerPayout = Math.multiplyExact(
                gameState.player.issuedAmount.amount.quantity,
                CommitImage.playerPayoutCalculator(casinoImage, playerImage))
        val actualPayoutCalculator = { holder: AbstractParty ->
            tx.outputStates
                    .filterIsInstance<LockableTokenState>()
                    .filter { it.holder == holder && it.issuer == gameState.tokenIssuer }
                    .takeIf { it.isNotEmpty() }
                    ?.reduce(LockableTokenState::plus)
                    ?.amount
                    ?.quantity
                    ?: 0L
        }
        val actualCasinoPayout = actualPayoutCalculator(gameState.casino.committer.holder)
        val actualPlayerPayout = actualPayoutCalculator(gameState.player.committer.holder)
        "The player payout should be correct" using (actualPlayerPayout == expectedPlayerPayout)
        "The casino payout should be correct" using
                (actualCasinoPayout == (gameState.bettedAmount.quantity - expectedPlayerPayout))
    }

    private fun verifyClose(tx: LedgerTransaction, close: Commands.Close) {
        val gameRef = tx.inputs[close.inputIndex]
        "The input must be a GameState" using (gameRef.state.data is GameState)
    }

    interface HasInput {
        val inputIndex: Int
    }

    interface HasOutput {
        val outputIndex: Int
    }

    sealed class Commands : CommandData {
        data class Create(override val outputIndex: Int) : Commands(), HasOutput {
            init {
                require(0 <= outputIndex) { "Index must be positive" }
            }
        }

        data class Resolve(override val inputIndex: Int) : Commands(), HasInput {
            init {
                require(0 <= inputIndex) { "Index must be positive" }
            }
        }

        data class Close(override val inputIndex: Int) : Commands(), HasInput {
            init {
                require(0 <= inputIndex) { "Index must be positive" }
            }
        }
    }
}