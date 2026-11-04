package me.rerere.momoly.data.character

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import me.rerere.momoly.data.worldbook.WorldBookRepository
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.serialization.encodeToString
import me.rerere.momoly.data.world.*
import me.rerere.momoly.data.settings.WorldLifeStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CharacterWorldImportTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CharacterRepository.charsDir(context).deleteRecursively()
        WorldBookRepository.worldDir(context).deleteRecursively()
        File(context.filesDir,"world-life-v1.json").delete()
        WorldLifeStore.forgetCacheForTests()
    }

    @Test
    fun cancellationWritesNoContactOrWorldBook() = runBlocking {
        val before = files()
        var asked = ""
        try {
            CharacterRepository.import(context, source("Visitor", "Other world")) {
                asked = it
                false
            }
            fail("Import must be cancelled")
        } catch (_: CharacterImportCancelledException) { }
        assertEquals("Other world", asked)
        assertEquals(before, files())
        assertTrue(CharacterRepository.listNames(context).isEmpty())
        assertTrue(WorldBookRepository.listNames(context).isEmpty())
    }

    @Test
    fun approvedContactsWithSameBookKeepSameWorldDespiteDuplicateResourceNames() = runBlocking {
        val first = CharacterRepository.import(context, source("Alice", "Campus")) { true }
        val second = CharacterRepository.import(context, source("Bob", "Campus")) { true }
        assertEquals("Campus", first.worldName)
        assertEquals(first.worldName, second.worldName)
        assertEquals("Campus", CharacterRepository.load(context, "Bob").worldName)
        assertEquals(2, CharacterRepository.listNames(context).size)
    }

    @Test
    fun missingWorldBookFailsBeforeConfirmationOrWriting() = runBlocking {
        val file = File(context.cacheDir, "no-book.json").apply { writeText("""{"name":"Invalid"}""") }
        var called = false
        try {
            CharacterRepository.import(context, Uri.fromFile(file)) { called = true; true }
            fail("World book is required")
        } catch (_: IllegalArgumentException) { }
        assertFalse(called)
        assertTrue(CharacterRepository.listNames(context).isEmpty())
    }

    @Test
    fun existingCardsUseEmbeddedBookNameAndIgnoreGlobalRuleBookIds() {
        val card = CharacterParser.parse(JSONObject("""{
            "data":{"name":"Alice","character_book":{"name":"Campus"},
            "enabled_world_book_ids":["global-chat-rules","some-book-id"]}
        }"""))
        assertEquals("Campus", card.worldName)
    }

    private fun source(name: String, world: String): Uri {
        val map=WorldMap(places=listOf(WorldPlace("home","家")),userLocation="home")
        val initial=LifeInitial(initialTime="2048-06-12T17:30:00",initialLocation="home",background="休息中",history="学生")
        val entry=JSONObject().put("id",1).put("content",lifeJson.encodeToString(map))
            .put("extensions",JSONObject().put("momoly",JSONObject().put("kind","map")))
        val data = JSONObject().put("name", name)
            .put("extensions",JSONObject().put("momoly_life",JSONObject(lifeJson.encodeToString(initial))))
            .put("character_book", JSONObject().put("name", world).put("entries",JSONArray().put(entry)))
        val file = File(context.cacheDir, "$name.json").apply { writeText(JSONObject().put("data", data).toString()) }
        return Uri.fromFile(file)
    }

    @Test fun mapMissingRejectsWithoutAnyResourceWrite() = runBlocking {
        val uri=source("NoMap","Other")
        val f=File(uri.path!!);val root=JSONObject(f.readText())
        root.getJSONObject("data").getJSONObject("character_book").put("entries",JSONArray())
        f.writeText(root.toString())
        val before=files()
        val error=runCatching { CharacterRepository.import(context,uri) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("地图"))
        assertEquals(before,files())
    }
    @Test fun previewCancellationKeepsResourcesAndWorldUnchanged() = runBlocking {
        val before=files()
        var previewed=false
        val error=runCatching { CharacterRepository.import(context,source("Preview","Other"),confirmPreview={
            previewed=true;assertEquals("休息中",it.life!!.background);false
        }) }.exceptionOrNull()
        assertTrue(previewed)
        assertTrue(error is CharacterImportCancelledException)
        assertEquals(before,files())
        assertTrue(WorldLifeStore.read(context).worlds.isEmpty())
    }
    @Test fun existingWorldCanSupplyMapAndAdaptTimeWithoutReset() = runBlocking {
        val map=WorldMap(places=listOf(WorldPlace("home","家")),userLocation="home")
        val initial=LifeInitial(initialTime="2048-06-12T17:30:00",initialLocation="home",background="休息中",history="学生")
        WorldLifeStore.register(context,"Campus",map,emptyList(),"first","first",initial,.5f)
        val future=gameTime("2048-06-15T12:00:00")
        WorldLifeStore.world(context,"Campus") { it.copy(now=future,target=future) }
        val uri=source("Visitor","Campus");val f=File(uri.path!!);val root=JSONObject(f.readText())
        root.getJSONObject("data").getJSONObject("character_book").put("entries",JSONArray());f.writeText(root.toString())
        val card=CharacterRepository.import(context,uri)
        assertEquals(future,gameTime(card.life!!.initialTime))
        assertEquals(future,WorldLifeStore.read(context).worlds.getValue("Campus").now)
        assertTrue(card.life!!.background.contains("2048-06-15"))
    }
    private fun files(): Set<String> = context.filesDir.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(context.filesDir).path }.toSet()
}
