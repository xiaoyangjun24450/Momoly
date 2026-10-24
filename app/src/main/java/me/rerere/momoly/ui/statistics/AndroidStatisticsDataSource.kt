package me.rerere.momoly.ui.statistics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.momoly.data.chat.ChatRepository
import me.rerere.momoly.data.settings.AppStatisticsStore

internal class AndroidStatisticsDataSource(
    private val context: Context,
) : StatisticsDataSource {
    override suspend fun snapshot(sinceEpochMillis: Long): StatisticsSnapshot = withContext(Dispatchers.IO) {
        val stats = ChatRepository.statistics(context, sinceEpochMillis)
        StatisticsSnapshot(
            totalConversations = stats.totalConversations,
            totalMessages = stats.totalMessages,
            promptTokens = stats.promptTokens,
            completionTokens = stats.completionTokens,
            cachedTokens = stats.cachedTokens,
            launchCount = AppStatisticsStore.launchCount(context),
            messagesPerDay = stats.messagesPerDay,
        )
    }
}
