package me.rerere.momoly.data.world

/** 首页、聊天及后续提示词注入共用的世界状态快照。占位状态不能当作真实状态注入。 */
data class WorldContext(
    val worldName: String,
    val userLocation: String,
    val time: String,
    val characterId: String?,
    val characterLocation: String?,
    val isPlaceholder: Boolean,
)

fun interface WorldContextProvider {
    fun snapshot(worldName: String, characterId: String?): WorldContext
}

/** 阶段 1 只展示固定值；阶段 2 替换这个 provider 接入世界时间和位置。 */
object PlaceholderWorldContextProvider : WorldContextProvider {
    override fun snapshot(worldName: String, characterId: String?) = WorldContext(
        worldName = worldName,
        userLocation = "家",
        time = "08:00",
        characterId = characterId,
        characterLocation = characterId?.let { "家" },
        isPlaceholder = true,
    )
}
