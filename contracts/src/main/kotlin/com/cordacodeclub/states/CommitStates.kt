package com.cordacodeclub.states

import com.cordacodeclub.contracts.CommitContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import java.math.BigInteger
import java.time.Instant
import java.util.*

@BelongsToContract(CommitContract::class)
data class CommittedState(
        val hash: SecureHash,
        val creator: AbstractParty,
        val revealDeadline: Instant,
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
data class CommitImage(val picked: ByteArray) {

    companion object {
        const val requiredLength = 256
        const val bitsInByte = 8

        fun createRandom(random: Random) = BigInteger(requiredLength, random)
                .toByteArray()
                .let {
                    // Funny things happen with extra 0s or missing 0s in high weight
                    when {
                        it.size * bitsInByte < requiredLength ->
                            ByteArray(requiredLength / bitsInByte - it.size).plus(it)
                        requiredLength < it.size * bitsInByte ->
                            it.copyOfRange((it.size - requiredLength / bitsInByte), it.size)
                        else -> it
                    }
                }
                .let { CommitImage(it) }
    }

    init {
        require(picked.size * bitsInByte == requiredLength) {
            "There should be 256 bits"
        }
    }

    val hash: SecureHash by lazy { SecureHash.sha256(picked) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommitImage

        if (!picked.contentEquals(other.picked)) return false

        return true
    }

    override fun hashCode(): Int {
        return picked.contentHashCode()
    }
}