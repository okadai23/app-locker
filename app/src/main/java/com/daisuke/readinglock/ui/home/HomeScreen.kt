package com.daisuke.readinglock.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.data.Book
import com.daisuke.readinglock.data.DailyProgress
import com.daisuke.readinglock.data.ReadingSession
import com.daisuke.readinglock.data.Settings
import com.daisuke.readinglock.domain.LogicalDayResolver
import com.daisuke.readinglock.domain.ProgressCalculator
import com.daisuke.readinglock.domain.StreakCalculator
import com.daisuke.readinglock.domain.TodayProgress
import com.daisuke.readinglock.system.Permissions
import com.daisuke.readinglock.ui.Routes
import com.daisuke.readinglock.ui.components.Bookmark
import com.daisuke.readinglock.ui.components.EmptyCard
import com.daisuke.readinglock.ui.components.Pill
import com.daisuke.readinglock.ui.components.PrimaryButton
import com.daisuke.readinglock.ui.components.ProgressRing
import com.daisuke.readinglock.ui.components.SectionLabel
import com.daisuke.readinglock.ui.components.SessionRow
import com.daisuke.readinglock.ui.components.StreakChip
import com.daisuke.readinglock.ui.jp
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.ui.theme.rl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel() {
    private val db = AppGraph.db

    val today = AppGraph.engine.today
    val active = AppGraph.engine.active
    val settings: Flow<Settings> = AppGraph.settings.flow

    /** リセット直後や初期化前の空文字を今の論理日で埋める */
    private val date = today
        .map { it.logicalDate.ifEmpty { LogicalDayResolver.logicalDate(System.currentTimeMillis()) } }
        .distinctUntilChanged()

    val sessions: Flow<List<ReadingSession>> = date.flatMapLatest { db.sessions().observeByDate(it) }
    val books: Flow<List<Book>> = db.books().observeAll()
    val streak: Flow<Int> = combine(db.dailyProgress().observeAll(), date, ::streakOf)

    private fun streakOf(rows: List<DailyProgress>, date: String): Int =
        StreakCalculator.streak(rows.filter { it.achievedAt != null }.map { it.logicalDate }.toSet(), date)
}

@Composable
fun HomeScreen(
    onStartReading: () -> Unit,
    onOpenSession: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: HomeViewModel = viewModel { HomeViewModel() },
) {
    val progress by vm.today.collectAsState()
    val active by vm.active.collectAsState()
    val sessions by vm.sessions.collectAsState(emptyList())
    val books by vm.books.collectAsState(emptyList())
    val streak by vm.streak.collectAsState(0)
    val settings by vm.settings.collectAsState(Settings())
    val titles = books.associate { it.id to it.title }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "読書ロック",
                    fontFamily = Mincho,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = rl.heading,
                )
                Text(
                    LocalDate.parse(
                        progress.logicalDate.ifEmpty { LogicalDayResolver.logicalDate(System.currentTimeMillis()) },
                    ).jp(),
                    fontSize = 12.sp,
                    color = rl.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (streak > 0) StreakChip(streak)
        }

        if (settings.warnings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            WarningBanner(settings.warnings) { onNavigate(Routes.SETUP) }
        }

        Spacer(Modifier.height(26.dp))
        StatusBadge(progress.unlocked, Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(22.dp))
        TodayRing(progress, Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(20.dp))
        when {
            active != null -> PrimaryButton("読書中の画面へ", onOpenSession, tonal = true)
            progress.unlocked -> PrimaryButton("続けて読む", onStartReading, tonal = true)
            else -> PrimaryButton("読書を開始", onStartReading)
        }

        SectionLabel("今日のセッション", Modifier.padding(top = 26.dp, bottom = 10.dp))
        if (sessions.isEmpty()) {
            EmptyCard("まだ記録がありません")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEach { session ->
                    SessionRow(
                        source = session.source,
                        title = session.bookId?.let { titles[it] } ?: "紙の本",
                        minutes = ProgressCalculator.elapsedMinutes(session.effectiveSeconds),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavLink("履歴") { onNavigate(Routes.HISTORY) }
            Dot()
            NavLink("本リスト") { onNavigate(Routes.BOOKS) }
            Dot()
            NavLink("ブロック設定") { onNavigate(Routes.BLOCK) }
        }
    }
}

@Composable
private fun NavLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = rl.muted,
    )
}

@Composable
private fun Dot() {
    Box(Modifier.size(3.dp).clip(RoundedCornerShape(50)).background(rl.faint))
}

@Composable
private fun StatusBadge(unlocked: Boolean, modifier: Modifier = Modifier) {
    if (unlocked) {
        Box(modifier.clip(Pill).background(rl.red).padding(horizontal = 16.dp, vertical = 7.dp)) {
            Text("達成済み ✓", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = rl.onRed)
        }
    } else {
        Row(
            modifier.clip(Pill).background(rl.tonal).padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(rl.primary))
            Text("ロック中", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = rl.primary)
        }
    }
}

@Composable
private fun TodayRing(progress: TodayProgress, modifier: Modifier = Modifier) {
    // 再ロック中(基準ノルマは達成済みでも未解禁)はロック中の見た目に戻す
    val achieved = progress.unlocked
    val ink = if (achieved) rl.red else rl.heading
    val confettiRed = rl.red
    val confettiNavy = if (rl.isDark) rl.ringTrack else Color(0xFF2E4057)
    ProgressRing(
        fraction = ProgressCalculator.fraction(progress.totalSeconds, progress.effectiveQuotaMinutes),
        trackColor = if (achieved) rl.red else rl.ringTrack,
        progressColor = rl.red.takeIf { achieved } ?: rl.primary,
        modifier = modifier,
        decoration = if (!achieved) null else {
            {
                Canvas(Modifier.fillMaxSize()) {
                    val k = size.width / 236f
                    fun dot(x: Float, y: Float, r: Float, color: Color, alpha: Float) =
                        drawCircle(color, r * k, Offset(x * k, y * k), alpha)
                    dot(40f, 34f, 3f, confettiRed, .55f)
                    dot(206f, 46f, 2.5f, confettiNavy, .45f)
                    dot(222f, 150f, 3f, confettiRed, .4f)
                    dot(20f, 132f, 2.5f, confettiNavy, .4f)
                    dot(118f, 10f, 2.5f, confettiRed, .5f)
                }
            }
        },
    ) {
        if (achieved) {
            Bookmark(rl.red, 14.dp, 20.dp)
            Spacer(Modifier.height(3.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${progress.elapsedMinutes}",
                fontFamily = Mincho,
                fontSize = 58.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
            )
            Text(
                "/${progress.effectiveQuotaMinutes}分",
                fontFamily = Mincho,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (achieved) rl.red else rl.muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                achieved -> "今日の約束を果たしました"
                progress.totalSeconds == 0 -> "今日はまだ読んでいません"
                else -> "解禁まで あと${progress.remainingMinutes}分"
            },
            fontSize = 12.sp,
            color = rl.muted,
        )
    }
}

/** §9-3 / §9-4 の権限欠落。タップでウィザードの該当ステップへ */
@Composable
private fun WarningBanner(warnings: Set<String>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rl.red.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(rl.red))
        Column(Modifier.weight(1f)) {
            Text("ロックが正しく働いていません", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = rl.red)
            Text(
                warnings.joinToString("、") { Permissions.label(it) },
                fontSize = 11.5.sp,
                color = rl.red.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("›", fontSize = 18.sp, color = rl.red)
    }
}
