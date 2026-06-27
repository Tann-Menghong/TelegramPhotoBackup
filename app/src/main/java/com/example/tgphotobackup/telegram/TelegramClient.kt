package com.example.tgphotobackup.telegram

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class TelegramClient(private val token: String) {

    private val base = "https://api.telegram.org/bot$token"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    data class SendResult(val messageId: Long, val fileId: String)

    // ── Verification ────────────────────────────────────────────────────────

    fun getMe(): Result<String> = runGet("$base/getMe") { json ->
        json.getJSONObject("result").optString("username")
    }

    fun sendTestMessage(chatId: String): Result<Long> = try {
        val body = okhttp3.FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", "TG Backup: connection test OK ✅")
            .build()
        val req = Request.Builder().url("$base/sendMessage").post(body).build()
        client.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            if (json.optBoolean("ok"))
                Result.success(json.getJSONObject("result").getLong("message_id"))
            else
                Result.failure(IOException(json.optString("description", "sendMessage failed")))
        }
    } catch (e: Exception) { Result.failure(e) }

    // ── Upload ───────────────────────────────────────────────────────────────

    fun sendDocument(
        chatId: String,
        fileName: String,
        mime: String,
        length: Long,
        caption: String? = null,
        maxRetries: Int = 3,
        stream: () -> InputStream
    ): SendResult {
        var lastException: Exception? = null
        var nextDelayMs = 2_000L
        repeat(maxRetries) { attempt ->
            if (attempt > 0) Thread.sleep(nextDelayMs)
            try { return sendDocumentOnce(chatId, fileName, mime, length, caption, stream) }
            catch (e: IOException) {
                lastException = e
                // honour Telegram's retry_after if present in the error message
                val retryAfterSec = Regex("retry after (\\d+)s").find(e.message ?: "")
                    ?.groupValues?.get(1)?.toLongOrNull()
                nextDelayMs = if (retryAfterSec != null) retryAfterSec * 1_000L
                              else minOf(nextDelayMs * 2, 30_000L)
            }
        }
        throw lastException ?: IOException("sendDocument failed after $maxRetries retries")
    }

    private fun sendDocumentOnce(
        chatId: String, fileName: String, mime: String, length: Long,
        caption: String?, stream: () -> InputStream
    ): SendResult {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", fileName,
                StreamRequestBody(mime.toMediaTypeOrNull(), length, stream))
        if (!caption.isNullOrBlank()) builder.addFormDataPart("caption", caption)

        val req = Request.Builder().url("$base/sendDocument").post(builder.build()).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw IOException("Empty Telegram response")
            val json = JSONObject(text)
            if (resp.code == 429) {
                val retryAfter = json.optJSONObject("parameters")?.optLong("retry_after") ?: 5L
                throw IOException("Rate limited — retry after ${retryAfter}s")
            }
            if (!json.optBoolean("ok"))
                throw IOException("Telegram error: " + json.optString("description"))
            val result = json.getJSONObject("result")
            val doc = result.optJSONObject("document")
                ?: result.optJSONObject("video")
                ?: throw IOException("No document/video in Telegram response")
            return SendResult(result.getLong("message_id"), doc.getString("file_id"))
        }
    }

    // ── Download / Restore ───────────────────────────────────────────────────

    /**
     * Returns the server-side file_path for [fileId].
     * Bot API limit: files > 20 MB cannot be retrieved this way.
     */
    fun getFilePath(fileId: String): Result<String> = runGet("$base/getFile?file_id=$fileId") { json ->
        json.getJSONObject("result").getString("file_path")
    }

    /**
     * Downloads the file at [filePath] (from [getFilePath]) as raw bytes.
     * Do not use for files > 20 MB — the Bot API will reject the getFile call first.
     */
    fun downloadBytes(filePath: String): Result<ByteArray> = try {
        val url = "https://api.telegram.org/file/bot$token/$filePath"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed: ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw IOException("Empty download response")
            Result.success(bytes)
        }
    } catch (e: Exception) { Result.failure(e) }

    // ── Verification ─────────────────────────────────────────────────────────

    /** Returns true if the file is still accessible on Telegram. */
    fun fileExists(fileId: String): Boolean =
        getFilePath(fileId).isSuccess

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> runGet(url: String, extract: (JSONObject) -> T): Result<T> = try {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            if (json.optBoolean("ok"))
                Result.success(extract(json))
            else
                Result.failure(IOException(json.optString("description", "API error")))
        }
    } catch (e: Exception) { Result.failure(e) }

    private class StreamRequestBody(
        private val mediaType: MediaType?,
        private val length: Long,
        private val streamProvider: () -> InputStream
    ) : RequestBody() {
        override fun contentType() = mediaType
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            streamProvider().use { input ->
                val buf = ByteArray(65_536)
                var n = input.read(buf)
                while (n >= 0) { sink.write(buf, 0, n); n = input.read(buf) }
            }
        }
    }
}
