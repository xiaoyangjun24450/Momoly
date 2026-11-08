package me.rerere.momoly.data.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

val lifeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
const val GAME_MINUTE = 60_000L

/** Local calendar encoding used by schedules; not an instant in UTC. */
fun realWorldTime(wall: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(wall), zone).toInstant(ZoneOffset.UTC).toEpochMilli()

/** Keep the world's date and use the phone's current local time of day. */
fun syncClockTime(worldTime: Long, wall: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
    gameDate(worldTime).toLocalDate().atTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(wall),zone).toLocalTime())
        .toInstant(ZoneOffset.UTC).toEpochMilli()

fun gameTime(text: String): Long = try {
    LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()
} catch (_: Exception) { throw IllegalArgumentException("初始时间须为完整日期时间，例如 2048-06-12T17:30:00") }

fun gameDate(time: Long): LocalDateTime = LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.UTC)
fun gameLabel(time: Long, date: Boolean = true): String = gameDate(time).format(
    DateTimeFormatter.ofPattern(if (date) "yyyy-MM-dd HH:mm" else "HH:mm"))
fun gameMessageLabel(previous: Long?, current: Long?): String? {
    if (current == null) return null
    val differentDay = previous == null || gameDate(previous).toLocalDate() != gameDate(current).toLocalDate()
    return if (differentDay || current - (previous ?: current) >= 5 * GAME_MINUTE)
        gameLabel(current, differentDay) else null
}

fun alignScheduleDates(schedules: List<LifeSchedule>, from: Long, to: Long): List<LifeSchedule> {
    val days = ChronoUnit.DAYS.between(gameDate(from).toLocalDate(), gameDate(to).toLocalDate())
    return schedules.map { s ->
        if (s.date.isBlank()) s else s.copy(date=java.time.LocalDate.parse(s.date).plusDays(days).toString())
    }
}

@Serializable data class WorldPlace(val id: String, val name: String, val region: String = "城区", val x: Float = .5f, val y: Float = .5f)
@Serializable data class WorldRoute(val from: String, val to: String, val minutes: Int)
@Serializable data class WorldMap(
    val version: Int = 1, val places: List<WorldPlace>, val routes: List<WorldRoute> = emptyList(),
    val userLocation: String, val climates: Map<String, List<String>> = emptyMap(),
) {
    fun validate(): WorldMap {
        require(version == 1) { "不支持的地图版本" }
        require(places.isNotEmpty() && places.size <= 500) { "地图须包含 1—500 个地点" }
        require(places.map { it.id }.distinct().size == places.size) { "地图地点编号重复" }
        require(places.all { it.id.isNotBlank() && it.name.isNotBlank() && it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }) { "地图地点缺少编号/名称，或坐标不在 0—1 之间" }
        require(places.any { it.id == userLocation }) { "地图的用户初始位置不存在" }
        require(routes.all { r -> r.from != r.to && r.minutes in 1..10080 && places.any { it.id == r.from } && places.any { it.id == r.to } }) { "地图路线的端点或耗时无效" }
        require(climates.values.all { it.isNotEmpty() && it.all(String::isNotBlank) }) { "气候的天气候选不能为空" }
        return this
    }
    fun name(id: String): String = places.firstOrNull { it.id == id }?.name ?: id
    fun travelMinutes(from: String, to: String): Int? {
        if (from == to) return 0
        val distances = mutableMapOf(from to 0)
        val remaining = places.map { it.id }.toMutableSet()
        while (remaining.isNotEmpty()) {
            val current = remaining.filter { it in distances }.minByOrNull { distances.getValue(it) } ?: return null
            if (current == to) return distances[current]
            remaining.remove(current)
            routes.forEach { route ->
                val next = when (current) { route.from -> route.to; route.to -> route.from; else -> null }
                if (next != null && next in remaining) distances[next] = minOf(distances[next] ?: Int.MAX_VALUE, distances.getValue(current) + route.minutes)
            }
        }
        return null
    }
}

/** Daily intervals are minutes after midnight; dated appointments use a yyyy-MM-dd date. */
@Serializable data class LifeSchedule(
    val id: String, val start: Int, val end: Int, val activity: String, val location: String,
    val date: String = "", val fixed: Boolean = false,
) {
    fun contains(time: Long): Boolean {
        val local = gameDate(time)
        return (date.isBlank() || date == local.toLocalDate().toString()) && local.hour * 60 + local.minute in start until end
    }
}

@Serializable data class LifeInitial(
    val version: Int = 1, val initialTime: String, val initialLocation: String,
    val background: String, val history: String,
    val schedule: List<LifeSchedule> = emptyList(), val affinity: Int = 30,
    val mood: String = "平静", val moodIntensity: Int = 50,
    val responseProfile: ResponseProfile? = null,
) {
    fun validate(map: WorldMap): LifeInitial {
        require(version == 1) { "不支持的角色生活版本" }
        gameTime(initialTime)
        require(map.places.any { it.id == initialLocation }) { "角色初始位置不在世界地图中：$initialLocation" }
        require(background.isNotBlank() && history.isNotBlank()) { "角色必须填写此时此刻（background）和前尘往事（history）" }
        require(affinity in 0..100 && moodIntensity in 0..100 && mood.isNotBlank()) { "初始好感/心情无效" }
        require(responseProfile?.valid() != false) { "等待耐心或情绪反应参数无效" }
        validateSchedules(schedule, map)
        require(schedule.all { map.travelMinutes(initialLocation,it.location)!=null }) { "初始位置无法到达日程地点" }
        return this
    }
}

fun validateSchedules(schedules: List<LifeSchedule>, map: WorldMap) {
    require(schedules.size <= 200 && schedules.map { it.id }.distinct().size == schedules.size) { "日程过多或编号重复" }
    schedules.forEach {
        require(it.id.isNotBlank() && it.start in 0..1439 && it.end in 1..1440 && it.start < it.end && it.activity.isNotBlank()) { "日程须有有效的起止时间段与活动" }
        require(map.places.any { p -> p.id == it.location }) { "日程地点不存在：${it.location}" }
        if (it.date.isNotBlank()) java.time.LocalDate.parse(it.date)
    }
    schedules.groupBy { it.date }.values.forEach { group ->
        val sorted = group.sortedBy { it.start }
        sorted.zipWithNext().forEach { (a,b) ->
            require(a.end <= b.start) { "日程时间冲突：${a.activity} / ${b.activity}" }
            val travel = map.travelMinutes(a.location, b.location)
            require(travel != null && travel < b.end - b.start) { "日程地点不可达或移动耗时超过活动区间" }
        }
    }
}

@Serializable data class LifeFact(
    val id: String, val title: String, val content: String, val mutable: Boolean = false,
    val scope: String = "public", val source: String = "import", val revision: Int = 0,
    val binding: String = "",
)
@Serializable data class LifeRevision(val event: String, val time: Long, val actor: String, val field: String, val before: String, val after: String, val reason: String)
@Serializable data class LifeTravel(val from: String, val to: String, val arrives: Long)
@Serializable data class LifeActor(
    val file: String, val name: String, val initial: LifeInitial, val location: String,
    val background: String, val schedule: List<LifeSchedule>, val affinity: Int,
    val mood: String, val moodIntensity: Int, val talkativeness: Float = .5f,
    val travel: LifeTravel? = null, val activeSchedule: String = "", val nextContact: Long,
    val sessionId: String = "", val pendingInput: Long = 0, val answeredInput: Long = 0,
    val inputReadyAt: Long = 0, val draft: String = "", val unread: Int = 0,
    val pendingAt: Long? = null,
    val relationship: LifeRelationship = LifeRelationship(),
)
@Serializable data class LifeDelivery(
    val sessionId: String, val message: me.rerere.momoly.data.chat.ChatMessage, val gameTime: Long,
    val replyEvidence: String = "", val startsContact: Boolean = false, val proactive: Boolean = false,
)
@Serializable data class LifeWorld(
    val name: String, val map: WorldMap, val now: Long, val target: Long,
    val actors: Map<String, LifeActor> = emptyMap(), val facts: List<LifeFact> = emptyList(),
    val revisions: List<LifeRevision> = emptyList(), val sequence: Long = 0,
    val messageTimes: Map<String, Long> = emptyMap(), val outbox: List<LifeDelivery> = emptyList(),
)
@Serializable data class LifeUniverse(
    val version: Int = 2, val active: String? = null, val wallAnchor: Long = 0,
    val elapsedAnchor: Long = 0, val boot: Int = -1, val worlds: Map<String, LifeWorld> = emptyMap(),
    val timeZone: String = "",
)

object LifeRules {
    fun accrue(u: LifeUniverse, wall: Long, elapsed: Long, boot: Int,
               zone: ZoneId = ZoneId.systemDefault()): LifeUniverse {
        require(u.version == 2) { "世界存档版本不支持" }
        val now = realWorldTime(wall, zone)
        val zoneChanged = u.timeZone.isNotBlank() && u.timeZone != zone.id
        val backwards = u.wallAnchor != 0L && wall < u.wallAnchor
        val previousZone = u.timeZone.takeIf(String::isNotBlank)?.let(ZoneId::of) ?: zone
        val days = if (u.wallAnchor == 0L) 0 else ChronoUnit.DAYS.between(
            gameDate(realWorldTime(u.wallAnchor,previousZone)).toLocalDate(),gameDate(now).toLocalDate())
        return u.copy(version=2, wallAnchor=wall, elapsedAnchor=elapsed, boot=boot, timeZone=zone.id,
            worlds=u.worlds.mapValues { (_, w) ->
                val target=syncClockTime(w.target+days*1440*GAME_MINUTE,wall,zone)
                if (u.wallAnchor == 0L || zoneChanged || backwards || target < w.now)
                    align(w, target)
                else w.copy(target=target)
            })
    }

    private fun align(w: LifeWorld, now: Long): LifeWorld {
        val actors = w.actors.mapValues { (_, a) -> a.copy(
            nextContact=now+(a.nextContact-w.now).coerceAtLeast(0), activeSchedule="",
            pendingAt=if (a.pendingInput>a.answeredInput) now else null,
            relationship=RelationshipRules.rebase(a.relationship, now-w.now),
            travel=a.travel?.let { it.copy(arrives=now+(it.arrives-w.now).coerceAtLeast(0)) }) }
        val aligned=advance(w.copy(now=now, target=now, actors=actors), now)
        return aligned.copy(actors=aligned.actors.mapValues { (_, a) -> a.copy(
            background=a.travel?.let { "正在从${w.map.name(it.from)}前往${w.map.name(it.to)}。" }
                ?: "现在在${w.map.name(a.location)}，${activity(a,now)?.activity ?: "自主安排"}。") })
    }
    fun select(u: LifeUniverse, name: String, wall: Long, elapsed: Long, boot: Int): LifeUniverse =
        accrue(u, wall, elapsed, boot).copy(active = name)

    fun activity(a: LifeActor, at: Long): LifeSchedule? = a.schedule.firstOrNull { it.date.isNotBlank() && it.contains(at) }
        ?: a.schedule.firstOrNull { it.date.isBlank() && it.contains(at) }
    fun inputIsDue(w: LifeWorld, a: LifeActor): Boolean =
        (a.pendingAt ?: w.messageTimes["${a.sessionId}:${a.pendingInput}"] ?: w.now) <= w.now
    fun weather(w: LifeWorld, location: String, at: Long = w.now): String {
        val region = w.map.places.firstOrNull { it.id == location }?.region.orEmpty()
        val choices = w.map.climates[region] ?: listOf("晴", "多云", "阴", "小雨")
        val seed = (w.name + region + (at / (3 * 60 * GAME_MINUTE))).hashCode()
        return choices[Math.floorMod(seed, choices.size)]
    }
    fun nextContact(a: LifeActor, now: Long): Long {
        val minutes = (150 - a.talkativeness.coerceIn(0f,1f) * 90 - a.affinity / 5 + Math.floorMod(a.file.hashCode() + (now / GAME_MINUTE).toInt(), 25)).toLong().coerceAtLeast(25)
        return now + (minutes / a.relationship.contactFactor.coerceIn(.05, 1.0)).toLong() * GAME_MINUTE
    }
    fun nextBoundary(w: LifeWorld): Long {
        var next = minOf(w.target, (w.now / (30 * GAME_MINUTE) + 1) * 30 * GAME_MINUTE)
        val day = Math.floorDiv(w.now, 1440 * GAME_MINUTE) * 1440 * GAME_MINUTE
        w.actors.values.forEach { a ->
            a.travel?.arrives?.takeIf { it > w.now }?.let { next = minOf(next,it) }
            if (a.nextContact > w.now) next = minOf(next, a.nextContact)
            a.schedule.forEach { s ->
                if (s.date.isBlank() || s.date == gameDate(w.now).toLocalDate().toString()) {
                    listOf(day + s.start * GAME_MINUTE, day + s.end * GAME_MINUTE).filter { it > w.now }.forEach { next = minOf(next,it) }
                }
            }
        }
        return next
    }
    fun advance(w: LifeWorld, at: Long): LifeWorld {
        require(at in w.now..w.target)
        val actors = w.actors.mapValues { (_,original) ->
            var a = original
            val arrived=a.travel?.takeIf { it.arrives <= at }
            arrived?.let { a = a.copy(location = it.to, travel = null) }
            val s = activity(a, at)
            val key = s?.let { "${gameDate(at).toLocalDate()}:${it.id}" }.orEmpty()
            if (key != a.activeSchedule && a.travel == null) {
                val travel = s?.let { w.map.travelMinutes(a.location,it.location) }
                a = a.copy(activeSchedule = key, travel = if (travel != null && travel > 0) LifeTravel(a.location, s.location, at + travel * GAME_MINUTE) else null)
            }
            if ((original.activeSchedule.isNotEmpty() && key!=original.activeSchedule) || arrived!=null) {
                a=a.copy(background=a.travel?.let { "正在从${w.map.name(it.from)}前往${w.map.name(it.to)}。" }
                    ?: "现在在${w.map.name(a.location)}，${s?.activity ?: "自主安排"}。")
            }
            val decay = ((at / (60 * GAME_MINUTE)) - (w.now / (60 * GAME_MINUTE))).toInt().coerceAtLeast(0)
            if (decay > 0 && a.relationship.restingMood == null)
                a = a.copy(moodIntensity = (a.moodIntensity - decay * 3).coerceAtLeast(minOf(20, a.moodIntensity)))
            a
        }
        var updated=w.copy(now = at, actors = actors)
        val revisions=mutableListOf<LifeRevision>()
        val facts=w.facts.map { fact ->
            val value=if(!fact.mutable) null else when {
                fact.binding.startsWith("weather:") -> weather(updated,fact.binding.substringAfter(':'))
                fact.binding.startsWith("activity:") -> actors[fact.binding.substringAfter(':')]?.let { a ->
                    a.travel?.let { "前往${w.map.name(it.to)}" } ?: "${w.map.name(a.location)}：${activity(a,at)?.activity ?: "自主安排"}"
                }
                else -> null
            }
            if(value==null || value==fact.content) fact else {
                val source="clock:$at:${fact.id}"
                revisions+=LifeRevision(source,at,"world","fact:${fact.id}",fact.content,value,"时间/日程事件")
                fact.copy(content=value,source=source,revision=fact.revision+1)
            }
        }
        return RelationshipRules.advance(updated.copy(facts=facts,revisions=updated.revisions+revisions), at)
    }
    fun situation(w: LifeWorld, a: LifeActor): String = buildString {
        append(gameLabel(w.now)); append("，")
        append(a.travel?.let { "从${w.map.name(it.from)}前往${w.map.name(it.to)}，预计${gameLabel(it.arrives,false)}到达" } ?: w.map.name(a.location))
        append("，${weather(w,a.location)}。")
        append(activity(a,w.now)?.activity ?: "自主安排")
        append("。${a.background}")
    }
}
