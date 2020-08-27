package com.cordacodeclub.states

import com.cordacodeclub.contracts.CommitContract
import com.cordacodeclub.contracts.GameContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty

/**
 * This class loosely couples different commits, so that they stay together, apart from the reveal step. It is:
 * - created alongside [CommittedState]s with [CommitContract.Commands.Commit].
 * - referenced when [CommitContract.Commands.Reveal].
 * - is consumed alongside all [RevealedState]s corresponding to the [CommittedState]s from creation.
 */
@BelongsToContract(GameContract::class)
data class GameState(
        val commitIds: List<UniqueIdentifier>,
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty>) : LinearState {
}