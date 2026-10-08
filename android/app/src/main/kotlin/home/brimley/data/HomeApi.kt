package home.brimley.data

import home.brimley.model.MusicShelf
import home.brimley.model.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

class HomeApi(private val baseUrl: String, private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    private fun request(path: String): Request.Builder =
        Request.Builder().url(baseUrl.trimEnd('/') + path).header("Authorization", "Bearer $token")

    suspend fun today(): Today = withContext(Dispatchers.IO) {
        client.newCall(request("/api/today").get().build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException("today ${res.code}")
            json.decodeFromString(Today.serializer(), body)
        }
    }

    suspend fun musicShelves(): List<MusicShelf> = withContext(Dispatchers.IO) {
        client.newCall(request("/api/music").get().build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(errorMessage(body) ?: "music ${res.code}")
            json.decodeFromString(ShelvesResponse.serializer(), body).shelves
        }
    }

    suspend fun playMusic(uri: String) = withContext(Dispatchers.IO) {
        post("/api/music/play", buildJsonObject { put("uri", uri); put("source", "kitchen") }.toString())
    }

    suspend fun controlMusic(action: String) = withContext(Dispatchers.IO) {
        post("/api/music/control", buildJsonObject { put("action", action) }.toString())
    }

    suspend fun setJobDone(jobId: String, done: Boolean, date: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("jobId", jobId); put("done", done); put("date", date) }
        post("/api/jobs/done", body.toString())
    }

    suspend fun claimBounty(row: Int) = withContext(Dispatchers.IO) {
        post("/api/jobs/bounty/claim", buildJsonObject { put("row", row) }.toString())
    }

    suspend fun postNotePng(base64Png: String) = withContext(Dispatchers.IO) {
        post("/api/notes", buildJsonObject { put("png", base64Png) }.toString())
    }

    private fun post(path: String, body: String) {
        client.newCall(request(path).post(body.toRequestBody(jsonType)).build()).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(errorMessage(res.body?.string().orEmpty()) ?: "$path ${res.code}")
        }
    }

    // The backend answers errors as { "error": "…" } in words a kid can read.
    private fun errorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()
}

@Serializable
private data class ShelvesResponse(val shelves: List<MusicShelf>)
