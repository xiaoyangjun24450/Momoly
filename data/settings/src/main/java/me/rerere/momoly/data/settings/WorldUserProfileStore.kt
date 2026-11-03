package me.rerere.momoly.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** 世界名称沿用角色库的世界标识；空字符串是“未分配世界”，不与全局资料混用。 */
object WorldUserProfileStore {
    data class Profile(
        val name: String = "user",
        val description: String = "",
        val avatarFile: String? = null,
        val configured: Boolean = false,
        val readFailed: Boolean = false,
    )

    private fun prefs(context: Context) = context.getSharedPreferences("world_user_profiles", Context.MODE_PRIVATE)
    private fun directory(context: Context) = File(context.filesDir, "world_user_avatars").also { it.mkdirs() }
    private fun key(world: String) = "world:$world"

    fun load(context: Context, world: String): Profile {
        val raw = prefs(context).getString(key(world), null) ?: return Profile()
        return try {
            val json = JSONObject(raw)
            require(json.getInt("version") == 1) { "Unsupported world profile version" }
            val avatar = json.optString("avatar").takeIf { it.isNotBlank() }
            require(avatar == null || File(avatar).name == avatar) { "Invalid avatar filename" }
            Profile(json.getString("name"), json.getString("description"), avatar, json.optBoolean("configured"))
        } catch (_: Exception) {
            // 原始内容保持不动；界面提示读取失败，只有用户明确保存才替换。
            Profile(readFailed = true)
        }
    }

    private fun encode(profile: Profile): String = JSONObject()
        .put("version", 1).put("name", profile.name).put("description", profile.description)
        .put("avatar", profile.avatarFile.orEmpty()).put("configured", profile.configured).toString()

    /** 一次性迁移已有世界，头像复制为独立文件；之后新建世界从空资料开始。 */
    @Synchronized
    fun initializeExisting(context: Context, worlds: List<String>) {
        val preferences = prefs(context)
        if (preferences.getBoolean("migrated", false)) return
        val legacyAvatar = UserProfileStore.getAvatarPath(context)?.let(::File)?.takeIf { it.isFile }
        val created = mutableListOf<File>()
        try {
            val editor = preferences.edit()
            worlds.distinct().filterNot { preferences.contains(key(it)) }.forEach { world ->
                val avatar = legacyAvatar?.let { original ->
                    File(directory(context), "${UUID.randomUUID()}.jpg").also {
                        created += it
                        original.copyTo(it)
                    }
                }
                editor.putString(key(world), encode(Profile(UserProfileStore.getName(context),
                    UserProfileStore.getDescription(context), avatar?.name, configured = true)))
            }
            check(editor.putBoolean("migrated", true).commit()) { "Unable to migrate world profiles" }
        } catch (error: Exception) {
            created.forEach(File::delete)
            throw error
        }
    }

    fun worlds(context: Context): List<String> = prefs(context).all.keys
        .filter { it.startsWith("world:") }.map { it.removePrefix("world:") }.sorted()

    fun avatar(context: Context, world: String): Bitmap? {
        val name = load(context, world).avatarFile ?: return null
        require(File(name).name == name) { "Invalid avatar filename" }
        val path = File(directory(context), name).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 新头像先写独立文件，资料一次提交；失败不覆盖旧资料或旧图片。 */
    @Synchronized
    fun save(context: Context, world: String, name: String, description: String,
             avatar: Bitmap?, replaceAvatar: Boolean) {
        val old = load(context, world)
        var created: File? = null
        try {
            if (replaceAvatar && avatar != null) {
                created = File(directory(context), "${UUID.randomUUID()}.jpg")
                created.outputStream().use { check(avatar.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            }
            val updated = Profile(name.trim().ifBlank { "user" }, description.trim(),
                if (replaceAvatar) created?.name else old.avatarFile, configured = true)
            check(prefs(context).edit().putString(key(world), encode(updated)).commit()) {
                "Unable to save world profile"
            }
        } catch (error: Exception) {
            created?.delete()
            throw error
        }
        if (replaceAvatar) old.avatarFile?.takeIf { File(it).name == it }
            ?.let { File(directory(context), it).delete() }
    }
}
