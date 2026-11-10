package org.fossify.messages.helpers

import java.time.DayOfWeek

const val AUTO_REPLY_ALL_DAYS = 0b1111111
const val AUTO_REPLY_MIN_COOLDOWN_SECONDS = 60
const val AUTO_REPLY_DEFAULT_COOLDOWN_SECONDS = 60 * 60
const val AUTO_REPLY_DEFAULT_START_MINUTES = 9 * 60
const val AUTO_REPLY_DEFAULT_END_MINUTES = 17 * 60

data class AutoReplySettings(
    val enabled: Boolean,
    val message: String,
    val cooldownSeconds: Int,
    val scheduleEnabled: Boolean,
    /** Bitmask of selected days, see [AutoReplyRules.dayBit]. */
    val scheduleDays: Int,
    /** Minutes since midnight. */
    val scheduleStartMinutes: Int,
    /** Minutes since midnight. An end before the start means the range runs past midnight. */
    val scheduleEndMinutes: Int,
)

/** The decisions behind automatic replies, kept free of Android classes so they can be unit tested. */
object AutoReplyRules {
    private const val MILLIS_IN_SECOND = 1000L

    /** Monday is bit 0, Sunday is bit 6. */
    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isDaySelected(days: Int, day: DayOfWeek): Boolean = days and dayBit(day) != 0

    /**
     * Whether [day] at [minuteOfDay] falls inside the schedule. The selected days refer to the
     * day a time range starts on, so an overnight range that started on a selected day is still
     * active after midnight. An identical start and end time runs for a full 24 hours.
     */
    fun isWithinSchedule(
        days: Int,
        startMinutes: Int,
        endMinutes: Int,
        day: DayOfWeek,
        minuteOfDay: Int,
    ): Boolean {
        val startsToday = isDaySelected(days, day)
        if (!endsNextDay(startMinutes, endMinutes)) {
            return startsToday && minuteOfDay in startMinutes until endMinutes
        }

        val startedYesterday = isDaySelected(days, day.minus(1))
        return startsToday && minuteOfDay >= startMinutes ||
            startedYesterday && minuteOfDay < endMinutes
    }

    /** Whether a time range runs past midnight. */
    fun endsNextDay(startMinutes: Int, endMinutes: Int): Boolean = endMinutes <= startMinutes

    /** Whether enough time has passed since the last automatic reply to the same sender. */
    fun isCooldownOver(lastReplyMillis: Long?, nowMillis: Long, cooldownSeconds: Int): Boolean {
        if (lastReplyMillis == null) return true
        val elapsed = nowMillis - lastReplyMillis
        // a negative value means the clock was moved back, don't stay silent because of that
        return elapsed < 0 || elapsed >= cooldownSeconds * MILLIS_IN_SECOND
    }

    /**
     * Replies are sent as SMS, which needs a phone number. Senders that are a name ("AB-CD0")
     * or an email address can't receive one.
     */
    fun canReceiveReply(address: String): Boolean {
        return address.any { it.isDigit() } && address.none { it.isLetter() }
    }

    /** Everything except the per-sender cooldown, which needs the stored reply times. */
    fun isActiveFor(
        settings: AutoReplySettings,
        address: String,
        day: DayOfWeek,
        minuteOfDay: Int,
    ): Boolean {
        if (!settings.enabled || settings.message.isBlank() || !canReceiveReply(address)) {
            return false
        }

        return !settings.scheduleEnabled || isWithinSchedule(
            days = settings.scheduleDays,
            startMinutes = settings.scheduleStartMinutes,
            endMinutes = settings.scheduleEndMinutes,
            day = day,
            minuteOfDay = minuteOfDay
        )
    }
}
