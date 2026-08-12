package io.legado.app.video.converter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.video.ui.theme.VideoColors
import kotlinx.coroutines.launch

class BookVideoActivity : ComponentActivity() {

    companion object {
        fun start(context: Context, book: Book) {
            context.startActivity(
                Intent(context, BookVideoActivity::class.java).apply {
                    putExtra("bookUrl", book.bookUrl)
                    putExtra("bookName", book.name)
                    putExtra("bookAuthor", book.author)
                    putExtra("bookIntro", book.intro ?: book.customIntro ?: "")
                    putExtra("bookCover", book.coverUrl ?: book.customCoverUrl ?: "")
                }
            )
        }
    }

    private var book: Book? = null
    private var chapters: List<BookChapter> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookUrl = intent.getStringExtra("bookUrl") ?: run {
            finish()
            return
        }

        book = appDb.bookDao.getBook(bookUrl) ?: run {
            finish()
            return
        }

        chapters = appDb.bookChapterDao.getChapterList(bookUrl)

        setContent {
            MaterialTheme {
                BookVideoConverterScreen(
                    book = book!!,
                    chapters = chapters,
                    onBack = { finish() },
                    onConvert = { config ->
                        startConversion(config)
                    }
                )
            }
        }
    }

    private fun startConversion(config: BookConversionConfig) {
        book?.let { b ->
            val converter = BookToVideoConverter()
            lifecycleScope.launch {
                converter.convertBookToVideoProject(b, config) { progress ->
                    runOnUiThread {
                        // Could update UI with progress
                    }
                }.onSuccess { project ->
                    VideoWorkbenchActivity.start(this@BookVideoActivity)
                    finish()
                }.onFailure { error ->
                    // Show error
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookVideoConverterScreen(
    book: Book,
    chapters: List<BookChapter>,
    onBack: () -> Unit,
    onConvert: (BookConversionConfig) -> Unit
) {
    var selectedStartChapter by remember { mutableStateOf(0) }
    var selectedEndChapter by remember { mutableStateOf(chapters.lastIndex) }
    var chaptersPerScene by remember { mutableStateOf(1) }
    var style by remember { mutableStateOf("cinematic") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var maxSceneLength by remember { mutableStateOf(300) }
    var includeCharacters by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showStyleMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小说转视频", color = VideoColors.OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = VideoColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VideoColors.Background)
            )
        },
        containerColor = VideoColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BookInfoHeader(book)
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "选择章节范围",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VideoColors.OnSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedEndChapter - selectedStartChapter + 1} 章",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VideoColors.Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "起始章节",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnSurfaceVariant
                            )

                            if (chapters.isNotEmpty()) {
                                val startLabel = chapters.getOrNull(selectedStartChapter)?.title ?: "-"
                                Text(
                                    text = startLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VideoColors.OnSurface,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Slider(
                                    value = selectedStartChapter.toFloat(),
                                    onValueChange = { v ->
                                        selectedStartChapter = v.toInt().coerceAtMost(selectedEndChapter)
                                    },
                                    valueRange = 0f..chapters.lastIndex.toFloat(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = VideoColors.Primary,
                                        activeTrackColor = VideoColors.Primary
                                    )
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = "结束章节",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.OnSurfaceVariant
                                )

                                val endLabel = chapters.getOrNull(selectedEndChapter)?.title ?: "-"
                                Text(
                                    text = endLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VideoColors.OnSurface,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Slider(
                                    value = selectedEndChapter.toFloat(),
                                    onValueChange = { v ->
                                        selectedEndChapter = v.toInt().coerceAtLeast(selectedStartChapter)
                                    },
                                    valueRange = 0f..chapters.lastIndex.toFloat(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = VideoColors.Primary,
                                        activeTrackColor = VideoColors.Primary
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "视频风格",
                                style = MaterialTheme.typography.titleMedium,
                                color = VideoColors.OnSurface,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(12.dp))

                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showStyleMenu = true }
                                        .background(
                                            VideoColors.SurfaceVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getStyleDisplayName(style),
                                        color = VideoColors.OnSurface
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        null,
                                        tint = VideoColors.OnSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showStyleMenu,
                                    onDismissRequest = { showStyleMenu = false }
                                ) {
                                    listOf(
                                        "cinematic" to "🎬 电影风格",
                                        "anime" to "🎨 动漫风格",
                                        "realistic" to "📸 写实风格",
                                        "comic" to "💬 漫画风格",
                                        "cyberpunk" to "🌆 赛博朋克",
                                        "fantasy" to "✨ 奇幻风格",
                                        "wuxia" to "⚔️ 武侠风格",
                                        "documentary" to "📹 纪录片风格"
                                    ).forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                style = key
                                                showStyleMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "画面比例",
                                style = MaterialTheme.typography.labelMedium,
                                color = VideoColors.OnSurfaceVariant
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("16:9" to "横屏", "9:16" to "竖屏", "1:1" to "方形", "4:3" to "传统").forEach { (ratio, label) ->
                                    FilterChip(
                                        selected = aspectRatio == ratio,
                                        onClick = { aspectRatio = ratio },
                                        label = {
                                            Text(
                                                "$label $ratio",
                                                color = if (aspectRatio == ratio) VideoColors.OnPrimary else VideoColors.OnSurfaceVariant
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VideoColors.Primary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAdvanced = !showAdvanced },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "高级设置",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VideoColors.OnSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    tint = VideoColors.OnSurfaceVariant
                                )
                            }

                            if (showAdvanced) {
                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = "每N章合成一个分镜",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VideoColors.OnSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1, 2, 3, 5, 10).forEach { n ->
                                        FilterChip(
                                            selected = chaptersPerScene == n,
                                            onClick = { chaptersPerScene = n },
                                            label = {
                                                Text(
                                                    "${n}章",
                                                    color = if (chaptersPerScene == n) VideoColors.OnPrimary else VideoColors.OnSurfaceVariant
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = VideoColors.Primary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = "分镜最大长度 (字)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VideoColors.OnSurfaceVariant
                                )
                                Text(
                                    text = "$maxSceneLength 字",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = VideoColors.Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = maxSceneLength.toFloat(),
                                    onValueChange = { maxSceneLength = it.toInt() },
                                    valueRange = 100f..1000f,
                                    steps = 9,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = VideoColors.Primary,
                                        activeTrackColor = VideoColors.Primary
                                    )
                                )

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "智能提取角色",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VideoColors.OnSurface
                                    )
                                    Switch(
                                        checked = includeCharacters,
                                        onCheckedChange = { includeCharacters = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = VideoColors.Primary)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = VideoColors.Success.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = VideoColors.Success,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI智能处理流程",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VideoColors.OnSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "1. 自动解析小说章节内容\n2. AI提取人物角色\n3. 智能拆分分镜\n4. 自动生成视觉提示词",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onConvert(
                        BookConversionConfig(
                            startChapterIndex = selectedStartChapter,
                            endChapterIndex = selectedEndChapter,
                            chaptersPerScene = chaptersPerScene,
                            maxSceneLength = maxSceneLength,
                            style = style,
                            aspectRatio = aspectRatio,
                            includeCharacters = includeCharacters
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
            ) {
                Icon(Icons.Default.MovieCreation, null, tint = VideoColors.OnPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "开始AI转换",
                    color = VideoColors.OnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun BookInfoHeader(book: Book) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(VideoColors.SurfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = VideoColors.OnSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    // Cover image placeholder - Glide integration would go here
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VideoColors.SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = VideoColors.OnSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = VideoColors.OnSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = VideoColors.OnSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VideoColors.Primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${book.totalChapterNum}章",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.Primary
                        )
                    }

                    book.kind?.let {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VideoColors.SurfaceVariant
                        ) {
                            Text(
                                text = it.split(",").firstOrNull() ?: "",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getStyleDisplayName(style: String): String = when (style) {
    "cinematic" -> "🎬 电影风格"
    "anime" -> "🎨 动漫风格"
    "realistic" -> "📸 写实风格"
    "comic" -> "💬 漫画风格"
    "cyberpunk" -> "🌆 赛博朋克"
    "fantasy" -> "✨ 奇幻风格"
    "wuxia" -> "⚔️ 武侠风格"
    "documentary" -> "📹 纪录片风格"
    else -> style
}
