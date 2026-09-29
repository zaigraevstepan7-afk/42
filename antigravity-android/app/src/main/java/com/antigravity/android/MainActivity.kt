package com.antigravity.android

import android.content.Intent
import android.os.Bundle
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
import com.antigravity.android.net.CloudCodeClient
import com.antigravity.android.net.agentTools
import com.antigravity.android.ui.Canvas
import com.antigravity.android.ui.ChatScreen
import com.antigravity.android.ui.TextMain
import com.antigravity.android.ui.UiMessage
import com.antigravity.android.ui.WelcomeScreen
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
        var session by remember { mutableStateOf(store.read()) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val chats = remember { mutableStateListOf<ChatThread>() }
        var currentId by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

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
                val thread = chats.firstOrNull { it.id == currentId } ?: current()
                ChatScreen(
                    email = session?.email.orEmpty(),
                    model = thread.model,
                    models = MODELS,
                    messages = thread.messages,
                    conversations = chats.map { it.id to it.title },
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
                        phase = "welcome"
                    },
                    onSend = { text ->
                        val active = session ?: return@ChatScreen
                        if (text.isBlank() || busy) return@ChatScreen
                        thread.messages.add(UiMessage("user", text))
                        if (thread.title == "Новый чат") thread.title = text.take(42)
                        thread.contents.put(userText(text))
                        scope.launch {
                            busy = true
                            var live: Session = active
                            try {
                                withContext(Dispatchers.IO) {
                                    for (round in 0 until 8) {
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
                                        val (fresh, parts) = cloud.generate(live, thread.model, JSONObject().put("request", inner))
                                        live = fresh
                                        val calls = parts.filter { !it.callName.isNullOrBlank() }
                                        withContext(Dispatchers.Main) {
                                            session = fresh
                                            parts.filter { it.thought && !it.text.isNullOrBlank() }.forEach {
                                                thread.messages.add(UiMessage("tool", it.text.orEmpty()))
                                            }
                                            parts.filter { !it.thought && !it.text.isNullOrBlank() && it.callName == null }.forEach {
                                                thread.messages.add(UiMessage("model", it.text.orEmpty()))
                                            }
                                        }
                                        thread.contents.put(modelContent(parts))
                                        if (calls.isEmpty()) break
                                        val responses = JSONArray()
                                        calls.forEach { call ->
                                            val output = DeviceTools.run(call.callName.orEmpty(), call.callArgs ?: JSONObject())
                                            withContext(Dispatchers.Main) {
                                                thread.messages.add(UiMessage("tool", call.callName.orEmpty()))
                                            }
                                            responses.put(
                                                JSONObject().put(
                                                    "functionResponse",
                                                    JSONObject()
                                                        .put("name", call.callName)
                                                        .put("response", JSONObject().put("output", output)),
                                                ),
                                            )
                                        }
                                        thread.contents.put(JSONObject().put("role", "user").put("parts", responses))
                                        if (round == 7) break
                                    }
                                }
                                store.write(live)
                                session = live
                            } catch (t: Throwable) {
                                thread.messages.add(UiMessage("model", t.message ?: "ошибка"))
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
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
        Файлы, команды и загрузки делай инструментами list_dir, read_file, write_file, exec, download.
        Пути абсолютные. Не выдумывай вывод команд.
    """.trimIndent()
    return JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
}
