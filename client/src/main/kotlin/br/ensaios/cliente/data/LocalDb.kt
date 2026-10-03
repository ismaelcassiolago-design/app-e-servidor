package br.ensaios.cliente.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.ensaios.shared.ApiJson
import br.ensaios.shared.RecordDto
import br.ensaios.shared.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Registro local. pending = aguardando envio; draft = rascunho ainda não concluído (não é enviado). */
data class LocalRecord(val record: RecordDto, val pending: Boolean, val draft: Boolean = false)

/**
 * Banco local do celular. Guarda uma cópia dos dados do servidor e as alterações
 * ainda não enviadas (pending = 1), para o app funcionar sem internet.
 */
class LocalDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "local.db", null, 1) {

    companion object {
        @Volatile
        private var instance: LocalDb? = null

        fun get(context: Context): LocalDb =
            instance ?: synchronized(this) {
                instance ?: LocalDb(context.applicationContext).also { instance = it }
            }

        private val _changes = MutableStateFlow(0L)
        /** Muda de valor sempre que os dados locais mudam (as telas usam para recarregar). */
        val changes: StateFlow<Long> = _changes
    }

    private fun notifyChanged() {
        _changes.value = _changes.value + 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE records(
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                data TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                seq INTEGER NOT NULL DEFAULT 0,
                version INTEGER NOT NULL DEFAULT 0,
                created_by TEXT,
                created_at INTEGER,
                updated_by TEXT,
                updated_at INTEGER,
                pending INTEGER NOT NULL DEFAULT 0,
                local_rev INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_local_type ON records(type)")
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrações futuras entram aqui, sem apagar dados.
    }

    // ---------------------------------------------------------------- meta

    fun lastSeq(): Long =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key='last_seq'", null).use {
            if (it.moveToFirst()) it.getString(0).toLongOrNull() ?: 0L else 0L
        }

    fun setLastSeq(seq: Long) {
        val cv = ContentValues().apply {
            put("key", "last_seq")
            put("value", seq.toString())
        }
        writableDatabase.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete("records", null, null)
        writableDatabase.delete("meta", null, null)
        notifyChanged()
    }

    // ---------------------------------------------------------------- leitura

    private fun Cursor.toLocal(): LocalRecord = LocalRecord(
        record = RecordDto(
            id = getString(getColumnIndexOrThrow("id")),
            type = getString(getColumnIndexOrThrow("type")),
            data = ApiJson.parseToJsonElement(getString(getColumnIndexOrThrow("data"))) as JsonObject,
            deleted = getInt(getColumnIndexOrThrow("deleted")) == 1,
            seq = getLong(getColumnIndexOrThrow("seq")),
            version = getInt(getColumnIndexOrThrow("version")),
            createdBy = getString(getColumnIndexOrThrow("created_by")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedBy = getString(getColumnIndexOrThrow("updated_by")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        ),
        pending = getInt(getColumnIndexOrThrow("pending")) == 1,
        draft = getInt(getColumnIndexOrThrow("pending")) == 2,
    )

    fun listByType(type: String): List<LocalRecord> =
        readableDatabase.rawQuery(
            "SELECT * FROM records WHERE type=? AND deleted=0 ORDER BY created_at",
            arrayOf(type),
        ).use { c ->
            val list = mutableListOf<LocalRecord>()
            while (c.moveToNext()) list.add(c.toLocal())
            list
        }

    fun get(id: String): LocalRecord? =
        readableDatabase.rawQuery("SELECT * FROM records WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) it.toLocal() else null
        }

    fun pendingCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM records WHERE pending=1", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    /** Todos os registros de um tipo, incluindo excluídos (para não repetir números de série). */
    private fun allIncludingDeleted(type: String): List<LocalRecord> =
        readableDatabase.rawQuery("SELECT * FROM records WHERE type=?", arrayOf(type)).use { c ->
            val list = mutableListOf<LocalRecord>()
            while (c.moveToNext()) list.add(c.toLocal())
            list
        }

    /**
     * Próximo número da sequência do usuário para um tipo de ensaio. Nunca reinicia e nunca repete:
     * usa o maior entre o que já existe no banco e o último reservado neste celular.
     */
    @Synchronized
    fun nextSerialNumber(type: String, userId: String): Int {
        val inDb = allIncludingDeleted(type)
            .filter { it.record.createdBy == userId }
            .mapNotNull { it.record.data["serie_num"]?.toString()?.trim('"')?.toIntOrNull() }
            .maxOrNull() ?: 0
        val key = "serie_${type}_$userId"
        val reserved = readableDatabase.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0).toIntOrNull() ?: 0 else 0
        }
        val next = maxOf(inDb, reserved) + 1
        val cv = ContentValues().apply {
            put("key", key)
            put("value", next.toString())
        }
        writableDatabase.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return next
    }

    // ---------------------------------------------------------------- alterações locais

    /** Cria (id = null) ou altera um registro no celular e marca para envio. */
    @Synchronized
    fun saveLocal(type: String, id: String?, data: JsonObject, user: UserInfo, deleted: Boolean = false, draft: Boolean = false): String {
        val now = System.currentTimeMillis()
        val existing = id?.let { get(it) }
        val recordId = id ?: UUID.randomUUID().toString()
        val currentRev = if (existing == null) 0 else
            readableDatabase.rawQuery("SELECT local_rev FROM records WHERE id=?", arrayOf(recordId)).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        val cv = ContentValues().apply {
            put("id", recordId)
            put("type", type)
            put("data", ApiJson.encodeToString(JsonObject.serializer(), data))
            put("deleted", if (deleted) 1 else 0)
            put("seq", existing?.record?.seq ?: 0L)
            put("version", existing?.record?.version ?: 0)
            put("created_by", existing?.record?.createdBy ?: user.id)
            put("created_at", existing?.record?.createdAt ?: now)
            put("updated_by", user.id)
            put("updated_at", now)
            put("pending", if (draft) 2 else 1)
            put("local_rev", currentRev + 1)
        }
        writableDatabase.insertWithOnConflict("records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
        return recordId
    }

    /** Alterações a enviar, com a revisão local de cada uma. */
    fun pendingChanges(limit: Int): List<Pair<RecordDto, Int>> =
        readableDatabase.rawQuery(
            "SELECT * FROM records WHERE pending=1 ORDER BY updated_at LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            val list = mutableListOf<Pair<RecordDto, Int>>()
            while (c.moveToNext()) {
                list.add(c.toLocal().record to c.getInt(c.getColumnIndexOrThrow("local_rev")))
            }
            list
        }

    /** Marca como enviado, se não foi alterado de novo durante o envio. */
    @Synchronized
    fun markSent(id: String, rev: Int) {
        val cv = ContentValues().apply { put("pending", 0) }
        writableDatabase.update("records", cv, "id=? AND local_rev=? AND pending=1", arrayOf(id, rev.toString()))
    }

    // ---------------------------------------------------------------- dados do servidor

    private fun writeServer(record: RecordDto) {
        val cv = ContentValues().apply {
            put("id", record.id)
            put("type", record.type)
            put("data", ApiJson.encodeToString(JsonObject.serializer(), record.data))
            put("deleted", if (record.deleted) 1 else 0)
            put("seq", record.seq)
            put("version", record.version)
            put("created_by", record.createdBy)
            put("created_at", record.createdAt)
            put("updated_by", record.updatedBy)
            put("updated_at", record.updatedAt)
            put("pending", 0)
            put("local_rev", 0)
        }
        writableDatabase.insertWithOnConflict("records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Grava o que veio do servidor, sem passar por cima de alteração local ainda não enviada. */
    @Synchronized
    fun applyServer(records: List<RecordDto>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in records) {
                val pending = db.rawQuery("SELECT pending FROM records WHERE id=?", arrayOf(r.id)).use {
                    it.moveToFirst() && it.getInt(0) != 0
                }
                if (!pending) writeServer(r)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    /** Desfaz uma alteração recusada pelo servidor. */
    @Synchronized
    fun revert(id: String, current: RecordDto?) {
        if (current != null) writeServer(current) else writableDatabase.delete("records", "id=?", arrayOf(id))
        notifyChanged()
    }
}
