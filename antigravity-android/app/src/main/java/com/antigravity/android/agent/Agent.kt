package com.antigravity.android.agent

import com.antigravity.android.net.Citation
import com.topjohnwu.superuser.Shell
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ToolOutput(
    val text: String,
    val citations: List<Citation> = emptyList(),
)

object DeviceTools {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val browser = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun run(name: String, args: JSONObject): ToolOutput {
        val output = when (name) {
            "list_dir" -> ToolOutput(listDir(args.optString("path")))
            "read_file" -> ToolOutput(readFile(args.optString("path")))
            "write_file" -> ToolOutput(writeFile(args.optString("path"), args.optString("content")))
            "exec" -> ToolOutput(exec(args.optString("command")))
            "download" -> ToolOutput(download(args.optString("url"), args.optString("path")))
            "web_search" -> webSearch(args.optString("query"))
            "read_url" -> ToolOutput(readUrl(args.optString("url")))
            else -> ToolOutput("unknown tool $name")
        }
        return output.copy(text = output.text.take(24_000))
    }

    private fun listDir(path: String): String {
        val result = Shell.cmd("ls -la ${shellQuote(path)}").exec()
        return join(result.out, result.err, result.code)
    }

    private fun readFile(path: String): String {
        val file = File(path)
        if (file.exists() && file.canRead()) return file.readText().take(24_000)
        val result = Shell.cmd("sed -n '1,400p' ${shellQuote(path)}").exec()
        return join(result.out, result.err, result.code)
    }

    private fun writeFile(path: String, content: String): String {
        val file = File(path)
        val parent = file.parentFile
        if (parent != null && (parent.exists() || parent.mkdirs()) && parent.canWrite()) {
            file.writeText(content)
            return "wrote ${file.length()} bytes"
        }
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val result = Shell.cmd("mkdir -p ${shellQuote(parent?.path ?: "/")} && printf '%s' ${shellQuote(b64)} | base64 -d > ${shellQuote(path)}").exec()
        return join(result.out, result.err, result.code).ifBlank { "wrote via root" }
    }

    private fun exec(command: String): String {
        val result = Shell.cmd(command).exec()
        return join(result.out, result.err, result.code)
    }

    private fun download(url: String, path: String): String {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "http ${response.code}"
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val file = File(path)
            val parent = file.parentFile
            if (parent != null && (parent.exists() || parent.mkdirs()) && parent.canWrite()) {
                file.writeBytes(bytes)
                return "saved ${bytes.size} bytes"
            }
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val result = Shell.cmd("mkdir -p ${shellQuote(parent?.path ?: "/")} && printf '%s' ${shellQuote(b64)} | base64 -d > ${shellQuote(path)}").exec()
            return join(result.out, result.err, result.code).ifBlank { "saved ${bytes.size} bytes via root" }
        }
    }

    private fun webSearch(query: String): ToolOutput {
        if (query.isBlank()) return ToolOutput("пустой запрос")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", browser)
            .header("Accept-Language", "ru,en;q=0.8")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            if (!response.isSuccessful) return ToolOutput("поиск ${response.code}")
            val links = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .take(6)
                .toList()
            val snippets = Regex("""class="result__snippet"[^>]*>(.*?)</(?:a|td|span|div)>""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { stripHtml(it.groupValues[1]) }
                .toList()
            if (links.isEmpty()) return ToolOutput("ничего не найдено: $query")
            val citations = ArrayList<Citation>()
            val text = buildString {
                append("Результаты поиска «$query»:\n")
                links.forEachIndexed { index, match ->
                    val url = unwrapDuck(match.groupValues[1])
                    val title = stripHtml(match.groupValues[2]).ifBlank { url }
                    val snippet = snippets.getOrNull(index).orEmpty()
                    append("${index + 1}. $title\n$url\n$snippet\n\n")
                    if (url.startsWith("http")) citations += Citation(label = hostLabel(title, url), url = url)
                }
            }
            return ToolOutput(text.trim(), citations)
        }
    }

    private fun readUrl(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "нужен http(s) адрес"
        val request = Request.Builder().url(url).header("User-Agent", browser).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "http ${response.code}"
            val raw = response.body?.string().orEmpty()
            val text = stripHtml(
                raw.replace(Regex("(?is)<(script|style|noscript).*?</\\1>"), " "),
            )
            return text.take(12_000).ifBlank { "пустая страница" }
        }
    }

    private fun unwrapDuck(href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val uddg = Regex("[?&]uddg=([^&]+)").find(decoded)?.groupValues?.get(1) ?: return decoded
        return runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(uddg)
    }

    private fun stripHtml(value: String): String {
        return value
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun hostLabel(title: String, url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.").orEmpty()
        val brand = host.substringBefore('.').replaceFirstChar { it.uppercase() }
        return brand.ifBlank { title.take(22).ifBlank { "Источник" } }
    }

    private fun join(out: List<String>, err: List<String>, code: Int): String {
        return buildString {
            if (out.isNotEmpty()) append(out.joinToString("\n"))
            if (err.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append(err.joinToString("\n"))
            }
            if (code != 0) {
                if (isNotEmpty()) append('\n')
                append("exit $code")
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
