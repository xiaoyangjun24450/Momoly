package me.rerere.momoly.ui.chat

import android.graphics.Bitmap
import android.net.Uri

data class ChatUserProfile(
    val name: String,
    val description: String,
    val avatarColor: Long,
    val avatar: Bitmap?,
    val configured: Boolean = true,
    val readFailed: Boolean = false,
)

interface ChatUserProfileDataSource {
    fun loadName(world: String? = null): String
    fun load(world: String? = null): ChatUserProfile
    fun save(name: String, description: String)
    suspend fun saveAvatar(uri: Uri): Bitmap?
    suspend fun deleteAvatar()
    fun initializeWorlds(worlds: List<String>)
    fun worlds(): List<String>
    suspend fun previewAvatar(uri: Uri): Bitmap?
    suspend fun saveWorld(world: String, name: String, description: String, avatar: Bitmap?, replaceAvatar: Boolean)
}

class ChatUserProfileController(
    private val dataSource: ChatUserProfileDataSource,
) {
    fun loadName(world: String? = null): String = dataSource.loadName(world)

    fun load(world: String? = null): ChatUserProfile = dataSource.load(world)

    fun save(name: String, description: String) =
        dataSource.save(name.trim().ifBlank { "user" }, description)

    suspend fun saveAvatar(uri: Uri): Bitmap? = dataSource.saveAvatar(uri)

    suspend fun deleteAvatar() = dataSource.deleteAvatar()
    fun initializeWorlds(worlds: List<String>) = dataSource.initializeWorlds(worlds)
    fun worlds(): List<String> = dataSource.worlds()
    suspend fun previewAvatar(uri: Uri): Bitmap? = dataSource.previewAvatar(uri)
    suspend fun saveWorld(world: String, name: String, description: String, avatar: Bitmap?, replaceAvatar: Boolean) =
        dataSource.saveWorld(world, name.trim().ifBlank { "user" }, description.trim(), avatar, replaceAvatar)
}
