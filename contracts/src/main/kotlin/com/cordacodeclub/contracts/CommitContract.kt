package com.cordacodeclub.contracts

import com.cordacodeclub.states.CommittedState
import com.cordacodeclub.states.RevealedState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class CommitContract : Contract {

    companion object {
        val id = CommitContract::class.java.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for (command in commands) {
            when (command.value) {
                is Commands.Commit -> verifyCommit(tx, command.value as Commands.Commit, command.signers)
                is Commands.Reveal -> verifyReveal(tx, command.value as Commands.Reveal, command.signers)
                is Commands.Use -> verifyUse(tx, command.value as Commands.Use, command.signers)
            }
        }

        // TODO Do anything to prevent duplicate ids in inputs or outputs?
    }

    private fun verifyCommit(tx: LedgerTransaction, commit: Commands.Commit, signers: List<PublicKey>) {
        requireThat {
            "The output must be a CommitState" using (tx.outputStates[commit.outputIndex] is CommittedState)
            val committedState = tx.outputStates[commit.outputIndex] as CommittedState

            "The creator must sign" using signers.contains(committedState.creator.owningKey)
        }
    }

    private fun verifyReveal(tx: LedgerTransaction, reveal: Commands.Reveal, signers: List<PublicKey>) {
        requireThat {
            "The input must be a CommittedState" using (tx.inputs[reveal.inputIndex].state.data is CommittedState)
            val committedState = tx.inputs[reveal.inputIndex].state.data as CommittedState

            "The output must be a RevealedState" using (tx.outputStates[reveal.outputIndex] is RevealedState)
            val revealedState = tx.outputStates[reveal.outputIndex] as RevealedState

            "The linear ids must match" using (committedState.linearId == revealedState.linearId)
            "The commit image must match" using (committedState.hash == revealedState.image.hash)
            "The creator must be unchanged" using (committedState.creator == revealedState.creator)

            // No signatures required
        }
    }

    private fun verifyUse(tx: LedgerTransaction, use: Commands.Use, signers: List<PublicKey>) {
        requireThat {
            "The input must be a RevealedState" using (tx.inputs[use.inputIndex].state.data is RevealedState)
            val revealedState = tx.inputs[use.inputIndex].state.data as RevealedState

            "The creator must sign" using signers.contains(revealedState.creator.owningKey)
        }
    }

    sealed class Commands : CommandData {
        class Commit(val outputIndex: Int) : Commands()
        class Reveal(val inputIndex: Int, val outputIndex: Int) : Commands()
        class Use(val inputIndex: Int) : Commands()
    }

}