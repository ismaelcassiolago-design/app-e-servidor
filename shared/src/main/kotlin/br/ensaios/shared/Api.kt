package br.ensaios.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Versão do protocolo entre cliente e servidor. Aumente quando a API mudar de forma incompatível. */
const val PROTOCOL_VERSION = 1

/** JSON usado nos dois lados. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class PingResponse(
    val app: String,
    val serverName: String,
    val serverVersion: String,
    val protocol: Int,
    val minClientVersionCode: Int,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val device: String = "",
)

@Serializable
data class UserInfo(
    val id: String,
    val username: String,
    val name: String,
    /** Número do usuário, usado no número de série dos ensaios (ex.: 03). */
    val number: Int,
    val isAdmin: Boolean,
    val active: Boolean = true,
    val permissions: Set<String> = emptySet(),
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserInfo,
)

@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

/**
 * Um registro sincronizado (segmento, ensaio, cadastro...).
 * O conteúdo fica em [data] como JSON, para novos tipos não exigirem mudança no banco.
 */
@Serializable
data class RecordDto(
    val id: String,
    val type: String,
    val data: JsonObject,
    val deleted: Boolean = false,
    /** Número de sequência dado pelo servidor. 0 = ainda não sincronizado. */
    val seq: Long = 0,
    val version: Int = 0,
    val createdBy: String? = null,
    val createdAt: Long = 0,
    val updatedBy: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class SyncRequest(
    /** Último número de sequência que o cliente já recebeu. */
    val sinceSeq: Long,
    /** Alterações feitas no cliente e ainda não enviadas. */
    val changes: List<RecordDto> = emptyList(),
    val clientVersionCode: Int = 0,
)

@Serializable
data class Rejected(
    val id: String,
    val reason: String,
    /** Versão atual no servidor, para o cliente desfazer a alteração recusada. */
    val current: RecordDto? = null,
)

@Serializable
data class SyncResponse(
    val applied: List<String>,
    val rejected: List<Rejected>,
    val changes: List<RecordDto>,
    val maxSeq: Long,
    val hasMore: Boolean,
    val user: UserInfo,
    val serverTime: Long,
)

@Serializable
data class ErrorResponse(val error: String)
