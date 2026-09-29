package com.antigravity.android.net

import com.antigravity.android.auth.AntigravityAuthClient
import com.antigravity.android.auth.Session
import com.antigravity.android.auth.SessionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ModelPart(
    val text: String? = null,
    val thought: Boolean = false,
    val callName: String? = null,
    val callArgs: JSONObject? = null,
    val callId: String? = null,
)

class CloudCodeClient(
    private val auth: AntigravityAuthClient,
    private val store: SessionStore,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build(),
) {
    fun generate(session: Session, model: String, request: JSONObject): Pair<Session, List<ModelPart>> {
        val fresh = auth.ensureFresh(session, store)
        val wireModels = listOf(model, model.removeSuffix("-tiered") + "-tiered").distinct()
        var lastError: String? = null
        for (wire in wireModels) {
            val body = JSONObject(request.toString())
                .put("project", fresh.projectId)
                .put("model", wire)
                .put("userAgent", "antigravity")
                .put("requestType", "agent")
                .put("requestId", "agent-${UUID.randomUUID()}")
            val httpRequest = Request.Builder()
                .url("https://daily-cloudcode-pa.googleapis.com/v1internal:generateContent")
                .header("Authorization", "Bearer ${fresh.accessToken}")
                .header("Content-Type", "application/json")
                .header("User-Agent", "antigravity/hub/2.13.0 darwin/arm64")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
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
}

fun parseParts(raw: String): List<ModelPart> {
    val root = JSONObject(raw.ifBlank { "{}" })
    val response = root.optJSONObject("response") ?: root
    val candidates = response.optJSONArray("candidates") ?: JSONArray()
    if (candidates.length() == 0) {
        val message = root.optString("error").ifBlank { raw.take(400) }
        if (message.isNotBlank()) error(message)
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
    return JSONArray().put(
        JSONObject().put(
            "functionDeclarations",
            JSONArray()
                .put(decl("list_dir", "Список файлов в каталоге", "path" to "Абсолютный путь"))
                .put(decl("read_file", "Прочитать файл", "path" to "Абсолютный путь"))
                .put(decl("write_file", "Записать файл целиком", "path" to "Абсолютный путь", "content" to "Новое содержимое"))
                .put(decl("exec", "Выполнить команду shell от root", "command" to "Команда"))
                .put(decl("download", "Скачать URL в файл", "url" to "https URL", "path" to "Куда сохранить")),
        ),
    )
}
