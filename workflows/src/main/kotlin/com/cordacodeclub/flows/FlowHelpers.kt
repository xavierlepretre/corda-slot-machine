package com.cordacodeclub.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.IdentifiableException
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.ServiceHub
import java.security.PublicKey

/**
 * Whether a key is found locally
 */
fun ServiceHub.isLocalKey(key: PublicKey) = keyManagementService.filterMyKeys(listOf(key))
        .toList()
        .isNotEmpty()

class AccountNotFoundException(message: String?, cause: Throwable?, originalErrorId: Long? = null) :
        FlowException(message, cause, originalErrorId), IdentifiableException {
    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}

class AccountAlreadyExistsException(message: String?, cause: Throwable?, originalErrorId: Long? = null) :
        FlowException(message, cause, originalErrorId), IdentifiableException {
    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}

class MoreThanOneAccountFoundException(message: String?, cause: Throwable?, originalErrorId: Long? = null) :
        FlowException(message, cause, originalErrorId), IdentifiableException {
    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}

class NoTokensForLeaderboardException(message: String?, cause: Throwable?, originalErrorId: Long? = null) :
        FlowException(message, cause, originalErrorId), IdentifiableException {
    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}

class ScoreTooLowForLeaderboardException(message: String?, cause: Throwable?, originalErrorId: Long? = null) :
        FlowException(message, cause, originalErrorId), IdentifiableException {
    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}

class NothingToRetireFromLeaderboardException(message: String?, cause: Throwable?, originalErrorId: Long? = null) :
        FlowException(message, cause, originalErrorId), IdentifiableException {
    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}

/**
 * Gets a party for the existing account known by that name. If the account is local and has no key, then a new key
 * is created.
 */
fun FlowLogic<*>.getParty(accountName: String) = serviceHub.accountService
        .accountInfo(accountName)
        .let {
            if (it.isEmpty())
                throw AccountNotFoundException("No account with this name $accountName")
            else if (1 < it.size)
                throw MoreThanOneAccountFoundException("More than 1 account found with this name $accountName")
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
                    throw AccountNotFoundException("This account is not hosted here ${account.name}")
                serviceHub.createKeyForAccount(account)
            }
        }