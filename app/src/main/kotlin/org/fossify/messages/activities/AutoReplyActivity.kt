package org.fossify.messages.activities

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.fossify.commons.dialogs.CustomIntervalPickerDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatSecondsToTimeString
import org.fossify.commons.extensions.getDatePickerDialogTheme
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityAutoReplyBinding
import org.fossify.messages.databinding.ItemAutoReplyDayBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.AUTO_REPLY_MIN_COOLDOWN_SECONDS
import org.fossify.messages.helpers.AutoReplyHelper
import org.fossify.messages.helpers.AutoReplyRules
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Calendar

class AutoReplyActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAutoReplyBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.autoReplyNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.autoReplyNestedScrollview,
            topAppBar = binding.autoReplyAppbar
        )

        setupMessage()
        setupDays()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.autoReplyAppbar, NavigationIcon.Arrow)

        setupEnabled()
        setupCooldown()
        setupScheduleEnabled()
        setupScheduleTimes()
        updateTextColors(binding.autoReplyNestedScrollview)

        arrayOf(
            binding.autoReplyMessageLabel,
            binding.autoReplyScheduleLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupEnabled() = binding.apply {
        autoReplyEnabled.isChecked = config.autoReplyEnabled
        autoReplyEnabledHolder.setOnClickListener {
            autoReplyEnabled.toggle()
            config.autoReplyEnabled = autoReplyEnabled.isChecked
            if (autoReplyEnabled.isChecked) {
                // turning replies on starts fresh, everyone gets a reply again
                AutoReplyHelper.clearReplyLog(applicationContext)
            }
        }
    }

    private fun setupMessage() = binding.apply {
        autoReplyMessage.setText(config.autoReplyMessage)
        autoReplyEmptyMessageWarning.beVisibleIf(config.autoReplyMessage.isBlank())
        autoReplyMessage.onTextChangeListener { text ->
            config.autoReplyMessage = text
            autoReplyEmptyMessageWarning.beVisibleIf(text.isBlank())
        }
    }

    private fun setupCooldown() = binding.apply {
        autoReplyCooldown.text = formatSecondsToTimeString(config.autoReplyCooldownSeconds)
        autoReplyCooldownHolder.setOnClickListener {
            CustomIntervalPickerDialog(
                activity = this@AutoReplyActivity,
                selectedSeconds = config.autoReplyCooldownSeconds
            ) { seconds ->
                config.autoReplyCooldownSeconds = seconds.coerceAtLeast(AUTO_REPLY_MIN_COOLDOWN_SECONDS)
                autoReplyCooldown.text = formatSecondsToTimeString(config.autoReplyCooldownSeconds)
            }
        }
    }

    private fun setupScheduleEnabled() = binding.apply {
        autoReplyScheduleEnabled.isChecked = config.autoReplyScheduleEnabled
        autoReplyScheduleDetails.beVisibleIf(config.autoReplyScheduleEnabled)
        autoReplyScheduleEnabledHolder.setOnClickListener {
            autoReplyScheduleEnabled.toggle()
            config.autoReplyScheduleEnabled = autoReplyScheduleEnabled.isChecked
            autoReplyScheduleDetails.beVisibleIf(config.autoReplyScheduleEnabled)
        }
    }

    private fun setupDays() = binding.apply {
        getDaysInDisplayOrder().forEach { day ->
            val dayBinding = ItemAutoReplyDayBinding.inflate(layoutInflater, autoReplyDaysHolder, true)
            // every row shares the same view id, restoring saved state would mix the days up
            dayBinding.autoReplyDay.isSaveEnabled = false
            dayBinding.autoReplyDay.text = getString(getDayNameRes(day))
            dayBinding.autoReplyDay.isChecked = AutoReplyRules.isDaySelected(config.autoReplyScheduleDays, day)
            dayBinding.autoReplyDayHolder.setOnClickListener {
                dayBinding.autoReplyDay.toggle()
                val bit = AutoReplyRules.dayBit(day)
                config.autoReplyScheduleDays = if (dayBinding.autoReplyDay.isChecked) {
                    config.autoReplyScheduleDays or bit
                } else {
                    config.autoReplyScheduleDays and bit.inv()
                }

                autoReplyNoDaysWarning.beVisibleIf(config.autoReplyScheduleDays == 0)
            }
        }

        autoReplyNoDaysWarning.beVisibleIf(config.autoReplyScheduleDays == 0)
    }

    private fun setupScheduleTimes() = binding.apply {
        updateScheduleTimes()
        autoReplyStartTimeHolder.setOnClickListener {
            showTimePicker(config.autoReplyScheduleStart) { minutes ->
                config.autoReplyScheduleStart = minutes
                updateScheduleTimes()
            }
        }

        autoReplyEndTimeHolder.setOnClickListener {
            showTimePicker(config.autoReplyScheduleEnd) { minutes ->
                config.autoReplyScheduleEnd = minutes
                updateScheduleTimes()
            }
        }
    }

    private fun updateScheduleTimes() = binding.apply {
        val start = config.autoReplyScheduleStart
        val end = config.autoReplyScheduleEnd
        autoReplyStartTime.text = formatTime(start)
        autoReplyEndTime.text = if (AutoReplyRules.endsNextDay(start, end)) {
            getString(R.string.auto_reply_next_day, formatTime(end))
        } else {
            formatTime(end)
        }
    }

    private fun showTimePicker(currentMinutes: Int, callback: (minutes: Int) -> Unit) {
        val current = LocalTime.of(currentMinutes / MINUTES_IN_HOUR, currentMinutes % MINUTES_IN_HOUR)
        val is24Hour = DateFormat.is24HourFormat(this)
        if (isDynamicTheme()) {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(current.hour)
                .setMinute(current.minute)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                callback(timePicker.hour * MINUTES_IN_HOUR + timePicker.minute)
            }

            timePicker.show(supportFragmentManager, "")
        } else {
            TimePickerDialog(
                this,
                getDatePickerDialogTheme(),
                { _, hours, minutes -> callback(hours * MINUTES_IN_HOUR + minutes) },
                current.hour,
                current.minute,
                is24Hour
            ).show()
        }
    }

}

private const val MINUTES_IN_HOUR = 60

/** The days of the week, starting with the first day of the week of the current locale. */
private fun getDaysInDisplayOrder(): List<DayOfWeek> {
    val firstDay = when (Calendar.getInstance().firstDayOfWeek) {
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.MONDAY
    }

    return DayOfWeek.entries.indices.map { firstDay.plus(it.toLong()) }
}

private fun getDayNameRes(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> org.fossify.commons.R.string.monday
    DayOfWeek.TUESDAY -> org.fossify.commons.R.string.tuesday
    DayOfWeek.WEDNESDAY -> org.fossify.commons.R.string.wednesday
    DayOfWeek.THURSDAY -> org.fossify.commons.R.string.thursday
    DayOfWeek.FRIDAY -> org.fossify.commons.R.string.friday
    DayOfWeek.SATURDAY -> org.fossify.commons.R.string.saturday
    DayOfWeek.SUNDAY -> org.fossify.commons.R.string.sunday
}

/** Formats minutes since midnight in the 12 or 24 hour format the device uses. */
private fun Context.formatTime(minutesOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutesOfDay / MINUTES_IN_HOUR)
        set(Calendar.MINUTE, minutesOfDay % MINUTES_IN_HOUR)
    }

    return DateFormat.getTimeFormat(this).format(calendar.time)
}
