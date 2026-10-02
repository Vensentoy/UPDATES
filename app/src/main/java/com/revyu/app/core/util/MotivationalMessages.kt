package com.revyu.app.core.util

import java.time.DayOfWeek
import java.time.LocalDate

object MotivationalMessages {

    private val weekdayMessages = listOf(
        "No classes for today, Studywell and get some rest",
        "No classes today! Take time to recharge and review at your own pace.",
        "Free day today! Get some rest or tackle a quick review.",
        "No classes on your schedule today. Study well and take it easy!"
    )

    private val weekendMessages = listOf(
        "Happy weekend! Time to rest, recharge, and relax.",
        "Weekend mode on! No classes today—enjoy your break.",
        "It's the weekend! Take a well-deserved rest and study well when ready.",
        "No classes this weekend! Relax and rejuvenate for the coming week."
    )

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun noClassMessage(date: LocalDate = LocalDate.now()): String {
        val list = if (isWeekend(date)) weekendMessages else weekdayMessages
        val index = (date.dayOfYear + date.year) % list.size
        return list[index]
    }
}
