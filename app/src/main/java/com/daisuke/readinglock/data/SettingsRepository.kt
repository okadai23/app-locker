package com.daisuke.readinglock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.daisuke.readinglock.domain.QuotaRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

data class Settings(
    val quotaMinutes: Int = 30,
    val quotaMinutesPending: Int? = null,
    val theme: String = "system",
    /** セットアップウィザードの完了ステップ(ビットフラグ) */
    val setupCompletedSteps: Int = 0,
    val paperGraceSeconds: Int = 10,
    /** ホームの赤バナー用。Permissions の欠落コード。§9-3 / §9-4 */
    val warnings: Set<String> = emptySet(),
    /** サイトブロック(VPN)のユーザー設定。OFF 中は自動再起動も警告もしない */
    val siteBlockEnabled: Boolean = true,
    /** 解禁後にブロック対象アプリを使える上限。0 は無制限 */
    val relockLimitMinutes: Int = 30,
)

class SettingsRepository(private val context: Context) {

    val flow: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(
            quotaMinutes = p[QUOTA] ?: 30,
            quotaMinutesPending = p[QUOTA_PENDING],
            theme = p[THEME] ?: "system",
            setupCompletedSteps = p[SETUP_STEPS] ?: 0,
            paperGraceSeconds = p[PAPER_GRACE] ?: 10,
            warnings = p[WARNINGS] ?: emptySet(),
            siteBlockEnabled = p[SITE_BLOCK] ?: true,
            relockLimitMinutes = p[RELOCK_LIMIT] ?: 30,
        )
    }

    suspend fun current(): Settings = flow.first()

    /** 延長は即時、短縮は翌日4:00 反映(pending 保持)。§3 */
    suspend fun requestQuota(minutes: Int) {
        context.settingsStore.edit { p ->
            val (quota, pending) = QuotaRules.request(p[QUOTA] ?: 30, p[QUOTA_PENDING], minutes)
            p[QUOTA] = quota
            if (pending == null) p.remove(QUOTA_PENDING) else p[QUOTA_PENDING] = pending
        }
    }

    /** 4:00 リセット時に pending を適用し、適用後のノルマを返す */
    suspend fun applyPendingQuota(): Int {
        var applied = 30
        context.settingsStore.edit { p ->
            applied = QuotaRules.applyPending(p[QUOTA] ?: 30, p[QUOTA_PENDING])
            p[QUOTA] = applied
            p.remove(QUOTA_PENDING)
        }
        return applied
    }

    suspend fun setTheme(theme: String) {
        context.settingsStore.edit { it[THEME] = theme }
    }

    suspend fun markSetupStep(bit: Int) {
        context.settingsStore.edit { it[SETUP_STEPS] = (it[SETUP_STEPS] ?: 0) or bit }
    }

    suspend fun setPaperGraceSeconds(seconds: Int) {
        context.settingsStore.edit { it[PAPER_GRACE] = seconds }
    }

    suspend fun setWarnings(codes: Set<String>) {
        context.settingsStore.edit { it[WARNINGS] = codes }
    }

    suspend fun addWarning(code: String) {
        context.settingsStore.edit { it[WARNINGS] = (it[WARNINGS] ?: emptySet()) + code }
    }

    suspend fun setSiteBlockEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[SITE_BLOCK] = enabled }
    }

    suspend fun setRelockLimitMinutes(minutes: Int) {
        context.settingsStore.edit { it[RELOCK_LIMIT] = minutes.coerceAtLeast(0) }
    }

    private companion object {
        val QUOTA = intPreferencesKey("quota_minutes")
        val QUOTA_PENDING = intPreferencesKey("quota_minutes_pending")
        val THEME = stringPreferencesKey("theme")
        val SETUP_STEPS = intPreferencesKey("setup_completed_steps")
        val PAPER_GRACE = intPreferencesKey("paper_grace_seconds")
        val WARNINGS = stringSetPreferencesKey("warnings")
        val SITE_BLOCK = booleanPreferencesKey("site_block_enabled")
        val RELOCK_LIMIT = intPreferencesKey("relock_limit_minutes")
    }
}
