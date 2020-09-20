package com.cordacodeclub.states

import com.cordacodeclub.contracts.CommitContract
import com.cordacodeclub.contracts.GameContract
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import java.time.Instant

/**
 * This class loosely couples different commits, so that they stay together, apart from the reveal step. It is:
 * - created alongside [CommittedState]s with [CommitContract.Commands.Commit].
 * - referenced when [CommitContract.Commands.Reveal].
 * - is consumed alongside all [RevealedState]s corresponding to the [CommittedState]s from creation.
 */
@BelongsToContract(GameContract::class)
data class GameState(
        val casino: CommittedBettor,
        val player: CommittedBettor,
        val commitDeadline: Instant,
        val revealDeadline: Instant,
        val lockedWagersOutputIndex: Int,
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty>) : LinearState, SchedulableState {

    companion object {
        const val maxPayoutRatio = 200L - 1L
        const val foreClosureFlowName = "com.cordacodeclub.flows.ForeClosureFlow\$SimpleInitiator"
    }

    init {
        require(participants.isNotEmpty()) { "There must be participants" }
        require(casino.issuedAmount.issuer == player.issuedAmount.issuer) { "The issuers must be the same" }
        require(casino.committer.holder != player.committer.holder) { "The holders must be different" }
        require(commitDeadline < revealDeadline) { "The commit deadline must come before the reveal one" }
        require(0 <= lockedWagersOutputIndex) { "Locked wager output index must be positive" }
        require(casino.issuedAmount.amount == player.issuedAmount.amount.times(maxPayoutRatio)) {
            "The casino and player wagers need to be proportional to maxPayoutRatio"
        }
    }

    val tokenIssuer
        get() = casino.issuedAmount.issuer

    val bettedAmount
        get() = casino.issuedAmount.amount.plus(player.issuedAmount.amount)

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return ScheduledActivity(
                flowLogicRefFactory.create(foreClosureFlowName, linearId),
                revealDeadline)
    }
}