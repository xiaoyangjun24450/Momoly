package me.rerere.momoly.ui.chat

import java.io.File
import kotlinx.coroutines.test.runTest
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.data.character.CharacterCard
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.data.worldbook.WorldInfoSettings
import me.rerere.momoly.extension.Extension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptAssemblerTest {
    @Test
    fun legacySummaryDoesNotChangePromptOrHideOriginalHistory() = runTest {
        val history = listOf(
            ChatMessage(role = "user", content = "original first message", ts = 1),
            ChatMessage(role = "assistant", content = "original reply", ts = 2),
            ChatMessage(role = "user", content = "latest message", ts = 3),
        )
        val session = ChatSession(messages = history)
        val assembler = ChatPromptAssembler(object : ChatPromptEnvironment {
            override fun enabledExtensions(): List<Extension> = emptyList()
            override fun extensionConfig(id: String) = ""
            override fun globalVariables(): Map<String, String> = emptyMap()
            override fun userDescription(world: String) = ""
            override fun worldInfoSettings() = WorldInfoSettings()
            override fun filesDir() = File(".")
        })
        val request = ChatPromptAssembler.Request(
            session = session,
            card = CharacterCard(name = "Character", description = "Summary: {{summary}}"),
            userName = "User",
            worldBooks = emptyList(),
            preset = null,
            promptRegex = emptyList(),
            buildHistory = history,
            promptHistory = history,
            trimContributedHistory = true,
            updateTimed = true,
            reasoning = ReasoningLevel.AUTO,
            modelId = "model",
            generationMessage = ChatMessage(role = "assistant", ts = 4),
            commitVariables = true,
        )
        val withoutSummary = assembler.assemble(request)
        val withLegacySummary = assembler.assemble(request.copy(session = session.copy(
            title = "Existing title",
            extState = mapOf("builtin.summarize" to
                """{"summary":"stale summary marker","covered":2}"""),
        )))

        assertEquals(withoutSummary.apiMessages, withLegacySummary.apiMessages)
        val prompt = withLegacySummary.apiMessages.joinToString("\n") { it.content }
        assertTrue(prompt.contains("original first message"))
        assertTrue(prompt.contains("original reply"))
        assertTrue(prompt.contains("latest message"))
        assertFalse(prompt.contains("stale summary marker"))
    }
}
