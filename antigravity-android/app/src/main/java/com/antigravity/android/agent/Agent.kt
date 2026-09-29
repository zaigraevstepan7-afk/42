package com.antigravity.android.agent

import com.topjohnwu.superuser.Shell
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object DeviceTools {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

    fun run(name: String, args: JSONObject): String {
        return when (name) {
            "list_dir" -> listDir(args.optString("path"))
            "read_file" -> readFile(args.optString("path"))
            "write_file" -> writeFile(args.optString("path"), args.optString("content"))
            "exec" -> exec(args.optString("command"))
            "download" -> download(args.optString("url"), args.optString("path"))
            else -> "unknown tool $name"
        }.take(24_000)
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
