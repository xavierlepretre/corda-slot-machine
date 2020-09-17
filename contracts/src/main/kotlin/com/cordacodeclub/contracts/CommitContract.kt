package com.cordacodeclub.contracts

import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import com.cordacodeclub.states.getGamePointer
import net.corda.core.contracts.*
import net.corda.core.internal.toMultiMap
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class CommitContract : Contract {

    companion object {
        val id = CommitContract::class.java.canonicalName!!
        const val inputsKey = 1
        const val outputsKey = 2
    }

    override fun verify(tx: LedgerTransaction) {
        // Collect linear ids indices. Is it too restrictive as it may affect states that it does not care about.
        val inputIds = tx.inputStates
                .mapNotNull { if (it is LinearState) it else null }
                .groupBy { it.linearId }
        val outputIds = tx.outputStates
                .mapNotNull { if (it is LinearState) it else null }
                .groupBy { it.linearId }
        inputIds.forEach {
            if (it.value.size != 1)
                throw IllegalArgumentException("There is more than 1 input state with a given id")
        }
        outputIds.forEach {
            if (it.value.size != 1)
                throw IllegalArgumentException("There is more than 1 output state with a given id")
        }

        val coveredStates = tx.commandsOfType<Commands>()
                .also { require(it.isNotEmpty()) { "The CommitContract must find at least 1 command" } }
                .flatMap { command ->
                    when (command.value) {
                        is Commands.Commit ->
                            listOf(outputsKey to verifyCommit(tx, command.value as Commands.Commit, command.signers, outputIds).ref)
                        is Commands.Reveal ->
                            verifyReveal(tx, command.value as Commands.Reveal)
                                    .let { listOf(inputsKey to it.first.ref, outputsKey to it.second.ref) }
                        is Commands.Use ->
                            listOf(inputsKey to verifyUse(tx, command.value as Commands.Use, command.signers, inputIds).ref)
                    }
                }
                .toMultiMap()
        requireThat {
            "All inputs states which belong to one party must have an associated command" using tx.inputs
                    .filter { ref -> ref.state.data.let { it is CommittedState || it is RevealedState } }
                    .all { coveredStates[inputsKey]?.contains(it.ref) ?: false }
            "All outputs states which belong to one party must have an associated command" using tx.outputs
                    .mapIndexed { index, state -> index to state }
                    .filter { ref -> ref.second.data.let { it is CommittedState || it is RevealedState } }
                    .all { coveredStates[outputsKey]?.contains(StateRef(tx.id, it.first)) ?: false }
        }
    }

    private fun verifyCommit(tx: LedgerTransaction, commit: Commands.Commit, signers: List<PublicKey>,
                             outputIds: Map<UniqueIdentifier, List<LinearState>>): StateAndRef<CommittedState> {
        requireThat {
            "The output must be a CommitState" using (tx.outputStates[commit.outputIndex] is CommittedState)
            val committedState = tx.outputStates[commit.outputIndex] as CommittedState
            "The game output index must be possible" using (committedState.gameOutputIndex < tx.outputs.size
                    && 0 <= committedState.gameOutputIndex)
            "The game output must be at the right index" using
                    (tx.outputStates[committedState.gameOutputIndex] is GameState)
            val gameState = tx.outputStates[committedState.gameOutputIndex] as GameState
            "The game commit ids must all loop back" using (listOf(gameState.casino, gameState.player)
                    .map { it.committer.linearId }
                    .all { id ->
                        outputIds[id]?.let { states ->
                            (states.single() as? CommittedState)?.let {
                                it.gameOutputIndex == committedState.gameOutputIndex
                            }
                        } ?: false
                    })

            "The creator must sign" using signers.contains(committedState.creator.owningKey)

        }
        return tx.outRef(commit.outputIndex)
    }

    private fun verifyReveal(tx: LedgerTransaction, reveal: Commands.Reveal)
            : Pair<StateAndRef<CommittedState>, StateAndRef<RevealedState>> {
        requireThat {
            "The input must be a CommittedState" using (tx.inputs[reveal.inputIndex].state.data is CommittedState)
            @Suppress("UNCHECKED_CAST")
            val committedRef = tx.inputs[reveal.inputIndex] as StateAndRef<CommittedState>
            val committedState = committedRef.state.data

            "The output must be a RevealedState" using (tx.outputStates[reveal.outputIndex] is RevealedState)
            val revealedState = tx.outputStates[reveal.outputIndex] as RevealedState

            "The linear ids must match" using (committedState.linearId == revealedState.linearId)
            "The commit image must match" using (committedState.hash == revealedState.image.hash)
            "The creator must be unchanged" using (committedState.creator == revealedState.creator)
            "The game pointer must be unchanged" using (committedRef.getGamePointer() == revealedState.game)

            "The game must be referenced" using tx.referenceInputRefsOfType<GameState>()
                    .any { it.ref == committedRef.getGamePointer().pointer }

            "The reveal deadline must be satisfied" using
                    (tx.timeWindow?.untilTime != null
                            && tx.timeWindow?.untilTime!! <= committedState.revealDeadline)

            // No signatures required
        }
        return tx.inRef<CommittedState>(reveal.inputIndex) to tx.outRef(reveal.outputIndex)
    }

    private fun verifyUse(tx: LedgerTransaction, use: Commands.Use, signers: List<PublicKey>,
                          inputIds: Map<UniqueIdentifier, List<LinearState>>): StateAndRef<RevealedState> {
        requireThat {
            "The input must be a RevealedState" using (tx.inputs[use.inputIndex].state.data is RevealedState)
            val revealedState = tx.inputs[use.inputIndex].state.data as RevealedState

            "The game must be in input" using tx.inputs
                    .map { it.ref }
                    .contains(revealedState.game.pointer)

            // No signatures required as the winnings are calculated programmatically.
        }
        return tx.inRef(use.inputIndex)
    }

    sealed class Commands : CommandData {
        class Commit(val outputIndex: Int) : Commands() {
            init {
                require(0 <= outputIndex) { "Index must be positive" }
            }
        }

        class Reveal(val inputIndex: Int, val outputIndex: Int) : Commands() {
            init {
                require(0 <= outputIndex) { "Output index must be positive" }
                require(0 <= inputIndex) { "Input index must be positive" }
            }
        }

        class Use(val inputIndex: Int) : Commands() {
            init {
                require(0 <= inputIndex) { "Index must be positive" }
            }
        }
    }

}