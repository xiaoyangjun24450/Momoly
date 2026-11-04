package me.rerere.momoly.data.world

import kotlinx.serialization.Serializable

@Serializable data class LifeFactChange(val id: String, val content: String, val evidence: String)
@Serializable data class LifeTurn(
    val messages: List<String> = emptyList(), val mood: String? = null, val moodIntensity: Int? = null,
    val affinityDelta: Int = 0, val reason: String = "", val evidence: String = "",
    val background: String? = null, val addSchedules: List<LifeSchedule> = emptyList(),
    val cancelSchedules: List<String> = emptyList(), val facts: List<LifeFactChange> = emptyList(),
    val dailySchedules: List<LifeSchedule> = emptyList(),
    val agreementEvidence: String = "",
)

/** Only validated effects from actually delivered text enter world state. */
fun applyLifeTurn(w: LifeWorld, file: String, turn: LifeTurn, inputText: String, delivered: String, event: String): LifeWorld {
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
        val affinity = (actor.affinity + turn.affinityDelta.coerceIn(-3,3)).coerceIn(0,100)
        record("affinity",actor.affinity,affinity)
        val mood = turn.mood?.takeIf { it.isNotBlank() && it.length <= 30 } ?: actor.mood
        val intensity = turn.moodIntensity?.coerceIn(0,100) ?: actor.moodIntensity
        record("mood","${actor.mood}:${actor.moodIntensity}","$mood:$intensity")
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
