package com.cordacodeclub.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.ServiceHub
import java.security.PublicKey

/**
 * Whether a key is found locally
 */
fun ServiceHub.isLocalKey(key: PublicKey) = keyManagementService.filterMyKeys(listOf(key))
        .toList()
        .isNotEmpty()

/**
 * Gets a party for the existing account known by that name. If the account is local and has no key, then a new key
 * is created.
 */
fun FlowLogic<*>.getParty(accountName: String) = serviceHub.accountService
        .accountInfo(accountName)
        .let {
            if (it.isEmpty())
                throw FlowException("No account with this name $accountName")
            else if (1 < it.size)
                throw FlowException("More than 1 account found with this name $accountName")
            getParty(it.single())
        }

fun FlowLogic<*>.getParty(accountRef: StateAndRef<AccountInfo>) = getParty(accountRef.state.data)

fun FlowLogic<*>.getParty(account: AccountInfo) = serviceHub.identityService
        .publicKeysForExternalId(account.identifier.id)
        .toList()
        .let {
            if (it.isNotEmpty()) AnonymousParty(it.first())
            else {
                if (account.host != ourIdentity)
                    throw FlowException("This account is not hosted here ${account.name}")
                serviceHub.createKeyForAccount(account)
            }
        }