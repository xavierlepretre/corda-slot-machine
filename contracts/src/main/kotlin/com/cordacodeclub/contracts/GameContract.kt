package com.cordacodeclub.contracts

import com.cordacodeclub.states.*
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.internal.toMultiMap
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class GameContract : Contract {

    companion object {
        val id = GameContract::class.java.canonicalName
        const val inputsKey = 1
        const val outputsKey = 2
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

        val coveredStates = tx.commandsOfType<Commands>()
                .also { require(it.isNotEmpty()) { "The GameContract must find at least 1 command" } }
                .flatMap { command ->
                    when (command.value) {
                        is Commands.Create ->
                            listOf(outputsKey to
                                    verifyCreate(tx, command.value as Commands.Create, command.signers, outputIds).ref)
                        is Commands.Resolve ->
                            listOf(inputsKey to
                                    verifyResolve(tx, command.value as Commands.Resolve, command.signers, inputIds).ref)
                        is Commands.Close -> {
                            listOf(0 to verifyClose(tx))
                        }
                    }
                }
                .toMultiMap()
        requireThat {
            "All input game states must have an associated command" using tx.inputs
                    .filter { ref -> ref.state.data is GameState }
                    .all { coveredStates[inputsKey]?.contains(it.ref) ?: false }
            "All output game states must have an associated command" using tx.outputs
                    .mapIndexedNotNull { index, state ->
                        (state.data as? GameState)?.let { index }
                    }
                    .all { coveredStates[outputsKey]?.contains(StateRef(tx.id, it)) ?: false }
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, create: Commands.Create, signers: List<PublicKey>,
                             outputIds: Map<UniqueIdentifier, Pair<Int, LinearState>>): StateAndRef<GameState> {
        "The output must be a GameState" using (tx.outputStates[create.outputIndex] is GameState)
        val gameRef = tx.outRef<GameState>(create.outputIndex)
        val gameState = gameRef.state.data
        val associatedCommits = listOf(gameState.casino, gameState.player)
                .map { outputIds[it.committer.linearId] }
        "The commit ids must all be associated CommittedStates" using associatedCommits.all { pair ->
            pair?.let { (commitIndex, linearState) ->
                linearState is CommittedState
                        && linearState.gameOutputIndex == create.outputIndex
                        && tx.outputs[commitIndex].contract == CommitContract.id
            } ?: false
        }
        "The commits must all have the same reveal deadline" using (associatedCommits
                .mapNotNull { (it?.second as? CommittedState)?.revealDeadline }
                .distinct()
                .size == 1)
        "The game bettors must all have commits" using (associatedCommits
                .mapNotNull { (it?.second as? CommittedState)?.creator }
                .distinct()
                .size == 2)
        "The game bettors must be signers" using listOf(gameState.casino, gameState.player)
                .map { it.committer.holder.owningKey }
                .toSet()
                .equals(signers.toSet())
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

        return tx.outRef(create.outputIndex)
    }

    private fun verifyResolve(tx: LedgerTransaction, resolve: Commands.Resolve, signers: List<PublicKey>,
                              inputIds: Map<UniqueIdentifier, Pair<Int, LinearState>>): StateAndRef<GameState> {
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
        val actualCasinoPayout = tx.outputStates
                .filterIsInstance<LockableTokenState>()
                .filter { it.holder == gameState.casino.committer.holder && it.issuer == gameState.tokenIssuer }
                .takeIf { it.isNotEmpty() }
                ?.reduce(LockableTokenState::plus)
                ?.amount
                ?.quantity
                ?: 0L
        val actualPlayerPayout = tx.outputStates
                .filterIsInstance<LockableTokenState>()
                .filter { it.holder == gameState.player.committer.holder && it.issuer == gameState.tokenIssuer }
                .takeIf { it.isNotEmpty() }
                ?.reduce(LockableTokenState::plus)
                ?.amount
                ?.quantity
                ?: 0L
        "The player payout should be correct" using (actualPlayerPayout == expectedPlayerPayout)
        "The casino payout should be correct" using
                (actualCasinoPayout == (gameState.bettedAmount.quantity - expectedPlayerPayout))
        return tx.inRef(resolve.inputIndex)
    }

    private fun verifyClose(tx: LedgerTransaction) {

    }

    sealed class Commands : CommandData {
        class Create(val outputIndex: Int) : Commands()
        class Resolve(val inputIndex: Int) : Commands()
        object Close : Commands()
    }
}