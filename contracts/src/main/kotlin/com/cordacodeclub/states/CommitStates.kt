package com.cordacodeclub.states

import com.cordacodeclub.contracts.CommitContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable

@BelongsToContract(CommitContract::class)
data class CommittedState(
        val hash: SecureHash,
        val creator: AbstractParty,
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty> = listOf(creator)
) : LinearState {
    init {
        require(participants.contains(creator)) { "The creator must be a participant" }
    }
}

@BelongsToContract(CommitContract::class)
data class RevealedState(
        val image: CommitImage,
        val creator: AbstractParty,
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty> = listOf(creator)
) : LinearState {
    init {
        require(participants.contains(creator)) { "The creator must be a participant" }
    }
}

@CordaSerializable
data class CommitImage(val picked: Long, val salt: Long) {
    val hash: SecureHash by lazy {
        SecureHash.sha256(ByteArray(16) { index ->
            (when {
                index == 0 -> picked and 0xFF
                index < 8 -> picked ushr (index * 8) and 0xFF
                index == 8 -> salt and 0xFF
                else -> salt ushr ((index - 8) * 8) and 0xFF
            }).toByte()
        })
    }
}