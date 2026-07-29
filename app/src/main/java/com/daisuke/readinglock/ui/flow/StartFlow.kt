package com.daisuke.readinglock.ui.flow

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.data.Book
import com.daisuke.readinglock.data.BookStatus
import com.daisuke.readinglock.data.SessionState
import com.daisuke.readinglock.data.Source
import com.daisuke.readinglock.domain.ProgressCalculator
import com.daisuke.readinglock.domain.ReadingApps
import com.daisuke.readinglock.ui.components.AmberNote
import com.daisuke.readinglock.ui.components.BooksIllustration
import com.daisuke.readinglock.ui.components.Pill
import com.daisuke.readinglock.ui.components.PrimaryButton
import com.daisuke.readinglock.ui.components.ProgressRing
import com.daisuke.readinglock.ui.components.ScreenHeader
import com.daisuke.readinglock.ui.components.ScreenTitle
import com.daisuke.readinglock.ui.components.SourceChip
import com.daisuke.readinglock.ui.components.TextLink
import com.daisuke.readinglock.ui.components.dashedBorder
import com.daisuke.readinglock.ui.components.label
import com.daisuke.readinglock.ui.mmss
import com.daisuke.readinglock.ui.theme.Mincho
import com.daisuke.readinglock.ui.theme.rl
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FlowViewModel : ViewModel() {
    private val db = AppGraph.db

    val today = AppGraph.engine.today
    val active = AppGraph.engine.active
    val books = db.books().observeAll().map { list -> list.filter { it.status == BookStatus.ACTIVE } }

    fun addBook(title: String, source: Source, then: (Long) -> Unit = {}) {
        viewModelScope.launch {
            then(db.books().insert(Book(title = title, source = source, createdAt = System.currentTimeMillis())))
        }
    }

    fun start(bookId: Long?, source: Source) {
        viewModelScope.launch { AppGraph.engine.start(bookId, source) }
    }

    fun end() {
        viewModelScope.launch { AppGraph.engine.end() }
    }

    /**
     * 計測対象アプリを前面に出す。§4.1 は前面検知で再開するので画面に戻る必要はない。
     * DMM のように配布経路違いで複数パッケージある場合は実際に入っている方を開く
     */
    fun openReadingApp(context: Context, source: Source) {
        val intent = ReadingApps.packagesFor(source)
            .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } ?: return
        runCatching { context.startActivity(intent) }
    }
}

// ---------------------------------------------------------------- 1f ステップ1

@Composable
fun SourceSelectScreen(onBack: () -> Unit, onSelect: (Source) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        ScreenHeader("読書を開始", onBack) {
            Text("1 / 3", fontSize = 12.sp, color = rl.muted)
        }
        Spacer(Modifier.height(34.dp))
        ScreenTitle("どれで読みますか", "選んだソースだけが計測の対象になります")
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceCard(Source.KINDLE, "Kindleアプリを開いている間だけ計測") { onSelect(Source.KINDLE) }
            SourceCard(Source.DMM, "DMMブックスを開いている間だけ計測") { onSelect(Source.DMM) }
            SourceCard(Source.PAPER, "スマホを伏せてタイマーで計測") { onSelect(Source.PAPER) }
        }
    }
}

@Composable
private fun SourceCard(source: Source, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(rl.surface)
            .border(1.dp, rl.surfaceBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceChip(source, size = 46.dp, corner = 14.dp, fontSize = 19)
        Column(Modifier.weight(1f)) {
            Text(source.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
            Text(description, fontSize = 12.sp, color = rl.muted, modifier = Modifier.padding(top = 3.dp))
        }
        Text("›", fontSize = 18.sp, color = rl.faint)
    }
}

// ---------------------------------------------------------------- 1g ステップ2

@Composable
fun BookDeclareScreen(
    source: Source,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    vm: FlowViewModel = viewModel { FlowViewModel() },
) {
    val books by vm.books.collectAsState(emptyList())
    val candidates = books.filter { it.source == source }
    var selected by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }

    val chosen = selected ?: candidates.firstOrNull()?.id

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        ScreenHeader("読書を開始", onBack) {
            Text("2 / 3", fontSize = 12.sp, color = rl.muted)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(34.dp))
            ScreenTitle("読む本を宣言", "セッション中は本を変更できません")
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                candidates.forEach { book ->
                    BookChoice(book, book.id == chosen) { selected = book.id }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .dashedBorder(rl.dashed, 16.dp)
                        .clickable { adding = true }
                        .padding(15.dp),
                    Alignment.Center,
                ) {
                    Text("+ 本を登録", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = rl.muted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("この本で開始", enabled = chosen != null, onClick = {
            vm.start(chosen, source)
            onStarted()
        })
    }

    if (adding) {
        AddBookDialog(source, onDismiss = { adding = false }) { title ->
            adding = false
            vm.addBook(title, source) { selected = it }
        }
    }
}

@Composable
private fun BookChoice(book: Book, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rl.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) rl.primary else rl.surfaceBorder,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .border(if (selected) 6.dp else 2.dp, if (selected) rl.primary else rl.faint, RoundedCornerShape(50)),
        )
        Text(
            book.title,
            modifier = Modifier.weight(1f),
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = rl.heading,
        )
        SourceChip(book.source, size = 28.dp, corner = 9.dp, fontSize = 12)
    }
}

@Composable
fun AddBookDialog(source: Source, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = rl.surface,
        title = { Text("本を登録", fontFamily = Mincho, fontWeight = FontWeight.Bold, color = rl.heading) },
        text = {
            Column {
                Text("活字の本だけを登録するのがルールです", fontSize = 12.sp, color = rl.muted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("タイトル") },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceChip(source, size = 28.dp, corner = 9.dp, fontSize = 12)
                    Text(source.label, fontSize = 13.sp, color = rl.secondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onAdd(title.trim()) }) {
                Text("登録", color = rl.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる", color = rl.muted) } },
    )
}

// ---------------------------------------------------------------- 1h / 1i / 1j ステップ3

@Composable
fun MeasuringScreen(onFinished: () -> Unit, vm: FlowViewModel = viewModel { FlowViewModel() }) {
    val session by vm.active.collectAsState()
    val progress by vm.today.collectAsState()
    val books by vm.books.collectAsState(emptyList())
    val context = LocalContext.current

    val current = session
    if (current == null) {
        // セッションが終わった / 4:00 で強制終了された
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("セッションは終了しました", fontSize = 15.sp, color = rl.muted)
            Spacer(Modifier.height(20.dp))
            TextLink("ホームに戻る", onFinished, color = rl.primary)
        }
        return
    }

    val paper = current.source == Source.PAPER
    val paused = current.state == SessionState.PAUSED
    val quotaSeconds = progress.quotaMinutes * 60
    val title = current.bookId?.let { id -> books.firstOrNull { it.id == id }?.title }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (paper) "紙の本" else "計測中",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = rl.heading,
            )
            Spacer(Modifier.weight(1f))
            when {
                paused -> StatusDot("一時停止中", rl.faint, rl.muted)
                paper && !isCounting(current.effectiveSeconds) -> Text(
                    "タイマー待機中",
                    fontSize = 12.sp,
                    color = rl.muted,
                )
                else -> StatusDot("記録中", rl.green, rl.green)
            }
        }

        if (paused) {
            Spacer(Modifier.height(20.dp))
            AmberNote(
                title = if (paper) "スマホを持ち上げたのを検知しました" else "他のアプリの操作を検知しました",
                sub = if (paper) {
                    "もう一度伏せると計測を再開します"
                } else {
                    "${sourceAppName(current.source)} に戻ると計測を再開します"
                },
                glyph = "⏸",
            )
        }

        if (paper && !paused) {
            Spacer(Modifier.height(52.dp))
            BooksIllustration()
            Spacer(Modifier.height(26.dp))
        } else {
            Spacer(Modifier.height(if (paused) 32.dp else 44.dp))
        }

        val elapsed = current.effectiveSeconds
        ProgressRing(
            fraction = ProgressCalculator.fraction(elapsed, progress.quotaMinutes),
            trackColor = rl.ringTrack,
            progressColor = if (paused) rl.muted else rl.primary,
            modifier = Modifier.alpha(if (paused) 0.65f else 1f),
        ) {
            Text(
                if (paper && !paused) mmss(quotaSeconds - elapsed) else mmss(elapsed),
                fontFamily = Mincho,
                fontSize = 50.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Bold,
                color = if (paused) rl.muted else rl.heading,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (paper && !paused) "タイマー" else "/ ${progress.quotaMinutes}分",
                fontSize = 12.sp,
                color = if (paused) rl.faint else rl.muted,
            )
        }

        if (paper && !paused) {
            Spacer(Modifier.height(16.dp))
            Text("スマホを伏せたら計測開始", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = rl.heading)
            Spacer(Modifier.height(8.dp))
            Text(
                "画面に触れたり持ち上げたりすると\n計測は一時停止します",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = rl.muted,
                textAlign = TextAlign.Center,
            )
        }

        if (title != null) {
            Spacer(Modifier.height(14.dp))
            BookChip(current.source, title)
        }

        if (!paper && !paused) {
            Spacer(Modifier.height(26.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(rl.surface)
                    .border(1.dp, rl.surfaceBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    "${sourceAppName(current.source)} を開いて読書を続けてください",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = rl.heading,
                )
                Text(
                    "${sourceAppName(current.source)} 以外のアプリを開いている間、計測は自動的に一時停止します。",
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = rl.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (!paper) {
            PrimaryButton(
                text = if (paused) "読書に戻る" else "${sourceAppName(current.source)} を開く",
                onClick = { vm.openReadingApp(context, current.source) },
            )
            Spacer(Modifier.height(16.dp))
        }
        TextLink("セッションを終了", onClick = {
            vm.end()
            onFinished()
        })
    }
}

@Composable
private fun StatusDot(text: String, dot: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun BookChip(source: Source, title: String) {
    Row(
        Modifier
            .clip(Pill)
            .background(rl.surface)
            .border(1.dp, rl.surfaceBorder, Pill)
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceChip(source, size = 26.dp, corner = 8.dp, fontSize = 12)
        Text(title, fontSize = 12.5.sp, color = rl.secondary)
    }
}

private fun isCounting(effectiveSeconds: Int) = effectiveSeconds > 0

private fun sourceAppName(source: Source) = when (source) {
    Source.KINDLE -> "Kindle"
    Source.DMM -> "DMM ブックス"
    Source.PAPER -> "紙の本"
}
