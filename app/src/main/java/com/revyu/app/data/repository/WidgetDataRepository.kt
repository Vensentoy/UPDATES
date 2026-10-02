package com.revyu.app.data.repository

import androidx.room.withTransaction
import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.core.widget.DailyFlashcardData
import com.revyu.app.core.widget.DailyQuestionData
import com.revyu.app.core.widget.ExamReminder
import com.revyu.app.core.widget.ExamCountdownRow
import com.revyu.app.core.widget.ExamOccurrence
import com.revyu.app.core.widget.EventRowKind
import com.revyu.app.core.widget.FLASHCARD_SEED
import com.revyu.app.core.widget.NextRow
import com.revyu.app.core.widget.NowCard
import com.revyu.app.core.widget.PinOption
import com.revyu.app.core.widget.PracticeReadyRow
import com.revyu.app.core.widget.PracticeWidgetPicker
import com.revyu.app.core.widget.QUESTION_SEED
import com.revyu.app.core.widget.RecentScoreRow
import com.revyu.app.core.widget.SchoolEventRow
import com.revyu.app.core.widget.SmartScheduleData
import com.revyu.app.core.widget.WidgetDayCell
import com.revyu.app.core.widget.WidgetEventRow
import com.revyu.app.core.widget.WidgetScheduleData
import com.revyu.app.core.widget.WidgetWeekData
import com.revyu.app.data.local.dao.CalendarEventDao
import com.revyu.app.data.local.dao.ExamAttemptDao
import com.revyu.app.data.local.dao.FlashcardDao
import com.revyu.app.data.local.dao.HomeWidgetPrefDao
import com.revyu.app.data.local.dao.QuestionDao
import com.revyu.app.data.local.dao.StudyLoadDao
import com.revyu.app.data.local.dao.StudySetDao
import com.revyu.app.data.local.dao.SubjectDao
import com.revyu.app.data.local.entities.CalendarEventEntity
import com.revyu.app.data.local.entities.HomeWidgetPrefEntity
import com.revyu.app.data.local.entities.defaultHomeWidgetPrefs
import com.revyu.app.data.local.entities.homeWidgetCatalog
import com.revyu.app.data.local.RevyuDatabase
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Single read path for both the in-app Home widget grid and the native launcher AppWidget,
 * so a widget on the launcher and the equivalent Home preview always show the same thing.
 *
 * Timestamps are relative to an injectable [Clock] (system by default) so tests can pin
 * "today".
 */
class WidgetDataRepository(
    private val database: RevyuDatabase,
    private val calendarEventDao: CalendarEventDao,
    private val homeWidgetPrefDao: HomeWidgetPrefDao,
    private val studyLoadDao: StudyLoadDao,
    private val studySetDao: StudySetDao,
    private val flashcardDao: FlashcardDao,
    private val questionDao: QuestionDao,
    private val examAttemptDao: ExamAttemptDao,
    private val subjectDao: SubjectDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    private val monthDayFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.US)

    /** Persisted Home grid order + visibility. The launcher widgets ignore this. */
    fun observeHomeWidgetPrefs(): Flow<List<HomeWidgetPrefEntity>> =
        ensureSeeded().flatMapLatest { homeWidgetPrefDao.observeAll() }

    /** One-time seed + backfill so existing installs pick up newly-added catalog keys. */
    private fun ensureSeeded(): Flow<Unit> = flow {
        database.withTransaction {
            val existing = homeWidgetPrefDao.getAll()
            if (existing.isEmpty()) {
                homeWidgetPrefDao.upsertAll(defaultHomeWidgetPrefs())
            } else {
                val known = existing.map { it.key }.toSet()
                val toAdd = homeWidgetCatalog.filter { it !in known }
                if (toAdd.isNotEmpty()) {
                    val maxOrder = existing.maxOf { it.sortOrder }
                    homeWidgetPrefDao.upsertAll(
                        toAdd.mapIndexed { i, key ->
                            HomeWidgetPrefEntity(
                                key = key,
                                enabled = key in defaultHomeWidgetPrefs().map { it.key }.toSet(),
                                sortOrder = maxOrder + 1 + i
                            )
                        }
                    )
                }
            }
        }
        emit(Unit)
    }

    suspend fun updateHomeWidgetPrefs(prefs: List<HomeWidgetPrefEntity>) {
        database.withTransaction { homeWidgetPrefDao.upsertAll(prefs) }
    }

    suspend fun hasAnyImports(): Boolean = studyLoadDao.count() > 0

    /** Row data (title/time/room) for one [EventRowKind]. Pure mapping, no I/O. */
    private fun CalendarEventEntity.toWidgetRow(): WidgetEventRow = WidgetEventRow(
        title = subjectName,
        timeText = DateTimeUtils.formatTimeRange(startMinute, endMinute),
        roomText = room,
        kind = when (type) {
            CalendarEventType.CLASS.name -> EventRowKind.CLASS
            CalendarEventType.REVIEW.name -> EventRowKind.REVIEW
            CalendarEventType.SUGGESTED_STUDY.name -> EventRowKind.SUGGESTED_STUDY
            else -> EventRowKind.OTHER
        }
    )

    suspend fun todayScheduleData(maxRows: Int = WidgetScheduleData.MAX_ROWS): WidgetScheduleData {
        val today = LocalDate.now(clock)
        val events = calendarEventDao.getAll()
            .filter { it.dayOfWeek == today.dayOfWeek.value && it.recurringOrOn(today) }
            .sortedBy { it.startMinute }

        val hasSchedule = events.isNotEmpty() || studyLoadDao.count() > 0
        val hasClassToday = events.any { it.type == CalendarEventType.CLASS.name }

        return WidgetScheduleData(
            dayHeader = "Today",
            secondaryHeader = LocalDate.now(clock).format(dayFormatter),
            emptyText = if (studyLoadDao.count() == 0) {
                "Import your study load from the Create tab"
            } else if (!hasClassToday) {
                com.revyu.app.core.util.MotivationalMessages.noClassMessage(today)
            } else {
                "No remaining classes or study sessions today"
            },
            rows = events.take(maxRows).map { it.toWidgetRow() },
            hasSchedule = hasSchedule
        )
    }
    /**
     * Everything the native launcher widget renders: header, the in-session NOW card,
     * the upcoming-exam reminder card, the NEXT session, today's class count, and the
     * closest upcoming one-off school-calendar event.
     */
    suspend fun smartScheduleData(): SmartScheduleData {
        val today = LocalDate.now(clock)
        val weekdayLabel = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            .uppercase(Locale.US)
        val dateLabel = today.format(monthDayFormatter)
        val nowMinute = ClockMinutes.now(clock)

        val allEvents = calendarEventDao.getAll()
        // Dated one-off events (school calendar / exams) only count toward "today" on
        // their actual date; otherwise a dated event whose weekday matches today would
        // masquerade as happening today from weeks away.
        val todayEvents = allEvents.filter {
            it.dayOfWeek == today.dayOfWeek.value && it.recurringOrOn(today)
        }
        val todayClasses = todayEvents.filter { it.type == CalendarEventType.CLASS.name }
        val hasSchedule = allEvents.isNotEmpty() || studyLoadDao.count() > 0

        // Only real class sessions count as the current active class NOW card.
        val now = todayClasses
            .firstOrNull { it.startMinute <= nowMinute && nowMinute < it.endMinute }
            ?.let { e ->
                val total = (e.endMinute - e.startMinute).coerceAtLeast(1)
                val elapsed = (nowMinute - e.startMinute).coerceIn(0, total)
                NowCard(
                    subject = e.subjectName,
                    timeRange = DateTimeUtils.formatTimeRange(e.startMinute, e.endMinute),
                    minutesLeft = (e.endMinute - nowMinute).coerceAtLeast(0),
                    progressPercent = elapsed * 100 / total
                )
            }

        val todayUpcoming = todayEvents
            .filter { it.startMinute > nowMinute }
            .sortedBy { it.startMinute }
        val next = if (todayUpcoming.isNotEmpty()) {
            val e = todayUpcoming.first()
            NextRow(
                timeLabel = DateTimeUtils.formatTime(e.startMinute),
                title = e.subjectName,
                timeRange = DateTimeUtils.formatTimeRange(e.startMinute, e.endMinute)
            )
        } else {
            allEvents
                .filter {
                    it.type == CalendarEventType.CLASS.name &&
                        it.recurringOrOn(today) &&
                        it.dayOfWeek != today.dayOfWeek.value &&
                        it.eventDate == null
                }
                .minWithOrNull(
                    compareBy(
                        { DateTimeUtils.daysUntilNextOccurrence(it.dayOfWeek, today) },
                        { it.startMinute }
                    )
                )
                ?.let { e ->
                    NextRow(
                        timeLabel = DateTimeUtils.formatTime(e.startMinute),
                        title = e.subjectName,
                        timeRange = DateTimeUtils.formatTimeRange(e.startMinute, e.endMinute)
                    )
                }
        }

        val exam = nextExamReminder(allEvents, today, nowMinute)
        val nextSchoolEvent = nextSchoolEvent(allEvents, today)
        val classCountToday = todayClasses.size

        return SmartScheduleData(
            weekdayLabel = weekdayLabel,
            dateLabel = dateLabel,
            now = now,
            exam = exam,
            next = next,
            classCountToday = classCountToday,
            hasSchedule = hasSchedule,
            nextSchoolEvent = nextSchoolEvent,
            noClassMessage = if (classCountToday == 0) com.revyu.app.core.util.MotivationalMessages.noClassMessage(today) else null
        )
    }

    /** True when the event repeats weekly (no date) or is dated exactly [today]. */
    private fun CalendarEventEntity.recurringOrOn(today: LocalDate): Boolean =
        eventDate == null || eventDate == today

    /**
     * Closest upcoming dated event that isn't exam-like (the exam card already owns those)
     * and isn't in the past. The countdown is left as a raw day count so the RemoteViews
     * renderer can format it through string resources with its Context.
     */
    private fun nextSchoolEvent(
        allEvents: List<CalendarEventEntity>,
        today: LocalDate
    ): SchoolEventRow? =
        SchoolEventSelector.pick(allEvents, today) { it.isExamLike() }

    private val EXAM_KEYWORDS = listOf("exam", "midterm", "final", "quiz", "long test")

    private fun CalendarEventEntity.isExamLike(): Boolean =
        type == CalendarEventType.EXAM.name ||
            listOf(subjectName, subjectCode, note).any { text ->
                text != null && EXAM_KEYWORDS.any { text.contains(it, ignoreCase = true) }
            }

    private fun examDaysUntil(e: CalendarEventEntity, today: LocalDate): Long? {
        val days = if (e.eventDate != null) {
            ChronoUnit.DAYS.between(today, e.eventDate)
        } else {
            DateTimeUtils.daysUntilNextOccurrence(e.dayOfWeek, today).toLong()
        }
        return if (days < 0) null else days
    }

    private fun examTitle(subjectName: String): String {
        val lower = subjectName.lowercase(Locale.US)
        return when {
            "midterm" in lower -> "Midterm Exam is Coming!"
            "final" in lower -> "Final Exam is Coming!"
            "quiz" in lower -> "Quiz is Coming!"
            "long test" in lower -> "Long Test is Coming!"
            else -> "Exam is Coming!"
        }
    }

    /** Closest upcoming exam-like event (weekly or one-off), if any. */
    private suspend fun nextExamReminder(
        allEvents: List<CalendarEventEntity>,
        today: LocalDate,
        nowMinute: Int
    ): ExamReminder? {
        val best = allEvents
            .filter { it.isExamLike() }
            .mapNotNull { e ->
                val days = examDaysUntil(e, today) ?: return@mapNotNull null
                val effectiveDays =
                    if (e.eventDate == null && days == 0L && e.startMinute < nowMinute) 7L else days
                Triple(e, effectiveDays, e.startMinute)
            }
            .minWithOrNull(compareBy({ it.second }, { it.third }))
            ?.first ?: return null
        val firstName = best.subjectName.trim().substringBefore(" - ").substringBefore(" – ")
        val studySet = best.subjectId?.let { subId ->
            studySetDao.getAllOnce().firstOrNull { it.subjectId == subId }
        }
        return ExamReminder(
            title = examTitle(firstName),
            prompt = "Want to review first?",
            subjectName = best.subjectName,
            subjectId = best.subjectId,
            studySetId = studySet?.id
        )
    }

    /** 7-day strip used by the week-overview Home widget. */
    suspend fun weekData(): WidgetWeekData {
        val now = LocalDate.now(clock)
        val eventsByDay = calendarEventDao.getAll().groupBy { it.dayOfWeek }
        val weekStart = now.minusDays((now.dayOfWeek.value - 1).toLong())
        return WidgetWeekData(
            days = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                WidgetDayCell(
                    label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
                    eventCount = eventsByDay[date.dayOfWeek.value]?.size ?: 0,
                    hasEventToday = date == now
                )
            }
        )
    }

    /** Today's progress for the "study minutes" Home widget. */
    data class StudyMinutesData(
        val plannedToday: Int,
        val completedToday: Int
    )

    suspend fun studyMinutesData(): StudyMinutesData {
        val today = LocalDate.now(clock).dayOfWeek.value
        val events = calendarEventDao.getAll()
            .filter { it.dayOfWeek == today && it.type != CalendarEventType.CLASS.name }
        return StudyMinutesData(
            plannedToday = events.sumOf { it.endMinute - it.startMinute },
            completedToday = 0
        )
    }

    /** Next upcoming class for the "next class" Home widget. */
    data class NextClassData(
        val title: String,
        val subtitle: String?,
        val timeLabel: String,
        val dayLabel: String,
        val hasNext: Boolean,
        val accentIndex: Int?
    )

    suspend fun nextClassData(): NextClassData {
        val today = LocalDate.now(clock)
        val events = calendarEventDao.getAll().filter { it.type == CalendarEventType.CLASS.name }
        if (events.isEmpty()) {
            return NextClassData("", null, "", "", hasNext = false, accentIndex = null)
        }

        val nowMinute = ClockMinutes.now(clock)
        // Order by occurrence: today first (still upcoming), then this week, then next week.
        fun occurrenceScore(e: CalendarEventEntity): Long {
            val daysAhead = DateTimeUtils.daysUntilNextOccurrence(e.dayOfWeek, today).toLong()
            val rawMinute = e.startMinute
            return if (daysAhead == 0L && rawMinute < nowMinute) 7000L + rawMinute.toLong()
            else daysAhead * 1440L + rawMinute.toLong()
        }

        val next = events.minByOrNull { occurrenceScore(it) } ?: return NextClassData("", null, "", "", false, null)
        val daysAhead = DateTimeUtils.daysUntilNextOccurrence(next.dayOfWeek, today)
        val dayLabel = when {
            daysAhead == 0 -> "Today"
            daysAhead == 1 -> "Tomorrow"
            else -> DayOfWeek.of(next.dayOfWeek).getDisplayName(TextStyle.FULL, Locale.US)
        }
        return NextClassData(
            title = next.subjectName,
            subtitle = next.room,
            timeLabel = DateTimeUtils.formatTimeRange(next.startMinute, next.endMinute),
            dayLabel = dayLabel,
            hasNext = true,
            accentIndex = accentIndexForSubject(next.subjectId)
        )
    }

    private fun accentIndexForSubject(subjectId: String?): Int? = subjectId?.hashCode()?.and(Int.MAX_VALUE)?.mod(6)

    private suspend fun subjectNamesById(): Map<String, String> =
        subjectDao.getAll().associate { it.id to it.name }

    /** Flashcard of the day: one card across the pool of candidate Study Sets, stable per day. */
    suspend fun dailyFlashcard(pin: String?): DailyFlashcardData? {
        val allSets = studySetDao.getAllOnce()
        if (allSets.isEmpty()) return null
        val candidates = pin?.let { p -> allSets.filter { it.id == p } } ?: allSets
        if (candidates.isEmpty()) return null
        val candidateIds = candidates.map { it.id }.toSet()
        val cards = flashcardDao.getAllOnce().filter { it.studySetId in candidateIds }
        if (cards.isEmpty()) return null

        val index = PracticeWidgetPicker.pickIndex(LocalDate.now(clock), cards.size, FLASHCARD_SEED)
        val card = cards[index]
        val set = candidates.first { it.id == card.studySetId }
        val names = subjectNamesById()
        return DailyFlashcardData(
            front = card.front,
            back = card.back,
            setTitle = set.title,
            subjectName = names[set.subjectId] ?: set.title,
            studySetId = set.id
        )
    }

    /** Question of the day: one practice question across the candidate pool, stable per day. */
    suspend fun dailyQuestion(pin: String?): DailyQuestionData? {
        val allSets = studySetDao.getAllOnce()
        if (allSets.isEmpty()) return null
        val candidates = pin?.let { p -> allSets.filter { it.id == p } } ?: allSets
        if (candidates.isEmpty()) return null
        val candidateIds = candidates.map { it.id }.toSet()
        val questions = questionDao.getAllOnce().filter { it.studySetId in candidateIds }
        if (questions.isEmpty()) return null

        val index = PracticeWidgetPicker.pickIndex(LocalDate.now(clock), questions.size, QUESTION_SEED)
        val question = questions[index]
        val set = candidates.first { it.id == question.studySetId }
        val names = subjectNamesById()
        return DailyQuestionData(
            id = question.id,
            type = question.type,
            prompt = question.prompt,
            options = question.options,
            correctAnswers = question.correctAnswers,
            setTitle = set.title,
            subjectName = names[set.subjectId] ?: set.title,
            studySetId = set.id
        )
    }

    /** Most recent practice score per Study Set, newest first. */
    suspend fun recentPracticeScores(limit: Int = 6): List<RecentScoreRow> {
        val sets = studySetDao.getAllOnce().associateBy { it.id }
        val names = subjectNamesById()
        val now = Instant.now(clock)
        return examAttemptDao.getLatestPerStudySetOnce()
            .filter { it.submittedAt != null }
            .sortedByDescending { it.startedAt }
            .take(limit)
            .mapNotNull { attempt ->
                val set = sets[attempt.studySetId] ?: return@mapNotNull null
                RecentScoreRow(
                    setTitle = set.title,
                    subjectName = names[set.subjectId] ?: set.title,
                    scorePercentage = attempt.scorePercentage,
                    daysAgo = Duration.between(attempt.startedAt, now).toDays().coerceAtLeast(0),
                    studySetId = set.id
                )
            }
    }

    /** Closest upcoming exam from the calendar. */
    suspend fun nextExamCountdown(): ExamCountdownRow? {
        val occurrences = calendarEventDao.getAll()
            .filter { it.type == CalendarEventType.EXAM.name }
            .map {
                ExamOccurrence(
                    title = it.subjectName,
                    dayOfWeek = it.dayOfWeek,
                    startMinute = it.startMinute
                )
            }
        if (occurrences.isEmpty()) return null
        return PracticeWidgetPicker.nextExamCountdown(LocalDate.now(clock), ClockMinutes.now(clock), occurrences)
    }

    /** Study Sets that have never been practiced, grouped by subject. */
    suspend fun practiceReadySubjects(): List<PracticeReadyRow> {
        val attemptedSetIds = examAttemptDao.getLatestPerStudySetOnce().map { it.studySetId }.toSet()
        val names = subjectNamesById()
        return studySetDao.getAllOnce()
            .filter { it.id !in attemptedSetIds }
            .groupBy { it.subjectId }
            .map { (subjectId, sets) ->
                val first = sets.sortedBy { it.createdAt }.first()
                PracticeReadyRow(
                    subjectName = names[subjectId] ?: first.title,
                    firstStudySetId = first.id,
                    setCount = sets.size
                )
            }
    }

    /** All Study Sets with display names, for the pin picker. */
    suspend fun pinOptions(): List<PinOption> {
        val names = subjectNamesById()
        return studySetDao.getAllOnce()
            .sortedBy { (names[it.subjectId] ?: it.title).lowercase(Locale.US) + "|" + it.title.lowercase(Locale.US) }
            .map { PinOption(it.id, it.title, names[it.subjectId] ?: it.title) }
    }

    /** Current time-of-day in minutes, via the injectable clock. */
    private object ClockMinutes {
        fun now(clock: Clock): Int =
            java.time.LocalTime.now(clock).hour * 60 + java.time.LocalTime.now(clock).minute
    }
}