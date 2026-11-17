package me.rerere.momoly.ui.api

import java.net.URI
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ModelType

enum class ApiService(val displayName: String, val baseUrl: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com"),
    DOUBAO("豆包", "https://ark.cn-beijing.volces.com/api/v3"),
    QWEN("千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    KIMI("Kimi", "https://api.moonshot.cn/v1"),
}

enum class ApiConnectionProblem { ADDRESS, KEY, EMPTY }
class ApiConnectionException(val problem: ApiConnectionProblem) : IllegalArgumentException(problem.name)

fun normalizedApiBaseUrl(value: String): String {
    val address = value.trim().trimEnd('/')
    val uri = runCatching { URI(address) }.getOrNull()
    if (uri == null || uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        address.endsWith("/chat/completions") || address.endsWith("/models") || address.endsWith("/responses")) {
        throw ApiConnectionException(ApiConnectionProblem.ADDRESS)
    }
    return address
}

/** 连接只生成待确认配置，用户选中模型后才替换已保存设置。 */
suspend fun connectApi(runtime: ApiRuntime, baseUrl: String, apiKey: String, providerId: String): ApiProvider {
    val address = normalizedApiBaseUrl(baseUrl)
    val key = apiKey.trim()
    if (key.isBlank() || key.any { it.isISOControl() }) throw ApiConnectionException(ApiConnectionProblem.KEY)
    val provider = ApiProvider(
        id = providerId,
        name = ApiService.entries.firstOrNull { it.baseUrl == address }?.displayName ?: URI(address).host,
        baseUrl = address,
        apiKey = key,
    )
    val models = runtime.models(provider).filter { it.type == ModelType.CHAT && it.id.isNotBlank() }
        .distinctBy { it.id }.sortedBy { it.id }
    if (models.isEmpty()) throw ApiConnectionException(ApiConnectionProblem.EMPTY)
    return provider.copy(models = models)
}
