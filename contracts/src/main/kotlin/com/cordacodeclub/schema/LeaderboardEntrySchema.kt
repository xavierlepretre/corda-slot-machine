package com.cordacodeclub.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * The family of schemas for LeaderboardEntryState.
 */
object LeaderboardEntrySchema

/**
 * A LeaderboardEntryState schema.
 */
object LeaderboardEntrySchemaV1 : MappedSchema(
        schemaFamily = LeaderboardEntrySchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentLeaderboardEntry::class.java)) {

    const val COL_PLAYER = "player"
    const val COL_TOTAL = "total"
    const val COL_TOKEN_ISSUER = "token_issuer"
    const val COL_CREATION = "creation"

    @Entity
    @Table(name = "leaderboard_entry_states",
            indexes = [
                Index(name = "leaderboard_entry_player_idx", columnList = COL_PLAYER),
                Index(name = "leaderboard_entry_token_issuer_idx", columnList = COL_TOKEN_ISSUER)
            ])
    class PersistentLeaderboardEntry(
            @Column(name = COL_PLAYER, nullable = false)
            @Type(type = "corda-wrapper-binary")
            var player: ByteArray,
            @Column(name = COL_TOTAL, nullable = false)
            var total: Long,
            @Column(name = COL_TOKEN_ISSUER, nullable = false)
            @Type(type = "corda-wrapper-binary")
            var tokenIssuer: ByteArray,
            @Column(name = COL_CREATION, nullable = false)
            val creation: Instant) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this(ByteArray(0), 0, ByteArray(0), Instant.now())
    }
}