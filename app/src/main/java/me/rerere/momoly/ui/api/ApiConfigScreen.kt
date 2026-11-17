package me.rerere.momoly.ui.api

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.momoly.R
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.chatModelSelection
import me.rerere.momoly.di.LocalAppContainer
import me.rerere.momoly.ui.components.AppIconButton
import me.rerere.momoly.ui.components.SettingsSubPage

@Composable
fun ApiConfigScreen(onBack: () -> Unit) {
    ApiConnectionPage(LocalAppContainer.current.features.apiConfig, AndroidApiRuntime, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiConnectionPage(controller: ApiConfigController, runtime: ApiRuntime, onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val initial = remember(controller) { controller.load() }
    var config by remember(controller) { mutableStateOf(initial.config) }
    val initialProvider = remember(controller) {
        config.chatModelSelection()?.first ?: config.providers.firstOrNull { it.type == "openai" && it.apiKey.isNotBlank() }
    }
    val providerId = remember(controller) { initialProvider?.id ?: java.util.UUID.randomUUID().toString() }
    var address by remember(controller) { mutableStateOf(initialProvider?.baseUrl ?: ApiService.DEEPSEEK.baseUrl) }
    var apiKey by remember(controller) { mutableStateOf(initialProvider?.apiKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf<ApiProvider?>(null) }
    var showModels by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    val selected = config.chatModelSelection()
    val service = ApiService.entries.firstOrNull { it.baseUrl == address.trim().trimEnd('/') }
    LaunchedEffect(initial.recovered) {
        if (initial.recovered) Toast.makeText(context, R.string.api_config_recovered, Toast.LENGTH_LONG).show()
    }
    BackHandler(onBack = onBack)
    fun changed() { connected = null; error = null }

    SettingsSubPage(stringResource(R.string.api_config), onBack) {
        Text(stringResource(R.string.api_setup_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.api_setup_connection), style = MaterialTheme.typography.titleMedium)
                service?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderIcon(it.name.lowercase(), size = 24.dp)
                        Text(serviceLabel(it), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = address, onValueChange = { address = it; changed() }, enabled = !connecting,
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        label = { Text(stringResource(R.string.api_setup_address)) },
                        supportingText = { Text(stringResource(R.string.api_setup_address_hint)) },
                        trailingIcon = {
                            AppIconButton(Lucide.ChevronDown, stringResource(R.string.api_setup_service),
                                onClick = { keyboard?.hide(); expanded = !expanded }, enabled = !connecting,
                                modifier = Modifier.testTag("api_presets"), size = 48.dp)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("api_address"),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
                        ApiService.entries.forEach { preset ->
                            DropdownMenuItem(text = { Text(serviceLabel(preset)) },
                                leadingIcon = { ProviderIcon(preset.name.lowercase(), size = 24.dp) }, onClick = {
                                if (address != preset.baseUrl) { address = preset.baseUrl; apiKey = ""; changed() }
                                expanded = false
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.api_setup_custom)) }, onClick = {
                            address = ""; apiKey = ""; changed(); expanded = false
                        })
                    }
                }
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it; changed() },
                    label = { Text(stringResource(R.string.api_key_label)) },
                    singleLine = true, enabled = !connecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        AppIconButton(if (showKey) Lucide.EyeOff else Lucide.Eye,
                            stringResource(if (showKey) R.string.api_setup_hide_key else R.string.api_setup_show_key),
                            onClick = { showKey = !showKey }, size = 48.dp)
                    }, modifier = Modifier.fillMaxWidth().testTag("api_key"),
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium) }
                Button(
                    enabled = !connecting && address.isNotBlank() && apiKey.isNotBlank(),
                    onClick = {
                        keyboard?.hide(); connecting = true; error = null; connected = null
                        scope.launch {
                            try {
                                connected = connectApi(runtime, address, apiKey, providerId)
                                query = ""; showModels = true
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { error = connectionErrorMessage(e)
                            } finally { connecting = false }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("api_connect"),
                ) {
                    if (connecting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (connecting) R.string.api_setup_connecting else R.string.api_setup_connect))
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.api_setup_model), style = MaterialTheme.typography.titleMedium)
                Text(selected?.second?.id ?: stringResource(R.string.api_setup_not_configured),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(if (selected == null) R.string.api_setup_model_hint else R.string.api_setup_model_saved),
                    style = MaterialTheme.typography.bodyMedium)
                if (connected != null) {
                    TextButton(onClick = { query = ""; showModels = true }) {
                        Text(stringResource(R.string.api_setup_choose_model))
                    }
                }
            }
        }
    }
    val candidate = connected
    if (showModels && candidate != null) {
        ModalBottomSheet(onDismissRequest = { showModels = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.api_setup_choose_model), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.api_setup_select_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(query, { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.api_setup_search)) }, modifier = Modifier.fillMaxWidth().testTag("api_model_search"))
                val models = candidate.models.filter { it.id.contains(query, true) || it.name.contains(query, true) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (models.isEmpty()) item { Text(stringResource(R.string.api_setup_no_match), Modifier.padding(vertical = 16.dp)) }
                    items(models, key = { it.id }) { model ->
                        TextButton(onClick = {
                            try {
                                config = controller.selectConnectedModel(candidate, model.id)
                                address = candidate.baseUrl; apiKey = candidate.apiKey
                                showModels = false; error = null
                            } catch (_: Exception) {
                                showModels = false; error = R.string.api_setup_save_failed
                            }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("api_model_${model.id}")) {
                            Text(model.id, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            if (config.currentModel == "${candidate.id}::${model.id}") {
                                Icon(Lucide.Check, null, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun serviceLabel(service: ApiService): String = when (service) {
    ApiService.DOUBAO -> stringResource(R.string.api_setup_doubao)
    ApiService.QWEN -> stringResource(R.string.api_setup_qwen)
    else -> service.displayName
}

private fun connectionErrorMessage(error: Exception): Int = when {
    error is ApiConnectionException -> when (error.problem) {
        ApiConnectionProblem.ADDRESS -> R.string.api_setup_invalid_address
        ApiConnectionProblem.KEY -> R.string.api_setup_invalid_key
        ApiConnectionProblem.EMPTY -> R.string.api_setup_empty
    }
    error.message?.startsWith("HTTP 401:") == true || error.message?.startsWith("HTTP 403:") == true -> R.string.api_setup_auth_failed
    error.message?.startsWith("HTTP 404:") == true -> R.string.api_setup_not_found
    error.message?.startsWith("HTTP 429:") == true -> R.string.api_setup_rate_limit
    else -> R.string.api_setup_failed
}
