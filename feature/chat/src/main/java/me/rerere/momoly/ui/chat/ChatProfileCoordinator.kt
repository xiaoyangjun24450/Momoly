package me.rerere.momoly.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 加载用户资料并同步刷新依赖资料的提示资源。 */
internal class ChatProfileCoordinator(
    private val scope: CoroutineScope,
    private val profile: ChatProfileStateHolder,
    private val promptContext: ChatPromptContextCoordinator,
) {
    private var revision = 0L
    fun reload() {
        val expected = ++revision
        val world = profile.world
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { profile.load(world) }
            if (expected == revision && world == profile.world) profile.apply(world, loaded)
        }
    }

    fun update(name: String, description: String) {
        scope.launch {
            profile.save(name, description)
            reload()
            promptContext.reloadGlobalRegex()
        }
    }
}
