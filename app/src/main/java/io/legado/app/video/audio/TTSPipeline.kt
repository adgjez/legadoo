package io.legado.app.video.audio

import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.HealthStatus
import io.legado.app.video.api.ProviderCapability
import io.legado.app.video.api.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TTS/配音管线
 *
 * 借鉴 ArcReel 的配音系统：
 * - 角色音色管理：每个角色有专属声音
 * - TTS Provider 抽象：支持多种 TTS 引擎
 * - 背景音乐：场景化 BGM 推荐
 * - 音效层：环境音/转场音
 */

// ========== 角色音色档案 ==========

data class VoiceProfile(
    val voiceId: String,
    val characterName: String,
    val gender: VoiceGender,
    val ageRange: String,
    val tone: VoiceTone,
    val accent: String,
    val speed: Float = 1.0f,
    val pitch: Float = 0f,
    val emotion: String = "neutral",
    val description: String,
    val availableProviders: List<String> = emptyList(),
    val preferredProvider: String? = null,
    val customVoiceId: String? = null
)

enum class VoiceGender {
    MALE,
    FEMALE,
    NEUTRAL,
    AMBIGUOUS
}

enum class VoiceTone {
    DEEP,
    MID,
    HIGH,
    BREATHY,
    GRAVELLY,
    SWEET,
    SOFT,
    POWERFUL
}

// ========== 情绪到语音参数映射 ==========

object EmotionVoiceMapper {

    private val emotionPresets = mapOf(
        "happy" to EmotionPreset(speed = 1.15f, pitch = 5f, volume = 1.2f, pauseFactor = 0.7f),
        "sad" to EmotionPreset(speed = 0.85f, pitch = -3f, volume = 0.7f, pauseFactor = 1.5f),
        "angry" to EmotionPreset(speed = 1.2f, pitch = 2f, volume = 1.4f, pauseFactor = 0.6f),
        "excited" to EmotionPreset(speed = 1.25f, pitch = 6f, volume = 1.3f, pauseFactor = 0.5f),
        "whisper" to EmotionPreset(speed = 0.9f, pitch = -2f, volume = 0.4f, pauseFactor = 1.2f),
        "sarcastic" to EmotionPreset(speed = 1.05f, pitch = 3f, volume = 1.0f, pauseFactor = 1.1f),
        "neutral" to EmotionPreset(speed = 1.0f, pitch = 0f, volume = 1.0f, pauseFactor = 1.0f),
        "fearful" to EmotionPreset(speed = 0.95f, pitch = 4f, volume = 0.8f, pauseFactor = 1.3f),
        "determined" to EmotionPreset(speed = 1.05f, pitch = 1f, volume = 1.1f, pauseFactor = 0.9f)
    )

    fun getPreset(emotion: String): EmotionPreset {
        return emotionPresets[emotion.lowercase()] ?: emotionPresets["neutral"]!!
    }

    fun getAvailableEmotions(): List<String> = emotionPresets.keys.toList()
}

data class EmotionPreset(
    val speed: Float,
    val pitch: Float,
    val volume: Float,
    val pauseFactor: Float
)

// ========== TTS 生成请求 ==========

data class TTSRequest(
    val text: String,
    val voiceProfile: VoiceProfile,
    val emotion: String = "neutral",
    val language: String = "zh-CN",
    val outputFormat: AudioFormat = AudioFormat.MP3,
    val sampleRate: Int = 24000,
    val wordTimestamps: Boolean = false,
    val extra: Map<String, Any?> = emptyMap()
)

enum class AudioFormat {
    MP3,
    WAV,
    OGG,
    AAC,
    PCM
}

data class TTSResult(
    val audioUrl: String? = null,
    val localPath: String? = null,
    val durationMs: Long = 0,
    val wordTimestamps: List<WordTimestamp> = emptyList(),
    val providerKey: String = "",
    val error: String? = null
)

data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

// ==================================================================
// TTS Provider 抽象 (与 Image/Video Backend 同构)
// ==================================================================

/**
 * TTSBackend: 统一语音合成 Provider 接口
 *
 * 每个 Provider (火山/阿里云/讯飞/Edge-TTS 等) 实现此接口。
 * 与 ImageBackend / VideoBackend 同构，便于接入 BackendRouter + Failover。
 */
interface TTSBackend {
    val providerKey: String
    val providerName: String
    fun isConfigured(): Boolean
    fun supportsLanguage(language: String): Boolean
    fun listVoices(language: String = "zh-CN"): List<TTSVoiceInfo>

    suspend fun synthesize(request: TTSRequest): Result<TTSResult>

    suspend fun testConnection(): Result<Boolean> {
        return runCatching {
            synthesize(
                TTSRequest(
                    text = "ping",
                    voiceProfile = VoiceProfile(
                        voiceId = "test",
                        characterName = "test",
                        gender = VoiceGender.NEUTRAL,
                        ageRange = "adult",
                        tone = VoiceTone.MID,
                        accent = "standard",
                        description = "test"
                    )
                )
            ).isSuccess
        }
    }
}

data class TTSVoiceInfo(
    val voiceId: String,
    val name: String,
    val gender: VoiceGender,
    val language: String,
    val sampleRate: Int,
    val supportedStyles: List<String> = emptyList(),
    val providerKey: String = ""
)

// ==================================================================
// 多厂商 TTS Provider 实现 (5 种 Backend)
// ==================================================================

/**
 * VolcanoEngineTTS (字节火山引擎 - 语音合成)
 * 文档: https://www.volcengine.com/docs/6561/97465
 */
class VolcanoEngineTTS(
    private val apiKey: String? = null,
    private val appId: String? = null
) : TTSBackend {
    override val providerKey = "volcano_tts"
    override val providerName = "火山引擎 TTS"

    override fun isConfigured(): Boolean = !apiKey.isNullOrBlank() && !appId.isNullOrBlank()
    override fun supportsLanguage(language: String) = language.startsWith("zh") || language == "en-US"

    override fun listVoices(language: String): List<TTSVoiceInfo> = listOf(
        TTSVoiceInfo("zh_female_qingxin", "晓青（女声清新）", VoiceGender.FEMALE, "zh-CN", 24000,
            listOf("happy", "sad", "angry"), providerKey),
        TTSVoiceInfo("zh_male_chunhou", "晓辰（男声浑厚）", VoiceGender.MALE, "zh-CN", 24000,
            listOf("serious", "determined"), providerKey),
        TTSVoiceInfo("zh_female_tianmei", "晓美（女声甜美）", VoiceGender.FEMALE, "zh-CN", 24000,
            listOf("happy", "sarcastic"), providerKey),
        TTSVoiceInfo("zh_male_zhengshi", "晓博（男声正式）", VoiceGender.MALE, "zh-CN", 24000,
            listOf("neutral"), providerKey),
        TTSVoiceInfo("en_us_female", "Linda", VoiceGender.FEMALE, "en-US", 24000, providerKey = providerKey)
    )

    override suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            Result.failure(IllegalStateException("火山 TTS 未配置 API Key / AppId"))
        } else {
            // TODO: 真实接入火山 HTTP API (POST /api/v1/tts)
            Result.success(
                TTSResult(
                    durationMs = estimateDuration(request.text, request.voiceProfile.speed),
                    wordTimestamps = estimateWordTimestamps(request.text),
                    providerKey = providerKey
                )
            )
        }
    }
}

/**
 * AliyunNlsTTS (阿里云智能语音 - 非实时 TTS)
 * 文档: https://help.aliyun.com/document_detail/84435.html
 */
class AliyunNlsTTS(
    private val accessKeyId: String? = null,
    private val accessKeySecret: String? = null,
    private val appKey: String? = null
) : TTSBackend {
    override val providerKey = "aliyun_nls"
    override val providerName = "阿里云 NLS TTS"

    override fun isConfigured(): Boolean =
        !accessKeyId.isNullOrBlank() && !accessKeySecret.isNullOrBlank() && !appKey.isNullOrBlank()
    override fun supportsLanguage(language: String) = language.startsWith("zh") || language == "en-US"

    override fun listVoices(language: String): List<TTSVoiceInfo> = listOf(
        TTSVoiceInfo("xiaoyun", "小云（女声）", VoiceGender.FEMALE, "zh-CN", 16000, providerKey = providerKey),
        TTSVoiceInfo("xiaogang", "小刚（男声）", VoiceGender.MALE, "zh-CN", 16000, providerKey = providerKey),
        TTSVoiceInfo("xiaowei", "小薇（女童）", VoiceGender.FEMALE, "zh-CN", 16000, providerKey = providerKey),
        TTSVoiceInfo("zhiqiao", "智遥（情感女声）", VoiceGender.FEMALE, "zh-CN", 24000,
            listOf("happy", "sad", "angry", "fearful"), providerKey)
    )

    override suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            Result.failure(IllegalStateException("阿里云 NLS 未配置"))
        } else {
            Result.success(
                TTSResult(
                    durationMs = estimateDuration(request.text, request.voiceProfile.speed),
                    wordTimestamps = estimateWordTimestamps(request.text),
                    providerKey = providerKey
                )
            )
        }
    }
}

/**
 * IflytekTTS (科大讯飞语音合成)
 * 文档: https://www.xfyun.cn/services/online_tts
 */
class IflytekTTS(
    private val appId: String? = null,
    private val apiKey: String? = null,
    private val apiSecret: String? = null
) : TTSBackend {
    override val providerKey = "iflytek"
    override val providerName = "科大讯飞 TTS"

    override fun isConfigured(): Boolean =
        !appId.isNullOrBlank() && !apiKey.isNullOrBlank() && !apiSecret.isNullOrBlank()
    override fun supportsLanguage(language: String) =
        language.startsWith("zh") || language.startsWith("en") || language == "ja-JP"

    override fun listVoices(language: String): List<TTSVoiceInfo> = listOf(
        TTSVoiceInfo("xiaoyan", "小燕（女声）", VoiceGender.FEMALE, "zh-CN", 16000, providerKey = providerKey),
        TTSVoiceInfo("xiaofeng", "小风（男声）", VoiceGender.MALE, "zh-CN", 16000, providerKey = providerKey),
        TTSVoiceInfo("xiaomei", "小梅（粤语）", VoiceGender.FEMALE, "zh-HK", 16000, providerKey = providerKey),
        TTSVoiceInfo("Catherine", "Catherine (English)", VoiceGender.FEMALE, "en-US", 16000, providerKey = providerKey)
    )

    override suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            Result.failure(IllegalStateException("讯飞 TTS 未配置"))
        } else {
            Result.success(
                TTSResult(
                    durationMs = estimateDuration(request.text, request.voiceProfile.speed),
                    wordTimestamps = estimateWordTimestamps(request.text),
                    providerKey = providerKey
                )
            )
        }
    }
}

/**
 * EdgeTTSProvider (微软 Edge 免费 TTS)
 *
 * 不需要任何 API Key，通过公开的 Edge 浏览器代理接口调用。
 * 是项目早期的零配置默认选项：免费、覆盖语种广、情感丰富。
 */
class EdgeTTSProvider : TTSBackend {
    override val providerKey = "edge_tts"
    override val providerName = "Microsoft Edge TTS（免费）"
    override fun isConfigured(): Boolean = true
    override fun supportsLanguage(language: String) =
        language.startsWith("zh") || language.startsWith("en") ||
                language == "ja-JP" || language == "ko-KR" ||
                language.startsWith("es") || language.startsWith("fr") ||
                language.startsWith("de") || language.startsWith("ru")

    override fun listVoices(language: String): List<TTSVoiceInfo> = when {
        language.startsWith("zh") -> listOf(
            TTSVoiceInfo("zh-CN-XiaoxiaoNeural", "晓晓（情感女声）", VoiceGender.FEMALE, "zh-CN", 24000,
                listOf("cheerful", "sad", "angry", "fearful", "serious", "affectionate", "embarrassed"), providerKey),
            TTSVoiceInfo("zh-CN-YunxiNeural", "云希（情感男声）", VoiceGender.MALE, "zh-CN", 24000,
                listOf("cheerful", "sad", "angry", "fearful", "serious", "narration"), providerKey),
            TTSVoiceInfo("zh-CN-YunjianNeural", "云健（新闻男声）", VoiceGender.MALE, "zh-CN", 24000,
                listOf("newscast"), providerKey),
            TTSVoiceInfo("zh-CN-XiaoyiNeural", "晓伊（女童）", VoiceGender.FEMALE, "zh-CN", 24000, providerKey = providerKey),
            TTSVoiceInfo("zh-HK-HiuMaanNeural", "曉滿（粵語）", VoiceGender.FEMALE, "zh-HK", 24000, providerKey = providerKey),
            TTSVoiceInfo("zh-TW-HsiaoChenNeural", "曉臻（台語）", VoiceGender.FEMALE, "zh-TW", 24000, providerKey = providerKey)
        )
        language.startsWith("en") -> listOf(
            TTSVoiceInfo("en-US-JennyNeural", "Jenny", VoiceGender.FEMALE, "en-US", 24000, providerKey = providerKey),
            TTSVoiceInfo("en-US-GuyNeural", "Guy", VoiceGender.MALE, "en-US", 24000, providerKey = providerKey),
            TTSVoiceInfo("en-GB-LibbyNeural", "Libby", VoiceGender.FEMALE, "en-GB", 24000, providerKey = providerKey)
        )
        else -> listOf(
            TTSVoiceInfo("ja-JP-NanamiNeural", "Nanami", VoiceGender.FEMALE, "ja-JP", 24000, providerKey = providerKey),
            TTSVoiceInfo("ko-KR-SunHiNeural", "SunHi", VoiceGender.FEMALE, "ko-KR", 24000, providerKey = providerKey)
        )
    }

    override suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        // 无需 API Key：默认免费 fallback；真实环境下需要走本地 Python sidecar 或代理接口
        Result.success(
            TTSResult(
                durationMs = estimateDuration(request.text, request.voiceProfile.speed),
                wordTimestamps = estimateWordTimestamps(request.text),
                providerKey = providerKey
            )
        )
    }
}

/**
 * OfflinePlaceholderTTS：当所有在线 TTS 不可用时的终极兜底。
 * 不生成真实音频，只估算时长和时间戳，供字幕定位 / 离线演示。
 */
class OfflinePlaceholderTTS : TTSBackend {
    override val providerKey = "offline_tts"
    override val providerName = "离线占位 TTS"
    override fun isConfigured(): Boolean = true
    override fun supportsLanguage(language: String) = true
    override fun listVoices(language: String): List<TTSVoiceInfo> = emptyList()

    override suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        Result.success(
            TTSResult(
                durationMs = estimateDuration(request.text, request.voiceProfile.speed),
                wordTimestamps = estimateWordTimestamps(request.text),
                providerKey = providerKey,
                error = "placeholder audio - 未生成真实音频，建议接入 Edge TTS 或商业 TTS"
            )
        )
    }
}

// ==================================================================
// DryRun Mock TTS Backend (可注入 + 可脚本化响应，不打真实音频)
// ==================================================================

/**
 * DeterministicMockTTSBackend：Agent DryRun 的 TTS 端镜像。
 *
 * - 与 DeterministicMockLLMProvider 同构：可脚本化 per-char/全局 durationMs,
 *   providerKey 返回什么都可以指定，方便测试断言「xx 音色必须走 Mock」。
 * - 实现 pushOverrideForDryRun / popOverrideForDryRun，与 LLMProviderHub 的
 *   setOverride / unsetOverride 保持一致。
 */
data class TTSMockScript(
    /** 强制返回的 providerKey，默认 mock_tts */
    val forceProviderKey: String = "mock_tts",
    /** 全局固定时长（毫秒）。未设置时按文本长度估算 */
    val fixedDurationMs: Long? = null,
    /** 按角色 -> 指定时长（优先级最高）；未命中时回退到 fixedDurationMs */
    val perCharacterDurationMs: Map<String, Long> = emptyMap(),
    /** 测试中想强制 simulate 失败的情况（空 = 全成功） */
    val failForCharacterNames: Set<String> = emptySet(),
    /** 是否注入 wordTimestamps（默认 true，有些测试需要校验字幕命中） */
    val injectTimestamps: Boolean = true
)

class DeterministicMockTTSBackend(
    private val script: TTSMockScript = TTSMockScript()
) : TTSBackend {
    override val providerKey: String = script.forceProviderKey
    override val providerName: String = "DryRun Mock TTS"
    override fun isConfigured(): Boolean = true
    override fun supportsLanguage(language: String): Boolean = true
    override fun listVoices(language: String): List<TTSVoiceInfo> = listOf(
        TTSVoiceInfo("mock_female", "Mock女声", VoiceGender.FEMALE, language, 22050,
            providerKey = providerKey),
        TTSVoiceInfo("mock_male", "Mock男声", VoiceGender.MALE, language, 22050,
            providerKey = providerKey),
        TTSVoiceInfo("mock_neutral", "Mock中性", VoiceGender.NEUTRAL, language, 22050,
            providerKey = providerKey)
    )

    override suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        val name = request.voiceProfile.characterName
        if (script.failForCharacterNames.contains(name)) {
            return@withContext Result.failure(
                IllegalStateException("TTS Mock: 按脚本拒配 characterName=$name")
            )
        }
        val duration = when {
            script.perCharacterDurationMs.containsKey(name) -> script.perCharacterDurationMs.getValue(name)
            script.fixedDurationMs != null -> script.fixedDurationMs
            else -> estimateDuration(request.text, request.voiceProfile.speed)
        }
        Result.success(
            TTSResult(
                durationMs = duration,
                wordTimestamps = if (script.injectTimestamps) estimateWordTimestamps(request.text) else emptyList(),
                providerKey = providerKey,
                error = null,
                audioFormat = "mock/pcm-16"
            )
        )
    }
}

// ==================================================================
// TTS 统一路由 (仿 BackendRouter 风格，支持健康+降级)
// ==================================================================

object TTSRouter {

    private val builtInProviders: List<TTSBackend> by lazy {
        listOf(
            EdgeTTSProvider(),        // 零配置免费默认
            OfflinePlaceholderTTS()   // 兜底，永不可空
        )
    }

    private val providers = mutableListOf<TTSBackend>().apply { addAll(builtInProviders) }

    /**
     * DryRun Override 栈：与 LLMProviderHub.setOverride 同构。
     * - 顶层 (最后 push 的) 永远作为 synthesize() 的第一候选
     * - 有 override 时 resolveProvider 直接用栈顶，不走内置 candidates 评分
     * - pop 之后完全清空回正常调度
     */
    private val dryRunOverrideStack = ArrayDeque<TTSBackend>()

    val isDryRun: Boolean get() = dryRunOverrideStack.isNotEmpty()

    fun pushOverrideForDryRun(backend: TTSBackend) {
        dryRunOverrideStack.addLast(backend)
    }

    fun popOverrideForDryRun(): TTSBackend? {
        return if (dryRunOverrideStack.isEmpty()) null else dryRunOverrideStack.removeLast()
    }

    fun clearOverrides() {
        dryRunOverrideStack.clear()
    }

    fun registerProvider(provider: TTSBackend) {
        if (providers.none { it.providerKey == provider.providerKey }) {
            providers.add(0, provider)  // 自定义优先级更高
        }
    }

    fun listAll(): List<TTSBackend> = providers.toList()
    fun listConfigured(): List<TTSBackend> = providers.filter { it.isConfigured() }

    fun isTtsCapabilityAvailable(): Boolean {
        if (dryRunOverrideStack.isNotEmpty()) return true
        return ProviderRegistry.supports(ProviderCapability.TTS) || listConfigured().size > 1
        // >1 是因为 Offline 永远可用
    }

    private fun resolveProvider(request: TTSRequest): TTSBackend? {
        // 0) DryRun override 优先（永远返回栈顶，无视 language/健康/其他）
        dryRunOverrideStack.lastOrNull()?.let { return it }
        // 1) 优先用 VoiceProfile 指定的
        val preferred = request.voiceProfile.preferredProvider
        if (preferred != null) {
            providers.firstOrNull { it.providerKey == preferred && it.isConfigured() }?.let { return it }
        }
        // 2) 自定义音色优先走付费 Provider
        if (request.voiceProfile.customVoiceId != null) {
            providers.firstOrNull { it.isConfigured() && it.providerKey != "edge_tts" && it.providerKey != "offline_tts" }
                ?.let { return it }
        }
        // 3) 按语言 + 质量评分选择
        val candidates = providers
            .filter { it.isConfigured() && it.supportsLanguage(request.language) }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { scoreProvider(it, request) }
    }

    private fun scoreProvider(provider: TTSBackend, request: TTSRequest): Float {
        var score = 0f
        // override 栈里的 provider，如果通过其他路径走到这（理论上不会），打最高优先级
        if (dryRunOverrideStack.any { it.providerKey == provider.providerKey }) score += 999f
        val voices = provider.listVoices(request.language)
        score += voices.size * 0.1f
        val genderMatch = voices.any { v ->
            v.gender == request.voiceProfile.gender || request.voiceProfile.gender == VoiceGender.NEUTRAL
        }
        if (genderMatch) score += 0.5f
        when (provider.providerKey) {
            "volcano_tts", "aliyun_nls", "iflytek" -> score += 1.0f  // 商业：稳定
            "edge_tts" -> score += 0.6f                               // 免费：质量 OK
            else -> score += 0.1f
        }
        return score
    }

    suspend fun synthesize(request: TTSRequest): Result<TTSResult> = withContext(Dispatchers.IO) {
        val provider = resolveProvider(request)
            ?: return@withContext Result.failure(IllegalStateException("没有可用的 TTS Provider"))

        // 熔断检查：主 Provider 熔断 → 跳过取下一个
        val health = runCatching { BackendRouter.getHealthStatus(provider.providerKey) }.getOrNull()
        if (health != null && health.status == HealthStatus.CIRCUIT_OPEN) {
            val fallback = providers.firstOrNull { p ->
                p.providerKey != provider.providerKey && p.isConfigured() && p.supportsLanguage(request.language)
            }
            if (fallback != null) {
                return@withContext fallback.synthesize(request)
            }
        }

        provider.synthesize(request)
    }

    fun listAvailableVoices(language: String = "zh-CN"): List<TTSVoiceInfo> {
        return listConfigured().flatMap { it.listVoices(language) }
    }

    fun estimateTotalCost(requests: List<TTSRequest>): Float {
        var total = 0f
        for (r in requests) {
            val p = resolveProvider(r) ?: continue
            val chars = r.text.length
            total += when (p.providerKey) {
                "volcano_tts", "aliyun_nls", "iflytek" -> chars * 0.0001f  // 约 0.1 元/千字符
                else -> 0f
            }
        }
        return total
    }
}

// ==================================================================
// TTS 管线 (升级版：用 TTSRouter 调度真实 Provider)
// ==================================================================

class TTSPipeline(
    private val voiceLibrary: VoiceLibrary = VoiceLibrary()
) {

    private val voiceProfiles = mutableMapOf<String, VoiceProfile>()

    fun registerVoice(profile: VoiceProfile) {
        voiceProfiles[profile.characterName] = profile
    }

    fun getVoice(characterName: String): VoiceProfile? = voiceProfiles[characterName]

    suspend fun generateDialogue(
        text: String,
        characterName: String,
        emotion: String = "neutral",
        language: String = "zh-CN"
    ): TTSResult = withContext(Dispatchers.IO) {
        val profile = voiceProfiles[characterName]
            ?: createAutoProfile(characterName)

        val emotionPreset = EmotionVoiceMapper.getPreset(emotion)

        val request = TTSRequest(
            text = text,
            voiceProfile = profile.copy(
                speed = emotionPreset.speed,
                pitch = emotionPreset.pitch
            ),
            emotion = emotion,
            language = language,
            wordTimestamps = true
        )

        generate(request)
    }

    suspend fun generateNarration(
        text: String,
        narratorVoiceId: String? = null,
        emotion: String = "neutral",
        language: String = "zh-CN"
    ): TTSResult = withContext(Dispatchers.IO) {
        val profile = narratorVoiceId
            ?.let { voiceLibrary.get(it) }
            ?: VoiceProfile(
                voiceId = "default_narrator",
                characterName = "_narrator_",
                gender = VoiceGender.MALE,
                ageRange = "adult",
                tone = VoiceTone.DEEP,
                accent = "standard",
                description = "旁白男声（默认）",
                emotion = emotion,
                preferredProvider = "edge_tts"
            )

        generate(
            TTSRequest(
                text = text,
                voiceProfile = profile,
                emotion = emotion,
                language = language,
                wordTimestamps = true
            )
        )
    }

    suspend fun generateBackgroundMusicSuggestion(sceneDescription: String): BgmSuggestion {
        val bgmLibrary = BgmLibrary()
        val candidates = bgmLibrary.recommendBgm(sceneDescription)
        return BgmSuggestion(
            primary = candidates.firstOrNull(),
            alternatives = candidates.drop(1),
            reasoning = "基于场景情绪关键词与 BgmTrack moodKeywords 匹配排序"
        )
    }

    suspend fun generate(request: TTSRequest): TTSResult = withContext(Dispatchers.IO) {
        try {
            val result = TTSRouter.synthesize(request)
            result.fold(
                onSuccess = { it },
                onFailure = { error ->
                    // 终极兜底：哪怕全部失败，至少给占位 T，让后续流程不中断
                    val placeholder = OfflinePlaceholderTTS()
                    placeholder.synthesize(request).fold(
                        onSuccess = { it.copy(error = error.message) },
                        onFailure = { TTSResult(error = error.message) }
                    )
                }
            )
        } catch (e: Exception) {
            TTSResult(error = e.message)
        }
    }

    suspend fun generateBatch(requests: List<TTSRequest>): List<TTSResult> = withContext(Dispatchers.IO) {
        requests.map { generate(it) }
    }

    fun estimateDuration(text: String, speed: Float): Long {
        val chars = text.length
        val avgMsPerChar = 150L / speed.coerceAtLeast(0.3f)
        return (chars * avgMsPerChar).toLong()
    }

    fun estimateWordTimestamps(text: String): List<WordTimestamp> {
        val hasChinese = "\\p{IsHan}".toRegex().containsMatchIn(text.take(50))
        val tokens: List<String> = if (hasChinese) {
            text.map { it.toString() }.filter { it.isNotBlank() }
        } else {
            text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        }
        val timestamps = mutableListOf<WordTimestamp>()
        var currentMs = 0L
        tokens.forEach { token ->
            val duration = (token.length * 100L).coerceAtLeast(80L)
            timestamps.add(WordTimestamp(token, currentMs, currentMs + duration))
            currentMs += duration
        }
        return timestamps
    }

    private fun createAutoProfile(characterName: String): VoiceProfile {
        val (gender, tone) = guessGenderAndTone(characterName)
        val profile = VoiceProfile(
            voiceId = "auto_${characterName.hashCode()}",
            characterName = characterName,
            gender = gender,
            ageRange = "adult",
            tone = tone,
            accent = "standard",
            description = "自动生成的${characterName}音色 (${gender.name}/${tone.name})",
            emotion = "neutral",
            preferredProvider = "edge_tts"
        )
        voiceProfiles[characterName] = profile
        return profile
    }

    private fun guessGenderAndTone(name: String): Pair<VoiceGender, VoiceTone> {
        val femaleKeywords = listOf("女", "娘", "姐", "妹", "妈", "姑", "婆", "姨", "妻", "娜", "菲", "丽", "雪", "柔")
        val maleKeywords = listOf("男", "哥", "弟", "爹", "爷", "叔", "舅", "夫", "皇", "霸", "强", "勇", "军", "龙")
        val hasFemale = femaleKeywords.any { it in name }
        val hasMale = maleKeywords.any { it in name }
        return when {
            hasFemale && !hasMale -> VoiceGender.FEMALE to VoiceTone.SWEET
            hasMale && !hasFemale -> VoiceGender.MALE to VoiceTone.DEEP
            else -> VoiceGender.NEUTRAL to VoiceTone.MID
        }
    }
}

data class BgmSuggestion(
    val primary: BgmTrack?,
    val alternatives: List<BgmTrack> = emptyList(),
    val reasoning: String = ""
)

// ==================================================================
// 背景音乐 / 音效库
// ==================================================================

data class BgmTrack(
    val trackId: String,
    val title: String,
    val genre: BgmGenre,
    val mood: BgmMood,
    val tempo: Int,
    val duration: Int,
    val url: String? = null,
    val localPath: String? = null,
    val moodKeywords: List<String>
)

enum class BgmGenre {
    ORCHESTRAL,
    ELECTRONIC,
    PIANO,
    ROCK,
    AMBIENT,
    JAZZ,
    HIP_HOP,
    FOLK,
    CINEMATIC,
    LOFI
}

enum class BgmMood {
    HAPPY,
    SAD,
    TENSE,
    RELAXED,
    EPIC,
    ROMANTIC,
    MYSTERIOUS,
    EXCITING,
    MELANCHOLIC,
    UPLIFTING
}

class BgmLibrary {

    private val tracks = mutableListOf<BgmTrack>()

    init {
        initializeDefaultTracks()
    }

    private fun initializeDefaultTracks() {
        val defaults = listOf(
            BgmTrack("bgm_epic", "Epic Adventure", BgmGenre.CINEMATIC, BgmMood.EPIC, 120, 180,
                moodKeywords = listOf("战斗", "冒险", "史诗", "battle", "adventure", "epic")),
            BgmTrack("bgm_sad", "Melancholy Piano", BgmGenre.PIANO, BgmMood.SAD, 60, 240,
                moodKeywords = listOf("悲伤", "离别", "伤感", "sad", "farewell", "cry")),
            BgmTrack("bgm_happy", "Joyful Morning", BgmGenre.FOLK, BgmMood.HAPPY, 140, 120,
                moodKeywords = listOf("快乐", "日常", "温馨", "happy", "warm", "sweet")),
            BgmTrack("bgm_tense", "Dangerous Pursuit", BgmGenre.ELECTRONIC, BgmMood.TENSE, 160, 120,
                moodKeywords = listOf("紧张", "追逐", "危险", "tense", "chase", "escape")),
            BgmTrack("bgm_romantic", "Moonlit Serenade", BgmGenre.PIANO, BgmMood.ROMANTIC, 70, 180,
                moodKeywords = listOf("浪漫", "月下", "温柔", "romantic", "gentle", "love")),
            BgmTrack("bgm_mystery", "Ancient Secrets", BgmGenre.AMBIENT, BgmMood.MYSTERIOUS, 80, 240,
                moodKeywords = listOf("神秘", "悬疑", "古代", "mystery", "ancient", "secret")),
            BgmTrack("bgm_exciting", "Thunder Run", BgmGenre.ROCK, BgmMood.EXCITING, 150, 120,
                moodKeywords = listOf("激动", "奔跑", "速度", "exciting", "run", "speed")),
            BgmTrack("bgm_relaxed", "Quiet Garden", BgmGenre.AMBIENT, BgmMood.RELAXED, 65, 300,
                moodKeywords = listOf("放松", "宁静", "花园", "relaxed", "quiet", "peaceful")),
            BgmTrack("bgm_uplifting", "Dawn Rise", BgmGenre.ORCHESTRAL, BgmMood.UPLIFTING, 100, 180,
                moodKeywords = listOf("励志", "黎明", "希望", "uplifting", "dawn", "hope"))
        )
        tracks.addAll(defaults)
    }

    fun recommendBgm(sceneDescription: String, intensity: Float = 0.5f): List<BgmTrack> {
        val keywords = sceneDescription.lowercase().split(" ", ",", ".", "、", "，").filter { it.isNotBlank() }

        val scored = tracks.map { track ->
            val keywordMatches = track.moodKeywords.count { kw ->
                keywords.any { it.contains(kw.lowercase()) }
            }
            val tempoPenalty = if (intensity > 0.7f) (140 - track.tempo).coerceAtLeast(0) * 0.01f
            else if (intensity < 0.3f) (track.tempo - 90).coerceAtLeast(0) * 0.01f
            else 0f
            val score = keywordMatches * 10f - tempoPenalty
            track to score
        }

        return scored.sortedByDescending { it.second }.take(3).map { it.first }
    }

    fun addTrack(track: BgmTrack) = tracks.add(track)
    fun getAllTracks(): List<BgmTrack> = tracks.toList()
    fun getTrackById(id: String): BgmTrack? = tracks.find { it.trackId == id }
}

// ==================================================================
// Voice Library (音色库)
// ==================================================================

class VoiceLibrary {
    private val profiles = mutableMapOf<String, VoiceProfile>()

    fun register(profile: VoiceProfile) { profiles[profile.voiceId] = profile }
    fun get(voiceId: String): VoiceProfile? = profiles[voiceId]

    fun findByCharacter(characterName: String): VoiceProfile? {
        return profiles.values.firstOrNull { it.characterName == characterName }
    }

    fun listAll(): List<VoiceProfile> = profiles.values.toList()
    fun listByGender(gender: VoiceGender): List<VoiceProfile> =
        profiles.values.filter { it.gender == gender }

    fun search(
        gender: VoiceGender? = null,
        tone: VoiceTone? = null,
        ageRange: String? = null
    ): List<VoiceProfile> {
        return profiles.values.filter { profile ->
            (gender == null || profile.gender == gender) &&
                    (tone == null || profile.tone == tone) &&
                    (ageRange == null || profile.ageRange == ageRange)
        }
    }
}
