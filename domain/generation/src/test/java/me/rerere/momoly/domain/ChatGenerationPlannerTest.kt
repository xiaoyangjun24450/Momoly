package me.rerere.momoly.domain

import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGenerationPlannerTest {
    @Test
    fun sendUsesEntireHistoryAndCreatesAssistantMessage() {
        val messages = listOf(ChatMessage(role = "user", content = "hello", ts = 10))

        val plan = ChatGenerationPlanner.create(
            ChatSession(messages = messages),
            modelId = "model",
        )

        assertEquals(messages, plan.buildHistory)
        assertEquals(messages, plan.promptHistory)
        assertEquals("assistant", plan.message.role)
        assertEquals("model", plan.message.model)
        assertTrue(plan.updateTimedWorldInfo)
    }

}
