package com.cordacodeclub.states

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty

data class CommitState(val hash: SecureHash, val creator: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> = listOf(creator)
}

data class CommitImage(val picked: Long, val salt: Long) {
    val hash: SecureHash

    init {Long.MAX_VALUE

        hash = SecureHash.sha256(ByteArray(16) { index ->
            if (index == 0) (picked and 0xFF).toByte()
            else if (index < 8) ((picked ushr (index * 8)) and 0xFF).toByte()
            else if (index == 9) (salt and 0xFF).toByte()
            else ((salt ushr ((index - 8) * 8)) and 0xFF).toByte()
        })
    }
}