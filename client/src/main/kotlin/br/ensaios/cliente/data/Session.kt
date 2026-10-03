package br.ensaios.cliente.data

import android.content.Context
import br.ensaios.shared.ApiJson
import br.ensaios.shared.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Dados da sessão guardados no celular: endereço do servidor, login e usuário. */
object Session {
    private const val FILE = "sessao"

    private val _user = MutableStateFlow<UserInfo?>(null)
    /** Usuário logado (null = tela de login). */
    val user: StateFlow<UserInfo?> = _user

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val token = token(context)
        val json = sp(context).getString("user", null)
        _user.value = if (token.isNotEmpty() && json != null) {
            try {
                ApiJson.decodeFromString(UserInfo.serializer(), json)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun serverUrl(context: Context): String = sp(context).getString("server_url", "") ?: ""

    fun setServerUrl(context: Context, url: String) =
        sp(context).edit().putString("server_url", normalizeUrl(url)).apply()

    fun token(context: Context): String = sp(context).getString("token", "") ?: ""

    fun lastUserId(context: Context): String? = sp(context).getString("last_user_id", null)

    fun login(context: Context, token: String, user: UserInfo) {
        sp(context).edit()
            .putString("token", token)
            .putString("user", ApiJson.encodeToString(UserInfo.serializer(), user))
            .putString("last_user_id", user.id)
            .apply()
        _user.value = user
    }

    /** Atualiza nome e permissões do usuário quando o servidor manda mudanças. */
    fun updateUser(context: Context, user: UserInfo) {
        sp(context).edit().putString("user", ApiJson.encodeToString(UserInfo.serializer(), user)).apply()
        _user.value = user
    }

    fun logout(context: Context) {
        sp(context).edit().remove("token").remove("user").apply()
        _user.value = null
    }

    /** Aceita "ensaios.empresa.com.br", "192.168.0.10:8080" ou o endereço completo. */
    fun normalizeUrl(input: String): String {
        val t = input.trim().trimEnd('/')
        if (t.isEmpty()) return ""
        if (t.contains("://")) return t
        val looksLocal = t.first().isDigit() || t.startsWith("localhost")
        return if (looksLocal) "http://$t" else "https://$t"
    }
}
