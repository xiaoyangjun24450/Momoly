package me.rerere.momoly.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.serialization.encodeToString
import me.rerere.momoly.data.world.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class WorldLifeStoreTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val save get()=File(context.filesDir,"world-life-v1.json")
    private val backup get()=File(save.path+".before-real-time")

    @Before fun clean() {
        WorldLifeStore.forgetCacheForTests()
        save.delete();backup.delete()
    }

    @Test fun legacyMigrationBacksUpOriginalAndDoesNotRepeatOnReopen() {
        val start=gameTime("2048-06-12T17:30:00")
        val map=WorldMap(places=listOf(WorldPlace("home","家")),userLocation="home")
        val world=LifeWorld("A",map,start,start+1440*GAME_MINUTE,messageTimes=mapOf("s:1" to start))
        val original=lifeJson.encodeToString(LifeUniverse(version=1,active="A",worlds=mapOf("A" to world)))
            .replace("\"actors\":{}","\"restUntil\":${world.target},\"actors\":{}")
        save.writeText(original)
        val before=realWorldTime()
        val migrated=WorldLifeStore.read(context)
        assertEquals(original,backup.readText())
        assertEquals(2,migrated.version)
        val current=migrated.worlds.getValue("A")
        assertTrue(current.now in before..realWorldTime())
        assertEquals(current.now,current.target)
        assertEquals(world.messageTimes,current.messageTimes)
        assertFalse(save.readText().contains("restUntil"))
        WorldLifeStore.forgetCacheForTests()
        assertEquals(migrated,WorldLifeStore.read(context))
        assertEquals(original,backup.readText())
    }

    @Test fun unsupportedVersionKeepsOriginalFile() {
        val original=lifeJson.encodeToString(LifeUniverse(version=9))
        save.writeText(original)
        assertThrows(IllegalStateException::class.java) { WorldLifeStore.read(context) }
        assertEquals(original,save.readText())
        assertFalse(backup.exists())
    }
}
