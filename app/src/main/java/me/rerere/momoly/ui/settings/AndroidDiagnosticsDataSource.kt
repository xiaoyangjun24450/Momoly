package me.rerere.momoly.ui.settings

import android.content.Context
import me.rerere.momoly.core.diagnostics.CrashReportStore
import me.rerere.momoly.core.diagnostics.SafeLog
import me.rerere.momoly.data.diagnostics.RemoteDiagnostics

internal class AndroidDiagnosticsDataSource(
    private val context: Context,
) : DiagnosticsDataSource {
    override fun load(): DiagnosticsState = DiagnosticsState(
        remoteAvailable = RemoteDiagnostics.isAvailable(context),
        remoteEnabled = RemoteDiagnostics.isEnabled(context),
        report = runCatching { CrashReportStore.readLatest(context) }.getOrNull(),
        safeLogCount = SafeLog.snapshot().size,
    )

    override fun setRemoteEnabled(enabled: Boolean): Boolean = RemoteDiagnostics.setEnabled(context, enabled)
    override fun clearReport(): Boolean = CrashReportStore.clear(context)
    override fun safeLogsText(): String = SafeLog.format(SafeLog.snapshot())
}
