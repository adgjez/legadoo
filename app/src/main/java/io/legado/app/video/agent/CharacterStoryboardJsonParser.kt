package io.legado.app.video.agent

import io.legado.app.video.pipeline.CharacterProfile
import io.legado.app.video.pipeline.StoryboardFrame
import io.legado.app.video.pipeline.FrameStatus

/**
 * CharacterStoryboardJsonParser
 *
 * 把四智能体输出的 JSON 字符串解析为强类型的 Engine 数据结构。
 * 不依赖任何第三方 JSON 库（Gson/Moshi/Kotlinx.serialization 都可能在 JVM 单元测试未集成），
 * 只做一个「足够解析 Agent 固定 JSON 格式」的轻量解析器。
 *
 * 支持的输入 JSON 形状：
 *  ① CharacterAnalyst 输出：
 *    [
 *      { "name": "林瑶", "role": "protagonist",
 *        "visual_prompt": "a girl ...", "color_palette": ["blue","jade"] }
 *    ]
 *
 *  ② StoryboardPlanner 输出：
 *    [
 *      { "index": 1, "image_prompt": "shot_1_...", "duration": 5,
 *        "shot": "medium", "mood": "tense",
 *        "characters_involved": ["林瑶","墨渊"] }
 *    ]
 *
 * 解析算法：
 *  - 先 strip 所有注释/空白，然后用状态机扫描：在顶层数组元素里找 key/value 对；
 *  - 遇到 "key": value 就入栈；当 value 是 [ 数组就递归读子元素；
 *  - 整个解析是 fail-fast + best-effort：失败回 emptyList 不抛异常。
 */
object CharacterStoryboardJsonParser {

    // ==================================================================
    // ① CharacterAnalyst JSON → List<CharacterProfile>
    // ==================================================================

    fun parseCharacters(raw: String): List<CharacterProfile> {
        val objs = extractTopLevelObjects(raw) ?: return emptyList()
        return objs.mapIndexed { i, o ->
            val name = o["name"]?.asString() ?: "Char_$i"
            val role = o["role"]?.asString() ?: "supporting"
            val visual = o["visual_prompt"]?.asString() ?: ""
            val type = when (role.lowercase()) {
                "主角","protagonist","main","hero" -> "protagonist"
                "反派","antagonist","villain" -> "antagonist"
                "配角","supporting","major","secondary" -> "supporting"
                else -> role.ifBlank { "minor" }
            }
            val palette = (o["color_palette"] as? JsonList)?.values?.mapNotNull { it.asString() }.orEmpty()
            val appearance = buildString {
                append(visual)
                if (palette.isNotEmpty()) append(" palette=").append(palette.joinToString(","))
            }
            CharacterProfile(
                id = "parsed_char_${name}_$i",
                name = name,
                role = role,
                type = type,
                appearance = appearance,
                personality = "",
                visualPrompt = visual,
                referenceImagePath = "",
                generatedImagePath = "",
                voiceName = "",
                voiceDescription = "",
                identityPrompt = buildIdentityHint(name, visual, palette)
            )
        }
    }

    // ==================================================================
    // ② StoryboardPlanner JSON → List<StoryboardFrame>
    // ==================================================================

    fun parseStoryboard(raw: String, nameToCharId: (String) -> String? = { it }): List<StoryboardFrame> {
        val objs = extractTopLevelObjects(raw) ?: return emptyList()
        return objs.mapNotNull { o ->
            val index = o["index"]?.asInt() ?: return@mapNotNull null
            val prompt = o["image_prompt"]?.asString() ?: return@mapNotNull null
            val duration = (o["duration"]?.asInt() ?: 5).coerceAtLeast(1)
            val charsInvolved = (o["characters_involved"] as? JsonList)
                ?.values
                ?.mapNotNull { it.asString() }
                ?.mapNotNull(nameToCharId)
                .orEmpty()
            val mood = o["mood"]?.asString()
            val shot = o["shot"]?.asString()
            val frameId = "frame_%03d_%s".format(index, mood ?: shot ?: "x")
            StoryboardFrame(
                frameId = frameId,
                index = index - 1,   // JSON index 是 1-based，Engine 内部 0-based
                prompt = if (mood != null && shot != null) "$prompt [shot=$shot mood=$mood]" else prompt,
                characterRefs = charsInvolved,
                status = FrameStatus.PENDING
            )
        }
    }

    // ==================================================================
    // 辅助：从 Character / Storyboard JSON 文本里提取顶层数组对象
    // ==================================================================

    private sealed class JsonVal
    private data class JsonStr(val v: String) : JsonVal()
    private data class JsonNum(val v: Number) : JsonVal()
    private data class JsonBool(val v: Boolean) : JsonVal()
    private object JsonNull : JsonVal()
    private data class JsonMap(val entries: MutableMap<String, JsonVal?> = mutableMapOf()) : JsonVal()
    private data class JsonList(val values: MutableList<JsonVal?> = mutableListOf()) : JsonVal()

    private fun JsonVal?.asString(): String? = when (this) {
        is JsonStr -> v
        is JsonNum -> v.toString()
        is JsonBool -> v.toString()
        JsonNull, null -> null
        is JsonList, is JsonMap -> null
    }

    private fun JsonVal?.asInt(): Int? = when (this) {
        is JsonNum -> v.toInt()
        is JsonStr -> v.toIntOrNull()
        else -> null
    }

    /**
     * 只支持 [ {...}, {...} ] 顶层数组，每个元素是 key: value map。
     * 内部不支持嵌套 map（value 可以是 string/number/bool/null/string array/number array）
     * — 对 Agent 输出的 JSON 形状完全足够。
     */
    private fun extractTopLevelObjects(raw: String): List<JsonMap>? {
        val s = raw
        val len = s.length
        var i = 0
        fun skipWs() { while (i < len && s[i].isWhitespace()) i++ }
        fun expect(ch: Char): Boolean { skipWs(); return i < len && s[i++] == ch }

        skipWs()
        if (i >= len || s[i] != '[') {
            // 如果给的是单个对象（{...}），也尝试包一层
            return if (s.getOrNull(i) == '{') {
                listOfNotNull(readObject(s) { len }.also { i = it }.second)
            } else null
        }
        i++
        val out = mutableListOf<JsonMap>()
        while (true) {
            skipWs()
            if (i >= len) break
            if (s[i] == ']') { i++; break }
            if (s[i] != '{') return null
            val (next, obj) = readObject(s) { len }
            i = next
            obj ?: return null
            out += obj
            skipWs()
            if (i < len && s[i] == ',') { i++; continue }
            if (i < len && s[i] == ']') break
        }
        return out
    }

    /** 返回 (新的位置, JsonMap?)  */
    private fun readObject(s: String, len: () -> Int): Pair<Int, JsonMap?> {
        val l = len()
        var i = findOpenBrace(s)
        if (i < 0) return 0 to null
        i++
        val out = JsonMap()
        fun skipWs() { while (i < l && s[i].isWhitespace()) i++ }
        fun readStr(): String? {
            skipWs()
            if (i >= l || s[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < l) {
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c == '\\' && i < l) {
                    when (val ec = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        else -> sb.append(ec)
                    }
                } else {
                    sb.append(c)
                }
            }
            return null
        }
        fun readVal(): JsonVal? {
            skipWs()
            if (i >= l) return null
            return when (val c = s[i]) {
                '"' -> { i--; JsonStr(readStr().orEmpty()) }
                '{' -> {
                    // nested object → 不展开，跳过内部到下一个 '}'
                    var depth = 1; i++
                    while (i < l && depth > 0) {
                        when (s[i]) {
                            '"' -> { i++; while (i < l) { if (s[i++] == '"') break; if (s[i] == '\\' && i + 1 < l) i++ } }
                            '{' -> { depth++; i++ }
                            '}' -> { depth--; if (depth == 0) { i++; break }; i++ }
                            else -> i++
                        }
                    }
                    JsonStr("<nested_object>")
                }
                '[' -> {
                    val list = JsonList()
                    i++
                    while (i < l) {
                        skipWs()
                        if (s[i] == ']') { i++; break }
                        list.values += readVal()
                        skipWs()
                        if (i < l && s[i] == ',') { i++; continue }
                    }
                    list
                }
                't','f' -> {
                    val buf = StringBuilder()
                    while (i < l && s[i].isLetter()) { buf.append(s[i++]) }
                    JsonBool(buf.toString().toBoolean())
                }
                'n' -> {
                    while (i < l && s[i].isLetter()) i++
                    JsonNull
                }
                '-', in '0'..'9' -> {
                    val buf = StringBuilder()
                    while (i < l && (s[i] in "0123456789+-.eE")) { buf.append(s[i++]) }
                    val raw = buf.toString()
                    val num: Number = runCatching { raw.toInt() }.getOrNull()
                        ?: runCatching { raw.toLong() }.getOrNull()
                        ?: runCatching { raw.toDouble() }.getOrNull() ?: 0
                    JsonNum(num)
                }
                else -> { i++; JsonNull }
            }
        }

        while (i < l) {
            skipWs()
            if (i < l && s[i] == '}') { i++; return i to out }
            val key = readStr() ?: return i to null
            skipWs()
            if (i >= l || s[i++] != ':') return i to null
            val v = readVal()
            out.entries[key] = v
            skipWs()
            if (i < l && s[i] == ',') { i++; continue }
            if (i < l && s[i] == '}') { i++; return i to out }
        }
        return i to null
    }

    private fun findOpenBrace(s: String): Int {
        for (j in s.indices) if (!s[j].isWhitespace()) {
            return if (s[j] == '{') j else -1
        }
        return -1
    }

    private fun buildIdentityHint(name: String, visual: String, palette: List<String>): String {
        val tokens = visual
            .split(Regex("\\s+"))
            .filter { it.length in 3..15 && !it[0].isUpperCase() }
            .distinct()
            .take(6)
            .joinToString(",")
        return "same_character:$name core_traits:{$tokens}" +
                if (palette.isNotEmpty()) " palette=${palette.joinToString("/")}" else ""
    }
}
