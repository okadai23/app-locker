package com.daisuke.readinglock.system

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daisuke.readinglock.AppIntents
import com.daisuke.readinglock.ui.components.ProgressRing
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.domain.ProgressCalculator
import com.daisuke.readinglock.domain.TodayProgress

/** デザイン(#g-lock)のカラー。仕上げは M5 */
private object LockColors {
    val Background = Color(0xFF20293B)
    val ChipText = Color(0xFFC9CFDC)
    val ChipIconText = Color(0xFFEAE5D6)
    val RingTrack = Color(0x1FFFFFFF)
    val RingProgress = Color(0xFFE8DFC9)
    val BigNumber = Color(0xFFF5F0E4)
    val Muted = Color(0xFF8E96A8)
    val Message = Color(0xFFEAE5D6)
    val PrimaryButton = Color(0xFFA6453C)
    val PrimaryButtonText = Color(0xFFFBF6EA)
    val SecondaryText = Color(0xFFE8DFC9)
    val SecondaryBorder = Color(0x59E8DFC9)
    val Footnote = Color(0xFF66708A)
}

data class LockContent(val appName: String, val progress: TodayProgress)

/**
 * WindowManager 直付けの全画面オーバーレイ。閉じる手段は2ボタンのみ、戻るは消費して無視。§5.1
 */
class LockOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var state by mutableStateOf<LockContent?>(null)

    val isShowing: Boolean get() = root != null

    fun show(content: LockContent) {
        state = content
        if (root != null) return
        if (!Settings.canDrawOverlays(context)) return

        val themed = ContextThemeWrapper(context, com.daisuke.readinglock.R.style.Theme_ReadingLock)
        val lifecycleOwner = OverlayLifecycleOwner()
        // 戻るキー/ジェスチャーは一切通さない
        val container = object : FrameLayout(themed) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }
        val composeView = ComposeView(themed).apply { setContent { LockScreen(state) } }
        container.addView(composeView)
        // ComposeView は親を辿ってオーナーを見つけるので container だけに付ければよい
        lifecycleOwner.attachTo(container)

        runCatching { windowManager?.addView(container, layoutParams()) }
            .onFailure { lifecycleOwner.destroy(); return }

        root = container
        owner = lifecycleOwner
    }

    fun hide() {
        val view = root ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        owner?.destroy()
        root = null
        owner = null
        state = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // FLAG_NOT_FOCUSABLE を付けない = キーイベントを受け取れる = 戻るを消費できる
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    @Composable
    private fun LockScreen(content: LockContent?) {
        if (content == null) return
        val progress = content.progress
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LockColors.Background)
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            BlockedAppChip(content.appName)
            Spacer(Modifier.height(44.dp))
            RemainingRing(progress)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "あと${progress.remainingMinutes}分の読書で\n解禁されます",
                color = LockColors.Message,
                fontSize = 16.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "今日の読書 ${progress.elapsedMinutes}/${progress.effectiveQuotaMinutes}分",
                color = LockColors.Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            OverlayButton(
                label = "読書を開始",
                background = LockColors.PrimaryButton,
                textColor = LockColors.PrimaryButtonText,
                height = 54.dp,
            ) { launch(AppIntents.startFlow(context)) }
            Spacer(Modifier.height(12.dp))
            OverlayButton(
                label = "読書ロックを開く",
                background = Color.Transparent,
                textColor = LockColors.SecondaryText,
                height = 48.dp,
                borderColor = LockColors.SecondaryBorder,
            ) { launch(AppIntents.home(context)) }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "「読書を開始」は開始フローへ、\n「読書ロックを開く」はホームへ。${content.appName} には戻れません",
                color = LockColors.Footnote,
                fontSize = 11.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    @Composable
    private fun BlockedAppChip(appName: String) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0x12FFFFFF))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(100.dp))
                .padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x24FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = appName.take(1),
                    color = LockColors.ChipIconText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(text = "$appName はロック中です", color = LockColors.ChipText, fontSize = 13.sp)
        }
    }

    @Composable
    private fun RemainingRing(progress: TodayProgress) {
        ProgressRing(
            fraction = ProgressCalculator.fraction(progress.totalSeconds, progress.effectiveQuotaMinutes),
            trackColor = LockColors.RingTrack,
            progressColor = LockColors.RingProgress,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${progress.remainingMinutes}",
                    color = LockColors.BigNumber,
                    fontFamily = Mincho,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "分",
                    color = LockColors.BigNumber,
                    fontFamily = Mincho,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                text = "のこり読書時間",
                color = LockColors.Muted,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
        }
    }

    @Composable
    private fun OverlayButton(
        label: String,
        background: Color,
        textColor: Color,
        height: androidx.compose.ui.unit.Dp,
        borderColor: Color? = null,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(100.dp))
                .background(background)
                .then(
                    if (borderColor != null) {
                        Modifier.border(1.5.dp, borderColor, RoundedCornerShape(100.dp))
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = if (borderColor == null) 16.sp else 14.sp,
                fontWeight = if (borderColor == null) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }

    private fun launch(intent: android.content.Intent) {
        hide()
        context.startActivity(intent)
    }
}
