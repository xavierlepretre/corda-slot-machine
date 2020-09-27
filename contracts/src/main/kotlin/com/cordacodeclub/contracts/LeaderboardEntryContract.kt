package com.cordacodeclub.contracts

import com.cordacodeclub.states.LeaderboardEntryState
import com.cordacodeclub.states.LockableTokenState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.time.Duration

class LeaderboardEntryContract : Contract {

    companion object {
        val id = LeaderboardEntryContract::class.java.canonicalName!!
        val maxTimeWindowRadius = Duration.ofSeconds(60)!!
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

        data class Overtake(override val inputIndex: Int,
                            override val outputIndex: Int) : Commands(), HasInput, HasOutput {
            init {
                require(0 <= inputIndex) { "Input index must be positive" }
                require(0 <= outputIndex) { "Output index must be positive" }
            }
        }

        data class Retire(override val inputIndex: Int) : Commands(), HasInput {
            init {
                require(0 <= inputIndex) { "Index must be positive" }
            }
        }
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>().map { command ->
            when (command.value) {
                is Commands.Create -> verifyCreate(tx, command.value as Commands.Create, command.signers)
                is Commands.Overtake -> verifyOvertake(tx, command.value as Commands.Overtake, command.signers)
                is Commands.Retire -> verifyRetire(tx, command.value as Commands.Retire, command.signers)
            }
            command.value
        }
        "The LeaderboardEntryContract must find at least 1 command" using commands.isNotEmpty()
        val coveredInputs = commands.filterIsInstance<HasInput>()
                .map { it.inputIndex }
        val coveredOutputs = commands.filterIsInstance<HasOutput>()
                .map { it.outputIndex }
        "All covered inputs must have no overlap" using (coveredInputs.distinct().size == coveredInputs.size)
        "All covered outputs must have no overlap" using (coveredOutputs.distinct().size == coveredOutputs.size)
        val allEntryInputs = tx.inputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is LeaderboardEntryState }
                .map { it.first }
        "All input leaderboard entry states must have an associated command" using (
                allEntryInputs.size == coveredInputs.size
                        && allEntryInputs.all { coveredInputs.contains(it) })
        val allEntryOutputs = tx.outputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is LeaderboardEntryState }
                .map { it.first }
        "All output leaderboard entry states must have an associated command" using (
                allEntryOutputs.size == coveredOutputs.size
                        && allEntryOutputs.all { coveredOutputs.contains(it) })
    }

    private fun verifyTokensMatch(tx: LedgerTransaction, createdState: LeaderboardEntryState) {
        val referencedTokensTotal = tx.referenceInputsOfType<LockableTokenState>()
                .filter { it.holder == createdState.player && it.issuer == createdState.tokenIssuer }
                .reduce { sum, lockableTokenState -> sum + lockableTokenState }
                .amount
        "The referenced tokens sum must match the entry total" using (referencedTokensTotal == createdState.total)
    }

    private fun verifyTimeWindow(tx: LedgerTransaction, createdState: LeaderboardEntryState) {
        "There must be a time-window" using (tx.timeWindow != null)
        val timeWindow = tx.timeWindow!!
        "There must be a time-window with 2 bounds" using (timeWindow.fromTime != null && timeWindow.untilTime != null)
        val fromTime = timeWindow.fromTime!!
        val untilTime = timeWindow.untilTime!!
        "The time-window bounds must not be far from the creation date" using (
                (createdState.creationDate - maxTimeWindowRadius <= fromTime)
                        && (untilTime <= createdState.creationDate + maxTimeWindowRadius))
    }

    private fun verifyCreate(tx: LedgerTransaction, create: Commands.Create, signers: List<PublicKey>) {
        val outputState = tx.outputStates[create.outputIndex]
        "The output must be a LeaderboardEntryState" using (outputState is LeaderboardEntryState)
        val createdState = outputState as LeaderboardEntryState

        verifyTokensMatch(tx, createdState)
        verifyTimeWindow(tx, createdState)

        "The created entry player must be a signer" using signers.contains(createdState.player.owningKey)
    }

    private fun verifyOvertake(tx: LedgerTransaction, overtake: Commands.Overtake, signers: List<PublicKey>) {
        val inputState = tx.inputStates[overtake.inputIndex]
        "The input must be a LeaderboardEntryState" using (inputState is LeaderboardEntryState)
        val retiredState = inputState as LeaderboardEntryState
        val outputState = tx.outputStates[overtake.outputIndex]
        "The output must be a LeaderboardEntryState" using (outputState is LeaderboardEntryState)
        val createdState = outputState as LeaderboardEntryState

        verifyTokensMatch(tx, createdState)
        verifyTimeWindow(tx, createdState)
        "The token issuer must be conserved" using (retiredState.tokenIssuer == createdState.tokenIssuer)
        "The overtaker must overtake" using (retiredState.total < createdState.total)

        "The created entry player must be a signer" using signers.contains(createdState.player.owningKey)
    }

    private fun verifyRetire(tx: LedgerTransaction, retire: Commands.Retire, signers: List<PublicKey>) {
        val inputState = tx.inputStates[retire.inputIndex]
        "The input must be a LeaderboardEntryState" using (inputState is LeaderboardEntryState)
        val retiredState = inputState as LeaderboardEntryState

        "The retired entry player must be a signer" using signers.contains(retiredState.player.owningKey)
    }

}