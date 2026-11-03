package me.rerere.momoly.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorldUserProfileStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clean() {
        context.getSharedPreferences("world_user_profiles", 0).edit().clear().commit()
        context.getSharedPreferences("user_profile", 0).edit().clear().commit()
    }

    @Test fun legacyProfilesAreCopiedOnceAndNewWorldsStartEmpty() {
        UserProfileStore.setName(context, "Original")
        UserProfileStore.setDescription(context, "Student")
        WorldUserProfileStore.initializeExisting(context, listOf("Campus", "Fantasy", ""))
        WorldUserProfileStore.save(context, "Campus", "Alice", "Scholar", null, false)
        WorldUserProfileStore.initializeExisting(context, listOf("Campus", "New"))
        assertEquals("Alice", WorldUserProfileStore.load(context, "Campus").name)
        assertEquals("Original", WorldUserProfileStore.load(context, "Fantasy").name)
        assertEquals("Student", WorldUserProfileStore.load(context, "").description)
        assertFalse(WorldUserProfileStore.load(context, "New").configured)
        assertEquals("Original", UserProfileStore.getName(context))
    }

    @Test fun avatarMigrationAndReplacementDoNotModifyOtherWorldsOrLegacy() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val legacy = File(context.filesDir, "legacy.jpg")
        legacy.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        UserProfileStore.setAvatarPath(context, legacy.path)
        WorldUserProfileStore.initializeExisting(context, listOf("A", "B"))
        val a = WorldUserProfileStore.load(context, "A").avatarFile
        val b = WorldUserProfileStore.load(context, "B").avatarFile
        assertNotEquals(a, b)
        WorldUserProfileStore.save(context, "A", "Alice", "", null, true)
        assertNull(WorldUserProfileStore.avatar(context, "A"))
        assertNotNull(WorldUserProfileStore.avatar(context, "B"))
        assertTrue(legacy.isFile)
        WorldUserProfileStore.save(context, "A", "Alice", "", bitmap, true)
        assertNotNull(WorldUserProfileStore.avatar(context, "A"))
        assertEquals(b, WorldUserProfileStore.load(context, "B").avatarFile)
    }

    @Test fun corruptProfileFallsBackWithoutOverwritingItsRawData() {
        val prefs = context.getSharedPreferences("world_user_profiles", 0)
        prefs.edit().putString("world:Broken", "invalid json").commit()
        val loaded = WorldUserProfileStore.load(context, "Broken")
        assertTrue(loaded.readFailed)
        assertEquals("user", loaded.name)
        assertEquals("invalid json", prefs.getString("world:Broken", null))
    }

    @Test fun worldNamesAndUnassignedWorldCannotCollideAndPersistAcrossContexts() {
        val names = listOf("", "a/b", "a_b", "仙侠", "world:仙侠")
        names.forEachIndexed { index, world ->
            WorldUserProfileStore.save(context, world, "user$index", "persona$index", null, false)
        }
        val reopened = context.createPackageContext(context.packageName, 0)
        names.forEachIndexed { index, world ->
            assertEquals("user$index", WorldUserProfileStore.load(reopened, world).name)
            assertEquals("persona$index", WorldUserProfileStore.load(reopened, world).description)
        }
        assertEquals(names.toSet(), WorldUserProfileStore.worlds(reopened).toSet())
    }
}
