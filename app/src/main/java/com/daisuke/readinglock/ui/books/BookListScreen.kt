package com.daisuke.readinglock.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.data.Book
import com.daisuke.readinglock.data.BookStatus
import com.daisuke.readinglock.data.Source
import com.daisuke.readinglock.ui.components.BooksIllustration
import com.daisuke.readinglock.ui.components.Card
import com.daisuke.readinglock.ui.components.DashedRow
import com.daisuke.readinglock.ui.components.Pill
import com.daisuke.readinglock.ui.components.ScreenHeader
import com.daisuke.readinglock.ui.components.SectionLabel
import com.daisuke.readinglock.ui.components.SourceChip
import com.daisuke.readinglock.ui.components.label
import com.daisuke.readinglock.ui.theme.rl
import kotlinx.coroutines.launch

class BooksViewModel : ViewModel() {
    private val dao = AppGraph.db.books()
    val books = dao.observeAll()

    fun add(title: String, source: Source) = viewModelScope.launch {
        dao.insert(Book(title = title, source = source, createdAt = System.currentTimeMillis()))
    }

    fun setStatus(id: Long, status: BookStatus) = viewModelScope.launch { dao.setStatus(id, status) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}

@Composable
fun BookListScreen(onBack: () -> Unit, vm: BooksViewModel = viewModel { BooksViewModel() }) {
    val books by vm.books.collectAsState(emptyList())
    val active = books.filter { it.status == BookStatus.ACTIVE }
    val archived = books.filter { it.status == BookStatus.ARCHIVED }
    var adding by remember { mutableStateOf(false) }

    if (books.isEmpty()) {
        EmptyBookList(onBack) { adding = true }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        ) {
            ScreenHeader("本リスト", onBack)
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                active.forEach { book ->
                    BookRow(
                        book = book,
                        onArchive = { vm.setStatus(book.id, BookStatus.ARCHIVED) },
                        onDelete = { vm.delete(book.id) },
                    )
                }
                DashedRow("+ 本を登録") { adding = true }
            }
            if (archived.isNotEmpty()) {
                SectionLabel("アーカイブ済み", Modifier.padding(top = 26.dp, bottom = 10.dp))
                Column(
                    Modifier.alpha(0.6f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    archived.forEach { book ->
                        Card {
                            SourceChip(book.source, size = 30.dp, corner = 9.dp, fontSize = 12)
                            Text(
                                book.title,
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                color = rl.muted,
                                textDecoration = TextDecoration.LineThrough,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "戻す",
                                modifier = Modifier
                                    .clickable { vm.setStatus(book.id, BookStatus.ACTIVE) }
                                    .padding(6.dp),
                                fontSize = 12.sp,
                                color = rl.faint,
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddBookSheet(onDismiss = { adding = false }) { title, source ->
            adding = false
            vm.add(title, source)
        }
    }
}

@Composable
private fun BookRow(book: Book, onArchive: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card {
        SourceChip(book.source, size = 30.dp, corner = 9.dp, fontSize = 12)
        Text(
            book.title,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = rl.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            Text(
                "⋯",
                modifier = Modifier.clickable { menu = true }.padding(horizontal = 6.dp),
                fontSize = 16.sp,
                color = rl.faint,
                letterSpacing = 2.sp,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("アーカイブ") },
                    onClick = { menu = false; onArchive() },
                )
                DropdownMenuItem(
                    text = { Text("削除", color = rl.red) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun EmptyBookList(onBack: () -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("本リスト", onBack)
        Spacer(Modifier.weight(1f))
        BooksIllustration()
        Text(
            "読む本を登録しましょう",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = rl.heading,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "活字の本だけを登録するのがルールです",
            fontSize = 12.sp,
            color = rl.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(
            Modifier
                .padding(top = 28.dp)
                .height(52.dp)
                .clip(Pill)
                .background(rl.primary)
                .clickable(onClick = onAdd)
                .padding(horizontal = 34.dp),
            Alignment.Center,
        ) {
            Text("+ 本を登録", color = rl.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
    }
}

/** タイトル + ソースを選ぶだけの登録ダイアログ */
@Composable
private fun AddBookSheet(onDismiss: () -> Unit, onAdd: (String, Source) -> Unit) {
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(Source.KINDLE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = rl.surface,
        title = { Text("本を登録", fontWeight = FontWeight.Bold, color = rl.heading) },
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
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Source.entries.forEach { candidate ->
                        val selected = candidate == source
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) rl.tonal else Color.Transparent)
                                .clickable { source = candidate }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SourceChip(candidate, size = 28.dp, corner = 9.dp, fontSize = 12)
                            Text(candidate.label, fontSize = 13.5.sp, color = rl.heading)
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onAdd(title.trim(), source) }) {
                Text("登録", color = rl.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("やめる", color = rl.muted) }
        },
    )
}
