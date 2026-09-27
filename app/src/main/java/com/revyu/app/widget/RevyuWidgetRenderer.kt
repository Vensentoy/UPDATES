package com.revyu.app.widget

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import com.revyu.app.R
import com.revyu.app.core.preferences.AccentColor
import com.revyu.app.core.preferences.CatExpression
import com.revyu.app.core.preferences.getCatDrawableRes
import com.revyu.app.core.widget.SmartScheduleData

/**
 * Pure RemoteViews builder — no I/O here, so it stays unit-testable and cheap to call.
 *
 * The widget is a stack of pre-declared cards (NOW, exam reminder, NEXT, footer). Every
 * RemoteViews action is a top-level setTextViewText / setViewVisibility / setImageViewBitmap /
 * setOnClickPendingIntent. No ProgressBar interaction-method, no nested layouts to inflate at
 * apply time, and no reflection setInt actions, all of which some launcher hosts
 * (Transsion/XOS, ColorOS/Oppo) reject when they apply the RemoteViews in their own process,
 * which they then surface as "Problem loading widget" / "An error occurred when loading widget".
 *
 * All section ids exist in every layout so a render can never target a missing view; smaller
 * grids simply keep some sections gone.
 */
object RevyuWidgetRenderer {

    /** Launcher cells -> layout. A phone usually snaps to 2x2, 4x2, or 4x4. */
    const val WIDTH_CELLS_THRESHOLD_MEDIUM = 160 // dp; approx 3 cells
    const val WIDTH_CELLS_THRESHOLD_LARGE = 250 // dp; approx 4 cells

    fun layoutResForMinWidth(minWidthDp: Int): Int = when {
        minWidthDp >= WIDTH_CELLS_THRESHOLD_LARGE -> R.layout.widget_schedule_large
        minWidthDp >= WIDTH_CELLS_THRESHOLD_MEDIUM -> R.layout.widget_schedule_medium
        else -> R.layout.widget_schedule_small
    }

    /** Which section groups a layout can show without overflowing. */
    fun canShowExam(layoutRes: Int): Boolean =
        layoutRes == R.layout.widget_schedule_large || layoutRes == R.layout.widget_schedule_medium

    fun canShowNext(layoutRes: Int): Boolean =
        layoutRes == R.layout.widget_schedule_large || layoutRes == R.layout.widget_schedule_small

    fun canShowFooter(layoutRes: Int): Boolean =
        layoutRes == R.layout.widget_schedule_large || layoutRes == R.layout.widget_schedule_medium

    fun canShowSchoolEvent(layoutRes: Int): Boolean =
        canShowFooter(layoutRes)

    fun render(
        context: android.content.Context,
        layoutRes: Int,
        data: SmartScheduleData,
        accentColor: AccentColor = AccentColor.ORANGE
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)

        // Header.
        views.setImageViewResource(
            R.id.widget_header_cat,
            accentColor.getCatDrawableRes(CatExpression.LAY)
        )
        views.setTextViewText(
            R.id.widget_header_title,
            if (data.hasSchedule) context.getString(R.string.app_name)
            else context.getString(R.string.widget_placeholder_title)
        )
        views.setTextViewText(R.id.widget_header_weekday, data.weekdayLabel)
        views.setTextViewText(R.id.widget_header_date, data.dateLabel)

        // NOW card (all layouts).
        val now = data.now
        if (now != null) {
            views.setTextViewText(R.id.widget_now_title, now.subject)
            views.setTextViewText(R.id.widget_now_time, now.timeRange)
            views.setTextViewText(
                R.id.widget_now_minutes,
                context.resources.getString(R.string.widget_minutes_left, now.minutesLeft)
            )
            views.setTextViewText(R.id.widget_now_chip_subject, now.subject)
            views.setImageViewBitmap(R.id.widget_now_progress, progressBitmap(context, now.progressPercent))
            views.setViewVisibility(R.id.widget_now_card, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_now_card, View.GONE)
        }

        // Exam reminder card.
        val exam = data.exam
        if (canShowExam(layoutRes) && exam != null) {
            views.setTextViewText(R.id.widget_exam_title, exam.title)
            views.setTextViewText(R.id.widget_exam_prompt, exam.prompt)
            views.setViewVisibility(R.id.widget_exam_card, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_exam_card, View.GONE)
        }

        // NEXT section.
        val next = data.next
        if (canShowNext(layoutRes) && next != null) {
            views.setTextViewText(R.id.widget_next_time, next.timeLabel)
            views.setTextViewText(R.id.widget_next_range, next.timeRange)
            views.setTextViewText(R.id.widget_next_title, next.title)
            views.setViewVisibility(R.id.widget_next_section, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_next_section, View.GONE)
        }

        // Footer.
        if (canShowFooter(layoutRes)) {
            views.setTextViewText(
                R.id.widget_footer_count,
                if (!data.hasSchedule) {
                    context.getString(R.string.widget_footer_none)
                } else if (data.classCountToday > 0) {
                    context.resources.getString(R.string.widget_footer_classes, data.classCountToday)
                } else {
                    data.noClassMessage ?: "No classes today"
                }
            )
            views.setViewVisibility(R.id.widget_footer_row, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_footer_row, View.GONE)
        }

        // Next school calendar event ("◷ Next school event in 23 days / INTRAMURALS").
        val schoolEvent = data.nextSchoolEvent
        if (canShowSchoolEvent(layoutRes) && schoolEvent != null) {
            views.setTextViewText(
                R.id.widget_school_event_label,
                schoolEventCountdown(context, schoolEvent.daysUntil)
            )
            views.setTextViewText(R.id.widget_school_event_title, schoolEvent.title)
            views.setViewVisibility(R.id.widget_school_event_row, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_school_event_row, View.GONE)
        }

        return views
    }

    private fun schoolEventCountdown(context: android.content.Context, days: Long): String =
        when (days) {
            0L -> context.getString(R.string.widget_school_event_today)
            1L -> context.getString(R.string.widget_school_event_tomorrow)
            else -> context.getString(R.string.widget_school_event_in, days)
        }

    /**
     * The "minutes left" bar as a pre-drawn bitmap (track + rounded fill) delivered with
     * [RemoteViews.setImageViewBitmap]. Bitmaps are a plain Parcelable to the host, so this
     * avoids the ProgressBar interaction-method that some launcher hosts (Transsion/XOS)
     * execute reflectively and can reject — which surfaced as "Problem loading widget".
     * The ImageView stretches the bitmap fitXY, so the fill fraction is preserved.
     */
    private fun progressBitmap(context: android.content.Context, percent: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (96 * density).toInt().coerceAtLeast(1)
        val height = (6 * density).toInt().coerceAtLeast(1)
        val radius = 3 * density
        val clamped = percent.coerceIn(0, 100)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7E2D6.toInt() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B2A4A.toInt() }
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, radius, radius, track)
        if (clamped > 0) {
            val fillWidth = width * (clamped / 100f)
            canvas.drawRoundRect(RectF(0f, 0f, fillWidth, height.toFloat()), radius, radius, fill)
        }
        return bitmap
    }

    /** Strategy to pick the layout for a given widget once its cell size is known. */
    fun appWidgetManagerSizeToLayout(appWidgetManager: AppWidgetManager, widgetId: Int): Int {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        return layoutResForMinWidth(minWidthDp)
    }
}