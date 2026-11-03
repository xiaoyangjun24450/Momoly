package me.rerere.momoly.ui.chat

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.momoly.data.speech.TtsUiState

data class ChatProfileState(
    val userName: String,
    val userAvatar: Bitmap?,
    val speakingTimestamp: Long?,
    val tts: TtsUiState,
)

/** 持有聊天界面使用的用户资料快照。 */
class ChatProfileStateHolder(
    private val controller: ChatUserProfileController,
    private val currentWorld: () -> String = { "" },
) {
    var name by mutableStateOf(controller.loadName())
        private set

    var avatar by mutableStateOf<Bitmap?>(null)
        private set

    var loadedWorld by mutableStateOf<String?>(null)
        private set
    val world get() = currentWorld()
    fun currentName(): String = if (loadedWorld == world) name else controller.loadName(world)
    fun load(world: String): ChatUserProfile = controller.load(world)

    fun apply(world: String, profile: ChatUserProfile) {
        loadedWorld = world
        name = profile.name
        avatar = profile.avatar
    }

    suspend fun save(name: String, description: String) {
        controller.saveWorld(world, name, description, null, replaceAvatar = false)
    }
}
