package org.routingplatform.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated HTTPS client for the private training and conversation hub. */
class AndroidAiHubClient(context: Context) {
    private val prefs = context.getSharedPreferences("routing-ai-hub-v1", Context.MODE_PRIVATE)

    data class HubStatus(val status: String, val model: String) {
        val ready: Boolean get() = status == "configured"
    }

    data class TrainingRun(val id: String, val status: String, val progress: Int, val stage: String, val error: String)

    fun savedUrl(): String = prefs.getString(KEY_URL, "") ?: ""

    @Synchronized
    fun saveConnection(baseUrl: String, token: String) {
        val normalized = baseUrl.trim().trimEnd('/')
        val parsed = URL(normalized)
        require(parsed.protocol == "https" && parsed.host.isNotBlank() && parsed.userInfo == null && parsed.query == null && parsed.ref == null) {
            "Der KI-Hub benötigt eine gültige HTTPS-Adresse ohne Zugangsdaten im Link."
        }
        require(token.length in 32..512) { "Der Hub-Token muss zwischen 32 und 512 Zeichen enthalten." }
        val encrypted = encrypt(token)
        check(prefs.edit().putString(KEY_URL, normalized).putString(KEY_TOKEN, encrypted.first)
            .putString(KEY_IV, encrypted.second).commit()) { "Die Hub-Verbindung konnte nicht sicher gespeichert werden." }
    }

    fun status(): HubStatus {
        val response = request("GET", "/v1/ai/status")
        return HubStatus(response.optString("status"), response.optString("model"))
    }

    fun chat(message: String): String {
        require(message.isNotBlank() && message.length <= 1200) { "Die Nachricht muss 1 bis 1200 Zeichen enthalten." }
        return request("POST", "/v1/ai/chat", JSONObject().put("task", "conversation").put("message", message))
            .getString("reply")
    }

    fun startTraining(name: String, preset: String): TrainingRun {
        require(preset in setOf("speech_clarity", "route_explanation", "traffic_summary", "hazard_summary", "gps_confidence"))
        val payload = JSONObject().put("name", name.take(80)).put("preset", preset)
            .put("mode", "lora").put("start", true)
        return parseRun(request("POST", "/v1/training/runs", payload)).also { prefs.edit().putString(KEY_LAST_RUN, it.id).apply() }
    }

    fun lastTrainingRun(): TrainingRun? {
        val id = prefs.getString(KEY_LAST_RUN, null) ?: return null
        return runCatching { trainingRun(id) }.getOrNull()
    }

    fun trainingRun(id: String): TrainingRun = parseRun(request("GET", "/v1/training/runs").getJSONArray("runs")
        .let { runs -> (0 until runs.length()).map { runs.getJSONObject(it) }.firstOrNull { it.optString("id") == id }
            ?: throw IllegalStateException("Der Trainingslauf ist im Hub nicht mehr vorhanden.") })

    private fun parseRun(value: JSONObject) = TrainingRun(
        id = value.optString("id"), status = value.optString("status"),
        progress = value.optInt("progress").coerceIn(0, 100), stage = value.optString("stage"),
        error = value.optString("error"),
    )

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val base = savedUrl()
        require(base.startsWith("https://")) { "Bitte zuerst eine HTTPS-Hub-Adresse und einen Token verbinden." }
        val token = decrypt(prefs.getString(KEY_TOKEN, null), prefs.getString(KEY_IV, null))
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = if (path == "/v1/ai/chat") 45_000 else 12_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(16_000) }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "KI-Hub antwortet mit HTTP $code." })
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(value: String?, iv: String?): String {
        require(!value.isNullOrBlank() && !iv.isNullOrBlank()) { "Bitte den KI-Hub-Token erneut eingeben." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    companion object {
        private const val KEY_ALIAS = "routing-platform-ai-hub-token-v1"
        private const val KEY_URL = "url"
        private const val KEY_TOKEN = "token_ciphertext"
        private const val KEY_IV = "token_iv"
        private const val KEY_LAST_RUN = "last_training_run"
    }
}
