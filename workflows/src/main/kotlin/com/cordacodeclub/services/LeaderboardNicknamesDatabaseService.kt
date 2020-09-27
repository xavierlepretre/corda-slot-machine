package com.cordacodeclub.services

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import java.util.*

/**
 * A database service subclass for handling a table of leaderboard nicknames.
 *
 * @param services The node's service hub.
 */
@CordaService
class LeaderboardNicknamesDatabaseService(services: ServiceHub) : DatabaseService(services) {
    init {
        setUpStorage()
    }

    companion object {
        const val TABLE_NAME = "leaderboard_nicknames"
        const val COL_LINEAR_ID = "linear_id"
        const val COL_NICKNAME = "nickname"
        const val minNicknameLength = 6
        const val maxNicknameLength = 24
        val nicknameRegex = Regex("^[0-9_a-zA-Z]+$")
    }

    fun isValidNickname(nickname: String) = nicknameRegex.matches(nickname)
            && nickname.length.let { (minNicknameLength <= it) && (it <= maxNicknameLength) }

    fun addLeaderboardNickname(linearId: UUID, nickname: String) {
        if (!isValidNickname(nickname))
            throw IllegalArgumentException("Nickname $nickname is invalid")
        val query = "INSERT INTO $TABLE_NAME VALUES(?, ?)"
        val params = mapOf(1 to linearId.toString(), 2 to nickname)
        val rows = executeUpdate(query, params)
        if (rows != 1) {
            "Failed to insert $linearId / $nickname".also {
                log.error(it)
                throw RuntimeException(it)
            }
        }
        log.info("Inserted $linearId / $nickname.")
    }

    fun hasNickname(linearId: UUID): Boolean {
        val query = "SELECT COUNT($COL_LINEAR_ID) AS rowCount FROM $TABLE_NAME WHERE $COL_LINEAR_ID = ?"
        val params = mapOf(1 to linearId.toString())
        val results = executeQuery(query, params) { it -> it.getInt("rowCount") }
        if (results.isEmpty()) {
            throw IllegalArgumentException("Wrong query.")
        }
        val count = results.single()
        log.info("Nickname $linearId read from nicknames table.")
        return count == 1
    }

    fun getNickname(linearId: UUID): String {
        val query = "SELECT $COL_NICKNAME FROM $TABLE_NAME WHERE $COL_LINEAR_ID = ?"
        val params = mapOf(1 to linearId.toString())
        val results = executeQuery(query, params) { it -> it.getString(COL_NICKNAME) }
        if (results.isEmpty()) {
            throw IllegalArgumentException("LinearId $linearId not present in database.")
        }
        val nickname = results.single()
        log.info("Nickname $linearId read from nicknames table.")
        return nickname
    }

    fun deleteNickname(linearId: UUID) {
        val query = "DELETE FROM $TABLE_NAME WHERE $COL_LINEAR_ID = ? LIMIT 1"
        val params = mapOf(1 to linearId.toString())
        val rows = executeUpdate(query, params)
        if (rows != 1) {
            "Failed to delete $linearId".also {
                log.error(it)
                throw RuntimeException(it)
            }
        }
        log.info("Deleted $linearId.")
    }

    private fun setUpStorage() {
        val query = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME(
                $COL_LINEAR_ID VARCHAR(36) NOT NULL,
                $COL_NICKNAME VARCHAR($maxNicknameLength),
                CONSTRAINT PK_$TABLE_NAME PRIMARY KEY ($COL_LINEAR_ID)
            )"""

        executeUpdate(query, emptyMap())
        log.info("Created crypto_values table.")
    }
}

val ServiceHub.leaderboardNicknamesDatabaseService
    get() = cordaService(LeaderboardNicknamesDatabaseService::class.java)