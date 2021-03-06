package com.cordacodeclub.webserver

import com.cordacodeclub.flows.*
import com.cordacodeclub.flows.LockableTokenFlows.Fetch.NotEnoughTokensException
import com.cordacodeclub.states.LeaderboardEntryState
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(Controller::class.java)
    }

    private val proxy = rpc.proxy
    private val quickConfig = proxy.startFlow(::GetNotaryAndCasino)
            .returnValue.getOrThrow()

    @Suppress("unused")
    @PostMapping(value = ["/create"], produces = ["text/plain"])
    private fun create(request: HttpServletRequest): ResponseEntity<String> {
        val name = request.getParameter("name")
        logger.debug("Create user $name")
        return try {
            val player = proxy.startFlow(UserAccountFlows.Create::Initiator, name)
                    .returnValue.getOrThrow()
                    .second
            proxy.startFlow(LockableTokenFlows.Issue::InitiatorBeg,
                    LockableTokenFlows.Issue.Request(quickConfig.notary, player, quickConfig.casinoHost))
                    .returnValue.getOrThrow()
            val balance = proxy.startFlow(LockableTokenFlows.Balance::Local, player, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(balance.toString())
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you need to reset")
        } catch (error: AccountAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Account $name already exists")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @Suppress("unused")
    @GetMapping(value = ["/balance"], produces = ["text/plain"])
    private fun balance(@RequestParam(value = "name") name: String): ResponseEntity<String> {
        logger.debug("Get balance for user $name")
        return try {
            val balance = proxy.startFlow(LockableTokenFlows.Balance::SimpleLocal, name, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(balance.toString())
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you need to reset")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @Suppress("unused")
    @PostMapping(value = ["/spin"], produces = ["application/json"])
    private fun spin(request: HttpServletRequest): ResponseEntity<Any> {
        val name = request.getParameter("name")
        val wager = request.getParameter("wager").toLong()
        logger.debug("Spin for user $name with $wager")
        return try {
            val result = proxy.startFlow(GameFlows::SimpleInitiator, quickConfig.notaryName,
                    name, wager, quickConfig.casinoHost, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            val balance = proxy.startFlow(LockableTokenFlows.Balance::SimpleLocal, name, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(SpinResult(result).copy(balance = balance, last_win = result.payout_credits))
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you may want to reset")
        } catch (error: NotEnoughTokensException) {
            val balance = proxy.startFlow(LockableTokenFlows.Balance::SimpleLocal, name, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Not enough tokens to spin for $wager. Balance: $balance")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @Suppress("unused")
    @PostMapping(value = ["/enterLeaderboard"], produces = ["application/json"])
    private fun enterLeaderboard(request: HttpServletRequest): ResponseEntity<Any> {
        val name = request.getParameter("name")
        val nickname = request.getParameter("nickname")
        logger.debug("Enter leaderboard user $name as $nickname")
        return try {
            val createTx = proxy.startFlow(LeaderboardFlows.Create::SimpleInitiator,
                    name, nickname, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            createTx.tx.outputsOfType<LeaderboardEntryState>().single()
            ResponseEntity.ok(LeaderboardEntryResult())
        } catch (error: NullPointerException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("name or nickname not found")
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you may want to reset")
        } catch (error: NoTokensForLeaderboardException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error.message)
        } catch (error: ScoreTooLowForLeaderboardException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error.message)
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @Suppress("unused")
    // I wanted to use Delete but it getParameter to null
    @PostMapping(value = ["/leaveLeaderboard"], produces = ["application/json"])
    private fun leaveLeaderboard(request: HttpServletRequest): ResponseEntity<Any> {
        val name = request.getParameter("name")!!
        logger.debug("Leave leaderboard for user $name")
        return try {
            proxy.startFlow(LeaderboardFlows.Retire::SimpleInitiator, name)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(LeaderboardLeaveResult())
        } catch (error: NullPointerException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("name not found")
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you may want to reset")
        } catch (error: NothingToRetireFromLeaderboardException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error.message)
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @Suppress("unused")
    @GetMapping(value = ["/leaderboard"], produces = ["application/json"])
    private fun getLeaderboard(@Suppress("UNUSED_PARAMETER") request: HttpServletRequest): ResponseEntity<Any> {
        val name = request.getParameter("name")
        logger.debug("List leaderboard by user $name")
        return try {
            val leaderboardEntries = proxy.startFlow(LeaderboardFlows.Fetch::Local,
                    quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            val me = name?.let {
                proxy.startFlow(UserAccountFlows.Get::Local, it).returnValue.getOrThrow()
            }
            ResponseEntity.ok(Leaderboard.fromNamedEntries(leaderboardEntries, me))
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    // the following are just for testing, not used in production

    @Suppress("unused")
    @GetMapping(value = ["/test"], produces = ["text/plain"])
    private fun test(): String {
        return "Test OK"
    }

    @Suppress("unused")
    @PostMapping(value = ["/echo"], produces = ["text/plain"])
    private fun echo(request: HttpServletRequest): ResponseEntity<String> {
        val payload = request.getParameter("payload")
        return ResponseEntity.ok("Echo $payload")
    }

    @Suppress("unused")
    @PostMapping(value = ["/payout"], produces = ["text/plain"])
    private fun spinPayout(request: HttpServletRequest): ResponseEntity<String> {
        val name = request.getParameter("name")
        val wager = request.getParameter("wager").toLong()
        return try {
            val result = proxy.startFlow(GameFlows::SimpleInitiator, quickConfig.notaryName, name,
                    wager, quickConfig.casinoHost, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            // returns a single simple element from the GameResult
            ResponseEntity.ok("Created ${result.payout_credits}")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

}