package me.rerere.momoly.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.momoly.data.settings.WorldUserProfileStore
import me.rerere.momoly.data.settings.UserAvatarStore
import me.rerere.momoly.data.settings.UserProfileStore

internal class AndroidChatUserProfileDataSource(
    private val context: Context,
) : ChatUserProfileDataSource {
    override fun loadName(world: String?): String =
        if (world == null) UserProfileStore.getName(context) else WorldUserProfileStore.load(context, world).name

    override fun load(world: String?): ChatUserProfile = if (world != null) {
        val profile = WorldUserProfileStore.load(context, world)
        ChatUserProfile(profile.name, profile.description, UserProfileStore.getAvatarColor(context),
            WorldUserProfileStore.avatar(context, world), profile.configured, profile.readFailed)
    } else ChatUserProfile(
        name = UserProfileStore.getName(context),
        description = UserProfileStore.getDescription(context),
        avatarColor = UserProfileStore.getAvatarColor(context),
        avatar = UserAvatarStore.load(context),
    )

    override fun initializeWorlds(worlds: List<String>) = WorldUserProfileStore.initializeExisting(context, worlds)
    override fun worlds(): List<String> = WorldUserProfileStore.worlds(context)
    override suspend fun previewAvatar(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        UserAvatarStore.decodeScaled(context, uri)
    }
    override suspend fun saveWorld(world: String, name: String, description: String, avatar: Bitmap?, replaceAvatar: Boolean) =
        withContext(Dispatchers.IO) { WorldUserProfileStore.save(context, world, name, description, avatar, replaceAvatar) }

    override fun save(name: String, description: String) {
        UserProfileStore.setName(context, name)
        UserProfileStore.setDescription(context, description)
    }

    override suspend fun saveAvatar(uri: Uri): Bitmap? = UserAvatarStore.save(context, uri)

    override suspend fun deleteAvatar() {
        UserAvatarStore.delete(context)
    }
}
