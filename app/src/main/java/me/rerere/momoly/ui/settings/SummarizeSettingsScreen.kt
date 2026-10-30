package me.rerere.momoly.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.momoly.R
import me.rerere.momoly.di.LocalAppContainer
import me.rerere.momoly.extension.builtin.SummarizeExtension
import me.rerere.momoly.ui.components.SettingsSubPage
import me.rerere.momoly.ui.components.Space8
import me.rerere.momoly.ui.components.Space12

/** 对话摘要设置：自动摘要开关 + 保留条数 / 目标长度 / 触发阈值。 */
@Composable
internal fun SummarizeSettingsScreen(onBack: () -> Unit) {
    val controller = LocalAppContainer.current.features.extensions
    var cfg by remember {
        mutableStateOf(SummarizeExtension.parseConfig(controller.config(SummarizeExtension.ID)))
    }
    fun save(next: SummarizeExtension.Config) {
        cfg = next
        controller.setConfig(SummarizeExtension.ID, SummarizeExtension.encodeConfig(next))
    }
    BackHandler(onBack = onBack)
    SettingsSubPage(stringResource(R.string.ext_summarize), onBack, spacing = Space12) {
        SettingsCard(stringResource(R.string.ext_summarize)) {
            SwitchRow(stringResource(R.string.ext_summarize_auto), cfg.auto) { save(cfg.copy(auto = it)) }
            NumberRow(stringResource(R.string.ext_summarize_keep_recent), cfg.keepRecent) { save(cfg.copy(keepRecent = it.coerceAtLeast(0))) }
            NumberRow(stringResource(R.string.ext_summarize_target_tokens), cfg.targetTokens) { save(cfg.copy(targetTokens = it.coerceAtLeast(1))) }
            NumberRow(stringResource(R.string.ext_summarize_trigger_tokens), cfg.triggerTokens) { save(cfg.copy(triggerTokens = it.coerceAtLeast(1))) }
        }
    }
}

/** 分组容器：标题 + 圆角卡片（与扩展设置页同款）。 */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space8)) {
        Text(title, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Space8))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Space12),
            verticalArrangement = Arrangement.spacedBy(Space8),
        ) { content() }
    }
}

@Composable
private fun NumberRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space12))
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter { it.isDigit() }
                onChange(text.toIntOrNull() ?: 0)
            },
            singleLine = true,
            modifier = Modifier.width(96.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
