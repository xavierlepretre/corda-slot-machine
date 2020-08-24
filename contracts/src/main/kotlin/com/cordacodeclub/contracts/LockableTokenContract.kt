package com.cordacodeclub.contracts

import com.cordacodeclub.contracts.LockableTokenContract.Commands.*
import com.cordacodeclub.states.LockableTokenState
import com.cordacodeclub.states.LockableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements.using
import net.corda.core.internal.toMultiMap
import net.corda.core.transactions.LedgerTransaction

class LockableTokenContract : Contract {

    companion object {
        val id = LockableTokenContract::class.java.canonicalName
        val inputsKey = 1
        val outputsKey = 2
    }

    interface HasInputs {
        val inputIndices: List<Int>
    }

    interface HasOutputs {
        val outputIndices: List<Int>
    }

    sealed class Commands : CommandData {
        class Issue(override val outputIndices: List<Int>) : Commands(), HasOutputs {
            init {
                require(outputIndices.isNotEmpty()) { "Issue must have outputs" }
            }
        }

        class Lock(override val inputIndices: List<Int>,
                   override val outputIndices: List<Int>) : Commands(), HasInputs, HasOutputs {
            init {
                require(inputIndices.isNotEmpty()) { "Lock must have inputs" }
                require(outputIndices.isNotEmpty()) { "Lock must have outputs" }
            }
        }

        class Release(override val inputIndices: List<Int>,
                      override val outputIndices: List<Int>) : Commands(), HasInputs, HasOutputs {
            init {
                require(inputIndices.isNotEmpty()) { "Release must have inputs" }
                require(outputIndices.isNotEmpty()) { "Release must have outputs" }
            }
        }

        class Redeem(override val inputIndices: List<Int>,
                     override val outputIndices: List<Int>) : Commands(), HasInputs, HasOutputs {
            init {
                require(inputIndices.isNotEmpty()) { "Redeem must have inputs" }
                // There may be no outputs.
            }
        }
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()

        val coveredLockableStates = commands.flatMap { command ->

            // Common tests
            val rawInputs = (command.value as? HasInputs)
                    ?.inputIndices
                    ?.map { tx.inputStates[it] }
                    ?: listOf()
            val rawOutputs = (command.value as? HasOutputs)
                    ?.outputIndices
                    ?.map { tx.outputStates[it] }
                    ?: listOf()
            "The inputs must be lockable token states" using rawInputs.all { it is LockableTokenState }
            "The outputs must be lockable token states" using rawOutputs.all { it is LockableTokenState }
            val inputs = rawInputs.filterIsInstance<LockableTokenState>()
            val outputs = rawOutputs.filterIsInstance<LockableTokenState>()
            // The inputs can have 0 values
            "The outputs must have positive amounts" using outputs.all { 0 < it.amount.quantity }
            val inputIssuers = inputs.map { it.issuer }.distinct()
            val outputIssuers = outputs.map { it.issuer }.distinct()
            val inputSum = inputs.map { it.amount }
                    .takeIf { it.isNotEmpty() }
                    ?.reduceRight { left, right -> left + right }
                    ?: Amount.zero(LockableTokenType)
            val outputSum = outputs.map { it.amount }
                    .takeIf { it.isNotEmpty() }
                    ?.reduceRight { left, right -> left + right }
                    ?: Amount.zero(LockableTokenType)
            val lockedInputSum = inputs.filter { it.isLocked }
                    .map { it.amount }
                    .takeIf { it.isNotEmpty() }
                    ?.reduce { left, right -> left + right }
                    ?: Amount.zero(LockableTokenType)
            val lockedOutputSum = outputs.filter { it.isLocked }
                    .map { it.amount }
                    .takeIf { it.isNotEmpty() }
                    ?.reduceRight { left, right -> left + right }
                    ?: Amount.zero(LockableTokenType)

            when (command.value) {
                is Issue -> {
                    // No check on inputs issuer.
                    "The outputs must have a single issuer" using (outputIssuers.size == 1)
                    // No check on same input and output issuers.
                    // No check on lock status of inputs.
                    "The outputs must be unlocked" using outputs.all { !it.isLocked }
                    // No check on sums.
                    // No check on locked sums.
                    // No check on holder signatures.
                    "The issuer must sign" using command.signers.contains(outputIssuers.single().owningKey)
                }
                is Lock -> {
                    "The inputs must have a single issuer" using (inputIssuers.size == 1)
                    "The outputs must have a single issuer" using (outputIssuers.size == 1)
                    "The input and output issuers must be the same" using (inputIssuers.single() == outputIssuers.single())
                    "There must be unlocked inputs" using inputs.any { !it.isLocked }
                    "There must be locked outputs" using outputs.any { it.isLocked }
                    "The sums must be unchanged" using (inputSum == outputSum)
                    "The locked sums must increase" using (lockedInputSum < lockedOutputSum)
                    "The unlocked input holders must sign" using command.signers
                            .containsAll(inputs.mapNotNull { it.holder?.owningKey })
                    // No check on issuer signatures.
                }
                is Release -> {
                    "The inputs must have a single issuer" using (inputIssuers.size == 1)
                    "The outputs must have a single issuer" using (outputIssuers.size == 1)
                    "The input and output issuers must be the same" using (inputIssuers.single() == outputIssuers.single())
                    "There must be locked inputs" using inputs.any { it.isLocked }
                    "There must be unlocked outputs" using outputs.any { !it.isLocked }
                    "The sums must be unchanged" using (inputSum == outputSum)
                    "The locked sums must decrease" using (lockedOutputSum < lockedInputSum)
                    "The unlocked input holders must sign" using command.signers
                            .containsAll(inputs.mapNotNull { it.holder?.owningKey })
                    // No check on issuer signatures.
                }
                is Redeem -> {
                    "The inputs must have a single issuer" using (inputIssuers.size == 1)
                    "The outputs must have at most one issuer" using (outputIssuers.size <= 1)
                    "The input and output issuers must be the same" using (outputIssuers.isEmpty()
                            || inputIssuers.single() == outputIssuers.single())
                    "The inputs must be unlocked" using inputs.all { !it.isLocked }
                    "The outputs must be unlocked" using outputs.all { !it.isLocked }
                    "The sums must decrease or be zero" using (inputSum == Amount.zero(LockableTokenType)
                            || outputSum < inputSum)
                    // No check on locked sums.
                    "The input holders must sign" using command.signers
                            .containsAll(inputs.mapNotNull { it.holder?.owningKey })
                    "The input issuer must sign" using command.signers
                            .contains(inputIssuers.single().owningKey)
                }
            }

            val inputPairs = (command.value as? HasInputs)?.inputIndices
                    ?.map { inputsKey to it }
                    ?: listOf()
            val outputPairs = (command.value as? HasOutputs)?.outputIndices
                    ?.map { outputsKey to it }
                    ?: listOf()

            inputPairs.plus(outputPairs)
        }.groupBy({ it.first }) { it.second }

        val coveredInputs = coveredLockableStates[inputsKey] ?: listOf()
        val coveredOutputs = coveredLockableStates[outputsKey] ?: listOf()
        "All covered token inputs must have no overlap" using (coveredInputs.distinct().size == coveredInputs.size)
        "All covered token outputs must have no overlap" using (coveredOutputs.distinct().size == coveredOutputs.size)
        val allLockableInputs = tx.inputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is LockableTokenState }
                .map { it.first }
        "All lockable token inputs must be covered by a command" using (
                coveredInputs.size == allLockableInputs.size
                        && coveredInputs.containsAll(allLockableInputs))
        val allLockableOutputs = tx.outputStates
                .mapIndexed { index, state -> index to state }
                .filter { it.second is LockableTokenState }
                .map { it.first }
        "All lockable token outputs must be covered by a command" using (
                coveredOutputs.size == allLockableOutputs.size
                        && coveredOutputs.containsAll(allLockableOutputs))
    }
}