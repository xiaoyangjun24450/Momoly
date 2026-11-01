package me.rerere.momoly.data

import android.content.Context
import org.json.JSONObject

/** 内置资源保持酒馆 JSON 格式，既能直接编辑，也能单独导入酒馆。 */
object OnlineChatDefaults {
    const val PRESET_NAME = "默认预设"
    const val WORLD_BOOK_ID = "momoly-online-chat-rules-v1"

    fun readPreset(context: Context): JSONObject = readAsset(context, "online-chat-preset.json")

    fun readWorldBook(context: Context): JSONObject = readAsset(context, "online-chat-worldbook.json")

    private fun readAsset(context: Context, name: String): JSONObject {
        val text = context.assets.open("defaults/$name").bufferedReader().use { it.readText() }
        return JSONObject(text)
    }
}
