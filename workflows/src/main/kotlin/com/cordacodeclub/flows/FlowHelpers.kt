package com.cordacodeclub.flows

import net.corda.core.node.ServiceHub
import java.security.PublicKey

/**
 * Whether a key is found locally
 */
fun ServiceHub.isLocalKey(key: PublicKey) = keyManagementService.filterMyKeys(listOf(key))
        .toList()
        .isNotEmpty()