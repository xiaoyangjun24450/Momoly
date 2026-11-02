package me.rerere.momoly.data

import android.content.Context
import org.json.JSONObject

/** 内置资源保持酒馆 JSON 格式，既能直接编辑，也能单独导入酒馆。 */
object OnlineChatDefaults {
    const val PRESET_NAME = "默认预设"
    const val WORLD_BOOK_ID = "momoly-online-chat-rules-v1"

    fun readPreset(context: Context): JSONObject = readAsset(context, "online-chat-preset.json")

    fun readWorldBook(context: Context): JSONObject = readAsset(context, "online-chat-worldbook.json")

    /** 只用于已识别的内置资源；缺少版本的旧默认也升级，同版编辑不在每次加载时重置。 */
    fun needsUpdate(installed: JSONObject, bundled: JSONObject): Boolean {
        val current = installed.optJSONObject("extensions")?.optJSONObject("momoly")
        val target = bundled.getJSONObject("extensions").getJSONObject("momoly")
        val version = if (current?.optString("purpose") == target.getString("purpose")) {
            current.optInt("version", 0)
        } else 0
        return version < target.getInt("version")
    }

    private fun readAsset(context: Context, name: String): JSONObject {
        val text = context.assets.open("defaults/$name").bufferedReader().use { it.readText() }
        return JSONObject(text)
    }
}
