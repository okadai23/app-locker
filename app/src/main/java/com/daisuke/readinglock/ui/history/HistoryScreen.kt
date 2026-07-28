package com.daisuke.readinglock.ui.history

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.data.Book
import com.daisuke.readinglock.data.DailyProgress
import com.daisuke.readinglock.data.ReadingSession
import com.daisuke.readinglock.domain.LogicalDayResolver
import com.daisuke.readinglock.domain.ProgressCalculator
import com.daisuke.readinglock.domain.StreakCalculator
import com.daisuke.readinglock.ui.clock
import com.daisuke.readinglock.ui.components.SectionLabel
import com.daisuke.readinglock.ui.components.SessionRow
import com.daisuke.readinglock.ui.components.ScreenHeader
import com.daisuke.readinglock.ui.components.StreakChip
import com.daisuke.readinglock.ui.dowLabel
import com.daisuke.readinglock.ui.jp
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.ui.theme.rl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel : ViewModel() {
    private val db = AppGraph.db

    val today: LocalDate = LogicalDayResolver.logicalLocalDate(System.currentTimeMillis())

    val month = MutableStateFlow(YearMonth.from(today))
    val selected = MutableStateFlow(today)

    val progress: Flow<Map<String, DailyProgress>> =
        db.dailyProgress().observeAll().map { rows -> rows.associateBy { it.logicalDate } }

    val streak: Flow<Int> = db.dailyProgress().observeAll().map { rows ->
        StreakCalculator.streak(
            rows.filter { it.achievedAt != null }.map { it.logicalDate }.toSet(),
            today.toString(),
        )
    }

    val sessions: Flow<List<ReadingSession>> =
        selected.flatMapLatest { db.sessions().observeByDate(it.toString()) }

    val books: Flow<List<Book>> = db.books().observeAll()
}

@Composable
fun HistoryScreen(onBack: () -> Unit, vm: HistoryViewModel = viewModel { HistoryViewModel() }) {
    val month by vm.month.collectAsState()
    val selected by vm.selected.collectAsState()
    val progress by vm.progress.collectAsState(emptyMap())
    val sessions by vm.sessions.collectAsState(emptyList())
    val books by vm.books.collectAsState(emptyList())
    val streak by vm.streak.collectAsState(0)
    val titles = books.associate { it.id to it.title }
    val total = sessions.sumOf { it.effectiveSeconds }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        ScreenHeader("履歴", onBack) { if (streak > 0) StreakChip(streak) }

        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(rl.surface)
                .border(1.dp, rl.surfaceBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹",
                    modifier = Modifier
                        .clickable { vm.month.value = month.minusMonths(1) }
                        .padding(horizontal = 8.dp),
                    fontSize = 14.sp,
                    color = rl.faint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${month.year}年 ${month.monthValue}月",
                    fontFamily = Mincho,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = rl.heading,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "›",
                    modifier = Modifier
                        .clickable { vm.month.value = month.plusMonths(1) }
                        .padding(horizontal = 8.dp),
                    fontSize = 14.sp,
                    color = rl.faint,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { i ->
                    Text(
                        dowLabel(i),
                        modifier = Modifier.weight(1f),
                        fontSize = 10.5.sp,
                        color = rl.faint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            MonthGrid(
                month = month,
                today = vm.today,
                selected = selected,
                progress = progress,
                onSelect = { vm.selected.value = it },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legend("達成", rl.achievedDay, null)
                Legend("未達成", Color.Transparent, rl.dashed)
            }
        }

        SectionLabel(
            "${selected.jp()}・合計 ${ProgressCalculator.elapsedMinutes(total)}分",
            Modifier.padding(top = 22.dp, bottom = 10.dp),
        )
        if (sessions.isEmpty()) {
            Text("記録がありません", fontSize = 12.5.sp, color = rl.muted, modifier = Modifier.padding(start = 2.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEach { session ->
                    SessionRow(
                        source = session.source,
                        title = session.bookId?.let { titles[it] } ?: "紙の本",
                        minutes = ProgressCalculator.elapsedMinutes(session.effectiveSeconds),
                        time = clock(session.startedAt),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    progress: Map<String, DailyProgress>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // 日曜始まり
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val day = index - lead + 1
                    Box(Modifier.weight(1f), Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            DayCell(
                                day = day,
                                achieved = progress[date.toString()]?.achievedAt != null,
                                isToday = date == today,
                                isSelected = date == selected,
                                isFuture = date > today,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    achieved: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val background = when {
        isSelected -> rl.primary
        achieved -> rl.achievedDay
        else -> Color.Transparent
    }
    val ink = when {
        isSelected -> rl.onPrimary
        achieved -> rl.achievedDayInk
        isFuture -> rl.dotInactive
        isToday -> rl.primary
        else -> rl.muted
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(shape)
            .background(background)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, rl.primary, shape) else Modifier)
            .clickable(enabled = !isFuture, onClick = onClick),
        Alignment.Center,
    ) {
        Text(
            "$day",
            fontSize = 12.5.sp,
            fontWeight = if (achieved || isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = ink,
        )
    }
}

@Composable
private fun Legend(text: String, fill: Color, border: Color?) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(fill)
                .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(50)) else Modifier),
        )
        Text(text, fontSize = 10.5.sp, color = rl.muted)
    }
}
