package services

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object ApiService {
    private const val BASE_URL = "http://10.0.2.2:5000" // Standard emulator loopback to host
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun post(endpoint: String, payload: Map<String, Any?>): JSONObject {
        val body = JSONObject(payload).toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return JSONObject(response.body?.string() ?: "{}")
        }
    }

    fun get(endpoint: String): JSONObject {
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return JSONObject(response.body?.string() ?: "{}")
        }
    }

    // specific API methods
    fun signIn(email: String, pword: String): JSONObject {
        return post("/signin", mapOf("email" to email, "password" to pword))
    }

    fun signUp(fullName: String, email: String, pword: String): JSONObject {
        return post("/signup", mapOf("fullName" to fullName, "email" to email, "password" to pword))
    }

    fun getAnnouncements(): JSONObject {
        return get("/announcements")
    }

    fun getChecklist(userId: String): JSONObject {
        return post("/checklist", mapOf("userId" to userId))
    }

    fun getRecentAppointments(userId: String): JSONObject {
        return post("/appointments/recent", mapOf("userId" to userId))
    }
}
