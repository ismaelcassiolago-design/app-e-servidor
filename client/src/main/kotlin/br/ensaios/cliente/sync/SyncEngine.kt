package br.ensaios.cliente.sync

import android.content.Context
import br.ensaios.cliente.BuildConfig
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.Session
import br.ensaios.cliente.net.ApiClient
import br.ensaios.cliente.net.ApiException
import br.ensaios.shared.SyncRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncStatus(
    val running: Boolean = false,
    val lastSuccessAt: Long = 0,
    val message: String = "",
    val ok: Boolean = true,
)

/**
 * Sincronização por diferença:
 * 1. envia só o que foi alterado no celular;
 * 2. recebe só o que mudou no servidor desde o último número de sequência recebido.
 */
object SyncEngine {
    private const val BATCH = 200
    private val mutex = Mutex()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    fun loadStatus(context: Context) {
        val sp = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
        _status.value = SyncStatus(
            lastSuccessAt = sp.getLong("last_success", 0),
            message = sp.getString("message", "") ?: "",
            ok = sp.getBoolean("ok", true),
        )
    }

    private fun saveStatus(context: Context, status: SyncStatus) {
        _status.value = status
        context.getSharedPreferences("sync", Context.MODE_PRIVATE).edit()
            .putLong("last_success", status.lastSuccessAt)
            .putString("message", status.message)
            .putBoolean("ok", status.ok)
            .apply()
    }

    /** Retorna true se sincronizou, false se falhou (sem internet, servidor desligado...). */
    suspend fun sync(context: Context): Boolean = mutex.withLock {
        val url = Session.serverUrl(context)
        val token = Session.token(context)
        if (url.isEmpty() || token.isEmpty()) return@withLock false

        val db = LocalDb.get(context)
        val api = ApiClient(url, token)
        _status.value = _status.value.copy(running = true)
        val rejectedReasons = mutableListOf<String>()
        try {
            var since = db.lastSeq()
            var rounds = 0
            while (true) {
                val pending = db.pendingChanges(BATCH)
                val resp = api.sync(
                    SyncRequest(
                        sinceSeq = since,
                        changes = pending.map { it.first },
                        clientVersionCode = BuildConfig.VERSION_CODE,
                    )
                )
                val applied = resp.applied.toSet()
                pending.forEach { (record, rev) -> if (record.id in applied) db.markSent(record.id, rev) }
                resp.rejected.forEach { r ->
                    db.revert(r.id, r.current)
                    rejectedReasons.add(r.reason)
                }
                db.applyServer(resp.changes)
                since = resp.maxSeq
                db.setLastSeq(since)
                Session.updateUser(context, resp.user)

                rounds++
                val morePending = pending.size >= BATCH
                if ((!resp.hasMore && !morePending) || rounds >= 100) break
            }
            val msg = if (rejectedReasons.isEmpty()) "Sincronizado" else
                "Sincronizado. Recusado pelo servidor: " + rejectedReasons.distinct().joinToString("; ")
            saveStatus(context, SyncStatus(running = false, lastSuccessAt = System.currentTimeMillis(), message = msg, ok = rejectedReasons.isEmpty()))
            true
        } catch (e: ApiException) {
            if (e.code == 401) Session.logout(context)
            saveStatus(context, _status.value.copy(running = false, message = e.message ?: "Erro", ok = false))
            false
        } catch (e: Exception) {
            saveStatus(context, _status.value.copy(running = false, message = e.message ?: e.toString(), ok = false))
            false
        }
    }
}
