package com.cordacodeclub.contracts

import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
import com.cordacodeclub.states.getGamePointer
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class CommitContract : Contract {

    companion object {
        val id = CommitContract::class.java.canonicalName!!
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

        val commands = tx.commandsOfType<Commands>()
                .map { command ->
                    when (command.value) {
                        is Commands.Commit ->
                            verifyCommit(tx, command.value as Commands.Commit, command.signers, outputIds)
                        is Commands.Reveal ->
                            verifyReveal(tx, command.value as Commands.Reveal)
                        is Commands.Use ->
                            verifyUse(tx, command.value as Commands.Use)
                        is Commands.Close -> verifyClosure(tx, command.value as Commands.Close)
                    }
                    command.value
                }
        "The CommitContract must find at least 1 command" using commands.isNotEmpty()
        val coveredInputs = commands.filterIsInstance<HasInput>()
                .map { it.inputIndex }
        val coveredOutputs = commands.filterIsInstance<HasOutput>()
                .map { it.outputIndex }
        "All covered inputs must have no overlap" using (coveredInputs.distinct().size == coveredInputs.size)
        "All covered outputs must have no overlap" using (coveredOutputs.distinct().size == coveredOutputs.size)
        val allRelevantInputs = tx.inputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is CommittedState || it.second is RevealedState }
                .map { it.first }
        "All inputs states which belong to one party must have an associated command" using (
                allRelevantInputs.size == coveredInputs.size
                        && allRelevantInputs.all { coveredInputs.contains(it) })
        val allRelevantOutputs = tx.outputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is CommittedState || it.second is RevealedState }
                .map { it.first }
        "All outputs states which belong to one party must have an associated command" using (
                allRelevantOutputs.size == coveredOutputs.size
                        && allRelevantOutputs.all { coveredOutputs.contains(it) })
    }

    private fun verifyCommit(tx: LedgerTransaction, commit: Commands.Commit, signers: List<PublicKey>,
                             outputIds: Map<UniqueIdentifier, List<LinearState>>) {
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

    private fun verifyReveal(tx: LedgerTransaction, reveal: Commands.Reveal) {
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

    private fun verifyUse(tx: LedgerTransaction, use: Commands.Use) {
        "The input must be a RevealedState" using (tx.inputs[use.inputIndex].state.data is RevealedState)
        val revealedState = tx.inputs[use.inputIndex].state.data as RevealedState

        "The game must be in input" using tx.inputs
                .map { it.ref }
                .contains(revealedState.game.pointer)
        // No signatures required as the winnings are calculated programmatically.
    }

    private fun verifyClosure(tx: LedgerTransaction, close: Commands.Close): StateAndRef<CommittedState> {
        requireThat {
            "The input must be a CommittedState" using (tx.inputs[close.inputIndex].state.data is CommittedState)
            @Suppress("UNCHECKED_CAST")
            val committedStateAndRef = tx.inputs[close.inputIndex] as StateAndRef<CommittedState>
            val committedState = committedStateAndRef.state.data
//            "The game must be referenced" using tx.referenceInputRefsOfType<GameState>()
//                    .any { it.ref == committedStateAndRef.getGamePointer().pointer }
            "The reveal deadline must be satisfied" using (tx.timeWindow?.fromTime != null
                    && committedState.revealDeadline < tx.timeWindow?.fromTime!!)
            return tx.inRef(close.inputIndex)
        }
    }

    interface HasInput {
        val inputIndex: Int
    }

    interface HasOutput {
        val outputIndex: Int
    }

    sealed class Commands : CommandData {
        class Commit(override val outputIndex: Int) : Commands(), HasOutput {
            init {
                require(0 <= outputIndex) { "Index must be positive" }
            }
        }

        class Reveal(override val inputIndex: Int, override val outputIndex: Int) : Commands(), HasInput, HasOutput {
            init {
                require(0 <= outputIndex) { "Output index must be positive" }
                require(0 <= inputIndex) { "Input index must be positive" }
            }
        }

        class Use(override val inputIndex: Int) : Commands(), HasInput {
            init {
                require(0 <= inputIndex) { "Index must be positive" }
            }
        }

        class Close(override val inputIndex: Int) : Commands(), HasInput {
            init {
                require(0 <= inputIndex) { "Index must be positive" }
            }
        }
    }

}