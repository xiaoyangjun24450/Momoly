package me.rerere.momoly.data.world

import kotlinx.serialization.Serializable

@Serializable data class LifeFactChange(val id: String, val content: String, val evidence: String)
@Serializable data class AbsenceSuggestion(val reason: String = "", val returnsAt: String? = null, val evidence: String = "")
@Serializable data class LifeTurn(
    val messages: List<String> = emptyList(), val mood: String? = null, val moodIntensity: Int? = null,
    val affinityDelta: Int = 0, val reason: String = "", val evidence: String = "",
    val background: String? = null, val addSchedules: List<LifeSchedule> = emptyList(),
    val cancelSchedules: List<String> = emptyList(), val facts: List<LifeFactChange> = emptyList(),
    val dailySchedules: List<LifeSchedule> = emptyList(),
    val agreementEvidence: String = "",
    val expectsReply: Boolean = false, val replyEvidence: String = "",
    val responseProfile: ResponseProfile? = null,
    val absence: AbsenceSuggestion? = null,
    val anxietyDelta: Int = 0, val irritationDelta: Int = 0,
)

/** Only validated effects from actually delivered text enter world state. */
fun applyLifeTurn(w: LifeWorld, file: String, turn: LifeTurn, inputText: String, delivered: String, event: String,
                  inputMessages: List<me.rerere.momoly.data.chat.ChatMessage> = emptyList()): LifeWorld {
    if (w.revisions.any { it.event == event }) return w
    var actor = w.actors.getValue(file)
    val changes = mutableListOf<LifeRevision>()
    fun record(field: String, before: Any?, after: Any?, reason: String = turn.reason) {
        if (before != after) changes += LifeRevision(event,w.now,file,field,before.toString(),after.toString(),reason)
    }
    val grounded = turn.reason.isNotBlank() && turn.evidence.isNotBlank() &&
        (inputText.contains(turn.evidence) || delivered.contains(turn.evidence))
    if (actor.initial.schedule.isEmpty() && actor.schedule.all { it.id=="autonomous" } &&
        turn.dailySchedules.isNotEmpty() && turn.dailySchedules.all { it.date.isBlank() } &&
        runCatching { validateSchedules(turn.dailySchedules,w.map) }.isSuccess) {
        record("schedule",actor.schedule,turn.dailySchedules,"根据角色原设定建立日常安排")
        actor=actor.copy(schedule=turn.dailySchedules)
    }
    if (grounded) {
        // 等待损失已由时钟结算，角色自己编的解释不能再加减一次。
        val emotionalChange = actor.relationship.waiting == null || inputText.isNotBlank()
        val affinity = (actor.affinity + if(emotionalChange) turn.affinityDelta.coerceIn(-3,3) else 0).coerceIn(0,100)
        record("affinity",actor.affinity,affinity)
        val mood = turn.mood?.takeIf { emotionalChange && it.isNotBlank() && it.length <= 30 } ?: actor.mood
        val intensity = if(emotionalChange) turn.moodIntensity?.coerceIn(0,100) ?: actor.moodIntensity else actor.moodIntensity
        val background = turn.background?.takeIf { it.isNotBlank() && it.length <= 600 } ?: actor.background
        record("background",actor.background,background)
        actor = actor.copy(affinity=affinity,mood=mood,moodIntensity=intensity,background=background)
        // Dated appointments require explicit agreement in both sides of this delivered exchange.
        if (inputText.isNotBlank() && turn.agreementEvidence.isNotBlank() &&
            turn.agreementEvidence in delivered && turn.evidence in inputText) {
            val added = turn.addSchedules.filter { s ->
                s.date.isNotBlank() && runCatching { gameTime("${s.date}T00:00:00") + s.start * GAME_MINUTE >= w.now }.getOrDefault(false) &&
                    actor.schedule.none { old -> old.fixed && (old.date.isBlank() || old.date==s.date) && old.start<s.end && s.start<old.end }
            }
            val kept = actor.schedule.filterNot { it.id in turn.cancelSchedules && !it.fixed }
            val proposed = kept.filterNot { old -> added.any { it.id == old.id } && !old.fixed } + added.filter { n -> kept.none { it.id == n.id && it.fixed } }
            if (runCatching { validateSchedules(proposed,w.map) }.isSuccess) {
                record("schedule",actor.schedule,proposed)
                actor = actor.copy(schedule=proposed)
            }
        }
    }
    var relationship = actor.relationship
    if (relationship.profileSource == "default" && turn.responseProfile?.valid() == true &&
        (relationship.waiting == null || relationship.waiting.deliveredAt >= w.now)) {
        record("responseProfile", relationship.profile, turn.responseProfile, "首次依据人物设定初始化反应参数")
        relationship = relationship.copy(profile = turn.responseProfile, profileSource = "model",
            waiting = relationship.waiting?.let { it.copy(expectedReplyAt = maxOf(
                it.deliveredAt + turn.responseProfile.patienceMinutes * GAME_MINUTE, relationship.absence?.returnsAt ?: it.deliveredAt)) })
    }
    turn.absence?.let { absence ->
        val source = inputMessages.lastOrNull { it.role == "user" && absence.evidence in it.content }?.ts
            ?: relationship.lastUserMessageTs.takeIf { inputMessages.isEmpty() }
        val returnsAt = absence.returnsAt?.let { runCatching { gameTime(it) }.getOrNull() }
        if (source != null && source > 0 && absence.reason.isNotBlank() && absence.reason.length <= 200 &&
            absence.evidence.isNotBlank() && absence.evidence.length <= 400 && absence.evidence in inputText &&
            (absence.returnsAt == null || (returnsAt != null && returnsAt > w.now && returnsAt <= w.now + 30L*1440*GAME_MINUTE))) {
            val known = KnownAbsence(absence.reason, returnsAt, source, absence.evidence)
            record("absence", relationship.absence, known, "用户已告知：${absence.evidence}")
            relationship = relationship.copy(absence = known,
                waiting = relationship.waiting?.let { it.copy(expectedReplyAt = maxOf(it.expectedReplyAt, returnsAt ?: it.expectedReplyAt)) })
        }
    }
    if (grounded && turn.evidence in inputText && relationship.lastUserMessageTs > relationship.lastAssessedInput) {
        fun change(value: Double, delta: Int): Double {
            val bounded = delta.coerceIn(-10, 10)
            return (value + bounded * if(bounded < 0) relationship.profile.recoverySpeed else 1.0).coerceIn(0.0, 100.0)
        }
        val anxiety = change(relationship.anxiety, turn.anxietyDelta)
        val irritation = change(relationship.irritation, turn.irritationDelta)
        record("anxiety", relationship.anxiety, anxiety)
        record("irritation", relationship.irritation, irritation)
        relationship = relationship.copy(anxiety = anxiety, irritation = irritation,
            lastAssessedInput = relationship.lastUserMessageTs,
            latestChange = turn.reason.take(300), latestChangeAt = w.now,
            stage = if(maxOf(anxiety, irritation) >= 5) "recovering" else "normal")
    }
    actor = RelationshipRules.reflectMood(actor.copy(relationship = relationship))
    val previousActor = w.actors.getValue(file)
    record("mood", "${previousActor.mood}:${previousActor.moodIntensity}", "${actor.mood}:${actor.moodIntensity}")
    // Private chat cannot silently promote claims to public facts. Only explicitly character-scoped mutable entries.
    val facts = w.facts.map { old ->
        val change = turn.facts.firstOrNull { it.id == old.id }
        if (old.mutable && old.binding.isBlank() && old.scope == file && change != null && change.content.length <= 2000 &&
            change.evidence.isNotBlank() && change.evidence in delivered) {
            record("fact:${old.id}",old.content,change.content,change.evidence)
            old.copy(content=change.content,source=event,revision=old.revision+1)
        } else old
    }
    return w.copy(actors=w.actors + (file to actor),facts=facts,revisions=w.revisions + changes)
}

/** 正常生活请求与纯正文补整理必须使用同一组状态规则。 */
const val RELATIONSHIP_OUTPUT_INSTRUCTIONS = """
等待与冷落损失由程序按时间结算，你不能再次因为未回复扣好感，也不能自行清零已有情绪。可根据本轮用户实际内容提出 anxietyDelta、irritationDelta（各 -10 到 10，负数为缓解），与 affinityDelta 共用 reason 和来自 userMessages 的逐字 evidence；不得按消息条数加分。
若本次正文确实提问、邀约或明确需要用户回应，返回 expectsReply=true 和 replyEvidence（正文中对应的一段逐字片段）；晚安、告别、单纯分享且无需回复时 false。程序只对实际送达的对应片段开始等待。
用户明确说将离开、忙碌或休息时，可返回 absence={"reason":"简短原因","returnsAt":"完整世界日期时间，未知则null","evidence":"用户原话"}；不能从沉默猜测原因或返回时间。用户给出相对时间时以当前世界时间换算。
仅在提示明确说人物反应参数尚未初始化时，可根据原人物设定返回一次 responseProfile={"patienceMinutes":60到4320整数,"sensitivity":0.25到2,"recoverySpeed":0.25到2}；1为中性倍率。之后固定，不随聊天随意改动。
"""
