package br.ensaios.cliente.net

import br.ensaios.shared.ApiJson
import br.ensaios.shared.ChangePasswordRequest
import br.ensaios.shared.ErrorResponse
import br.ensaios.shared.LoginRequest
import br.ensaios.shared.LoginResponse
import br.ensaios.shared.PingResponse
import br.ensaios.shared.SyncRequest
import br.ensaios.shared.SyncResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

/** Comunicação com o app servidor. */
class ApiClient(baseUrl: String, private val token: String = "") {

    companion object {
        private val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val base = baseUrl.trimEnd('/')

    private suspend fun <T> call(path: String, bodyJson: String?, serializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            if (base.isEmpty()) throw ApiException("Configure o endereço do servidor")
            val builder = try {
                Request.Builder().url(base + path)
            } catch (e: IllegalArgumentException) {
                throw ApiException("Endereço do servidor inválido")
            }
            if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
            if (bodyJson != null) builder.post(bodyJson.toRequestBody(JSON)) else builder.get()
            try {
                http.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        val msg = try {
                            ApiJson.decodeFromString(ErrorResponse.serializer(), text).error
                        } catch (e: Exception) {
                            "Erro ${resp.code} no servidor"
                        }
                        throw ApiException(msg, resp.code)
                    }
                    ApiJson.decodeFromString(serializer, text)
                }
            } catch (e: IOException) {
                throw ApiException("Sem conexão com o servidor (${e.message ?: "falha de rede"})")
            } catch (e: kotlinx.serialization.SerializationException) {
                throw ApiException("Resposta inesperada. Confira se o endereço é do servidor de ensaios.")
            }
        }

    suspend fun ping(): PingResponse = call("/api/ping", null, PingResponse.serializer())

    suspend fun login(req: LoginRequest): LoginResponse =
        call("/api/login", ApiJson.encodeToString(LoginRequest.serializer(), req), LoginResponse.serializer())

    suspend fun sync(req: SyncRequest): SyncResponse =
        call("/api/sync", ApiJson.encodeToString(SyncRequest.serializer(), req), SyncResponse.serializer())

    suspend fun changePassword(req: ChangePasswordRequest): ErrorResponse =
        call("/api/senha", ApiJson.encodeToString(ChangePasswordRequest.serializer(), req), ErrorResponse.serializer())
}
