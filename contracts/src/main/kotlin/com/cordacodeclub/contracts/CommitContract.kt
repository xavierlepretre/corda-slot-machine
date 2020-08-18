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

    sealed class Commands : CommandData {
        class Commit(val outputIndex: Int) : Commands()
    }

}