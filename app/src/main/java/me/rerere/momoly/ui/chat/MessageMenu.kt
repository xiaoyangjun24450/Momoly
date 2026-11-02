package me.rerere.momoly.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.momoly.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleStop
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.FileJson
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Volume2

// 消息长按菜单：常显图标行取消后，复制/重答/朗读等操作全部集中到这里，
// 因此顺序按使用频率排（复制最前，删除最末）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageMenu(
    onDismiss: () -> Unit,
    onCopy: () -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    speaking: Boolean = false,
    onSelectCopy: () -> Unit = {},
    onViewRequestBody: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDeleteCurrentVersion: () -> Unit = {},
    onDeleteAllVersions: () -> Unit = {},
    hasMultipleVersions: Boolean = true,
    canViewRequestBody: Boolean = false,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 复制正文：只复制当前版本内容，与旧的常显复制图标一致
            MenuRow(
                icon = {
                    Icon(
                        Lucide.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.copy),
                onClick = onCopy
            )

            MenuRow(
                icon = {
                    Icon(
                        Lucide.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.select_copy),
                onClick = { onSelectCopy() }
            )

            // 重新生成：null 表示当前消息不可重答（例如正在生成的那条）
            if (onRegenerate != null) {
                MenuRow(
                    icon = {
                        Icon(
                            Lucide.RotateCcw,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    label = stringResource(R.string.regenerate),
                    onClick = onRegenerate
                )
            }

            // 朗读/停止朗读：播放中切换成停止图标，文案同步切换
            if (onSpeak != null) {
                MenuRow(
                    icon = {
                        Icon(
                            if (speaking) Lucide.CircleStop else Lucide.Volume2,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    label = stringResource(if (speaking) R.string.stop_speaking else R.string.speak),
                    onClick = onSpeak
                )
            }

            if (canViewRequestBody) {
                MenuRow(
                    icon = {
                        Icon(
                            Lucide.FileJson,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    label = stringResource(R.string.request_body),
                    onClick = onViewRequestBody,
                )
            }

            MenuRow(
                icon = {
                    Icon(
                        Lucide.SquarePen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.edit),
                onClick = { onEdit() }
            )

            // 删除当前版本
            MenuRow(
                icon = {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                label = stringResource(R.string.delete_current_version),
                labelColor = MaterialTheme.colorScheme.error,
                onClick = { onDeleteCurrentVersion() }
            )

            // 删除全部版本，仅多版本时显示
            if (hasMultipleVersions) {
                MenuRow(
                    icon = {
                        Icon(
                            Lucide.Trash2,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    label = stringResource(R.string.delete_all_versions),
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { onDeleteAllVersions() }
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = labelColor
        )
    }
}
