package com.example.executorchllamademo.adso.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The learner: goals, interests and settings. A single row (id = 0). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val name: String = "",
    val studyLevel: String = "Undergraduate",
    val goalType: String = "Exam prep",
    val goalText: String = "Get an A in Data Structures",
    val deadlineDays: Int = 30,
    val minutesPerDay: Int = 30,
    val interests: String = "Coding,Maths", // comma separated
    val language: String = "en",
    val darkTheme: Boolean = true,
    val onboarded: Boolean = false,
    val planNote: String = "",
    /** v2: pathway of the first course (kept for older installs; now stored per course). */
    val pathwayJson: String = "",
    /** v3: the course the learner is working on. */
    val activeCourse: String = "ds",
    /** v3: sampling temperature, kept between 0.7 and 0.8 (project rule). */
    val temperature: Float = 0.7f,
)

/** Current skill level per topic, the level found by the skill check, and whether the topic is done. */
@Entity(tableName = "skill")
data class SkillEntity(
    @PrimaryKey val topicId: String,
    val baseLevel: String,
    val level: String,
    /** v3: finished in AdSo (passed its quiz or reviewed). Drives the pathway's % complete. */
    val completed: Boolean = false,
)

/** One row per day of study, for the progress graph and accuracy. */
@Entity(tableName = "day_log")
data class DayLogEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val minutes: Int = 0,
    val sessions: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
)

/** v3: a course the learner added. Built-in courses store only their pathway here; custom ones store their content too. */
@Entity(tableName = "course")
data class CourseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topicsJson: String = "",
    val checkJson: String = "",
    val pathwayJson: String = "",
    val custom: Boolean = false,
)

/** v3: chat history with Llama. */
@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val updated: Long,
)

@Entity(tableName = "chat_message")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val mine: Boolean,
    val text: String,
    val meta: String = "",
    val time: Long,
)

@Dao
interface AdsoDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun profile(): ProfileEntity?

    @Upsert
    suspend fun saveProfile(p: ProfileEntity)

    @Query("SELECT * FROM skill")
    suspend fun skills(): List<SkillEntity>

    @Upsert
    suspend fun saveSkills(s: List<SkillEntity>)

    @Query("SELECT * FROM day_log WHERE date >= :from ORDER BY date")
    suspend fun logsSince(from: String): List<DayLogEntity>

    @Query("SELECT * FROM day_log WHERE date = :date")
    suspend fun log(date: String): DayLogEntity?

    @Upsert
    suspend fun saveLog(l: DayLogEntity)

    @Upsert
    suspend fun saveLogs(l: List<DayLogEntity>)

    @Query("SELECT COALESCE(SUM(answered), 0) FROM day_log")
    suspend fun totalAnswered(): Int

    @Query("SELECT COALESCE(SUM(correct), 0) FROM day_log")
    suspend fun totalCorrect(): Int

    @Query("SELECT * FROM course")
    suspend fun courses(): List<CourseEntity>

    @Upsert
    suspend fun saveCourse(c: CourseEntity)

    @Query("SELECT * FROM chat ORDER BY updated DESC")
    suspend fun chats(): List<ChatEntity>

    @Insert
    suspend fun insertChat(c: ChatEntity): Long

    @Query("UPDATE chat SET updated = :time WHERE id = :id")
    suspend fun touchChat(id: Long, time: Long)

    @Query("SELECT * FROM chat_message WHERE chatId = :chatId ORDER BY id")
    suspend fun messages(chatId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(m: ChatMessageEntity): Long

    @Query("DELETE FROM chat_message WHERE chatId = :chatId")
    suspend fun deleteMessages(chatId: Long)

    @Query("DELETE FROM chat WHERE id = :id")
    suspend fun deleteChat(id: Long)
}

@Database(
    entities = [ProfileEntity::class, SkillEntity::class, DayLogEntity::class, CourseEntity::class, ChatEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AdsoDatabase : RoomDatabase() {
    abstract fun dao(): AdsoDao

    companion object {
        /** v2 stores the Llama-written learning pathway. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN pathwayJson TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3: courses, chat history, topic completion, temperature. Keeps all existing data. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN activeCourse TEXT NOT NULL DEFAULT 'ds'")
                db.execSQL("ALTER TABLE profile ADD COLUMN temperature REAL NOT NULL DEFAULT 0.7")
                db.execSQL("ALTER TABLE skill ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `course` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `topicsJson` TEXT NOT NULL, `checkJson` TEXT NOT NULL, `pathwayJson` TEXT NOT NULL, `custom` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `updated` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chatId` INTEGER NOT NULL, `mine` INTEGER NOT NULL, `text` TEXT NOT NULL, `meta` TEXT NOT NULL, `time` INTEGER NOT NULL)")
            }
        }

        @Volatile private var instance: AdsoDatabase? = null
        fun get(context: Context): AdsoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AdsoDatabase::class.java, "adso.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
