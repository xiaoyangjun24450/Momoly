package me.rerere.momoly.ui.world

import android.content.Context
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.data.settings.WorldLifeStore
import me.rerere.momoly.domain.chat.ChatDataRepository

/** 游戏时间独立于消息版本保存，编辑正文不会改写时间。 */
internal class LifeChatRepository(private val context: Context, private val delegate: ChatDataRepository) : ChatDataRepository by delegate {
    private fun world(file: String) = WorldLifeStore.read(context).worlds.values.firstOrNull { file in it.actors }
    override suspend fun save(session: ChatSession) {
        world(session.charFile)?.let { w -> session.messages.forEach { WorldLifeStore.stamp(context,w.name,session.id,it.ts, if(it.role=="user") w.target else w.now) } }
        delegate.save(session)
    }
    override suspend fun putMessage(sessionId: String, message: ChatMessage) {
        delegate.getMetadata(sessionId)?.let { s -> world(s.charFile)?.let { WorldLifeStore.stamp(context,it.name,sessionId,message.ts, if(message.role=="user") it.target else it.now) } }
        delegate.putMessage(sessionId,message)
    }
    override suspend fun commitReply(sessionId: String, message: ChatMessage, timedWorldInfo: Map<String,Int>, localVariables: Map<String,String>?) {
        delegate.getMetadata(sessionId)?.let { s -> world(s.charFile)?.let { WorldLifeStore.stamp(context,it.name,sessionId,message.ts) } }
        delegate.commitReply(sessionId,message,timedWorldInfo,localVariables)
    }
}
