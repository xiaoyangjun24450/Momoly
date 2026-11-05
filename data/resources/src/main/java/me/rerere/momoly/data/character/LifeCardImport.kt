package me.rerere.momoly.data.character

import android.content.Context
import kotlinx.serialization.encodeToString
import me.rerere.momoly.data.settings.WorldLifeStore
import me.rerere.momoly.data.world.*
import org.json.JSONObject

/** Momoly-specific card extension. No executable card content is evaluated here. */
object LifeCardImport {
    data class Prepared(val initial: LifeInitial, val map: WorldMap, val facts: List<LifeFact>)
    fun initial(data: JSONObject): LifeInitial? = data.optJSONObject("extensions")?.optJSONObject("momoly_life")?.let {
        lifeJson.decodeFromString<LifeInitial>(it.toString())
    }
    fun map(book: JSONObject): WorldMap? {
        val maps = entries(book).filter { it.optJSONObject("extensions")?.optJSONObject("momoly")?.optString("kind") == "map" }
        require(maps.size <= 1) { "世界书只能有一个地图条目" }
        return maps.singleOrNull()?.let {
            try { lifeJson.decodeFromString<WorldMap>(it.getString("content")).validate() }
            catch (e: Exception) { throw IllegalArgumentException("地图格式错误：${e.message}",e) }
        }
    }
    fun entries(book: JSONObject): List<JSONObject> {
        val array = book.optJSONArray("entries")
        if (array != null) return (0 until array.length()).map { array.getJSONObject(it) }
        val objects = book.optJSONObject("entries") ?: return emptyList()
        return objects.keys().asSequence().map { objects.getJSONObject(it) }.toList()
    }
    fun facts(book: JSONObject): List<LifeFact> = entries(book).mapNotNull { e ->
        val x = e.optJSONObject("extensions")?.optJSONObject("momoly") ?: return@mapNotNull null
        if (x.optString("kind") != "fact") return@mapNotNull null
        val id = x.getString("id")
        require(id.isNotBlank()) { "可变世界书条目编号不能为空" }
        LifeFact(id,e.optString("comment"),e.optString("content"),x.optBoolean("mutable"),x.optString("scope","public"),binding=x.optString("binding"))
    }.also { list -> require(list.map { it.id }.distinct().size == list.size) { "世界书事实编号重复" } }
    fun prepare(context: Context, data: JSONObject, book: JSONObject, world: String): Prepared {
        val initial = initial(data) ?: throw IllegalArgumentException("角色缺少初始时间、初始位置和背景（extensions.momoly_life）")
        val supplied = map(book)
        val wall=System.currentTimeMillis()
        val universe=WorldLifeStore.read(context)
        val existing = LifeRules.accrue(universe,wall,universe.elapsedAnchor,universe.boot).worlds[world]
        val map = existing?.map ?: supplied ?: throw IllegalArgumentException("无法导入：角色卡未附带世界地图，目标世界也没有可用地图。请补充世界地图后重试。")
        if (existing != null && supplied != null) require(supplied == existing.map) { "角色地图与已有世界地图不一致，请使用当前世界地图；不会覆盖或新增地点。" }
        initial.validate(map)
        val now = syncClockTime(existing?.target ?: gameTime(initial.initialTime),wall)
        val adapted = initial.copy(
            initialTime = gameDate(now).toString(),
            schedule = alignScheduleDates(initial.schedule,gameTime(initial.initialTime),now).filter { it.date.isBlank() ||
                gameTime("${it.date}T00:00:00") + it.end * GAME_MINUTE > now },
            background = "${gameLabel(now)}，${map.name(initial.initialLocation)}。${initial.history}\n" +
                existing?.facts.orEmpty().filter { it.scope == "public" }.joinToString("\n") { "${it.title}：${it.content}" } +
                "\n刚接入当前世界，尚未与这里的人共同经历新的事件。",
        )
        val extensions = data.optJSONObject("extensions") ?: JSONObject().also { data.put("extensions",it) }
        extensions.put("momoly_life",JSONObject(lifeJson.encodeToString(adapted)))
        // Keep the original scene as source material, never as today's running clock.
        if (!extensions.has("momoly_original_entry")) {
            extensions.put("momoly_original_entry",JSONObject().put("life",lifeJson.encodeToString(initial))
                .put("scenario",data.optString("scenario")).put("first_mes",data.optString("first_mes")))
        }
        return Prepared(adapted,map,facts(book))
    }
}
