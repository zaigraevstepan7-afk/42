package com.antigravity.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.antigravity.android.ui.ReplyPhase
import com.antigravity.android.ui.SettingsScreen
import com.antigravity.android.ui.TextMain
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SessionStore(this)
        cloud = CloudCodeClient(auth, store)
        enableEdgeToEdge()
        setContent { RootApp() }
    }

    @Composable
    private fun RootApp() {
        var phase by remember { mutableStateOf("check") }
        var settings by remember { mutableStateOf(false) }
        var session by remember { mutableStateOf(store.read()) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
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
                    model = thread.model,
                    models = MODELS,
                    messages = thread.messages,
                    conversations = chats.map { it.id to it.title },
                    activeId = thread.id,
                    busy = busy,
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
                    onSend = { text ->
                        val active = session ?: return@ChatScreen
                        if (text.isBlank() || busy) return@ChatScreen
                        thread.messages.add(UiMessage("user", text, sending = true))
                        if (thread.title == "Новый чат") thread.title = text.take(42)
                        thread.contents.put(userText(text))
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
                if (settings) {
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
        val searchReady = AtomicBoolean(false)
        val shownText = AtomicBoolean(false)
        val streaming = AtomicBoolean(true)
        val buffered = AtomicReference("")
        val foundCitations = AtomicReference<List<Citation>>(emptyList())
        val intro = launch {
            delay(300)
            if (halt.get()) return@launch
            patchUserSending(thread, false)
            delay(260)
            if (halt.get() || shownText.get()) return@launch
            patchAssistant(thread) {
                it.copy(phase = ReplyPhase.Searching, searchLabel = "Поиск в интернете...", thinking = true)
            }
            delay(650)
            if (halt.get() || shownText.get()) return@launch
            patchAssistant(thread) {
                it.copy(
                    phase = ReplyPhase.Searching,
                    searchLabel = searchQueryLabel(prompt),
                    thinking = true,
                )
            }
            delay(400)
            searchReady.set(true)
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
                                patchAssistant(thread) {
                                    it.copy(
                                        searchLabel = searchQueryLabel(delta.searchQueries.last()),
                                        phase = if (shownText.get()) it.phase else ReplyPhase.Searching,
                                        thinking = !shownText.get(),
                                    )
                                }
                            }
                            if (!delta.thought.isNullOrBlank() && !shownText.get()) {
                                patchAssistant(thread) {
                                    it.copy(
                                        thinking = true,
                                        phase = if (it.phase == ReplyPhase.Waiting) ReplyPhase.Searching else it.phase,
                                        searchLabel = it.searchLabel ?: "Поиск в интернете...",
                                    )
                                }
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
                    thread.contents.put(modelContent(parts))
                    if (calls.isEmpty()) break
                    val responses = JSONArray()
                    calls.forEach { call ->
                        val query = call.callArgs?.optString("query").orEmpty()
                        if (call.callName == "web_search" && query.isNotBlank()) {
                            onMain {
                                patchAssistant(thread) {
                                    it.copy(phase = ReplyPhase.Searching, thinking = true, searchLabel = searchQueryLabel(query))
                                }
                            }
                        }
                        val output = DeviceTools.run(call.callName.orEmpty(), call.callArgs ?: JSONObject())
                        if (output.citations.isNotEmpty()) {
                            val merged = (foundCitations.get() + output.citations).distinctBy { it.url }
                            foundCitations.set(merged)
                        }
                        responses.put(
                            JSONObject().put(
                                "functionResponse",
                                JSONObject()
                                    .put("name", call.callName)
                                    .put("response", JSONObject().put("output", output.text)),
                            ),
                        )
                    }
                    thread.contents.put(JSONObject().put("role", "user").put("parts", responses))
                    onMain {
                        shownText.set(false)
                        searchReady.set(true)
                        patchAssistant(thread) {
                            it.copy(phase = ReplyPhase.Searching, thinking = true, searchLabel = "Поиск в интернете...")
                        }
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

private fun searchQueryLabel(query: String): String {
    val clipped = if (query.length > 56) query.take(56) + "…" else query
    return "Поиск по запросу «$clipped»"
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

private fun userText(text: String): JSONObject {
    return JSONObject()
        .put("role", "user")
        .put("parts", JSONArray().put(JSONObject().put("text", text)))
}

private fun modelContent(parts: List<com.antigravity.android.net.ModelPart>): JSONObject {
    val array = JSONArray()
    parts.forEach { part ->
        if (!part.callName.isNullOrBlank()) {
            array.put(
                JSONObject().put(
                    "functionCall",
                    JSONObject().put("name", part.callName).put("args", part.callArgs ?: JSONObject()),
                ),
            )
        } else if (part.text != null) {
            array.put(JSONObject().put("text", part.text).put("thought", part.thought))
        }
    }
    return JSONObject().put("role", "model").put("parts", array)
}

private fun systemInstruction(): JSONObject {
    val text = """
        Ты работаешь внутри телефона с root. Отвечай на языке пользователя.
        Для фактов, новостей, цен, дат и всего актуального сначала вызови web_search, затем при необходимости read_url.
        Не выдумывай источники. В ответе упоминай названия сайтов из результатов поиска.
        Файлы, команды и загрузки делай инструментами list_dir, read_file, write_file, exec, download.
        Пути абсолютные. Не выдумывай вывод команд.
        Форматируй ответ markdown: сначала короткий абзац, затем жирный заголовок, затем абзацы.
    """.trimIndent()
    return JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
}
