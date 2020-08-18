package com.cordacodeclub.states

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable

data class CommitState(val hash: SecureHash, val creator: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> = listOf(creator)
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