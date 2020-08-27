package com.cordacodeclub.contracts

import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.GameState
import com.cordacodeclub.states.RevealedState
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
                    }
                }
                .toMultiMap()
    }

    private fun verifyCreate(tx: LedgerTransaction, create: Commands.Create, signers: List<PublicKey>,
                             outputIds: Map<UniqueIdentifier, Pair<Int, LinearState>>): StateAndRef<GameState> {
        "The output must be a GameState" using (tx.outputStates[create.outputIndex] is GameState)
        val gameState = tx.outputStates[create.outputIndex] as GameState
        "The commit ids must all be associated CommittedStates" using gameState.commitIds.all { commitId ->
            outputIds[commitId]?.let { (commitIndex, linearState) ->
                linearState is CommittedState
                        && linearState.gameOutputIndex == create.outputIndex
                        && tx.outputs[commitIndex].contract == CommitContract.id
            } ?: false
        }
        return tx.outRef(create.outputIndex)
    }

    private fun verifyResolve(tx: LedgerTransaction, resolve: Commands.Resolve, signers: List<PublicKey>,
                              inputIds: Map<UniqueIdentifier, Pair<Int, LinearState>>): StateAndRef<GameState> {
        val gameRef = tx.inputs[resolve.inputIndex]
        "The input must be a GameState" using (gameRef.state.data is GameState)
        val gameState = gameRef.state.data as GameState
        "The commit ids must all be associated RevealedStates" using gameState.commitIds.all { revealId ->
            inputIds[revealId]?.let { (revealIndex, linearState) ->
                linearState is RevealedState
                        && linearState.game.pointer == gameRef.ref
                        && tx.inRef<RevealedState>(revealIndex).state.contract == CommitContract.id
            } ?: false
        }
        return tx.inRef(resolve.inputIndex)
    }

    sealed class Commands : CommandData {
        class Create(val outputIndex: Int) : Commands()
        class Resolve(val inputIndex: Int) : Commands()
    }
}