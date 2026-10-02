package com.revyu.app.di

import android.content.Context
import com.revyu.app.core.preferences.AppSettingsStore
import com.revyu.app.core.util.FileTextExtractor
import com.revyu.app.core.util.PdfReviewerRenderer
import com.revyu.app.data.local.RevyuDatabase
import com.revyu.app.data.remote.ApiKeyManager
import com.revyu.app.data.remote.ApiKeyProvider
import com.revyu.app.data.remote.OpenRouterClient
import com.revyu.app.BuildConfig
import com.revyu.app.core.util.MaterialExtractor
import com.revyu.app.data.remote.MarkItDownClient
import com.revyu.app.data.repository.CalendarEventRepository
import com.revyu.app.data.repository.GenerationRepository
import com.revyu.app.data.repository.SettingsRepository
import com.revyu.app.data.repository.SchoolCalendarRepository
import com.revyu.app.data.repository.SmartCalendarRepository
import com.revyu.app.data.repository.StudyLoadRepository
import com.revyu.app.data.repository.StudyMaterialRepository
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import com.revyu.app.data.repository.WidgetDataRepository

/**
 * Simple hand-written service locator instead of Hilt/Dagger.
 *
 * Every dependency graph problem here is a plain Kotlin constructor call you can read
 * top to bottom — no annotation processor, no generated code, no KSP version to keep in
 * lockstep with the Kotlin/AGP versions. For a one-person project on a deadline, that
 * trade (a little more boilerplate here, a lot less "why won't this compile") is worth it.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: RevyuDatabase by lazy { RevyuDatabase.getInstance(appContext) }

    val apiKeyProvider: ApiKeyProvider by lazy { ApiKeyProvider() }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext, apiKeyProvider)
    }

    init {
        // SettingsRepository's constructor is what pushes the persisted key into the
        // in-memory ApiKeyProvider. Without touching it here, a cold start that goes
        // straight to generation would see apiKey == null even when a key is saved.
        settingsRepository
    }

    private val openRouterApi by lazy {
        OpenRouterClient.create(apiKeyProvider, debug = true)
    }

    val fileTextExtractor: FileTextExtractor by lazy { FileTextExtractor(appContext) }

    val networkObserver: com.revyu.app.core.util.NetworkObserver by lazy {
        com.revyu.app.core.util.NetworkObserver(appContext)
    }
        val markItDownClient: MarkItDownClient by lazy {
        MarkItDownClient(
            baseUrl = BuildConfig.MARKITDOWN_BASE_URL,
            apiKey = BuildConfig.MARKITDOWN_API_KEY,
            debug = BuildConfig.DEBUG
        )
    }

    /** Routes uploads: PPTX to the MarkItDown server (on-device fallback), the rest on-device. */
    val materialExtractor: MaterialExtractor by lazy {
        MaterialExtractor(appContext, fileTextExtractor, markItDownClient, networkObserver)
    }
    val pdfReviewerRenderer: PdfReviewerRenderer by lazy { PdfReviewerRenderer(appContext) }

    val subjectRepository: SubjectRepository by lazy {
        SubjectRepository(database.subjectDao(), database.scheduleDao(), database.studySetDao())
    }

    val studyMaterialRepository: StudyMaterialRepository by lazy {
        StudyMaterialRepository(database.studyMaterialDao(), materialExtractor)
    }

    val studySetRepository: StudySetRepository by lazy {
        StudySetRepository(
            studySetDao = database.studySetDao(),
            flashcardDao = database.flashcardDao(),
            questionDao = database.questionDao(),
            examAttemptDao = database.examAttemptDao(),
            materialDao = database.studyMaterialDao()
        )
    }

    val generationRepository: GenerationRepository by lazy {
        GenerationRepository(
            api = openRouterApi,
            studySetRepository = studySetRepository,
            flashcardDao = database.flashcardDao(),
            questionDao = database.questionDao(),
            pdfRenderer = pdfReviewerRenderer
        )
    }

    val smartCalendarRepository: SmartCalendarRepository by lazy {
        SmartCalendarRepository(subjectRepository, studySetRepository)
    }

    val apiKeyManager: ApiKeyManager by lazy { ApiKeyManager(appContext) }

    val appSettingsStore: AppSettingsStore by lazy { AppSettingsStore(appContext) }

    val calendarEventRepository: CalendarEventRepository by lazy {
        CalendarEventRepository(
            database = database,
            calendarEventDao = database.calendarEventDao(),
            subjectDao = database.subjectDao(),
            scheduleDao = database.scheduleDao(),
            onDataChanged = ::widgetDataChanged
        )
    }

    val studyLoadRepository: StudyLoadRepository by lazy {
        StudyLoadRepository(
            database = database,
            subjectDao = database.subjectDao(),
            scheduleDao = database.scheduleDao(),
            studyLoadDao = database.studyLoadDao(),
            onDataChanged = ::widgetDataChanged
        )
    }

    val schoolCalendarOcr: com.revyu.app.core.util.schoolcalendar.SchoolCalendarOcr by lazy {
        com.revyu.app.core.util.schoolcalendar.SchoolCalendarOcr(appContext)
    }

    val schoolCalendarRepository: SchoolCalendarRepository by lazy {
        SchoolCalendarRepository(
            database = database,
            calendarEventDao = database.calendarEventDao(),
            onDataChanged = ::widgetDataChanged
        )
    }

    val widgetDataRepository: WidgetDataRepository by lazy {
        WidgetDataRepository(
            database = database,
            calendarEventDao = database.calendarEventDao(),
            homeWidgetPrefDao = database.homeWidgetPrefDao(),
            studyLoadDao = database.studyLoadDao(),
            studySetDao = database.studySetDao(),
            flashcardDao = database.flashcardDao(),
            questionDao = database.questionDao(),
            examAttemptDao = database.examAttemptDao(),
            subjectDao = database.subjectDao()
        )
    }

    /** Kept in the AppContainer so repositories never touch WorkManager directly. */
    private fun widgetDataChanged() {
        com.revyu.app.work.WorkScheduler.refreshWidgetsNow(appContext)
    }
}
