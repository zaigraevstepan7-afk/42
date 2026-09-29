package com.antigravity.android.auth

import android.content.Context
import com.antigravity.android.BuildConfig
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AntigravityOAuth {
    val CLIENT_ID: String get() = BuildConfig.OAUTH_CLIENT_ID
    val CLIENT_SECRET: String get() = BuildConfig.OAUTH_CLIENT_SECRET
    const val REDIRECT_URI = "http://127.0.0.1:51121/oauth-callback"
    const val CALLBACK_PORT = 51121
    const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo"
    const val LOAD_CODE_ASSIST = "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist"
    const val ONBOARD_USER = "https://daily-cloudcode-pa.googleapis.com/v1internal:onboardUser"
    const val ONBOARD_USER_AGENT = "antigravity/1.0.13"

    val SCOPES = listOf(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
        "https://www.googleapis.com/auth/cclog",
        "https://www.googleapis.com/auth/experimentsandconfigs",
    )

    fun newState(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun authUrl(state: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return buildString {
            append(AUTH_ENDPOINT)
            append("?response_type=code")
            append("&access_type=offline")
            append("&prompt=consent")
            append("&client_id=").append(enc(CLIENT_ID))
            append("&redirect_uri=").append(enc(REDIRECT_URI))
            append("&scope=").append(enc(SCOPES.joinToString(" ")))
            append("&state=").append(enc(state))
        }
    }
}

data class Session(
    val type: String = "antigravity",
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val email: String,
    val projectId: String,
)

class SessionStore(context: Context) {
    private val file = context.filesDir.resolve("antigravity-session.json")

    fun read(): Session? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            Session(
                type = json.optString("type", "antigravity"),
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                expiresAtEpochMs = json.getLong("expiresAtEpochMs"),
                email = json.optString("email"),
                projectId = json.getString("projectId"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun write(session: Session) {
        val json = JSONObject()
            .put("type", session.type)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("expiresAtEpochMs", session.expiresAtEpochMs)
            .put("email", session.email)
            .put("projectId", session.projectId)
        file.writeText(json.toString())
    }

    fun clear() {
        file.delete()
    }
}

class OAuthCallbackServer(
    private val expectedState: String,
    private val onCode: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val stop = AtomicBoolean(false)
    private var socket: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress("0.0.0.0", AntigravityOAuth.CALLBACK_PORT))
        server.soTimeout = 1000
        socket = server
        thread = Thread {
            val deadline = System.currentTimeMillis() + 180_000L
            while (!stop.get() && System.currentTimeMillis() < deadline) {
                try {
                    val client = server.accept()
                    handle(client)
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                    if (stop.get()) break
                }
            }
            if (!stop.get()) onError("Вход не дождался возврата")
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        stop.set(true)
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        thread?.interrupt()
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5000
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val query = path.substringAfter("?", "")
            val params = parseQuery(query)
            val code = params["code"]
            val state = params["state"]
            val error = params["error"]
            if (!code.isNullOrBlank() && state == expectedState) {
                onCode(code)
            } else if (!error.isNullOrBlank() && (state == null || state == expectedState)) {
                onError(error)
            }
            val body = "<html><body>Вход выполнен</body></html>"
            val bytes = body.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            try {
                val out = client.getOutputStream()
                out.write(header.toByteArray(Charsets.US_ASCII))
                out.write(bytes)
                out.flush()
            } catch (_: Exception) {
            }
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun parseQuery(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            return raw.split("&").mapNotNull { pair ->
                val key = pair.substringBefore("=")
                if (key.isBlank()) return@mapNotNull null
                val value = URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8")
                URLDecoder.decode(key, "UTF-8") to value
            }.toMap()
        }
    }
}

class AntigravityAuthClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    fun exchange(code: String): Session {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", AntigravityOAuth.CLIENT_ID)
            .add("client_secret", AntigravityOAuth.CLIENT_SECRET)
            .add("redirect_uri", AntigravityOAuth.REDIRECT_URI)
            .build()
        val tokenJson = postForm(AntigravityOAuth.TOKEN_ENDPOINT, form, userAgent = null)
        val access = tokenJson.getString("access_token")
        val refresh = tokenJson.optString("refresh_token")
        if (refresh.isBlank()) error("Google не вернул refresh token")
        val expiresIn = tokenJson.optLong("expires_in", 3600L)
        val email = userInfo(access)
        val project = resolveProject(access)
        return Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
            email = email,
            projectId = project,
        )
    }

    fun ensureFresh(session: Session, store: SessionStore): Session {
        if (session.expiresAtEpochMs - System.currentTimeMillis() > 60_000L) return session
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", session.refreshToken)
            .add("client_id", AntigravityOAuth.CLIENT_ID)
            .add("client_secret", AntigravityOAuth.CLIENT_SECRET)
            .build()
        val tokenJson = postForm(AntigravityOAuth.TOKEN_ENDPOINT, form, userAgent = "Go-http-client/2.0")
        val access = tokenJson.getString("access_token")
        val refresh = tokenJson.optString("refresh_token").ifBlank { session.refreshToken }
        val expiresIn = tokenJson.optLong("expires_in", 3600L)
        val fresh = session.copy(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
        )
        store.write(fresh)
        return fresh
    }

    private fun userInfo(access: String): String {
        val request = Request.Builder()
            .url(AntigravityOAuth.USERINFO_ENDPOINT)
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("userinfo ${response.code}: $body")
            return JSONObject(body).optString("email")
        }
    }

    private fun resolveProject(access: String): String {
        val loadBody = JSONObject().put("metadata", JSONObject().put("ideType", "ANTIGRAVITY"))
        val load = postJson(
            AntigravityOAuth.LOAD_CODE_ASSIST,
            loadBody,
            access,
            userAgent = "antigravity/hub/2.13.0 darwin/arm64",
        )
        extractProjectId(load)?.let { return it }
        val tier = defaultTier(load)
        repeat(5) {
            val onboardBody = JSONObject()
                .put("tier_id", tier)
                .put(
                    "metadata",
                    JSONObject()
                        .put("ide_type", "ANTIGRAVITY")
                        .put("ide_version", "1.0.13")
                        .put("ide_name", "antigravity"),
                )
            val onboard = postJson(
                AntigravityOAuth.ONBOARD_USER,
                onboardBody,
                access,
                userAgent = AntigravityOAuth.ONBOARD_USER_AGENT,
            )
            if (onboard.optBoolean("done")) {
                val response = onboard.optJSONObject("response")
                val id = extractProjectId(response) ?: extractProjectId(onboard)
                if (!id.isNullOrBlank()) return id
            }
            Thread.sleep(2_000L)
        }
        error("Проект cloudaicompanionProject не найден")
    }

    private fun postForm(url: String, body: FormBody, userAgent: String?): JSONObject {
        val builder = Request.Builder().url(url).post(body).header("Content-Type", "application/x-www-form-urlencoded")
        if (userAgent != null) builder.header("User-Agent", userAgent)
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("token ${response.code}: $text")
            return JSONObject(text)
        }
    }

    private fun postJson(url: String, body: JSONObject, access: String, userAgent: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $access")
            .header("Content-Type", "application/json")
            .header("Accept", "*/*")
            .header("User-Agent", userAgent)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("code assist ${response.code}: $text")
            return JSONObject(text.ifBlank { "{}" })
        }
    }
}

fun extractProjectId(json: JSONObject?, depth: Int = 0): String? {
    if (json == null || depth > 6) return null
    for (key in listOf("cloudaicompanionProject", "projectId", "project")) {
        if (!json.has(key) || json.isNull(key)) continue
        when (val value = json.get(key)) {
            is String -> if (value.isNotBlank()) return value.trim()
            is JSONObject -> {
                val nested = value.optString("id").ifBlank { value.optString("projectId") }
                if (nested.isNotBlank()) return nested.trim()
            }
        }
    }
    val keys = json.keys()
    while (keys.hasNext()) {
        val value = json.opt(keys.next())
        if (value is JSONObject) {
            extractProjectId(value, depth + 1)?.let { return it }
        }
    }
    return null
}

private fun defaultTier(load: JSONObject): String {
    val tiers = load.optJSONArray("allowedTiers") ?: JSONArray()
    for (i in 0 until tiers.length()) {
        val tier = tiers.optJSONObject(i) ?: continue
        if (tier.optBoolean("isDefault")) {
            val id = tier.optString("id")
            if (id.isNotBlank()) return id
        }
    }
    val current = load.optJSONObject("currentTier")?.optString("id").orEmpty()
    return current.ifBlank { "free-tier" }
}
