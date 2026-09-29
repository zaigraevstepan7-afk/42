package com.antigravity.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.antigravity.android.agent.DeviceTools
import com.antigravity.android.auth.AntigravityAuthClient
import com.antigravity.android.auth.AntigravityOAuth
import com.antigravity.android.auth.Session
import com.antigravity.android.auth.SessionStore
import com.antigravity.android.net.Citation
import com.antigravity.android.net.CloudCodeClient
import com.antigravity.android.net.agentTools
import com.antigravity.android.net.tokenize
import com.antigravity.android.ui.Canvas
import com.antigravity.android.ui.ChatScreen
import com.antigravity.android.ui.PendingFile
import com.antigravity.android.ui.ReplyPhase
import com.antigravity.android.ui.SettingsScreen
import com.antigravity.android.ui.TextMain
import com.antigravity.android.ui.ThinkStep
import com.antigravity.android.ui.UiMessage
import com.antigravity.android.ui.WelcomeScreen
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {
    private val auth = AntigravityAuthClient()
    private lateinit var store: SessionStore
    private lateinit var cloud: CloudCodeClient

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra(GoogleLoginActivity.EXTRA_CODE)
        val error = result.data?.getStringExtra(GoogleLoginActivity.EXTRA_ERROR)
        loginCallback?.invoke(code, error)
    }
    private var loginCallback: ((String?, String?) -> Unit)? = null
    private var onShare: ((PendingFile) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SessionStore(this)
        cloud = CloudCodeClient(auth, store)
        enableEdgeToEdge()
        setContent { RootApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedImage(intent)?.let { onShare?.invoke(it) }
    }

    private fun readSharedImage(source: Intent?): PendingFile? {
        if (source?.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        val uri = source.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return null
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        if (!mime.startsWith("image/")) return null
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        source.action = Intent.ACTION_MAIN
        source.removeExtra(Intent.EXTRA_STREAM)
        return PendingFile("screenshot.jpg", mime, bytes)
    }

    @Composable
    private fun RootApp() {
        var phase by remember { mutableStateOf("check") }
        var settings by remember { mutableStateOf(false) }
        var session by remember { mutableStateOf(store.read()) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var incoming by remember { mutableStateOf<PendingFile?>(null) }
        val chats = remember { mutableStateListOf<ChatThread>() }
        var currentId by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        var generateJob by remember { mutableStateOf<Job?>(null) }
        val halt = remember { AtomicBoolean(false) }

        fun current(): ChatThread {
            val found = chats.firstOrNull { it.id == currentId }
            if (found != null) return found
            val created = ChatThread()
            chats.add(0, created)
            currentId = created.id
            return created
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            val rooted = withContext(Dispatchers.IO) {
                try {
                    Shell.getShell().isRoot
                } catch (_: Throwable) {
                    false
                }
            }
            phase = when {
                !rooted -> "blocked"
                session != null -> "chat"
                else -> "welcome"
            }
            if (phase == "chat" && chats.isEmpty()) current()
            readSharedImage(intent)?.let { incoming = it }
            val current = session
            if (current != null && current.displayName.isBlank()) {
                val named = withContext(Dispatchers.IO) {
                    runCatching { auth.loadProfile(current.accessToken) }.getOrNull()
                }
                if (named != null && named.second.isNotBlank()) {
                    val updated = current.copy(email = named.first.ifBlank { current.email }, displayName = named.second)
                    store.write(updated)
                    session = updated
                }
            }
        }

        loginCallback = { code, err ->
            if (code.isNullOrBlank()) {
                error = err ?: "Вход отменён"
                busy = false
            } else scope.launch {
                busy = true
                try {
                    val next = withContext(Dispatchers.IO) { auth.exchange(code) }
                    store.write(next)
                    session = next
                    phase = "chat"
                    error = null
                    if (chats.isEmpty()) current()
                } catch (t: Throwable) {
                    error = t.message ?: "Вход не выполнен"
                } finally {
                    busy = false
                }
            }
        }

        onShare = { incoming = it }

        when (phase) {
            "check" -> Box(Modifier.fillMaxSize().background(Canvas))
            "blocked" -> Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) {
                Text("Нужны root-права", color = TextMain, fontSize = 18.sp)
            }
            "welcome" -> WelcomeScreen(busy = busy, error = error) {
                val state = AntigravityOAuth.newState()
                busy = true
                error = null
                login.launch(
                    Intent(this, GoogleLoginActivity::class.java)
                        .putExtra(GoogleLoginActivity.EXTRA_STATE, state)
                        .putExtra(GoogleLoginActivity.EXTRA_URL, AntigravityOAuth.authUrl(state)),
                )
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                val thread = chats.firstOrNull { it.id == currentId } ?: current()
                ChatScreen(
                    email = session?.email.orEmpty(),
                    displayName = session?.displayName.orEmpty(),
                    model = thread.model,
                    models = MODELS,
                    messages = thread.messages,
                    conversations = chats.map { it.id to it.title },
                    activeId = thread.id,
                    busy = busy,
                    incoming = incoming,
                    onIncomingConsumed = { incoming = null },
                    onModel = { thread.model = it },
                    onNewChat = {
                        val created = ChatThread()
                        chats.add(0, created)
                        currentId = created.id
                    },
                    onOpenChat = { currentId = it },
                    onLogout = {
                        store.clear()
                        session = null
                        settings = false
                        phase = "welcome"
                    },
                    onSettings = { settings = true },
                    onSend = { text, files, deep ->
                        val active = session ?: return@ChatScreen
                        if ((text.isBlank() && files.isEmpty()) || busy) return@ChatScreen
                        val images = files.filter { it.mime.startsWith("image/") }.map { it.bytes }
                        val shown = text
                        thread.messages.add(UiMessage("user", shown, sending = true, images = images))
                        if (thread.title == "Новый чат") thread.title = text.ifBlank { files.firstOrNull()?.name ?: "Вложение" }.take(42)
                        thread.contents.put(userContent(text, files, deep))
                        halt.set(false)
                        busy = true
                        generateJob = scope.launch {
                            runGeneration(thread, active, text, halt, { busy = it }) { session = it }
                        }
                    },
                    onStop = {
                        halt.set(true)
                        generateJob?.cancel()
                        cloud.cancel()
                        finalizeStop(thread)
                        busy = false
                    },
                    onRegenerate = {
                        if (busy) return@ChatScreen
                        val lastUser = thread.messages.lastOrNull { it.role == "user" }?.text ?: return@ChatScreen
                        rewindAfterUser(thread)
                        halt.set(false)
                        val active = session ?: return@ChatScreen
                        busy = true
                        generateJob = scope.launch {
                            runGeneration(thread, active, lastUser, halt, { busy = it }) { session = it }
                        }
                    },
                )
                AnimatedVisibility(
                    visible = settings,
                    enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
                    exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
                ) {
                    SettingsScreen(
                        email = session?.email.orEmpty(),
                        displayName = session?.displayName.orEmpty(),
                        models = MODELS,
                        model = thread.model,
                        onModel = { thread.model = it },
                        onBack = { settings = false },
                        onLogout = {
                            store.clear()
                            session = null
                            settings = false
                            phase = "welcome"
                        },
                    )
                }
                }
            }
        }
    }

    private suspend fun runGeneration(
        thread: ChatThread,
        start: Session,
        prompt: String,
        halt: AtomicBoolean,
        setBusy: (Boolean) -> Unit,
        onSession: (Session) -> Unit,
    ) = coroutineScope {
        setBusy(true)
        thread.messages.add(
            UiMessage("model", "", phase = ReplyPhase.Waiting, complete = false, thinking = false),
        )
        val startedAt = System.currentTimeMillis()
        val searchReady = AtomicBoolean(true)
        val shownText = AtomicBoolean(false)
        val streaming = AtomicBoolean(true)
        val buffered = AtomicReference("")
        val foundCitations = AtomicReference<List<Citation>>(emptyList())
        val intro = launch {
            delay(180)
            if (!halt.get()) patchUserSending(thread, false)
        }
        try {
            var live = start
            withContext(Dispatchers.IO) {
                for (round in 0 until 8) {
                    if (halt.get()) break
                    val inner = JSONObject()
                        .put("systemInstruction", systemInstruction())
                        .put("contents", thread.contents)
                        .put(
                            "generationConfig",
                            JSONObject().put(
                                "thinkingConfig",
                                JSONObject().put("includeThoughts", true),
                            ),
                        )
                        .put("tools", agentTools())
                        .put("sessionId", thread.sessionId)
                    val (fresh, parts) = cloud.stream(live, thread.model, JSONObject().put("request", inner), halt) { delta ->
                        onMain {
                            if (delta.searchQueries.isNotEmpty()) {
                                patchAssistant(thread) { noteSearch(it, delta.searchQueries) }
                            }
                            if (!delta.thought.isNullOrBlank()) {
                                patchAssistant(thread) { noteThought(it, delta.thought.orEmpty(), !shownText.get()) }
                            }
                            val incoming = delta.text
                            if (!incoming.isNullOrBlank()) {
                                buffered.set(incoming)
                                if (delta.citations.isNotEmpty()) foundCitations.set(delta.citations)
                                if (searchReady.get() && streaming.get()) {
                                    shownText.set(true)
                                    patchAssistant(thread) {
                                        it.copy(
                                            text = incoming,
                                            phase = ReplyPhase.Streaming,
                                            thinking = false,
                                            citations = foundCitations.get(),
                                            searchLabel = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    live = fresh
                    val calls = parts.filter { !it.callName.isNullOrBlank() }
                    if (calls.isEmpty()) break
                    val replay = modelContent(parts)
                    val replayParts = replay.getJSONArray("parts")
                    val unsigned = (0 until replayParts.length()).any { index ->
                        val item = replayParts.optJSONObject(index)
                        item != null && item.has("functionCall") && item.optString("thoughtSignature").isBlank()
                    }
                    val notes = StringBuilder()
                    val responses = JSONArray()
                    calls.forEach { call ->
                        val rawName = call.callName.orEmpty()
                        val localName = rawName.substringAfterLast(':')
                        val query = call.callArgs?.optString("query").orEmpty()
                        if (localName == "web_search" && query.isNotBlank()) {
                            onMain { patchAssistant(thread) { noteSearch(it, listOf(query)) } }
                        } else {
                            onMain { patchAssistant(thread) { noteTool(it, localName, call.callArgs ?: JSONObject()) } }
                        }
                        val output = DeviceTools.run(localName, call.callArgs ?: JSONObject())
                        if (output.citations.isNotEmpty()) {
                            val merged = (foundCitations.get() + output.citations).distinctBy { it.url }
                            foundCitations.set(merged)
                        }
                        if (unsigned) {
                            if (notes.isNotEmpty()) notes.append("\n\n")
                            notes.append(output.text)
                        } else {
                            val response = JSONObject().put("name", rawName).put("response", JSONObject().put("output", output.text))
                            if (!call.callId.isNullOrBlank()) response.put("id", call.callId)
                            responses.put(JSONObject().put("functionResponse", response))
                        }
                    }
                    if (unsigned) {
                        thread.contents.put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", "Результат инструмента:\n$notes"))),
                        )
                    } else {
                        thread.contents.put(replay)
                        thread.contents.put(JSONObject().put("role", "user").put("parts", responses))
                    }
                }
            }
            streaming.set(false)
            intro.join()
            if (!halt.get()) {
                val full = buffered.get()
                val cites = foundCitations.get()
                if (!shownText.get() && full.isNotEmpty()) {
                    var shown = ""
                    for (chunk in tokenize(full)) {
                        if (halt.get()) break
                        shown += chunk
                        patchAssistant(thread) {
                            it.copy(text = shown, phase = ReplyPhase.Streaming, thinking = false, citations = cites)
                        }
                        delay(16)
                    }
                    shownText.set(true)
                }
                patchAssistant(thread) {
                    it.copy(
                        phase = ReplyPhase.Done,
                        complete = true,
                        thinking = false,
                        thoughtSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1),
                        citations = cites.ifEmpty { it.citations },
                    )
                }
                store.write(live)
                onSession(live)
            }
        } catch (_: CancellationException) {
            finalizeStop(thread)
        } catch (t: Throwable) {
            patchUserSending(thread, false)
            patchAssistant(thread) {
                it.copy(
                    text = t.message ?: "ошибка",
                    phase = ReplyPhase.Done,
                    complete = true,
                    thinking = false,
                )
            }
        } finally {
            patchUserSending(thread, false)
            setBusy(false)
        }
    }

    companion object {
        val MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-3-pro",
            "gemini-3.1-pro",
            "gemini-3.5-pro",
            "gemini-3.7-pro",
        )
    }
}

private fun patchUserSending(thread: ChatThread, sending: Boolean) {
    val idx = thread.messages.indexOfLast { it.role == "user" }
    if (idx >= 0) thread.messages[idx] = thread.messages[idx].copy(sending = sending)
}

private fun patchAssistant(thread: ChatThread, update: (UiMessage) -> UiMessage) {
    val idx = thread.messages.indexOfLast { it.role == "model" }
    if (idx >= 0) thread.messages[idx] = update(thread.messages[idx])
}

private fun finalizeStop(thread: ChatThread) {
    patchUserSending(thread, false)
    patchAssistant(thread) { current ->
        current.copy(
            sending = false,
            thinking = false,
            complete = true,
            phase = ReplyPhase.Done,
            text = current.text.ifBlank { "Остановлено" },
        )
    }
}

private fun rewindAfterUser(thread: ChatThread) {
    while (thread.messages.isNotEmpty() && thread.messages.last().role != "user") {
        thread.messages.removeAt(thread.messages.lastIndex)
    }
    while (thread.contents.length() > 0) {
        val last = thread.contents.optJSONObject(thread.contents.length() - 1) ?: break
        val role = last.optString("role")
        val first = last.optJSONArray("parts")?.optJSONObject(0)
        val tool = first?.has("functionResponse") == true
        if (role == "model" || tool) thread.contents.remove(thread.contents.length() - 1) else break
    }
}

private fun noteTool(message: UiMessage, name: String, args: JSONObject): UiMessage {
    val line = when (name) {
        "read_url" -> "Открывает страницу"
        "list_dir" -> "Смотрит папку ${args.optString("path")}"
        "read_file" -> "Читает файл ${args.optString("path").substringAfterLast('/')}"
        "write_file" -> "Создаёт файл ${args.optString("path").substringAfterLast('/')}"
        "exec" -> "Выполняет команду"
        "download" -> "Скачивает файл"
        else -> "Использует $name"
    }
    if (message.steps.lastOrNull()?.text == line) return message
    return message.copy(
        steps = message.steps + ThinkStep("tool", line),
        phase = if (message.text.isNotEmpty()) message.phase else ReplyPhase.Searching,
        thinking = false,
    )
}

private fun noteThought(message: UiMessage, full: String, stillThinking: Boolean): UiMessage {
    if (full.isBlank()) return message
    val previous = message.thought
    if (full == previous) return message.copy(thinking = stillThinking)
    val addition = if (full.startsWith(previous)) full.removePrefix(previous).trim() else full.trim()
    val steps = message.steps.toMutableList()
    val last = steps.lastOrNull()
    if (addition.isNotBlank()) {
        if (last != null && last.kind == "thought") {
            steps[steps.lastIndex] = last.copy(text = (last.text.trimEnd() + " " + addition).trim())
        } else {
            steps += ThinkStep("thought", addition)
        }
    }
    return message.copy(thought = full, steps = steps, thinking = stillThinking)
}

private fun noteSearch(message: UiMessage, queries: List<String>): UiMessage {
    val steps = message.steps.toMutableList()
    queries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { query ->
        val line = "Ищет информацию: «$query»"
        if (steps.none { it.kind == "search" && it.text == line }) steps += ThinkStep("search", line)
    }
    return message.copy(
        steps = steps,
        searchSteps = searchSteps(queries),
        phase = if (message.text.isNotEmpty()) message.phase else ReplyPhase.Searching,
        thinking = false,
    )
}

private fun searchSteps(queries: List<String>): List<String> {
    val lines = ArrayList<String>()
    lines.add("Поиск в интернете...")
    queries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { query ->
        lines.add("Поиск по запросу «$query»")
    }
    return lines
}

private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block()
    else Handler(Looper.getMainLooper()).post(block)
}

class ChatThread {
    val id: String = UUID.randomUUID().toString()
    var title by mutableStateOf("Новый чат")
    var model by mutableStateOf(MainActivity.MODELS.first())
    val messages = mutableStateListOf<UiMessage>()
    val contents = JSONArray()
    val sessionId: String = "-" + kotlin.math.abs(id.hashCode().toLong())
}

private fun userContent(text: String, files: List<PendingFile>, deep: Boolean): JSONObject {
    val parts = JSONArray()
    val images = files.filter { it.mime.startsWith("image/") }
    images.forEach { file ->
        val (mime, bytes) = shrinkImage(file.bytes, file.mime)
        parts.put(
            JSONObject().put(
                "inlineData",
                JSONObject()
                    .put("mimeType", mime)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
            ),
        )
    }
    val body = buildString {
        if (images.isNotEmpty() && text.isBlank()) append("Посмотри на приложенное изображение и ответь, что на нём.")
        else append(text)
        if (deep) {
            if (isNotEmpty()) append("\n\n")
            append("Размышляй глубже и подробнее.")
        }
    }
    if (body.isNotBlank()) parts.put(JSONObject().put("text", body))
    files.filter { !it.mime.startsWith("image/") }.forEach { file ->
        val decoded = runCatching { file.bytes.toString(Charsets.UTF_8).take(12_000) }.getOrDefault("")
        parts.put(JSONObject().put("text", "Вложение ${file.name}:\n$decoded"))
    }
    if (parts.length() == 0) parts.put(JSONObject().put("text", text.ifBlank { " " }))
    return JSONObject().put("role", "user").put("parts", parts)
}

private fun shrinkImage(bytes: ByteArray, mime: String): Pair<String, ByteArray> {
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return mime to bytes
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val scaled = if (maxSide > 1600) {
        val ratio = 1600f / maxSide
        android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    } else {
        bitmap
    }
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
    return "image/jpeg" to out.toByteArray()
}

private fun modelContent(parts: List<com.antigravity.android.net.ModelPart>): JSONObject {
    val array = JSONArray()
    var carried = parts.firstOrNull { !it.thoughtSignature.isNullOrBlank() }?.thoughtSignature
    parts.forEach { part ->
        if (!part.thoughtSignature.isNullOrBlank()) carried = part.thoughtSignature
        val obj = JSONObject()
        if (!part.callName.isNullOrBlank()) {
            val call = JSONObject().put("name", part.callName).put("args", part.callArgs ?: JSONObject())
            if (!part.callId.isNullOrBlank()) call.put("id", part.callId)
            obj.put("functionCall", call)
        } else if (part.text != null) {
            obj.put("text", part.text)
            if (part.thought) obj.put("thought", true)
        }
        val signature = part.thoughtSignature ?: if (!part.callName.isNullOrBlank()) carried else null
        if (!signature.isNullOrBlank()) obj.put("thoughtSignature", signature)
        if (obj.length() > 0) array.put(obj)
    }
    return JSONObject().put("role", "model").put("parts", array)
}

private fun systemInstruction(): JSONObject {
    val text = """
        Ты работаешь внутри телефона с root. Отвечай на языке пользователя.
        Если во вложении есть изображение, ты его видишь. Отвечай по содержимому картинки, не пиши что не можешь смотреть скриншоты.
        Для фактов, новостей, цен, дат и всего актуального сначала вызови web_search.
        В query передавай короткий поисковый запрос по смыслу, на языке источников, а не дословную реплику пользователя.
        Не выдумывай источники. В ответе упоминай названия сайтов из результатов поиска.
        Файлы, команды и загрузки делай инструментами list_dir, read_file, write_file, exec, download.
        Пути абсолютные. Не выдумывай вывод команд.
        Форматируй ответ markdown: сначала короткий абзац, затем жирный заголовок, затем абзацы.
    """.trimIndent()
    return JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
}
