package dev.grove.reader.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import dev.grove.reader.domain.encode

data class DeviceAuthorization(val deviceCode: String, val userCode: String,
    val verificationUri: String, val interval: Long, val expiresIn: Long)
data class AuthTokens(val accessToken:String,val refreshToken:String="",val expiresAt:Long=0L,val refreshExpiresAt:Long=0L)

class TokenVault(context: Context) {
    private val prefs = context.getSharedPreferences("grove.account", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("grove.token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("grove.token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Volatile var revision=0L
        private set
    fun accountId():String=prefs.getString("accountId","anonymous")?:"anonymous"
    fun login():String=prefs.getString("login","")?:""
    fun identity(profile:dev.grove.reader.domain.UserProfile) {prefs.edit().putString("accountId",profile.id).putString("login",profile.login).apply()}
    fun session():AuthTokens {
        val value=decrypt()
        if(value.isBlank())return AuthTokens("")
        return runCatching {JSONObject(value).let{AuthTokens(it.getString("access_token"),it.optString("refresh_token"),it.optLong("expires_at"),it.optLong("refresh_expires_at"))}}.getOrElse{AuthTokens(value)}
    }
    fun token():String=session().accessToken
    private fun decrypt(): String = runCatching {
        val encoded = prefs.getString("token", null) ?: return ""
        val parts = encoded.split(':')
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
            doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
        }
    }.getOrDefault("")
    @Synchronized fun save(tokens:AuthTokens) {
        val token=JSONObject().put("access_token",tokens.accessToken).put("refresh_token",tokens.refreshToken)
            .put("expires_at",tokens.expiresAt).put("refresh_expires_at",tokens.refreshExpiresAt).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray())
        prefs.edit().putString("token", Base64.getEncoder().encodeToString(cipher.iv)+":"+Base64.getEncoder().encodeToString(encrypted)).apply()
        revision++
    }
    @Synchronized fun updateIfRevision(expected:Long,tokens:AuthTokens):Boolean {if(revision!=expected)return false;save(tokens);return true}
    @Synchronized fun clear() { prefs.edit().clear().apply();revision++ }
}

class GitHubAuth(private val clientId: String,private val githubApp:Boolean=false) {
    private fun postBlocking(path:String,values:Map<String,String>):JSONObject {
        val connection = URL("https://github.com$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { stream -> stream.write(values.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }.toByteArray()) }
            if (connection.responseCode !in 200..299) throw GitHubException("无法连接 GitHub 授权服务，请重试。")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    private suspend fun post(path:String,values:Map<String,String>):JSONObject=withContext(Dispatchers.IO){postBlocking(path,values)}
    suspend fun begin(): DeviceAuthorization {
        val response = post("/login/device/code", if(githubApp)mapOf("client_id" to clientId) else mapOf("client_id" to clientId,"scope" to "read:user"))
        if(response.has("error")) throw GitHubException("GitHub 授权尚未启用，请检查应用的 Device Flow 配置。")
        val verification = response.getString("verification_uri")
        if (!verification.startsWith("https://github.com/")) throw GitHubException("GitHub 返回了无效的授权地址。")
        return DeviceAuthorization(response.getString("device_code"),response.getString("user_code"),verification,
            response.optLong("interval",5).coerceAtLeast(5),response.getLong("expires_in"))
    }
    suspend fun awaitToken(device: DeviceAuthorization): AuthTokens {
        var interval = device.interval
        val expires = System.nanoTime() + device.expiresIn * 1_000_000_000L
        while(System.nanoTime()<expires) {
            delay(interval * 1000)
            val response = post("/login/oauth/access_token",mapOf("client_id" to clientId,"device_code" to device.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
            response.optString("access_token").takeIf { it.isNotBlank() }?.let { return parseTokens(response) }
            when(response.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "access_denied" -> throw GitHubException("你已取消授权，可以继续以访客身份阅读。")
                "expired_token" -> break
                else -> throw GitHubException("GitHub 授权失败，请重新开始。")
            }
        }
        throw GitHubException("授权码已过期，请重新获取。")
    }
    fun refreshBlocking(tokens:AuthTokens):AuthTokens {
        val result=postBlocking("/login/oauth/access_token",mapOf("client_id" to clientId,"grant_type" to "refresh_token","refresh_token" to tokens.refreshToken))
        if(result.optString("access_token").isBlank())throw GitHubException("GitHub 登录已失效，请重新授权。",401)
        return parseTokens(result)
    }
    private fun parseTokens(json:JSONObject):AuthTokens {
        val now=System.currentTimeMillis()/1000
        return AuthTokens(json.getString("access_token"),json.optString("refresh_token"),
            json.optLong("expires_in").let{if(it>0)now+it else 0},json.optLong("refresh_token_expires_in").let{if(it>0)now+it else 0})
    }
}
