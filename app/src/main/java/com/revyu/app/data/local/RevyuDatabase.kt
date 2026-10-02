package com.revyu.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.revyu.app.data.local.dao.CalendarEventDao
import com.revyu.app.data.local.dao.ExamAttemptDao
import com.revyu.app.data.local.dao.FlashcardDao
import com.revyu.app.data.local.dao.HomeWidgetPrefDao
import com.revyu.app.data.local.dao.QuestionDao
import com.revyu.app.data.local.dao.ScheduleDao
import com.revyu.app.data.local.dao.StudyLoadDao
import com.revyu.app.data.local.dao.StudyMaterialDao
import com.revyu.app.data.local.dao.StudySetDao
import com.revyu.app.data.local.dao.SubjectDao
import com.revyu.app.data.local.entities.CalendarEventEntity
import com.revyu.app.data.local.entities.ExamAttemptEntity
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.data.local.entities.HomeWidgetPrefEntity
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.local.entities.ScheduleEntryEntity
import com.revyu.app.data.local.entities.StudyLoadEntity
import com.revyu.app.data.local.entities.StudyMaterialEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.SubjectEntity

@Database(
    entities = [
        SubjectEntity::class,
        ScheduleEntryEntity::class,
        StudyMaterialEntity::class,
        StudySetEntity::class,
        FlashcardEntity::class,
        QuestionEntity::class,
        ExamAttemptEntity::class,
        CalendarEventEntity::class,
        StudyLoadEntity::class,
        HomeWidgetPrefEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RevyuDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun studyMaterialDao(): StudyMaterialDao
    abstract fun studySetDao(): StudySetDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun questionDao(): QuestionDao
    abstract fun examAttemptDao(): ExamAttemptDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun studyLoadDao(): StudyLoadDao
    abstract fun homeWidgetPrefDao(): HomeWidgetPrefDao

    companion object {
        @Volatile private var INSTANCE: RevyuDatabase? = null

        /**
         * v1 -> v2: Smart Calendar. Subjects/schedule gain study-load fidelity columns and
         * three new tables back the calendar, import history, and the home widget grid.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN subjectCode TEXT")
                db.execSQL("ALTER TABLE subjects ADD COLUMN catalogCode TEXT")
                db.execSQL("ALTER TABLE subjects ADD COLUMN units INTEGER")
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN room TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_events` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `subjectId` TEXT,
                        `type` TEXT NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `startMinute` INTEGER NOT NULL,
                        `endMinute` INTEGER NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `subjectCode` TEXT,
                        `room` TEXT,
                        `note` TEXT,
                        `generationGroupId` TEXT,
                        `isCustom` INTEGER NOT NULL,
                        `sourceStudyLoadId` TEXT,
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_subjectId` ON `calendar_events` (`subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_dayOfWeek` ON `calendar_events` (`dayOfWeek`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_generationGroupId` ON `calendar_events` (`generationGroupId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_loads` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `fileName` TEXT NOT NULL,
                        `institution` TEXT,
                        `semester` TEXT,
                        `academicYear` TEXT,
                        `course` TEXT,
                        `yearLevel` TEXT,
                        `section` TEXT,
                        `studentName` TEXT,
                        `unitTotal` INTEGER NOT NULL,
                        `subjectCount` INTEGER NOT NULL,
                        `importedAt` INTEGER NOT NULL
                    )
                    """
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `home_widget_prefs` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `enabled` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """
                )
            }
        }

        /**
         * v2 -> v3: School Calendar. Adds an optional one-off date to calendar events so
         * dated (non-recurring) entries can live alongside the weekly schedule; null keeps
         * weekly behavior identical.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN eventDate TEXT")
            }
        }

        fun getInstance(context: Context): RevyuDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RevyuDatabase::class.java,
                    "revyu.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Safety net for as-yet-unforeseen future schema moves during active
                    // development; the real migration above is what users actually hit.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
