package com.daisuke.readinglock.ui.block

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.data.BlockedApp
import com.daisuke.readinglock.data.BlockedDomain
import com.daisuke.readinglock.data.Settings
import com.daisuke.readinglock.domain.LogicalDayResolver
import com.daisuke.readinglock.system.AppUsage
import com.daisuke.readinglock.system.DnsVpnService
import com.daisuke.readinglock.system.InstalledApp
import com.daisuke.readinglock.ui.components.AppAvatar
import com.daisuke.readinglock.ui.components.Caption
import com.daisuke.readinglock.ui.components.Card
import com.daisuke.readinglock.ui.components.Pill
import com.daisuke.readinglock.ui.components.PrimaryButton
import com.daisuke.readinglock.ui.components.ScreenHeader
import com.daisuke.readinglock.ui.components.SectionLabel
import com.daisuke.readinglock.ui.components.dashedBorder
import com.daisuke.readinglock.ui.duration
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.ui.theme.rl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 3時間以上を赤で出す(デザイン 1o) */
private const val HEAVY_USE_MS = 3L * 60 * 60 * 1000

/**
 * URL を貼られても DnsFilter のサフィックス一致が効く形に均す。
 * 不正な入力は空文字を返す(呼び出し側で弾く)
 */
fun normalizeDomain(input: String): String {
    val host = input.trim().lowercase()
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
        .removePrefix("www.")
        .trim('.')
    return if (host.contains('.') && host.none { it.isWhitespace() }) host else ""
}

class BlockViewModel(private val app: Application) : ViewModel() {
    private val db = AppGraph.db

    private val usage = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            usage.value = AppUsage.weekly(app)
            installed.value = AppUsage.launchable(app)
        }
    }

    /** 直近7日間の使用時間が長い順。§6 のとおり使用時間は保存しない */
    val blockedApps = combine(db.blockedApps().observeAll(), usage) { apps, stats ->
        apps.map { it to (stats[it.packageName] ?: 0L) }.sortedByDescending { it.second }
    }

    val candidates = combine(db.blockedApps().observeAll(), installed, usage) { blocked, all, stats ->
        val known = blocked.map { it.packageName }.toSet()
        all.filter { it.packageName !in known }
            .map { it to (stats[it.packageName] ?: 0L) }
            .sortedByDescending { it.second }
    }

    val domains = db.blockedDomains().observeAll()
    val settings = AppGraph.settings.flow

    fun setEnabled(app: BlockedApp, enabled: Boolean) = viewModelScope.launch {
        db.blockedApps().upsert(app.copy(enabled = enabled))
    }

    fun block(apps: List<InstalledApp>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        apps.forEach { db.blockedApps().upsert(BlockedApp(it.packageName, it.appName, addedAt = now)) }
    }

    fun addDomain(domain: String) = viewModelScope.launch {
        val normalized = normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            db.blockedDomains().upsert(BlockedDomain(domain = normalized, addedAt = System.currentTimeMillis()))
        }
    }

    /** 削除は翌日 4:00 に実行(抜け道防止)。§5.2 */
    fun scheduleDelete(domain: String) = viewModelScope.launch {
        db.blockedDomains().scheduleDelete(domain, LogicalDayResolver.nextResetTime(System.currentTimeMillis()))
    }

    fun cancelDelete(domain: String) = viewModelScope.launch { db.blockedDomains().cancelDelete(domain) }

    /** 延長は即時 / 短縮は翌日 4:00。§3 */
    fun setQuota(minutes: Int) = viewModelScope.launch { AppGraph.engine.setQuota(minutes) }

    /** 解禁後の利用上限。0 は無制限 */
    fun setRelockLimit(minutes: Int) = viewModelScope.launch { AppGraph.engine.setRelockLimit(minutes) }

    /** サイトブロック(VPN)のON/OFF。OFF は即時停止、ON は(同意済みなら)即時起動 */
    fun setSiteBlock(enabled: Boolean) = viewModelScope.launch {
        AppGraph.settings.setSiteBlockEnabled(enabled)
        if (enabled) DnsVpnService.start(app) else DnsVpnService.stop(app)
    }
}

@Composable
fun blockViewModel(): BlockViewModel {
    val context = LocalContext.current.applicationContext as Application
    return viewModel { BlockViewModel(context) }
}

// ---------------------------------------------------------------- 1o / 1p

@Composable
fun BlockScreen(
    onBack: () -> Unit,
    onAddApps: () -> Unit,
    onAddSite: () -> Unit,
    vm: BlockViewModel = blockViewModel(),
) {
    var sites by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = rl.bg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        ) {
            ScreenHeader("ブロック設定", onBack)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Pill)
                    .background(rl.tabTrack)
                    .padding(4.dp),
            ) {
                Tab("アプリ", !sites, Modifier.weight(1f)) { sites = false }
                Tab("サイト", sites, Modifier.weight(1f)) { sites = true }
            }
            if (sites) {
                SitesTab(vm, onAddSite) { message ->
                    scope.launch { snackbar.showSnackbar(message) }
                }
            } else {
                AppsTab(vm, onAddApps)
            }
        }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .clip(Pill)
            .background(if (selected) rl.primary else Color.Transparent)
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) rl.onPrimary else rl.secondary,
        )
    }
}

@Composable
private fun AppsTab(vm: BlockViewModel, onAddApps: () -> Unit) {
    val apps by vm.blockedApps.collectAsState(emptyList())
    Caption("直近7日間の使用時間が長い順", Modifier.padding(start = 2.dp, top = 16.dp, bottom = 10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { (app, used) ->
            Card {
                AppAvatar(app.packageName, app.appName)
                Column(Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = rl.heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        duration(used),
                        fontSize = 11.5.sp,
                        color = if (used >= HEAVY_USE_MS) rl.red else rl.muted,
                    )
                }
                Switch(
                    checked = app.enabled,
                    onCheckedChange = { vm.setEnabled(app, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = rl.surface,
                        checkedTrackColor = rl.primary,
                        checkedBorderColor = rl.primary,
                        uncheckedThumbColor = rl.faint,
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = rl.faint,
                    ),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .dashedBorder(rl.dashed, 14.dp)
                .clickable(onClick = onAddApps)
                .padding(14.dp),
            Alignment.Center,
        ) {
            Text("+ ブロックするアプリを追加", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = rl.muted)
        }
    }
}

@Composable
private fun SitesTab(vm: BlockViewModel, onAddSite: () -> Unit, onNotice: (String) -> Unit) {
    val domains by vm.domains.collectAsState(emptyList())
    val settings by vm.settings.collectAsState(Settings())
    val context = LocalContext.current
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.setSiteBlock(true)
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Column(Modifier.weight(1f)) {
            Text("サイトブロック(VPN)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
            Text(
                if (settings.siteBlockEnabled) "端末内VPNでドメインをブロック中" else "停止中(下のドメインは効きません)",
                fontSize = 11.5.sp,
                color = if (settings.siteBlockEnabled) rl.muted else rl.red,
            )
        }
        Switch(
            checked = settings.siteBlockEnabled,
            onCheckedChange = { on ->
                if (on) {
                    val consent = DnsVpnService.prepareIntent(context)
                    if (consent != null) vpnConsent.launch(consent) else vm.setSiteBlock(true)
                } else {
                    vm.setSiteBlock(false)
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = rl.surface,
                checkedTrackColor = rl.primary,
                checkedBorderColor = rl.primary,
                uncheckedThumbColor = rl.faint,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = rl.faint,
            ),
        )
    }
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        domains.forEach { domain ->
            val pending = domain.pendingDeleteAt != null
            Card(Modifier.alpha(if (pending) 0.55f else 1f)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).border(2.5.dp, rl.red, RoundedCornerShape(50)))
                Text(
                    domain.domain,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = rl.heading,
                    textDecoration = if (pending) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pending) {
                    Text(
                        "取り消す",
                        modifier = Modifier.clickable { vm.cancelDelete(domain.domain) }.padding(6.dp),
                        fontSize = 12.sp,
                        color = rl.faint,
                    )
                } else {
                    Text(
                        "✕",
                        modifier = Modifier
                            .clickable {
                                vm.scheduleDelete(domain.domain)
                                onNotice("削除は明日 4:00 に反映されます")
                            }
                            .padding(horizontal = 6.dp),
                        fontSize = 15.sp,
                        color = rl.faint,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .dashedBorder(rl.dashed, 14.dp)
                .clickable(onClick = onAddSite)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+", fontSize = 14.sp, color = rl.muted)
            Text("ドメインを追加(例: example.com)", fontSize = 13.sp, color = rl.faint)
        }
    }

    SectionLabel("ノルマ時間", Modifier.padding(top = 28.dp, bottom = 10.dp))
    val shown = settings.quotaMinutesPending ?: settings.quotaMinutes
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rl.surface)
            .border(1.dp, rl.surfaceBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("1日の読書ノルマ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
            Spacer(Modifier.weight(1f))
            Stepper("−") { if (shown > 5) vm.setQuota(shown - 5) }
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "$shown",
                    fontFamily = Mincho,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = rl.heading,
                )
                Text("分", fontFamily = Mincho, fontSize = 13.sp, color = rl.heading)
            }
            Stepper("+") { vm.setQuota(shown + 5) }
        }
        if (settings.quotaMinutesPending != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(rl.amberBg)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text("短縮方向の変更は明日から反映されます", fontSize = 12.sp, color = rl.amberSub)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    val limit = settings.relockLimitMinutes
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rl.surface)
            .border(1.dp, rl.surfaceBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("解禁後の利用上限", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
            Spacer(Modifier.weight(1f))
            Stepper("−") { vm.setRelockLimit(if (limit <= 5) 0 else limit - 5) }
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    if (limit == 0) "なし" else "$limit",
                    fontFamily = Mincho,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = rl.heading,
                )
                if (limit > 0) Text("分", fontFamily = Mincho, fontSize = 13.sp, color = rl.heading)
            }
            Stepper("+") { vm.setRelockLimit(limit + 5) }
        }
        Caption(
            if (limit == 0) {
                "解禁後は使い放題です。上限を設けると、使いすぎたときに再ロックされます"
            } else {
                "達成後にブロック対象アプリを合計${limit}分使うと、" +
                    "もう一周のノルマ(合計${shown * 2}分)を達成するまで再びロックされます"
            },
            Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, rl.dashed, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Text(glyph, fontSize = 16.sp, color = rl.secondary)
    }
}

// ---------------------------------------------------------------- 2a アプリを追加

@Composable
fun AddAppsScreen(onClose: () -> Unit, vm: BlockViewModel = blockViewModel()) {
    val candidates by vm.candidates.collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val visible = candidates.filter { query.isBlank() || it.first.appName.contains(query, ignoreCase = true) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        ScreenHeader("ブロックするアプリを追加", onClose, backGlyph = "✕")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Pill,
            placeholder = { Text("アプリ名で検索", color = rl.faint, fontSize = 13.5.sp) },
        )
        Caption("よく使うアプリ(直近7日間・未ブロックのみ)", Modifier.padding(start = 2.dp, top = 18.dp, bottom = 10.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visible.forEach { (app, used) ->
                val selected = app.packageName in picked
                Card(
                    modifier = Modifier
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) rl.primary else rl.surfaceBorder,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            picked = if (selected) picked - app.packageName else picked + app.packageName
                        },
                ) {
                    AppAvatar(app.packageName, app.appName)
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = rl.heading,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            duration(used),
                            fontSize = 11.5.sp,
                            color = if (used >= HEAVY_USE_MS) rl.red else rl.muted,
                        )
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) rl.primary else Color.Transparent)
                            .border(
                                if (selected) 0.dp else 2.dp,
                                if (selected) Color.Transparent else rl.dashed,
                                RoundedCornerShape(8.dp),
                            ),
                        Alignment.Center,
                    ) {
                        if (selected) {
                            Text("✓", color = rl.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "${picked.size}件をブロックに追加",
            enabled = picked.isNotEmpty(),
            onClick = {
                vm.block(candidates.map { it.first }.filter { it.packageName in picked })
                onClose()
            },
        )
    }
}

// ---------------------------------------------------------------- 2b サイトを追加

private val SUGGESTED = listOf(
    "nicovideo.jp",
    "comic-walker.com",
    "piccoma.com",
    "manga-bank.org",
    "twitch.tv",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSiteScreen(onClose: () -> Unit, vm: BlockViewModel = blockViewModel()) {
    var domain by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        ScreenHeader("ブロックするサイトを追加", onClose, backGlyph = "✕")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = domain,
                onValueChange = { input ->
                    // URL のコピペ(一括入力)はその場でドメインに均す。1文字ずつの手入力は触らない
                    val pasted = input.length - domain.length > 1
                    domain = if (pasted && (input.contains("://") || input.contains('/'))) {
                        normalizeDomain(input).ifEmpty { input }
                    } else {
                        input
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.5.sp,
                ),
                placeholder = { Text("example.com", color = rl.faint, fontFamily = FontFamily.Monospace) },
            )
            Caption(
                "サブドメインも含めてブロックされます(www.example.com など)",
                Modifier.padding(start = 4.dp, top = 10.dp),
            )
            Caption("よくブロックされるサイト", Modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SUGGESTED.forEach { candidate ->
                    Box(
                        Modifier
                            .clip(Pill)
                            .border(1.dp, rl.dashed, Pill)
                            .clickable { domain = candidate }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(candidate, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = rl.secondary)
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(rl.surface)
                    .border(1.dp, rl.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).border(2.5.dp, rl.red, RoundedCornerShape(50)))
                Text(
                    "追加したドメインは即時ブロックに反映されます。削除は翌日 4:00 から有効です",
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = rl.secondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "このサイトをブロック",
            enabled = normalizeDomain(domain).isNotEmpty(),
            onClick = {
                vm.addDomain(domain)
                onClose()
            },
        )
    }
}
