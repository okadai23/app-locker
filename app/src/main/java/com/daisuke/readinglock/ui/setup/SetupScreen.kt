package com.daisuke.readinglock.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.system.DnsVpnService
import com.daisuke.readinglock.system.Permissions
import com.daisuke.readinglock.ui.components.PrimaryButton
import com.daisuke.readinglock.ui.components.TextLink
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.ui.theme.rl
import kotlinx.coroutines.launch

/** setup_completed_steps のビット。§6 DataStore */
private val STEP_BITS = intArrayOf(1, 2, 4, 8)

private data class Step(val title: String, val body: String, val cta: String)

private val STEPS = listOf(
    Step(
        "使用状況へのアクセス",
        "いま開いているアプリを知るための許可です。\nブロック対象の検知と、Kindle 読書の\n計測のどちらにも使います。",
        "設定画面を開く",
    ),
    Step(
        "他のアプリの上に表示",
        "ブロック対象を開いた瞬間に、読書ロックの\n画面を上から重ねます。この許可がないと\nロックそのものが働きません。",
        "設定画面を開く",
    ),
    Step(
        "電池最適化の対象から外す",
        "読書時間の計測とロックは常駐して動きます。\n端末に眠らされると計測が飛ぶため、\n最適化の対象から外してください。",
        "設定画面を開く",
    ),
    Step(
        "サイトブロックの有効化",
        "端末内 VPN でマンガサイトなどの\nドメインをブロックします。通信内容が\n外部に送られることはありません。",
        "VPN を有効にする",
    ),
)

fun setupComplete(context: Context): Boolean = (0..3).all { granted(context, it) }

private fun granted(context: Context, step: Int): Boolean = when (step) {
    0 -> Permissions.hasUsageAccess(context)
    1 -> Settings.canDrawOverlays(context)
    2 -> context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
    else -> DnsVpnService.prepareIntent(context) == null
}

@Composable
fun SetupScreen(onDone: () -> Unit, onRequestVpn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }

    // 保存フラグではなく毎回の実チェックで進める。§8
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            scope.launch {
                STEP_BITS.forEachIndexed { i, bit ->
                    if (granted(context, i)) AppGraph.settings.markSetupStep(bit)
                }
                val next = (0..3).firstOrNull { !granted(context, it) }
                if (next == null) onDone() else index = next
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val step = STEPS[index]
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("はじめの設定", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
            Spacer(Modifier.weight(1f))
            Text("${index + 1} / 4", fontSize = 12.sp, color = rl.muted)
        }
        Spacer(Modifier.weight(1f))
        StepIcon(index)
        Text(
            step.title,
            fontFamily = Mincho,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = rl.heading,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            step.body,
            fontSize = 13.sp,
            lineHeight = 25.sp,
            color = rl.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(step.cta, onClick = { open(context, index, onRequestVpn) })
        Row(
            Modifier.padding(top = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(width = if (i == index) 20.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (i <= index) rl.primary else rl.dotInactive),
                )
            }
        }
        TextLink("あとで設定する", onDone, Modifier.padding(top = 14.dp))
    }
}

private fun open(context: Context, step: Int, onRequestVpn: () -> Unit) {
    when (step) {
        0 -> start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        1 -> start(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        )
        // 直接リクエストする ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS は専用権限が要るので一覧を開く
        2 -> start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        else -> onRequestVpn()
    }
}

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

/** 手順ごとの丸アイコン。実アイコン素材を持たないので図形で組む */
@Composable
private fun StepIcon(step: Int) {
    Box(
        Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(50))
            .background(rl.surface)
            .border(1.dp, rl.surfaceBorder, RoundedCornerShape(50)),
        Alignment.Center,
    ) {
        when (step) {
            0 -> Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Bar(18.dp, rl.dashed)
                Bar(34.dp, rl.primary)
                Bar(26.dp, rl.red)
            }
            1 -> Box(Modifier.size(46.dp)) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(rl.dashed)
                        .align(Alignment.TopStart),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(rl.primary)
                        .align(Alignment.BottomEnd),
                )
            }
            2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(rl.primary),
                )
                Box(
                    Modifier
                        .size(width = 4.dp, height = 10.dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp, topStart = 0.dp, bottomStart = 0.dp))
                        .background(rl.dashed),
                )
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 26.dp, height = 12.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .border(
                            5.dp,
                            rl.primary,
                            RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                        ),
                )
                Box(
                    Modifier
                        .size(width = 38.dp, height = 28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(rl.red),
                )
            }
        }
    }
}

@Composable
private fun Bar(height: androidx.compose.ui.unit.Dp, color: Color) {
    Box(Modifier.width(9.dp).height(height).clip(RoundedCornerShape(3.dp)).background(color))
}
