package br.ensaios.servidor

import android.content.Context

/** Configurações simples do app servidor. */
object Prefs {
    private const val FILE = "servidor"

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoStart(context: Context): Boolean = sp(context).getBoolean("auto_start", true)
    fun setAutoStart(context: Context, value: Boolean) = sp(context).edit().putBoolean("auto_start", value).apply()

    /** Endereço público do túnel da Cloudflare (ex.: https://ensaios.seudominio.com.br). */
    fun publicUrl(context: Context): String = sp(context).getString("public_url", "") ?: ""
    fun setPublicUrl(context: Context, value: String) = sp(context).edit().putString("public_url", value.trim()).apply()
}
