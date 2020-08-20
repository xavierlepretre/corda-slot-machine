package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.contracts.CommitContract.Commands.Commit
import com.cordacodeclub.states.CommittedState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.time.Instant

object CommitFlows {

    /**
     * In-lined flow initiated by the player.
     * It assumes the casino host is separate.
     * Its handler is [Responder].
     */
    class Initiator(
            val playerHash: SecureHash,
            val player: AbstractParty,
            val commitDeadline: Instant,
            val casinoSession: FlowSession,
            val casino: AbstractParty) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            if (casinoSession.counterparty != serviceHub.identityService.requireWellKnownPartyFromAnonymous(casino))
                throw FlowException("The casinoSession is not for the casino")
            val casinoHash = casinoSession.receive<SecureHash>().unwrap { it }

            // First notary, not caring much...
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val builder = TransactionBuilder(notary)
                    .addOutputState(CommittedState(playerHash, player, UniqueIdentifier(), listOf(player, casino)))
                    .addCommand(Command(Commit(0), player.owningKey))
                    .addOutputState(CommittedState(casinoHash, casino, UniqueIdentifier(), listOf(player, casino)))
                    .addCommand(Command(Commit(1), casino.owningKey))
                    .setTimeWindow(TimeWindow.untilOnly(commitDeadline))

            builder.verify(serviceHub)

            val partiallySignedTx = serviceHub.signInitialTransaction(builder, player.owningKey)
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx, listOf(casinoSession), listOf(player.owningKey)))
            return subFlow(FinalityFlow(fullySignedTx, casinoSession))

        }
    }

    /**
     * In-lined flow initiated by the casino.
     * Its initiator is [Initiator].
     */
    class Responder(
            val playerSession: FlowSession,
            val commitDeadline: Instant,
            val casinoHash: SecureHash,
            val casino: AbstractParty) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            playerSession.send(casinoHash)
            val fullySignedTx = subFlow(object : SignTransactionFlow(playerSession) {

                override fun checkTransaction(stx: SignedTransaction) {

                    // Only 1 command with a local key, i.e. to sign by me.
                    val myCommands = stx.tx.commands.filter { command ->
                        command.signers.any { key ->
                            // A key is local
                            serviceHub.keyManagementService.filterMyKeys(listOf(key))
                                    .toList()
                                    .isNotEmpty()
                        }
                    }
                    if (myCommands.size != 1)
                        throw FlowException("I should have only 1 command to sign")
                    val myCommand = myCommands.single().value
                    if (myCommand !is Commit)
                        throw FlowException("I should only sign a Commit command")
                    val myCommitState = stx.tx
                            .outRef<CommittedState>(myCommand.outputIndex)
                            .state.data
                    if (myCommitState.creator != casino)
                        throw FlowException("My commit state should be for casino")
                    if (myCommitState.hash != casinoHash)
                        throw FlowException("My commit state should have the hash I sent")

                    if (stx.tx.timeWindow != TimeWindow.untilOnly(commitDeadline))
                        throw FlowException("The time-window is incorrect")

                }

            })

            // TODO make the casino move on to revealing so that it can protect itself from a player that does not
            // launch a correct FinalityFlow.

            // All visible so we also get the other commit state.
            return subFlow(ReceiveFinalityFlow(playerSession, fullySignedTx.id, StatesToRecord.ALL_VISIBLE))

        }
    }

}