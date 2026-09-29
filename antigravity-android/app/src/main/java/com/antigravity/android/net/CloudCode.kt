package com.antigravity.android.net

import android.net.Uri
import com.antigravity.android.auth.AntigravityAuthClient
import com.antigravity.android.auth.Session
import com.antigravity.android.auth.SessionStore
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class ModelPart(
    val text: String? = null,
    val thought: Boolean = false,
    val callName: String? = null,
    val callArgs: JSONObject? = null,
    val callId: String? = null,
)

data class Citation(
    val label: String,
    val url: String,
)

data class StreamDelta(
    val thought: String? = null,
    val text: String? = null,
    val searchQueries: List<String> = emptyList(),
    val citations: List<Citation> = emptyList(),
)

class CloudCodeClient(
    private val auth: AntigravityAuthClient,
    private val store: SessionStore,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    private val active = AtomicReference<Call?>(null)

    fun cancel() {
        active.getAndSet(null)?.cancel()
    }

    fun generate(session: Session, model: String, request: JSONObject): Pair<Session, List<ModelPart>> {
        val fresh = auth.ensureFresh(session, store)
        val wireModels = listOf(model, model.removeSuffix("-tiered") + "-tiered").distinct()
        var lastError: String? = null
        for (wire in wireModels) {
            val httpRequest = requestOf(fresh, wire, request, stream = false)
            http.newCall(httpRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 404 && wire != wireModels.last()) {
                    lastError = text
                    return@use
                }
                if (!response.isSuccessful) error("generate ${response.code}: $text")
                return fresh to parseParts(text)
            }
        }
        error("generate 404: ${lastError.orEmpty()}")
    }

    suspend fun stream(
        session: Session,
        model: String,
        request: JSONObject,
        cancelled: AtomicBoolean,
        onDelta: (StreamDelta) -> Unit,
    ): Pair<Session, List<ModelPart>> {
        val fresh = auth.ensureFresh(session, store)
        val wireModels = listOf(model, model.removeSuffix("-tiered") + "-tiered").distinct()
        var lastError: String? = null
        for (wire in wireModels) {
            if (cancelled.get()) return fresh to emptyList()
            val httpRequest = requestOf(fresh, wire, request, stream = true)
            val call = http.newCall(httpRequest)
            active.set(call)
            try {
                call.execute().use { response ->
                    val body = response.body
                    if (response.code == 404 && wire != wireModels.last()) {
                        lastError = body?.string().orEmpty()
                        return@use
                    }
                    if (!response.isSuccessful) {
                        val text = body?.string().orEmpty()
                        if (response.code == 401 || response.code == 403) error("generate ${response.code}: $text")
                        if (wire != wireModels.last()) {
                            lastError = text
                            return@use
                        }
                        return fallbackGenerate(fresh, model, request, cancelled, onDelta)
                    }
                    val acc = StreamAccumulator()
                    val source = body?.source() ?: return@use
                    consumeSse(source, cancelled) { json ->
                        acc.accept(json)
                        onDelta(
                            StreamDelta(
                                thought = acc.thought.takeIf { it.isNotEmpty() },
                                text = acc.visible.takeIf { it.isNotEmpty() },
                                searchQueries = acc.queries.toList(),
                                citations = acc.citations.values.toList(),
                            ),
                        )
                    }
                    return fresh to acc.toParts()
                }
            } catch (t: Throwable) {
                if (cancelled.get()) return fresh to emptyList()
                if (t is java.io.IOException && wire != wireModels.last()) {
                    lastError = t.message
                    continue
                }
                throw t
            } finally {
                active.compareAndSet(call, null)
            }
        }
        return fallbackGenerate(fresh, model, request, cancelled, onDelta)
    }

    private suspend fun fallbackGenerate(
        session: Session,
        model: String,
        request: JSONObject,
        cancelled: AtomicBoolean,
        onDelta: (StreamDelta) -> Unit,
    ): Pair<Session, List<ModelPart>> {
        val (fresh, parts) = generate(session, model, request)
        val thought = parts.filter { it.thought }.joinToString("") { it.text.orEmpty() }
        val visible = parts.filter { !it.thought && it.callName == null }.joinToString("") { it.text.orEmpty() }
        if (thought.isNotBlank()) onDelta(StreamDelta(thought = thought))
        var shown = ""
        for (chunk in tokenize(visible)) {
            if (cancelled.get()) break
            shown += chunk
            onDelta(StreamDelta(text = shown, thought = thought.ifBlank { null }))
            kotlinx.coroutines.delay(18)
        }
        if (shown.isEmpty() && visible.isNotEmpty()) onDelta(StreamDelta(text = visible))
        return fresh to parts
    }

    private fun requestOf(session: Session, model: String, request: JSONObject, stream: Boolean): Request {
        val body = JSONObject(request.toString())
            .put("project", session.projectId)
            .put("model", model)
            .put("userAgent", "antigravity")
            .put("requestType", "agent")
            .put("requestId", "agent-${UUID.randomUUID()}")
        val url = if (stream) {
            "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse"
        } else {
            "https://daily-cloudcode-pa.googleapis.com/v1internal:generateContent"
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .header("User-Agent", "antigravity/hub/2.13.0 darwin/arm64")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }
}

private class StreamAccumulator {
    private val visibleBuf = StringBuilder()
    private val thoughtBuf = StringBuilder()
    private val calls = ArrayList<ModelPart>()
    private val seenCalls = HashSet<String>()
    val queries = LinkedHashSet<String>()
    val citations = LinkedHashMap<String, Citation>()

    val visible: String get() = visibleBuf.toString()
    val thought: String get() = thoughtBuf.toString()

    fun accept(json: JSONObject) {
        parseParts(json.toString()).forEach { part ->
            when {
                !part.callName.isNullOrBlank() -> {
                    val key = "${part.callName}:${part.callArgs}"
                    if (seenCalls.add(key)) calls += part
                }
                part.thought -> merge(thoughtBuf, part.text.orEmpty())
                !part.text.isNullOrBlank() -> merge(visibleBuf, part.text.orEmpty())
            }
        }
        parseGrounding(json, queries, citations)
    }

    fun toParts(): List<ModelPart> {
        val out = ArrayList<ModelPart>()
        if (thoughtBuf.isNotEmpty()) out += ModelPart(text = thoughtBuf.toString(), thought = true)
        if (visibleBuf.isNotEmpty()) out += ModelPart(text = visibleBuf.toString())
        out += calls
        return out
    }
}

private fun merge(buf: StringBuilder, incoming: String) {
    if (incoming.isEmpty()) return
    val current = buf.toString()
    when {
        incoming == current -> return
        incoming.startsWith(current) -> {
            buf.clear()
            buf.append(incoming)
        }
        current.startsWith(incoming) -> return
        else -> buf.append(incoming)
    }
}

private fun consumeSse(source: BufferedSource, cancelled: AtomicBoolean, onJson: (JSONObject) -> Unit) {
    val event = StringBuilder()
    fun flush() {
        val payload = event.toString().trim()
        event.clear()
        if (payload.isBlank() || payload == "[DONE]") return
        runCatching { JSONObject(payload) }.getOrNull()?.let(onJson)
    }
    while (!cancelled.get() && !source.exhausted()) {
        val line = source.readUtf8Line() ?: break
        when {
            line.startsWith("data:") -> {
                if (event.isNotEmpty()) event.append('\n')
                event.append(line.removePrefix("data:").trim())
            }
            line.isBlank() -> flush()
            line.startsWith("{") -> runCatching { JSONObject(line) }.getOrNull()?.let(onJson)
        }
    }
    if (event.isNotEmpty()) flush()
}

fun parseParts(raw: String): List<ModelPart> {
    val root = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    val response = root.optJSONObject("response") ?: root
    val candidates = response.optJSONArray("candidates") ?: JSONArray()
    if (candidates.length() == 0) {
        val message = root.optString("error").ifBlank { root.optJSONObject("error")?.optString("message").orEmpty() }
        if (message.isNotBlank() && root.has("error")) error(message)
        return emptyList()
    }
    val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return emptyList()
    val parts = content.optJSONArray("parts") ?: JSONArray()
    val out = ArrayList<ModelPart>(parts.length())
    for (i in 0 until parts.length()) {
        val part = parts.optJSONObject(i) ?: continue
        val call = part.optJSONObject("functionCall")
        if (call != null) {
            out += ModelPart(
                callName = call.optString("name"),
                callArgs = call.optJSONObject("args") ?: JSONObject(),
                callId = call.optString("id").ifBlank { null },
            )
        } else if (part.has("text")) {
            out += ModelPart(text = part.optString("text"), thought = part.optBoolean("thought"))
        }
    }
    return out
}

fun parseGrounding(
    raw: JSONObject,
    queries: MutableSet<String> = LinkedHashSet(),
    citations: MutableMap<String, Citation> = LinkedHashMap(),
): Pair<List<String>, List<Citation>> {
    val response = raw.optJSONObject("response") ?: raw
    val candidates = response.optJSONArray("candidates") ?: JSONArray()
    val meta = candidates.optJSONObject(0)?.optJSONObject("groundingMetadata") ?: return queries.toList() to citations.values.toList()
    val q = meta.optJSONArray("webSearchQueries")
    if (q != null) {
        for (i in 0 until q.length()) {
            val item = q.optString(i)
            if (item.isNotBlank()) queries += item
        }
    }
    val chunks = meta.optJSONArray("groundingChunks")
    if (chunks != null) {
        for (i in 0 until chunks.length()) {
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
            val url = web.optString("uri").ifBlank { web.optString("url") }
            val title = web.optString("title")
            if (url.isBlank()) continue
            citations.putIfAbsent(url, Citation(label = citationLabel(title, url), url = url))
        }
    }
    return queries.toList() to citations.values.toList()
}

fun citationLabel(title: String, url: String): String {
    val piped = title.substringAfterLast('|').trim()
    if (piped.isNotBlank() && piped.length <= 28) return piped
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.").orEmpty()
    if (host.isNotBlank()) {
        val brand = host.substringBefore('.')
        if (brand.isNotBlank()) return brand.replaceFirstChar { it.uppercase() }
    }
    return title.take(22).ifBlank { host.ifBlank { "Источник" } }
}

fun tokenize(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    val first = minOf(3, text.length)
    out += text.substring(0, first)
    var i = first
    var n = 0
    while (i < text.length) {
        val size = 4 + (n % 7)
        val end = minOf(text.length, i + size)
        out += text.substring(i, end)
        i = end
        n++
    }
    return out
}

fun agentTools(): JSONArray {
    fun decl(name: String, description: String, vararg required: Pair<String, String>): JSONObject {
        val properties = JSONObject()
        required.forEach { (key, desc) ->
            properties.put(key, JSONObject().put("type", "string").put("description", desc))
        }
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", JSONArray(required.map { it.first })),
            )
    }
    return JSONArray()
        .put(
            JSONObject().put(
                "functionDeclarations",
                JSONArray()
                    .put(decl("web_search", "Найти актуальные страницы в интернете", "query" to "Поисковый запрос"))
                    .put(decl("read_url", "Прочитать текст веб-страницы", "url" to "https адрес страницы"))
                    .put(decl("list_dir", "Список файлов в каталоге", "path" to "Абсолютный путь"))
                    .put(decl("read_file", "Прочитать файл", "path" to "Абсолютный путь"))
                    .put(decl("write_file", "Записать файл целиком", "path" to "Абсолютный путь", "content" to "Новое содержимое"))
                    .put(decl("exec", "Выполнить команду shell от root", "command" to "Команда"))
                    .put(decl("download", "Скачать URL в файл", "url" to "https URL", "path" to "Куда сохранить")),
            ),
        )
}
