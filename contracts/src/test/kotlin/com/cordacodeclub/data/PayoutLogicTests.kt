package com.cordacodeclub.data

import com.cordacodeclub.states.CommitImage
import net.corda.core.crypto.SecureHash
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PayoutLogicTests {

    private val random = Random()

    @Test
    fun `hash does not depend on order 2`() {
        val image1 = CommitImage.createRandom(random)
        val image2 = CommitImage.createRandom(random)

        assertEquals(
                PayoutLogic(listOf(image1, image2)).hash,
                PayoutLogic(listOf(image2, image1)).hash)
    }

    @Test
    fun `hash works for same value`() {
        val image = CommitImage(ByteArray(CommitImage.requiredLength / CommitImage.bitsInByte))

        assertEquals(
                SecureHash.parse("F5A5FD42D16A20302798EF6ED309979B43003D2320D9F0E8EA9831A92759FB4B"),
                PayoutLogic(listOf(image, image)).hash)

        assertEquals(
                SecureHash.parse("2EA9AB9198D1638007400CD2C3BEF1CC745B864B76011A0E1BC52180AC6452D4"),
                PayoutLogic(listOf(image, image, image)).hash)
    }

    @Test
    fun `can extract reel position from hash`() {
        val image = CommitImage(ByteArray(CommitImage.requiredLength / CommitImage.bitsInByte))
        val logic = PayoutLogic(listOf(image, image))

        // Those value are found in the above confirmed test, starting from the right "...59FB4B".
        assertEquals("b", logic.getReelPositionAt(16, 0).toString(16))
        assertEquals("4", logic.getReelPositionAt(16, 1).toString(16))
        assertEquals("b", logic.getReelPositionAt(16, 2).toString(16))
        assertEquals("f", logic.getReelPositionAt(16, 3).toString(16))
        assertEquals("9", logic.getReelPositionAt(16, 4).toString(16))
        assertEquals("5", logic.getReelPositionAt(16, 5).toString(16))
    }

    @Test
    fun `can get wheels positions`() {
        val image = CommitImage(ByteArray(CommitImage.requiredLength / CommitImage.bitsInByte))
        val logic = PayoutLogic(listOf(image, image))

        val expected = listOf(11, 4, 11, 15, 9, 5).toIntArray()
        val actual = logic.getReelPositions(16, 6)
        assertEquals(expected.size, actual.size)
        assertTrue(actual
                .mapIndexed { index, value -> value == expected[index] }
                .all { it })
    }

}