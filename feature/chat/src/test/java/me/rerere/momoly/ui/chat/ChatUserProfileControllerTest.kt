package me.rerere.momoly.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUserProfileControllerTest {
    @Test
    fun saveNormalizesBlankNamesBeforeWriting() {
        val source = FakeProfileDataSource()
        val controller = ChatUserProfileController(source)

        controller.save("   ", "A profile")

        assertEquals("user", source.savedName)
        assertEquals("A profile", source.savedDescription)
    }

    private class FakeProfileDataSource : ChatUserProfileDataSource {
        var savedName: String? = null
        var savedDescription: String? = null

        override fun loadName(world: String?): String = "user"

        override fun load(world: String?) = ChatUserProfile("user", "", 0, null)

        override fun save(name: String, description: String) {
            savedName = name
            savedDescription = description
        }

        override suspend fun saveAvatar(uri: Uri): Bitmap? = null

        override suspend fun deleteAvatar() = Unit
        override fun initializeWorlds(worlds: List<String>) = Unit
        override fun worlds(): List<String> = emptyList()
        override suspend fun previewAvatar(uri: Uri): Bitmap? = null
        override suspend fun saveWorld(world: String, name: String, description: String, avatar: Bitmap?, replaceAvatar: Boolean) {
            save(name, description)
        }
    }
}
