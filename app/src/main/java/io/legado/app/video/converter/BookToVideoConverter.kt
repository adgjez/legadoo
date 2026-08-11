package io.legado.app.video.converter

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoProjectSettings
import io.legado.app.video.data.entities.VideoScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID

data class BookConversionConfig(
    val startChapterIndex: Int = 0,
    val endChapterIndex: Int = -1,
    val chaptersPerScene: Int = 1,
    val maxSceneLength: Int = 300,
    val style: String = "cinematic",
    val aspectRatio: String = "16:9",
    val targetResolution: String = "720p",
    val includeCharacters: Boolean = true,
    val autoSplitDialogue: Boolean = true,
    val storyboardTemplate: String = "default"
)

data class ConversionProgress(
    val stage: String = "初始化",
    val progress: Float = 0f,
    val processedChapters: Int = 0,
    val totalChapters: Int = 0,
    val extractedCharacters: Int = 0,
    val createdScenes: Int = 0,
    val error: String? = null
)

class BookToVideoConverter {

    suspend fun convertBookToVideoProject(
        book: Book,
        config: BookConversionConfig = BookConversionConfig(),
        onProgress: (ConversionProgress) -> Unit
    ): Result<VideoProject> = coroutineScope {
        try {
            onProgress(ConversionProgress(stage = "加载目录", progress = 0.05f))

            val chapters = withContext(Dispatchers.IO) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }

            if (chapters.isEmpty()) {
                return@coroutineScope Result.failure(IllegalStateException("书籍尚未加载目录，请先加载目录"))
            }

            val endIdx = if (config.endChapterIndex < 0) chapters.lastIndex else config.endChapterIndex
            val selectedChapters = chapters.filterIndexed { index, _ ->
                index in config.startChapterIndex..endIdx
            }

            if (selectedChapters.isEmpty()) {
                return@coroutineScope Result.failure(IllegalStateException("没有选择任何章节"))
            }

            onProgress(ConversionProgress(stage = "创建项目", progress = 0.1f))

            val projectId = "proj_${System.currentTimeMillis()}"
            val project = VideoProject(
                id = projectId,
                name = "${book.name} - AI视频",
                description = book.intro ?: book.customIntro ?: "",
                sourceBookName = book.name,
                sourceBookAuthor = book.author,
                sourceBookUrl = book.bookUrl,
                genre = book.kind ?: book.customTag ?: "小说",
                style = config.style,
                aspectRatio = config.aspectRatio,
                targetResolution = config.targetResolution,
                totalScenes = 0,
                completedScenes = 0,
                progress = 0,
                status = VideoProject.STATUS_DRAFT
            )

            withContext(Dispatchers.IO) {
                appDb.videoProjectDao.insert(project)
            }

            val settings = VideoProjectSettings(
                id = "settings_$projectId",
                projectId = projectId,
                style = config.style,
                aspectRatio = config.aspectRatio,
                resolution = config.targetResolution,
                chaptersPerScene = config.chaptersPerScene,
                maxSceneLength = config.maxSceneLength,
                includeCharacters = config.includeCharacters,
                autoSplitDialogue = config.autoSplitDialogue,
                storyboardTemplate = config.storyboardTemplate
            )

            withContext(Dispatchers.IO) {
                appDb.videoProjectSettingsDao.insert(settings)
            }

            onProgress(ConversionProgress(stage = "提取角色", progress = 0.2f))

            val characters = if (config.includeCharacters) {
                extractCharacters(book, selectedChapters, onProgress)
            } else {
                emptyList()
            }

            onProgress(ConversionProgress(
                stage = "创建分镜", progress = 0.5f,
                extractedCharacters = characters.size
            ))

            val scenes = createScenesFromChapters(
                projectId = projectId,
                chapters = selectedChapters,
                config = config,
                characters = characters,
                onProgress = onProgress
            )

            onProgress(ConversionProgress(stage = "保存分镜", progress = 0.9f, createdScenes = scenes.size))

            withContext(Dispatchers.IO) {
                scenes.forEach { appDb.videoSceneDao.insert(it) }
                characters.forEach { appDb.videoCharacterDao.insert(it) }
            }

            val updatedProject = project.copy(
                totalScenes = scenes.size,
                progress = 0,
                status = VideoProject.STATUS_DRAFT
            )

            withContext(Dispatchers.IO) {
                appDb.videoProjectDao.insert(updatedProject)
            }

            onProgress(ConversionProgress(
                stage = "完成", progress = 1f,
                createdScenes = scenes.size,
                extractedCharacters = characters.size
            ))

            Result.success(updatedProject)
        } catch (e: Exception) {
            onProgress(ConversionProgress(error = e.message ?: "转换失败"))
            Result.failure(e)
        }
    }

    private suspend fun extractCharacters(
        book: Book,
        chapters: List<BookChapter>,
        onProgress: (ConversionProgress) -> Unit
    ): List<VideoCharacter> = withContext(Dispatchers.IO) {
        val characters = mutableListOf<VideoCharacter>()
        val characterNames = mutableSetOf<String>()
        val contentProcessor = ContentProcessor.get(book.name, book.origin)

        val step = 0.2f / chapters.size
        chapters.forEachIndexed { index, chapter ->
            val content = BookHelp.getContent(book, chapter)
            if (!content.isNullOrBlank()) {
                val processedContent = contentProcessor.getContent(
                    book, chapter, content, includeTitle = false
                ).toString()

                extractCharacterNames(processedContent).forEach { name ->
                    if (name.length in 2..10 && name !in characterNames) {
                        characterNames.add(name)
                        characters.add(
                            VideoCharacter(
                                id = "char_${System.currentTimeMillis()}_${characters.size}",
                                projectId = "",
                                name = name,
                                role = guessCharacterRole(name, processedContent),
                                description = "",
                                visualPrompt = "",
                                referenceImagePath = "",
                                status = VideoCharacter.STATUS_PENDING
                            )
                        )
                    }
                }
            }

            onProgress(ConversionProgress(
                stage = "提取角色",
                progress = 0.2f + step * (index + 1) * 0.5f,
                processedChapters = index + 1,
                totalChapters = chapters.size
            ))
        }

        characters.map { it.copy(projectId = "TEMP") }
    }

    private fun extractCharacterNames(content: String): List<String> {
        val names = mutableListOf<String>()
        val patterns = listOf(
            Regex("""([\u4e00-\u9fa5]{2,4})(?:道|说|问|答|笑|哭|怒|叹|低声|轻声|沉声|开口|说道)"""),
            Regex("""(?:\"|「)([^\"]{2,20})(?:\"|」)"""),
            Regex("""([\u4e00-\u9fa5]{2,4})(?:看着|望着|盯着|看向|打量)"""),
            Regex("""(?:[，,]\\s*)([\u4e00-\u9fa5]{2,4})(?:站在|坐在|走在|跑着|冲上来|走过来)"""),
        )

        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                match.groupValues.getOrNull(1)?.let { name ->
                    if (name.length in 2..4 && !name.contains("的") && !name.contains("了")) {
                        names.add(name)
                    }
                }
            }
        }

        return names.distinct().take(20)
    }

    private fun guessCharacterRole(name: String, content: String): String {
        val mentions = content.count { it.toString().contains(name) }
        return when {
            mentions > 50 -> VideoCharacter.ROLE_PROTAGONIST
            mentions > 20 -> VideoCharacter.ROLE_MAJOR
            mentions > 5 -> VideoCharacter.ROLE_SUPPORTING
            else -> VideoCharacter.ROLE_MINOR
        }
    }

    private suspend fun createScenesFromChapters(
        projectId: String,
        chapters: List<BookChapter>,
        config: BookConversionConfig,
        characters: List<VideoCharacter>,
        onProgress: (ConversionProgress) -> Unit
    ): List<VideoScene> = withContext(Dispatchers.IO) {
        val scenes = mutableListOf<VideoScene>()
        val contentProcessor = ContentProcessor.get("", "")
        var orderCounter = 0

        val chapterGroups = chapters.chunked(config.chaptersPerScene)

        chapterGroups.forEachIndexed { groupIndex, group ->
            val combinedContent = buildString {
                group.forEach { chapter ->
                    val content = BookHelp.getContent(Book(), chapter)
                    if (!content.isNullOrBlank()) {
                        val processed = contentProcessor.getContent(
                            Book(), chapter, content, includeTitle = false
                        ).toString()
                        append(processed)
                        append("\n\n")
                    }
                }
            }

            val splitContent = splitIntoScenes(combinedContent, config.maxSceneLength)

            splitContent.forEachIndexed { sceneIndex, sceneText ->
                val sceneType = determineSceneType(sceneText)
                val shotType = determineShotType(sceneText)
                val mood = determineMood(sceneText)
                val sceneCharacters = findCharactersInText(characters.map { it.name }, sceneText)

                scenes.add(
                    VideoScene(
                        id = "scene_${projectId}_${orderCounter}",
                        projectId = projectId,
                        title = generateSceneTitle(group, sceneIndex, sceneText),
                        summary = sceneText.take(200),
                        novelText = sceneText,
                        dialogue = extractDialogue(sceneText),
                        order = orderCounter,
                        sceneType = sceneType,
                        shotType = shotType,
                        cameraMovement = VideoScene.CAMERA_STATIC,
                        location = "",
                        timeOfDay = "",
                        weather = "",
                        mood = mood,
                        style = config.style,
                        visualPrompt = "",
                        negativePrompt = "",
                        videoPrompt = "",
                        durationSeconds = estimateDuration(sceneText),
                        characterIds = sceneCharacters,
                        referenceImagePaths = emptyList(),
                        generatedStoryboardPath = "",
                        generatedVideoPath = "",
                        videoTaskId = "",
                        videoStatus = VideoScene.STATUS_PENDING,
                        seed = System.currentTimeMillis() + orderCounter,
                        errorMessage = "",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                orderCounter++
            }

            onProgress(ConversionProgress(
                stage = "创建分镜",
                progress = 0.5f + 0.4f * (groupIndex + 1) / chapterGroups.size,
                processedChapters = (groupIndex + 1) * config.chaptersPerScene,
                totalChapters = chapters.size,
                createdScenes = scenes.size
            ))
        }

        scenes
    }

    private fun splitIntoScenes(text: String, maxLength: Int): List<String> {
        if (text.isBlank()) return emptyList()

        val paragraphs = text.split(Regex("\\n+"))
        val scenes = mutableListOf<String>()
        val currentScene = StringBuilder()

        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            if (currentScene.length + trimmed.length > maxLength && currentScene.isNotEmpty()) {
                scenes.add(currentScene.toString().trim())
                currentScene.clear()
            }

            currentScene.append(trimmed).append("\n")
        }

        if (currentScene.isNotBlank()) {
            scenes.add(currentScene.toString().trim())
        }

        return scenes.filter { it.length > 50 }
    }

    private fun determineSceneType(text: String): String {
        return when {
            text.contains("对话") || text.contains("说道") || text.contains("问") -> VideoScene.TYPE_NORMAL
            text.contains("战斗") || text.contains("打") || text.contains("杀") -> VideoScene.TYPE_CLIMAX
            text.contains("走") || text.contains("来到") || text.contains("前往") -> VideoScene.TYPE_TRANSITION
            text.contains("突然") || text.contains("这时") || text.contains("就在这时") -> VideoScene.TYPE_KEYFRAME
            else -> VideoScene.TYPE_NORMAL
        }
    }

    private fun determineShotType(text: String): String {
        return when {
            text.contains("\"") || text.contains("「") -> VideoScene.SHOT_CLOSE_UP
            text.contains("远处") || text.contains("远方") -> VideoScene.SHOT_EXTREME_LONG
            text.contains("周围") || text.contains("四周") -> VideoScene.SHOT_BIRD_EYE
            else -> VideoScene.SHOT_MEDIUM
        }
    }

    private fun determineMood(text: String): String {
        return when {
            text.contains("笑") || text.contains("开心") || text.contains("喜悦") -> "joyful"
            text.contains("怒") || text.contains("愤怒") || text.contains("生气") -> "angry"
            text.contains("哭") || text.contains("泪") || text.contains("伤心") -> "sad"
            text.contains("紧张") || text.contains("危险") || text.contains("战斗") -> "tense"
            else -> "neutral"
        }
    }

    private fun findCharactersInText(characterNames: List<String>, text: String): List<String> {
        return characterNames.filter { name -> name in text }
    }

    private fun generateSceneTitle(chapters: List<BookChapter>, sceneIndex: Int, text: String): String {
        val chapterRange = if (chapters.size == 1) {
            chapters.first().title
        } else {
            "${chapters.first().title} - ${chapters.last().title}"
        }
        val firstSentence = text.split(Regex("[。！？]")).firstOrNull { it.isNotBlank() }?.take(15) ?: "场景"
        return "$chapterRange · 场景${sceneIndex + 1}：$firstSentence"
    }

    private fun extractDialogue(text: String): String {
        val dialogues = Regex("""[「「]([^」」]+)[」」]|[""]([^""]+)[""]""")
            .findAll(text)
            .map { it.groupValues.filter { v -> v.isNotBlank() }.lastOrNull() ?: "" }
            .filter { it.isNotBlank() }
            .take(5)
        return dialogues.joinToString("\n")
    }

    private fun estimateDuration(text: String): Int {
        val charCount = text.length
        return (charCount / 50).coerceIn(3, 30)
    }
}
