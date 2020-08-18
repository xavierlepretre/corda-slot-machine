package com.cordacodeclub.states

import net.corda.core.crypto.SecureHash
import net.corda.testing.node.MockServices
import org.junit.Test
import kotlin.test.assertEquals

class CommitStatesTests {
    private val ledgerServices = MockServices()

    @Test
    fun `all bits are in the hash`() {
        val leftMostBit = 0x4000000000000000L
        val allHashes = mutableSetOf<SecureHash>()

        for (i in 0..63) {
            allHashes.add(CommitImage(0L, leftMostBit shr i).hash)
            allHashes.add(CommitImage(leftMostBit shr i, 0L).hash)
        }

        // The 0, 0 was duplicated.
        assertEquals(127, allHashes.size)
    }
}