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

    @Before fun clean() {
        WorldLifeStore.forgetCacheForTests()
        save.delete()
    }

    @Test fun unsupportedVersionKeepsOriginalFile() {
        val original=lifeJson.encodeToString(LifeUniverse(version=9))
        save.writeText(original)
        assertThrows(IllegalStateException::class.java) { WorldLifeStore.read(context) }
        assertEquals(original,save.readText())
    }

    @Test fun waitingAndLossSurviveDiskReloadWithoutChargingTwice() {
        val at=gameTime("2048-06-12T12:00:00")
        val initial=LifeInitial(initialTime="2048-06-12T12:00:00",initialLocation="home",background="休息",history="旧识")
        val actor=LifeActor("a","甲",initial,"home","休息",emptyList(),80,"平静",50,nextContact=at,sessionId="s")
        val map=WorldMap(places=listOf(WorldPlace("home","家")),userLocation="home")
        var world=LifeWorld("w",map,at,at,actors=mapOf("a" to actor))
        world=RelationshipRules.delivered(world,LifeDelivery("s",me.rerere.momoly.data.chat.ChatMessage("assistant","在吗？",ts=100),at,"在吗？",true,true),at)
        world=RelationshipRules.advance(world,at+30*60*GAME_MINUTE)
        WorldLifeStore.update(context) { it.copy(active="w",worlds=mapOf("w" to world)) }
        WorldLifeStore.forgetCacheForTests()
        val loaded=WorldLifeStore.read(context).worlds.getValue("w")
        assertEquals(world,loaded)
        assertEquals(loaded,RelationshipRules.advance(loaded,at+30*60*GAME_MINUTE))
    }
}
