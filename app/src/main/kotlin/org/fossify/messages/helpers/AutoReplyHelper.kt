package org.fossify.messages.helpers

import android.content.Context
import androidx.core.content.edit
import org.fossify.commons.extensions.showErrorToast
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.removeDiacriticsIfNeeded
import org.fossify.messages.extensions.updateLastConversationMessage
import org.fossify.messages.messaging.sendMessageCompat
import java.time.LocalDateTime

/** Sends the automatic reply configured in the settings to senders of incoming messages. */
object AutoReplyHelper {
    private const val REPLY_LOG_PREFS = "auto_reply_log"
    private const val MINUTES_IN_HOUR = 60
    private val lock = Any()

    /**
     * Replies to [address] if automatic replies are turned on, the schedule allows it and the
     * sender hasn't received one recently. Must be called from a background thread, after the
     * blocked number and blocked keyword checks.
     */
    fun replyIfNeeded(context: Context, address: String, subscriptionId: Int?) {
        try {
            val settings = context.config.autoReplySettings
            val now = LocalDateTime.now()
            val minuteOfDay = now.hour * MINUTES_IN_HOUR + now.minute
            if (!AutoReplyRules.isActiveFor(settings, address, now.dayOfWeek, minuteOfDay)) return

            // the one-to-one thread identifies the sender, also for messages received in a group
            val threadId = context.getThreadId(address)
            if (threadId == 0L || !claimReply(context, threadId, settings.cooldownSeconds)) return

            val body = context.removeDiacriticsIfNeeded(settings.message.trim())
            context.sendMessageCompat(body, listOf(address), subscriptionId, emptyList())

            val sentMessage = context.getMessages(
                threadId = threadId, includeScheduledMessages = false, limit = 1
            ).lastOrNull()
            if (sentMessage != null) {
                context.messagesDB.insertOrUpdate(sentMessage)
                context.updateLastConversationMessage(threadId)
            }

            refreshMessages()
            refreshConversations()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // a failed automatic reply must never break receiving the message it answers
            context.showErrorToast(e)
        }
    }

    /** Forgets who has been replied to, so that everyone gets a reply again. */
    fun clearReplyLog(context: Context) {
        synchronized(lock) {
            replyLog(context).edit { clear() }
        }
    }

    /**
     * Checks the cooldown of a sender and records the new reply in one step, so that two messages
     * arriving at the same time can't both be answered.
     */
    private fun claimReply(context: Context, threadId: Long, cooldownSeconds: Int): Boolean {
        synchronized(lock) {
            val log = replyLog(context)
            val key = threadId.toString()
            val now = System.currentTimeMillis()
            val lastReply = if (log.contains(key)) log.getLong(key, 0L) else null
            if (!AutoReplyRules.isCooldownOver(lastReply, now, cooldownSeconds)) return false

            log.edit {
                for ((sender, time) in log.all) {
                    val isExpired = time !is Long ||
                        AutoReplyRules.isCooldownOver(time, now, cooldownSeconds)
                    if (isExpired) remove(sender)
                }

                putLong(key, now)
            }
            return true
        }
    }

    private fun replyLog(context: Context) =
        context.getSharedPreferences(REPLY_LOG_PREFS, Context.MODE_PRIVATE)
}
