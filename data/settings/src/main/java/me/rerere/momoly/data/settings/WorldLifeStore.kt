package me.rerere.momoly.data.settings

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import me.rerere.momoly.data.world.*

/** One process-wide writer. A failed read never silently replaces an existing save. */
object WorldLifeStore {
    private val states = mutableMapOf<String, MutableStateFlow<LifeUniverse>>()
    private fun file(context: Context) = File(context.filesDir, "world-life-v1.json")
    @Synchronized fun observe(context: Context): StateFlow<LifeUniverse> = states.getOrPut(context.filesDir.absolutePath) {
        val file = file(context)
        val value = if (file.exists()) lifeJson.decodeFromString<LifeUniverse>(file.readText(Charsets.UTF_8)) else LifeUniverse()
        check(value.version == 2) { "世界存档版本不支持" }
        MutableStateFlow(value)
    }
    fun read(context: Context): LifeUniverse = observe(context).value
    @Synchronized fun update(context: Context, transform: (LifeUniverse) -> LifeUniverse): LifeUniverse {
        val flow = observe(context) as MutableStateFlow<LifeUniverse>
        val next = transform(flow.value)
        if (next == flow.value) return next
        persist(file(context),next)
        flow.value = next
        return next
    }
    private fun persist(file: File, next: LifeUniverse) {
        val temporary=File(file.path+".new")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(lifeJson.encodeToString(next).toByteArray(Charsets.UTF_8));output.fd.sync()
            }
            // AtomicFile.finishWrite swallows rename failures (notably on Windows test hosts).
            // NIO replaces atomically or throws; memory advances only after a successful commit.
            Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) { temporary.delete();throw error }
    }
    private fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun tick(context: Context, wall: Long = System.currentTimeMillis()) = update(context) {
        LifeRules.accrue(it, wall, SystemClock.elapsedRealtime(), boot(context))
    }
    fun select(context: Context, name: String, wall: Long = System.currentTimeMillis()) = update(context) {
        LifeRules.select(it, name, wall, SystemClock.elapsedRealtime(), boot(context))
    }
    fun world(context: Context, name: String, transform: (LifeWorld) -> LifeWorld) = update(context) { u ->
        val old = u.worlds[name] ?: return@update u
        u.copy(worlds = u.worlds + (name to transform(old)))
    }
    /** Register only committed cards; character import is the sole creation marker. */
    fun register(context: Context, worldName: String, map: WorldMap, facts: List<LifeFact>, file: String,
                 name: String, initial: LifeInitial, talkativeness: Float,
                 wall: Long = System.currentTimeMillis()) = update(context) { u ->
        if (file in u.worlds[worldName]?.actors.orEmpty()) return@update u
        val current=LifeRules.accrue(u,wall,SystemClock.elapsedRealtime(),boot(context))
        val time = syncClockTime(gameTime(initial.initialTime),wall)
        val world = current.worlds[worldName] ?: LifeWorld(worldName,map,time,time,facts=facts)
        val schedule=alignScheduleDates(initial.schedule,gameTime(initial.initialTime),world.now)
            .ifEmpty { listOf(LifeSchedule("autonomous",0,1440,"自主安排",initial.initialLocation)) }
        val actor = LifeActor(file,name,initial,initial.initialLocation,initial.background,schedule,
            initial.affinity, initial.mood,initial.moodIntensity,talkativeness,
            nextContact = world.now + 30 * GAME_MINUTE)
        val mergedFacts=(world.facts+facts).distinctBy { it.scope to it.id }
        val added = LifeRules.advance(world.copy(actors = world.actors + (file to actor),facts=mergedFacts), world.now)
        current.copy(worlds = current.worlds + (worldName to added))
    }
    fun stamp(context: Context, worldName: String, session: String, timestamp: Long, at: Long? = null) = world(context,worldName) { w ->
        val key = "$session:$timestamp"
        if (key in w.messageTimes) w else w.copy(messageTimes = w.messageTimes + (key to (at ?: w.now)))
    }
    fun time(context: Context, worldName: String, session: String, timestamp: Long): Long? =
        read(context).worlds[worldName]?.messageTimes?.get("$session:$timestamp")
    @Synchronized fun forgetCacheForTests() { states.clear() }
}
