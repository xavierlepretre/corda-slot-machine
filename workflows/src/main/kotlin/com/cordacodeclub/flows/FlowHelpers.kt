package com.cordacodeclub.flows

import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.ServiceHub
import java.security.PublicKey

/**
 * Whether a key is found locally
 */
fun ServiceHub.isLocalKey(key: PublicKey) = keyManagementService.filterMyKeys(listOf(key))
        .toList()
        .isNotEmpty()

fun FlowLogic<*>.getParty(accountName: String) = serviceHub.accountService
        .accountInfo(accountName)
        .let {
            if (it.isEmpty())
                throw FlowException("No account with this name $accountName")
            else if (1 < it.size)
                throw FlowException("More than 1 account found with this name $accountName")
            it.single()
        }
        .state.data
        .let {accountInfo ->
            serviceHub.identityService.publicKeysForExternalId(accountInfo.identifier.id)
                    .toList()
                    .let {
                        if (it.isNotEmpty()) AnonymousParty(it.first())
                        else {
                            if (accountInfo.host != ourIdentity)
                                throw FlowException("This account is not hosted here $accountName")
                            serviceHub.createKeyForAccount(accountInfo)
                        }
                    }
        }
