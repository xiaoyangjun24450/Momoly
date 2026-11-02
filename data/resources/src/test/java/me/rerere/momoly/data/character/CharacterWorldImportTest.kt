package me.rerere.momoly.data.character

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import me.rerere.momoly.data.worldbook.WorldBookRepository
import org.json.JSONObject
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
        val data = JSONObject().put("name", name).put("character_book", JSONObject().put("name", world))
        val file = File(context.cacheDir, "$name.json").apply { writeText(JSONObject().put("data", data).toString()) }
        return Uri.fromFile(file)
    }

    private fun files(): Set<String> = context.filesDir.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(context.filesDir).path }.toSet()
}
