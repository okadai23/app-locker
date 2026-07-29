package com.daisuke.readinglock.system

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 読書アプリの上に出す経過時間チップ。「◯分 / ◯分」だけを分単位で表示する。
 * 入力は一切奪わない(NOT_FOCUSABLE | NOT_TOUCHABLE)。
 */
class TimerOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var label by mutableStateOf("")

    val isShowing: Boolean get() = root != null

    fun show(elapsedMinutes: Int, targetMinutes: Int) {
        // 同じ文字列なら Compose は再描画しない = 分が変わったときだけ描き直る
        label = "${elapsedMinutes}分 / ${targetMinutes}分"
        if (root != null) return
        if (!Settings.canDrawOverlays(context)) return

        val themed = ContextThemeWrapper(context, com.daisuke.readinglock.R.style.Theme_ReadingLock)
        val lifecycleOwner = OverlayLifecycleOwner()
        val container = FrameLayout(themed)
        container.addView(ComposeView(themed).apply { setContent { Chip(label) } })
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
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        val density = context.resources.displayMetrics.density
        x = (12 * density).toInt()
        y = (40 * density).toInt() // ステータスバーを避ける
    }

    @Composable
    private fun Chip(text: String) {
        if (text.isEmpty()) return
        Text(
            text = text,
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0xBF20293B))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color(0xFFEAE5D6),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
