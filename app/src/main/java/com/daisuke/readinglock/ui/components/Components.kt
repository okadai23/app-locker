package com.daisuke.readinglock.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daisuke.readinglock.data.Source
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.ui.theme.rl
import kotlin.math.absoluteValue

/** しおり。clip-path:polygon(0 0,100% 0,100% 100%,50% 76%,0 100%) */
val BookmarkShape: Shape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2f, size.height * 0.76f)
    lineTo(0f, size.height)
    close()
}

val Pill = RoundedCornerShape(100.dp)

@Composable
fun Bookmark(color: Color, width: Dp = 10.dp, height: Dp = 14.dp) {
    Box(Modifier.size(width, height).clip(BookmarkShape).background(color))
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

/**
 * 進捗リング。上(-90°)始点・丸キャップ。ホーム/ロック/計測で同じ言語を使う。
 * 色は呼び出し側が渡す(ロックオーバーレイはテーマに関係なく常に濃紺なので)。
 */
@Composable
fun ProgressRing(
    fraction: Float,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 236.dp,
    stroke: Dp = 14.dp,
    decoration: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ring")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        decoration?.invoke(this)
        Canvas(Modifier.size(diameter - stroke * 1.3f)) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arc = Size(size.width - w, size.height - w)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width = w),
            )
            if (animated > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** ソースチップ(実ロゴ不使用・文字1字 + 固定チント) */
@Composable
fun SourceChip(source: Source, size: Dp = 32.dp, corner: Dp = 10.dp, fontSize: Int = 13) {
    val dark = rl.isDark
    val (bg, fg) = when (source) {
        Source.KINDLE -> if (dark) Color(0x1FDFE4EE) to Color(0xFFB8C4DC) else Color(0xFFDFE4EE) to Color(0xFF2E4057)
        Source.DMM -> if (dark) Color(0x1FF0DFDA) to Color(0xFFE0A79E) else Color(0xFFF0DFDA) to Color(0xFFA6453C)
        Source.PAPER -> if (dark) Color(0x1FE4E7DA) to Color(0xFFAEBB9A) else Color(0xFFE4E7DA) to Color(0xFF5A6B4A)
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(bg), Alignment.Center) {
        Text(source.mark, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

val Source.mark: String
    get() = when (this) {
        Source.KINDLE -> "K"
        Source.DMM -> "D"
        Source.PAPER -> "紙"
    }

val Source.label: String
    get() = when (this) {
        Source.KINDLE -> "Kindle"
        Source.DMM -> "DMM Books"
        Source.PAPER -> "紙の本"
    }

@Composable
fun StreakChip(days: Int) {
    Row(
        Modifier
            .clip(Pill)
            .background(rl.surface)
            .border(1.dp, rl.surfaceBorder, Pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bookmark(rl.red)
        Text("${days}日連続", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = rl.secondary)
    }
}

/** 面カード。角14 / 生成りの surface */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(rl.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** セッション1行。履歴では時刻を出す */
@Composable
fun SessionRow(source: Source, title: String, minutes: Int, time: String? = null) {
    Card {
        if (time != null) {
            Text(time, fontSize = 11.5.sp, color = rl.muted, modifier = Modifier.width(36.dp))
        }
        SourceChip(source, size = if (time != null) 28.dp else 32.dp, corner = 9.dp, fontSize = 12)
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 13.5.sp,
            color = rl.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${minutes}分", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = rl.secondary)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = rl.muted,
        letterSpacing = 0.7.sp,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 11.5.sp, color = rl.muted, lineHeight = 19.sp)
}

/** ← タイトル … 右側 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    backGlyph: String = "←",
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text(
                backGlyph,
                fontSize = 20.sp,
                color = rl.heading,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp),
            )
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun ScreenTitle(text: String, sub: String? = null) {
    Text(text, fontFamily = Mincho, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = rl.heading)
    if (sub != null) {
        Spacer(Modifier.height(6.dp))
        Text(sub, fontSize = 12.5.sp, color = rl.muted)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    height: Dp = 54.dp,
) {
    val bg = if (tonal) rl.tonal else rl.primary
    val fg = if (tonal) rl.primary else rl.onPrimary
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Pill)
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = rl.muted) {
    Text(
        text,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

/** 「+ 本を登録」のような破線の追加行 */
@Composable
fun DashedRow(text: String, corner: Dp = 14.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .dashedBorder(rl.dashed, corner)
            .clickable(onClick = onClick)
            .padding(14.dp),
        Alignment.Center,
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = rl.muted)
    }
}

@Composable
fun EmptyCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rl.surface)
            .dashedBorder(rl.dashed, 14.dp)
            .padding(22.dp),
        Alignment.Center,
    ) {
        Text(text, fontSize = 12.5.sp, color = rl.muted, textAlign = TextAlign.Center)
    }
}

/** 空状態の本のイラスト(2ページを少し傾けたもの) */
@Composable
fun BooksIllustration() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier
                .size(34.dp, 46.dp)
                .clip(RoundedCornerShape(3.dp, 8.dp, 8.dp, 3.dp))
                .background(rl.surface)
                .border(1.5.dp, rl.dashed, RoundedCornerShape(3.dp, 8.dp, 8.dp, 3.dp)),
        )
        Box(
            Modifier
                .size(34.dp, 46.dp)
                .clip(RoundedCornerShape(8.dp, 3.dp, 3.dp, 8.dp))
                .background(rl.surface)
                .border(1.5.dp, rl.dashed, RoundedCornerShape(8.dp, 3.dp, 3.dp, 8.dp)),
        )
    }
}

/** アプリ一覧のアバター。実アイコンを読むと重いので頭文字 + 固定チント */
private val AvatarColors = listOf(
    Color(0xFF20293B), Color(0xFFB9524A), Color(0xFF8A5E8E), Color(0xFF4E6B8C),
    Color(0xFF5A8A6E), Color(0xFFB07A2E), Color(0xFF3A6B5C), Color(0xFF7A6A9E),
)

@Composable
fun AppAvatar(packageName: String, appName: String) {
    val color = AvatarColors[packageName.hashCode().absoluteValue % AvatarColors.size]
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color), Alignment.Center) {
        Text(
            appName.take(1).ifBlank { "?" },
            color = Color(0xFFFBF6EA),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 琥珀色の注記バナー(一時停止・翌日反映) */
@Composable
fun AmberNote(title: String, sub: String? = null, glyph: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rl.amberBg)
            .border(1.dp, rl.amberBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(rl.amberSub),
                Alignment.Center,
            ) {
                Text(glyph, color = rl.amberBg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = rl.amberInk)
            if (sub != null) {
                Text(sub, fontSize = 11.5.sp, color = rl.amberSub, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
