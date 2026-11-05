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
}
