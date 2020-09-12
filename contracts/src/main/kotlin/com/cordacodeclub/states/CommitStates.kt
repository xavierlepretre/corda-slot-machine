package com.cordacodeclub.states

import com.cordacodeclub.contracts.CommitContract
import net.corda.core.contracts.*
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
        val gameOutputIndex: Int, // Because it comes in the same tx, it cannot use a StaticPointer
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty> = listOf(creator)
) : LinearState {
    init {
        require(participants.contains(creator)) { "The creator must be a participant" }
    }
}

fun StateAndRef<CommittedState>.getGamePointer() = StaticPointer(
        StateRef(this.ref.txhash, this.state.data.gameOutputIndex),
        GameState::class.java)

@BelongsToContract(CommitContract::class)
data class RevealedState(
        val image: CommitImage,
        val creator: AbstractParty,
        val game: StaticPointer<GameState>,
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

        fun BigInteger.toProperByteArray() = toByteArray()
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

        fun createRandom(random: Random) = CommitImage(
                BigInteger(requiredLength, random).toProperByteArray())

        /**
         * @return A number that is to be understood as n out of 10,000.
         */
        fun playerPayoutCalculator(casinoImage: CommitImage, playerImage: CommitImage): Long {
            // The first number is to be understood as out of 10,000.
            // The second number is to be understood as out of 1,000.
            val percentiles = listOf(
                    3L to GameState.maxPayoutRatio + 1L, // [1, "default", 6, 6, 6, 0.0003, 200, 200],
                    18L to 50L, // [2, "default", 4, 4, 4, 0.0015, 50, 50],
                    53L to 20L, // [3, "default", 2, 2, 2, 0.0035, 20, 20],
                    98L to 15L, // [4, "default", "1/3", "5/2", "4/6", 0.0045, 15, 15],
                    153L to 13L, // [5, "default", 5, 5, 5, 0.0055, 13, 13],
                    233L to 12L, // [6, "default", 1, 1, 1, 0.008, 12, 12],
                    333L to 10L, // [7, "default", 3, 3, 3, 0.01, 10, 10],
                    1_233L to 4L, // [8, "default", "1/3/5", "1/3/5", "1/3/5", 0.09, 4, 4],
                    10_000L to 0L
            )
            val imageResult = BigInteger(SecureHash.sha256(casinoImage.picked + playerImage.picked).bytes)
                    .mod(BigInteger.valueOf(10_000L))
                    .longValueExact()
            return (percentiles
                    .firstOrNull {
                        imageResult < it.first
                    }
                    ?: percentiles.last())
                    .second
        }
    }

    init {
        require(picked.size * bitsInByte == requiredLength) {
            "There should be 256 bits"
        }
    }

    constructor(bigInt: BigInteger) : this(bigInt.toProperByteArray())

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
