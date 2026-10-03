package br.ensaios.servidor.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.ensaios.shared.ApiJson
import br.ensaios.shared.RecordDto
import br.ensaios.shared.UserInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.util.UUID

/** Linha do histórico de alterações. */
data class HistoryEntry(
    val id: Long,
    val recordId: String,
    val type: String,
    val userId: String?,
    val userName: String?,
    val action: String,
    val summary: String,
    val at: Long,
)

data class ServerStats(val users: Int, val records: Int, val maxSeq: Long)

/**
 * Banco de dados do servidor (SQLite).
 * Todos os registros (segmentos, ensaios, cadastros) ficam na tabela "records" com o conteúdo em JSON.
 * Cada alteração recebe um número de sequência crescente, que é o que permite a sincronização por diferença.
 */
class ServerDb private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "servidor.db"
        const val DB_VERSION = 1

        @Volatile
        private var instance: ServerDb? = null

        fun get(context: Context): ServerDb =
            instance ?: synchronized(this) {
                instance ?: ServerDb(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users(
                id TEXT PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                number INTEGER NOT NULL,
                pass_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                is_admin INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                permissions TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE tokens(
                token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                device TEXT,
                created_at INTEGER NOT NULL,
                last_seen INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE records(
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                data TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                seq INTEGER NOT NULL,
                version INTEGER NOT NULL,
                created_by TEXT,
                created_at INTEGER,
                updated_by TEXT,
                updated_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_records_seq ON records(seq)")
        db.execSQL(
            """CREATE TABLE history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                record_id TEXT NOT NULL,
                type TEXT NOT NULL,
                user_id TEXT,
                action TEXT NOT NULL,
                before TEXT,
                after TEXT,
                at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_history_record ON history(record_id)")
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrações futuras entram aqui, uma por versão, sem apagar dados.
    }

    // ---------------------------------------------------------------- meta

    fun getMeta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun setMeta(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------------------------------------------------------------- usuários

    private fun Cursor.toUser(): UserInfo = UserInfo(
        id = getString(getColumnIndexOrThrow("id")),
        username = getString(getColumnIndexOrThrow("username")),
        name = getString(getColumnIndexOrThrow("name")),
        number = getInt(getColumnIndexOrThrow("number")),
        isAdmin = getInt(getColumnIndexOrThrow("is_admin")) == 1,
        active = getInt(getColumnIndexOrThrow("active")) == 1,
        permissions = getString(getColumnIndexOrThrow("permissions"))
            .split(",").map { p -> p.trim() }.filter { p -> p.isNotEmpty() }.toSet(),
    )

    fun listUsers(): List<UserInfo> =
        readableDatabase.rawQuery("SELECT * FROM users ORDER BY number", null).use { c ->
            val list = mutableListOf<UserInfo>()
            while (c.moveToNext()) list.add(c.toUser())
            list
        }

    fun getUser(id: String): UserInfo? =
        readableDatabase.rawQuery("SELECT * FROM users WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) it.toUser() else null
        }

    fun findUserByUsername(username: String): UserInfo? =
        readableDatabase.rawQuery(
            "SELECT * FROM users WHERE username=? COLLATE NOCASE", arrayOf(username.trim())
        ).use { if (it.moveToFirst()) it.toUser() else null }

    @Synchronized
    fun createUser(
        username: String,
        name: String,
        password: String,
        isAdmin: Boolean,
        permissions: Set<String>,
    ): UserInfo {
        val clean = username.trim().lowercase()
        require(clean.isNotEmpty()) { "Informe o usuário" }
        require(password.length >= 4) { "A senha precisa ter pelo menos 4 caracteres" }
        require(findUserByUsername(clean) == null) { "Já existe um usuário com esse nome" }
        val number = readableDatabase.rawQuery("SELECT COALESCE(MAX(number),0)+1 FROM users", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val salt = Passwords.newSalt()
        val id = UUID.randomUUID().toString()
        val cv = ContentValues().apply {
            put("id", id)
            put("username", clean)
            put("name", name.trim().ifEmpty { clean })
            put("number", number)
            put("pass_hash", Passwords.hash(password, salt))
            put("salt", salt)
            put("is_admin", if (isAdmin) 1 else 0)
            put("active", 1)
            put("permissions", permissions.joinToString(","))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("users", null, cv)
        return getUser(id)!!
    }

    @Synchronized
    fun updateUser(id: String, name: String, isAdmin: Boolean, active: Boolean, permissions: Set<String>) {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("is_admin", if (isAdmin) 1 else 0)
            put("active", if (active) 1 else 0)
            put("permissions", permissions.joinToString(","))
        }
        writableDatabase.update("users", cv, "id=?", arrayOf(id))
        if (!active) writableDatabase.delete("tokens", "user_id=?", arrayOf(id))
    }

    @Synchronized
    fun setPassword(id: String, password: String) {
        require(password.length >= 4) { "A senha precisa ter pelo menos 4 caracteres" }
        val salt = Passwords.newSalt()
        val cv = ContentValues().apply {
            put("pass_hash", Passwords.hash(password, salt))
            put("salt", salt)
        }
        writableDatabase.update("users", cv, "id=?", arrayOf(id))
    }

    /** Confere usuário e senha. Retorna o usuário se estiver certo e ativo. */
    fun checkPassword(username: String, password: String): UserInfo? {
        val row = readableDatabase.rawQuery(
            "SELECT id, pass_hash, salt, active FROM users WHERE username=? COLLATE NOCASE",
            arrayOf(username.trim()),
        ).use { c ->
            if (c.moveToFirst()) arrayOf(c.getString(0), c.getString(1), c.getString(2), c.getInt(3).toString())
            else null
        } ?: return null
        if (row[3] != "1") return null
        return if (Passwords.verify(password, row[2], row[1])) getUser(row[0]) else null
    }

    @Synchronized
    fun createToken(userId: String, device: String): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("token", token)
            put("user_id", userId)
            put("device", device)
            put("created_at", now)
            put("last_seen", now)
        }
        writableDatabase.insertOrThrow("tokens", null, cv)
        return token
    }

    fun userForToken(token: String): UserInfo? {
        val userId = readableDatabase.rawQuery(
            "SELECT user_id FROM tokens WHERE token=?", arrayOf(token)
        ).use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        val user = getUser(userId) ?: return null
        if (!user.active) return null
        val cv = ContentValues().apply { put("last_seen", System.currentTimeMillis()) }
        writableDatabase.update("tokens", cv, "token=?", arrayOf(token))
        return user
    }

    /** Último acesso de cada usuário (maior last_seen entre os aparelhos). */
    fun lastSeenByUser(): Map<String, Long> =
        readableDatabase.rawQuery("SELECT user_id, MAX(last_seen) FROM tokens GROUP BY user_id", null).use { c ->
            val map = mutableMapOf<String, Long>()
            while (c.moveToNext()) map[c.getString(0)] = c.getLong(1)
            map
        }

    // ---------------------------------------------------------------- registros

    private fun Cursor.toRecord(): RecordDto = RecordDto(
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
    )

    fun getRecord(id: String): RecordDto? =
        readableDatabase.rawQuery("SELECT * FROM records WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) it.toRecord() else null
        }

    private fun nextSeq(db: SQLiteDatabase): Long {
        val current = db.rawQuery("SELECT value FROM meta WHERE key='seq'", null).use {
            if (it.moveToFirst()) it.getString(0).toLong() else 0L
        }
        val next = current + 1
        val cv = ContentValues().apply {
            put("key", "seq")
            put("value", next.toString())
        }
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return next
    }

    /**
     * Grava uma alteração vinda de um cliente (já autorizada) e registra no histórico.
     * Retorna o registro como ficou no servidor.
     */
    @Synchronized
    fun applyChange(change: RecordDto, user: UserInfo): RecordDto {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = getRecord(change.id)
            val now = System.currentTimeMillis()
            val seq = nextSeq(db)
            val cv = ContentValues().apply {
                put("id", change.id)
                put("type", change.type)
                put("data", ApiJson.encodeToString(JsonObject.serializer(), change.data))
                put("deleted", if (change.deleted) 1 else 0)
                put("seq", seq)
                put("version", (existing?.version ?: 0) + 1)
                put("created_by", existing?.createdBy ?: user.id)
                put("created_at", existing?.createdAt ?: (if (change.createdAt > 0) change.createdAt else now))
                put("updated_by", user.id)
                put("updated_at", now)
            }
            db.insertWithOnConflict("records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            val action = when {
                existing == null -> "criou"
                change.deleted && !existing.deleted -> "excluiu"
                !change.deleted && existing.deleted -> "restaurou"
                else -> "alterou"
            }
            val hv = ContentValues().apply {
                put("record_id", change.id)
                put("type", change.type)
                put("user_id", user.id)
                put("action", action)
                put("before", existing?.let { ApiJson.encodeToString(JsonObject.serializer(), it.data) })
                put("after", ApiJson.encodeToString(JsonObject.serializer(), change.data))
                put("at", now)
            }
            db.insert("history", null, hv)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return getRecord(change.id)!!
    }

    /** Registros alterados depois de [sinceSeq], em ordem, no máximo [limit]. */
    fun changesSince(sinceSeq: Long, limit: Int): List<RecordDto> =
        readableDatabase.rawQuery(
            "SELECT * FROM records WHERE seq > ? ORDER BY seq LIMIT ?",
            arrayOf(sinceSeq.toString(), limit.toString()),
        ).use { c ->
            val list = mutableListOf<RecordDto>()
            while (c.moveToNext()) list.add(c.toRecord())
            list
        }

    fun maxSeq(): Long = getMeta("seq")?.toLongOrNull() ?: 0L

    fun stats(): ServerStats {
        val users = readableDatabase.rawQuery("SELECT COUNT(*) FROM users", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val records = readableDatabase.rawQuery("SELECT COUNT(*) FROM records WHERE deleted=0", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return ServerStats(users, records, maxSeq())
    }

    // ---------------------------------------------------------------- histórico

    fun history(limit: Int = 300): List<HistoryEntry> =
        readableDatabase.rawQuery(
            """SELECT h.id, h.record_id, h.type, h.user_id, u.name, h.action, h.after, h.at
               FROM history h LEFT JOIN users u ON u.id = h.user_id
               ORDER BY h.id DESC LIMIT ?""",
            arrayOf(limit.toString()),
        ).use { c ->
            val list = mutableListOf<HistoryEntry>()
            while (c.moveToNext()) {
                list.add(
                    HistoryEntry(
                        id = c.getLong(0),
                        recordId = c.getString(1),
                        type = c.getString(2),
                        userId = c.getString(3),
                        userName = c.getString(4),
                        action = c.getString(5),
                        summary = summarize(c.getString(6)),
                        at = c.getLong(7),
                    )
                )
            }
            list
        }

    /** Texto curto que identifica o registro no histórico (nome, número de série...). */
    private fun summarize(json: String?): String {
        if (json == null) return ""
        return try {
            val obj = ApiJson.parseToJsonElement(json) as JsonObject
            listOf("serie", "resumo", "nome", "placa", "identificacao")
                .firstNotNullOfOrNull { key -> obj[key]?.jsonPrimitive?.contentOrNull }
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
