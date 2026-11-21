package com.focusguard.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packages: String = "",
    val allowMode: Boolean = false,
    val strict: Boolean = false,
    val emoji: String = "📚"
) {
    fun pkgSet(): Set<String> = packages.split(',').filter { it.isNotBlank() }.toSet()
}

@Entity(tableName = "sessions")
data class SessionRec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val taskId: Long = 0,
    val startAt: Long,
    val endAt: Long = 0,
    val plannedMs: Long,
    val focusedMs: Long = 0,
    val completed: Boolean = false
)

@Entity(tableName = "attempts")
data class AttemptRec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val pkg: String,
    val ts: Long,
    val reason: String
)

@Entity(tableName = "daily_usage", primaryKeys = ["day", "pkg"])
data class DailyUsage(val day: String, val pkg: String, val ms: Long, val launches: Int)

@Entity(tableName = "tasks")
data class TaskRec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val priority: Int = 1, // 0 low, 1 medium, 2 high
    val due: Long = 0,     // epoch day, 0 = none
    val done: Boolean = false,
    val doneAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val focusMs: Long = 0
)

/** repeatMask: bit n (1..7, Monday=1) set = repeats on that weekday; 0 = one-off on epochDay. */
@Entity(tableName = "plan_blocks")
data class PlanBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val epochDay: Long,
    val startMin: Int,
    val endMin: Int,
    val repeatMask: Int = 0,
    val profileId: Long = 0
)

@Entity(tableName = "limits")
data class LimitRec(@PrimaryKey val pkg: String, val limitMs: Long)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id") fun all(): Flow<List<Profile>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun get(id: Long): Profile?
    @Upsert suspend fun upsert(p: Profile): Long
    @Delete suspend fun delete(p: Profile)
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionRec): Long
    @Query("UPDATE sessions SET endAt = :endAt, focusedMs = :focused, completed = :completed WHERE id = :id")
    suspend fun finish(id: Long, endAt: Long, focused: Long, completed: Boolean)
    @Query("SELECT * FROM sessions WHERE startAt BETWEEN :from AND :to ORDER BY startAt")
    fun between(from: Long, to: Long): Flow<List<SessionRec>>
}

@Dao
interface AttemptDao {
    @Insert suspend fun insert(a: AttemptRec)
    @Query("SELECT * FROM attempts WHERE ts BETWEEN :from AND :to") fun between(from: Long, to: Long): Flow<List<AttemptRec>>
}

@Dao
abstract class UsageDao {
    @Query("SELECT * FROM daily_usage WHERE day BETWEEN :from AND :to")
    abstract fun between(from: String, to: String): Flow<List<DailyUsage>>
    @Query("SELECT COUNT(*) FROM daily_usage WHERE day = :day") abstract suspend fun countDay(day: String): Int
    @Query("DELETE FROM daily_usage WHERE day = :day") abstract suspend fun deleteDay(day: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAll(rows: List<DailyUsage>)
    @Transaction
    open suspend fun replaceDay(day: String, rows: List<DailyUsage>) {
        deleteDay(day)
        insertAll(rows)
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done, priority DESC, CASE WHEN due = 0 THEN 99999999 ELSE due END, id")
    fun all(): Flow<List<TaskRec>>
    @Upsert suspend fun upsert(t: TaskRec): Long
    @Delete suspend fun delete(t: TaskRec)
    @Query("UPDATE tasks SET focusMs = focusMs + :ms WHERE id = :id") suspend fun addFocus(id: Long, ms: Long)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_blocks ORDER BY startMin") fun all(): Flow<List<PlanBlock>>
    @Query("SELECT * FROM plan_blocks") suspend fun allOnce(): List<PlanBlock>
    @Query("SELECT * FROM plan_blocks WHERE id = :id") suspend fun get(id: Long): PlanBlock?
    @Upsert suspend fun upsert(b: PlanBlock): Long
    @Delete suspend fun delete(b: PlanBlock)
}

@Dao
interface LimitDao {
    @Query("SELECT * FROM limits") fun all(): Flow<List<LimitRec>>
    @Query("SELECT * FROM limits") suspend fun allOnce(): List<LimitRec>
    @Upsert suspend fun upsert(l: LimitRec)
    @Query("DELETE FROM limits WHERE pkg = :pkg") suspend fun delete(pkg: String)
}

@Database(
    entities = [Profile::class, SessionRec::class, AttemptRec::class, DailyUsage::class,
        TaskRec::class, PlanBlock::class, LimitRec::class],
    version = 1, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun attemptDao(): AttemptDao
    abstract fun usageDao(): UsageDao
    abstract fun taskDao(): TaskDao
    abstract fun planDao(): PlanDao
    abstract fun limitDao(): LimitDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "focusguard.db")
                .fallbackToDestructiveMigration().build().also { inst = it }
        }
    }
}
