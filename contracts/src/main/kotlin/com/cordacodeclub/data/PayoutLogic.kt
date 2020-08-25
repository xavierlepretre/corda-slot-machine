package com.cordacodeclub.data

import com.cordacodeclub.states.CommitImage
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.ByteSequence
import java.math.BigInteger

object PayoutLogic {

    data class GameData(val modulo: Long, val payouts: List<Pair<Int, Int>>) {
        init {
            require(modulo >= payouts.map { it.first }.count()) { "Sum of probabilities should be less than or equal to 1" }
        }
    }

    private fun getRandomInteger(images: List<CommitImage>, modulo: Long): Int {
        val minImageCount = 2
        require(minImageCount <= images.size) { "There needs to be at least $minImageCount images" }

        // this is a way to merge the numbers
        val hash: SecureHash = images.map { it.picked }
                // why this sort? I'm only worried because it reduces the randomness of the result
                .sortedWith(Comparator { left, right ->
                    ByteSequence.of(left).compareTo(ByteSequence.of(right))
                })
                .reduce(ByteArray::plus)
                .let(SecureHash.Companion::sha256)

        val bigNumber = BigInteger(hash.bytes.let {
            it.set(0, 0) // Removing the sign bit for easier testing
            it
        })

        // mod will discard high-order bytes -- hope that lower-order bytes are sufficiently random/reachable
        return bigNumber.mod(BigInteger.valueOf(modulo)).toInt()
    }

    /*
      Here is the game data as it's encoded in the JavaScript -- the payouts here must match the payouts defined there.
      In JavaScript the probabilities are doubles (up to 1.0) -- here I recode those as integers up to 10,000.

        const rawDbData = [
          [1, "default", 6, 6, 6, 0.0003, 200, 200],
          [2, "default", 4, 4, 4, 0.0015, 50, 50],
          [3, "default", 2, 2, 2, 0.0035, 20, 20],
          [4, "default", "1/3", "5/2", "4/6", 0.0045, 15, 15],
          [5, "default", 5, 5, 5, 0.0055, 13, 13],
          [6, "default", 1, 1, 1, 0.008, 12, 12],
          [7, "default", 3, 3, 3, 0.01, 10, 10],
          [8, "default", "1/3/5", "1/3/5", "1/3/5", 0.09, 4, 4],
        ];
     */

    val gameData = GameData(10000, listOf(Pair(3, 200), Pair(15, 50), Pair(35, 20), Pair(45, 15), Pair(55, 13), Pair(80, 12), Pair(100, 10), Pair(900, 4)))

    fun calculatePayout(images: List<CommitImage>): Int {
        var random = getRandomInteger(images, gameData.modulo);
        for (pair in gameData.payouts) {
            if (random < pair.first) return pair.second
            random -= pair.first;
        }
        return 0;
    }
}
