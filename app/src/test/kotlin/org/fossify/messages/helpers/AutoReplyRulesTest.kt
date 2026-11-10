package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY

class AutoReplyRulesTest {
    private val weekdays = listOf(MONDAY, TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY)
        .fold(0) { acc, day -> acc or AutoReplyRules.dayBit(day) }

    private fun time(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun settings(
        enabled: Boolean = true,
        message: String = "Away",
        scheduleEnabled: Boolean = false,
        scheduleDays: Int = AUTO_REPLY_ALL_DAYS,
        start: Int = time(9),
        end: Int = time(17),
    ) = AutoReplySettings(
        enabled = enabled,
        message = message,
        cooldownSeconds = 3600,
        scheduleEnabled = scheduleEnabled,
        scheduleDays = scheduleDays,
        scheduleStartMinutes = start,
        scheduleEndMinutes = end
    )

    @Test
    fun dayBitsCoverTheWholeWeek() {
        assertEquals(1, AutoReplyRules.dayBit(MONDAY))
        assertEquals(64, AutoReplyRules.dayBit(SUNDAY))
        val allDays = DayOfWeek.entries.fold(0) { acc, day -> acc or AutoReplyRules.dayBit(day) }
        assertEquals(AUTO_REPLY_ALL_DAYS, allDays)
    }

    @Test
    fun daytimeRangeIncludesStartAndExcludesEnd() {
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(17), MONDAY, time(8, 59)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(17), MONDAY, time(9)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(17), MONDAY, time(16, 59)))
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(17), MONDAY, time(17)))
    }

    @Test
    fun daytimeRangeSkipsUnselectedDays() {
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(17), SATURDAY, time(12)))
        assertFalse(AutoReplyRules.isWithinSchedule(0, time(9), time(17), MONDAY, time(12)))
    }

    @Test
    fun overnightRangeIsActiveInTheEveningOfASelectedDay() {
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), FRIDAY, time(16, 59)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), FRIDAY, time(17)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), FRIDAY, time(23, 59)))
    }

    @Test
    fun overnightRangeContinuesAfterMidnightIntoAnUnselectedDay() {
        // started Friday evening, still running Saturday morning
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), SATURDAY, time(0)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), SATURDAY, time(8, 59)))
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), SATURDAY, time(9)))
        // Saturday isn't selected, so nothing starts Saturday evening
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), SATURDAY, time(20)))
    }

    @Test
    fun overnightRangeDoesNotCarryOverFromAnUnselectedDay() {
        // Sunday isn't selected, so Monday before the start time is outside the schedule
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), MONDAY, time(8)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(17), time(9), MONDAY, time(18)))
    }

    @Test
    fun overnightRangeWrapsFromSundayToMonday() {
        val sundayOnly = AutoReplyRules.dayBit(SUNDAY)
        assertTrue(AutoReplyRules.isWithinSchedule(sundayOnly, time(22), time(6), MONDAY, time(5)))
        assertFalse(AutoReplyRules.isWithinSchedule(sundayOnly, time(22), time(6), TUESDAY, time(5)))
    }

    @Test
    fun sameStartAndEndRunsForTwentyFourHours() {
        // nothing started on Sunday, so Monday begins at the start time
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(9), MONDAY, time(8, 59)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(9), MONDAY, time(9)))
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(9), TUESDAY, time(8, 59)))
        // Friday's range ends Saturday morning
        assertTrue(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(9), SATURDAY, time(8, 59)))
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(9), SATURDAY, time(9)))
        assertFalse(AutoReplyRules.isWithinSchedule(weekdays, time(9), time(9), SUNDAY, time(12)))
    }

    @Test
    fun endsNextDayWhenTheEndIsNotAfterTheStart() {
        assertFalse(AutoReplyRules.endsNextDay(time(9), time(17)))
        assertTrue(AutoReplyRules.endsNextDay(time(17), time(9)))
        assertTrue(AutoReplyRules.endsNextDay(time(9), time(9)))
    }

    @Test
    fun cooldownAllowsTheFirstReply() {
        assertTrue(AutoReplyRules.isCooldownOver(null, 1_000_000L, 3600))
    }

    @Test
    fun cooldownBlocksUntilTheTimeHasPassed() {
        val last = 1_000_000L
        assertFalse(AutoReplyRules.isCooldownOver(last, last + 1, 60))
        assertFalse(AutoReplyRules.isCooldownOver(last, last + 59_999, 60))
        assertTrue(AutoReplyRules.isCooldownOver(last, last + 60_000, 60))
    }

    @Test
    fun cooldownHandlesLongDurationsWithoutOverflow() {
        val thirtyDays = 30 * 24 * 60 * 60
        val last = 1_000_000L
        assertFalse(AutoReplyRules.isCooldownOver(last, last + 29L * 24 * 60 * 60 * 1000, thirtyDays))
        assertTrue(AutoReplyRules.isCooldownOver(last, last + 30L * 24 * 60 * 60 * 1000, thirtyDays))
    }

    @Test
    fun cooldownIgnoresAClockThatMovedBackwards() {
        assertTrue(AutoReplyRules.isCooldownOver(2_000_000L, 1_000_000L, 3600))
    }

    @Test
    fun phoneNumbersAndNumericShortCodesCanReceiveReplies() {
        assertTrue(AutoReplyRules.canReceiveReply("+1 (555) 123-4567"))
        assertTrue(AutoReplyRules.canReceiveReply("5551234567"))
        assertTrue(AutoReplyRules.canReceiveReply("503501"))
    }

    @Test
    fun namesAndEmailAddressesCanNotReceiveReplies() {
        assertFalse(AutoReplyRules.canReceiveReply("AB-CD0"))
        assertFalse(AutoReplyRules.canReceiveReply("MyBank"))
        assertFalse(AutoReplyRules.canReceiveReply("someone@example.com"))
        assertFalse(AutoReplyRules.canReceiveReply(""))
    }

    @Test
    fun inactiveWhenTurnedOffOrMessageIsBlank() {
        assertFalse(AutoReplyRules.isActiveFor(settings(enabled = false), "5551234567", MONDAY, time(12)))
        assertFalse(AutoReplyRules.isActiveFor(settings(message = "  \n"), "5551234567", MONDAY, time(12)))
    }

    @Test
    fun activeAtAnyTimeWhenTheScheduleIsOff() {
        val off = settings(scheduleEnabled = false, scheduleDays = 0)
        assertTrue(AutoReplyRules.isActiveFor(off, "5551234567", SUNDAY, time(3)))
    }

    @Test
    fun followsTheScheduleWhenItIsOn() {
        val scheduled = settings(scheduleEnabled = true, scheduleDays = weekdays)
        assertTrue(AutoReplyRules.isActiveFor(scheduled, "5551234567", MONDAY, time(12)))
        assertFalse(AutoReplyRules.isActiveFor(scheduled, "5551234567", MONDAY, time(20)))
        assertFalse(AutoReplyRules.isActiveFor(scheduled, "5551234567", SUNDAY, time(12)))
    }

    @Test
    fun neverActiveForSendersWithoutANumber() {
        assertFalse(AutoReplyRules.isActiveFor(settings(), "MyBank", MONDAY, time(12)))
    }
}
