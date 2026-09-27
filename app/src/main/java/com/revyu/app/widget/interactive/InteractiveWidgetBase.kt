package com.revyu.app.widget.interactive

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.revyu.app.MainActivity
import com.revyu.app.RevyuApplication
import com.revyu.app.work.WorkScheduler
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FLIP
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_NEXT
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PICK_OPTION
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PREV
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_REBUILD
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_RESTART
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_SUBMIT
import com.revyu.app.widget.interactive.InteractiveWidgetActions.EXTRA_OPTION_INDEX
import com.revyu.app.widget.interactive.InteractiveWidgetActions.INTERACTIVE_ACTIONS
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shared base for the four interactive launcher widgets (Flashcard, Quiz, Q&A, Exam).
 *
 * Follows the schedule widget's ColorOS lessons: onUpdate always commits a valid RemoteViews
 * synchronously (a plain "Loading…" placeholder) so the host never sees a widget with no
 * views, then the real content is painted right after. User taps are delivered as explicit
 * broadcast actions to the provider, which mutates the persisted per-widget session (pure
 * state machine, no DB on ordinary taps) and re-commits the widget synchronously.
 */
abstract class InteractiveWidgetBase : AppWidgetProvider() {

    abstract val sessionPrefix: String

    /** Build a fresh session from Room. Suspends — expected to be cheap-ish and rare. */
    abstract suspend fun buildSession(appContext: Context, widgetId: Int): PracticeWidgetSession?

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val renderer get() = PracticeWidgetRenderer
    private val store get() = PracticeWidgetSessionStore
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    protected fun sessionKey(widgetId: Int) = store.sessionKey(sessionPrefix, widgetId)

    override fun onEnabled(context: Context) {
        WorkScheduler.refreshWidgetsNow(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Always commit a placeholder synchronously — same reason as the schedule widget.
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, renderer.renderEmpty(context))
        }
        WorkScheduler.refreshWidgetsNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        WorkScheduler.refreshWidgetsNow(context, force = true)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { store.clear(context, sessionKey(it)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in INTERACTIVE_ACTIONS) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId <= 0) return
        val option = intent.getIntExtra(EXTRA_OPTION_INDEX, -1)
        val pendingResult = goAsync()
        scope.launch {
            try {
                handleAction(context, widgetId, action, option)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, widgetId: Int, action: String, option: Int) {
        val manager = AppWidgetManager.getInstance(context)
        when (action) {
            ACTION_REBUILD, ACTION_RESTART -> {
                store.clear(context, sessionKey(widgetId))
                rebuildAndRender(context, manager, widgetId)
            }
            ACTION_SUBMIT -> {
                val session = store.load(context, sessionKey(widgetId)) as? PracticeWidgetSession.Exam
                    ?: return
                val finished = PracticeWidgetSessionFlow.apply(session, ACTION_SUBMIT, -1) as PracticeWidgetSession.Exam
                runCatching { submitExamToDb(context, finished) }
                store.save(context, sessionKey(widgetId), finished)
                commit(context, manager, widgetId, finished)
            }
            else -> {
                val existing = store.load(context, sessionKey(widgetId))
                if (existing == null) {
                    rebuildAndRender(context, manager, widgetId)
                    return
                }
                val updated = PracticeWidgetSessionFlow.apply(existing, action, option)
                if (updated != existing) store.save(context, sessionKey(widgetId), updated)
                commit(context, manager, widgetId, updated)
            }
        }
    }

    /** Load-or-build the session, re-picking on a new day for non-exam widgets. */
    suspend fun renderWidget(appContext: Context, manager: AppWidgetManager, widgetId: Int) {
        val key = sessionKey(widgetId)
        val existing = store.load(appContext, key)
        val today = LocalDate.now().format(dateFmt)
        val stale = existing != null &&
            existing.pickedOn != today &&
            existing !is PracticeWidgetSession.Exam

        val session = if (existing == null || stale) {
            buildSession(appContext, widgetId)?.also { store.save(appContext, key, it) }
        } else {
            existing
        }

        if (session == null) {
            store.clear(appContext, key)
            manager.updateAppWidget(widgetId, renderer.renderEmpty(appContext))
        } else {
            commit(appContext, manager, widgetId, session)
        }
    }

    private suspend fun rebuildAndRender(context: Context, manager: AppWidgetManager, widgetId: Int) {
        renderWidget(context, manager, widgetId)
    }

    private suspend fun submitExamToDb(context: Context, finished: PracticeWidgetSession.Exam) {
        val container = (context.applicationContext as RevyuApplication).container
        val attempt = container.studySetRepository.getExamAttempt(finished.attemptId) ?: return
        container.studySetRepository.submitExamAttempt(
            attempt,
            PracticeWidgetSessionFlow.buildExamAnswers(finished)
        )
    }

    private suspend fun commit(context: Context, manager: AppWidgetManager, widgetId: Int, session: PracticeWidgetSession) {
        val model = renderer.map(context, session)
        val intents = buildIntents(context, widgetId, session, model)
        manager.updateAppWidget(widgetId, renderer.render(context, model, intents))
    }

    private fun buildIntents(
        context: Context,
        widgetId: Int,
        session: PracticeWidgetSession,
        model: RenderModel
    ): ClickIntents {
        fun pi(action: String, slot: Int, option: Int = -1): PendingIntent {
            val intent = Intent(context, this@InteractiveWidgetBase::class.java).apply {
                setAction(action)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                if (option >= 0) putExtra(EXTRA_OPTION_INDEX, option)
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 100 + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun open(): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_STUDY_SET_ID, session.studySetId)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 100 + 50,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val body = if (session is PracticeWidgetSession.Flashcard) pi(ACTION_FLIP, 5) else null
        val prev = if (model.prevVisible) pi(ACTION_PREV, 2) else null
        val primary = model.primaryAction?.let { pi(it, 3) }
        val options = model.options.indices.map { pi(ACTION_PICK_OPTION, 10 + it, it) }

        return ClickIntents(
            body = body,
            prev = prev,
            primary = primary,
            open = open(),
            options = options
        )
    }

    companion object {
        val PROVIDERS = listOf(
            FlashcardWidgetProvider::class.java,
            QuestionWidgetProvider::class.java,
            QnAWidgetProvider::class.java,
            ExamWidgetProvider::class.java
        )

        /** Re-render every placed instance of every interactive widget. */
        suspend fun refreshAll(appContext: Context, manager: AppWidgetManager) {
            PROVIDERS.forEach { cls ->
                val ids = manager.getAppWidgetIds(ComponentName(appContext, cls))
                if (ids.isEmpty()) return@forEach
                val provider = try {
                    cls.getDeclaredConstructor().newInstance() as InteractiveWidgetBase
                } catch (e: Exception) {
                    return@forEach
                }
                ids.forEach { id ->
                    runCatching { provider.renderWidget(appContext, manager, id) }
                }
            }
        }
    }
}