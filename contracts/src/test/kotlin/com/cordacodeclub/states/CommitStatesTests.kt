package com.cordacodeclub.states

import net.corda.core.crypto.SecureHash
import net.corda.testing.node.MockServices
import org.junit.Test
import kotlin.test.assertEquals

class CommitStatesTests {
    private val ledgerServices = MockServices()

    @Test
    fun `commit image hash is correct`() {
        assertEquals(SecureHash.zeroHash, CommitImage(5003932113484729189L, 6320170081553289949L).hash)
    }
}