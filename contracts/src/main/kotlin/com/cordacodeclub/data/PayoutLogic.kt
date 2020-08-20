package com.cordacodeclub.data

import com.cordacodeclub.states.CommitImage
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.ByteSequence
import java.math.BigInteger

class PayoutLogic(val images: List<CommitImage>) {

    companion object {
        const val minImageCount = 2
        const val maxReelPositionCount = 256 // That uses 8 bits
        const val maxReelIndex = 31 // There are only 248 bits available
    }

    init {
        require(2 <= images.size) { "There needs to be at least $minImageCount images" }
    }

    val hash: SecureHash by lazy {
        images.map { it.picked }
                .sortedWith(Comparator { left, right ->
                    ByteSequence.of(left).compareTo(ByteSequence.of(right))
                })
                .reduce(ByteArray::plus)
                .let(SecureHash.Companion::sha256)
    }

    fun getReelPositionAt(positionCount: Int, reelIndex: Int): Int {
        require(positionCount <= maxReelPositionCount) { "positionCount must be $maxReelPositionCount or less" }
        require(reelIndex <= maxReelIndex) { "reelIndex must be $maxReelIndex or less" }

        return BigInteger(hash.bytes
                .let {
                    it.set(0, 0) // Removing the sign bit for easier testing
                    it
                })
                .divide(BigInteger.valueOf(positionCount.toLong())
                        .pow(reelIndex))
                .mod(BigInteger.valueOf(positionCount.toLong()))
                .toInt()
    }

    fun getReelPositions(positionCount: Int, reelCount: Int) = (0 until reelCount)
            .map { getReelPositionAt(positionCount, it) }
            .toIntArray()

    fun getPayout(positionCount: Int, reelCount: Int) : Long{
        // TODO do better
        val visiblePositions = setOf(getReelPositions(positionCount, reelCount))
        if (visiblePositions.size == 1) return 10_000L
        if (visiblePositions.size == 2) return 1_000L
        return 0L
    }

}