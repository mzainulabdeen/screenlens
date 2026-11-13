package com.zain.screenlens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Gemini REST client: image + prompt -> streamed text (SSE), optional Google Search grounding. */
class GeminiClient {

    suspend fun streamDescribe(
        apiKey: String,
        model: String,
        prompt: String,
        jpegBase64: String,
        useGoogleSearch: Boolean,
        onChunk: suspend (String) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")

        val parts = JSONArray()
            .put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", jpegBase64)))
            .put(JSONObject().put("text", prompt))

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        if (useGoogleSearch) {
            body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("HTTP $code: ${errorMessage(err)}")
            }

            val sources = LinkedHashSet<String>()
            val reader = conn.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val json = try { JSONObject(line.removePrefix("data:").trim()) } catch (e: Exception) { continue }
                val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: continue

                val textParts = candidate.optJSONObject("content")?.optJSONArray("parts")
                if (textParts != null) {
                    val sb = StringBuilder()
                    for (i in 0 until textParts.length()) {
                        val p = textParts.optJSONObject(i) ?: continue
                        if (p.optBoolean("thought", false)) continue
                        sb.append(p.optString("text", ""))
                    }
                    if (sb.isNotEmpty()) withContext(Dispatchers.Main) { onChunk(sb.toString()) }
                }

                val chunks = candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
                if (chunks != null) {
                    for (i in 0 until chunks.length()) {
                        val title = chunks.optJSONObject(i)?.optJSONObject("web")?.optString("title", "").orEmpty()
                        if (title.isNotBlank()) sources.add(title)
                    }
                }
            }
            sources.toList()
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(raw: String): String = try {
        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(300)
    } catch (e: Exception) {
        raw.take(300)
    }
}
