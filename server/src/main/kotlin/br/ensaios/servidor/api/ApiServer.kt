package br.ensaios.servidor.api

import br.ensaios.servidor.BuildConfig
import br.ensaios.servidor.db.ServerDb
import br.ensaios.shared.ApiJson
import br.ensaios.shared.ChangePasswordRequest
import br.ensaios.shared.ErrorResponse
import br.ensaios.shared.LoginRequest
import br.ensaios.shared.LoginResponse
import br.ensaios.shared.PROTOCOL_VERSION
import br.ensaios.shared.Perm
import br.ensaios.shared.PingResponse
import br.ensaios.shared.RecordDto
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.Rejected
import br.ensaios.shared.SyncRequest
import br.ensaios.shared.SyncResponse
import br.ensaios.shared.UserInfo
import br.ensaios.shared.WriteRules
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay

/** Versão mínima do app cliente aceita por este servidor. */
const val MIN_CLIENT_VERSION_CODE = 1

/** Quantos registros o servidor devolve por vez na sincronização. */
private const val PAGE_SIZE = 500

/** Servidor HTTP que os clientes acessam (pelo túnel da Cloudflare ou pela rede local). */
class ApiServer(private val db: ServerDb, private val port: Int) {

    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") { module(db) }.start(wait = false)
    }

    fun stop() {
        engine?.stop(500, 2000)
        engine = null
    }
}

private suspend fun ApplicationCall.authUser(db: ServerDb): UserInfo? {
    val header = request.headers["Authorization"] ?: ""
    val token = header.removePrefix("Bearer ").trim()
    val user = if (token.isNotEmpty()) db.userForToken(token) else null
    if (user == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Sessão expirada. Entre novamente."))
    }
    return user
}

/** O usuário pode receber este registro? Sem a permissão "ver", só recebe os próprios e os cadastros. */
private fun canSee(user: UserInfo, record: RecordDto): Boolean =
    Perm.has(user, Perm.VER) || RecordTypes.isCadastro(record.type) || record.createdBy == user.id

private fun Application.module(db: ServerDb) {
    install(ContentNegotiation) { json(ApiJson) }
    install(Compression) { gzip() }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Erro no servidor"))
        }
    }

    routing {
        get("/api/ping") {
            call.respond(
                PingResponse(
                    app = "controle-ensaios",
                    serverName = db.getMeta("server_name") ?: "Servidor de ensaios",
                    serverVersion = BuildConfig.VERSION_NAME,
                    protocol = PROTOCOL_VERSION,
                    minClientVersionCode = MIN_CLIENT_VERSION_CODE,
                )
            )
        }

        post("/api/login") {
            val req = call.receive<LoginRequest>()
            val user = db.checkPassword(req.username, req.password)
            if (user == null) {
                delay(800) // dificulta tentativas repetidas de senha
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuário ou senha incorretos"))
                return@post
            }
            val token = db.createToken(user.id, req.device)
            call.respond(LoginResponse(token, user))
        }

        post("/api/senha") {
            val user = call.authUser(db) ?: return@post
            val req = call.receive<ChangePasswordRequest>()
            if (db.checkPassword(user.username, req.oldPassword) == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Senha atual incorreta"))
                return@post
            }
            try {
                db.setPassword(user.id, req.newPassword)
                call.respond(HttpStatusCode.OK, ErrorResponse("ok"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Senha inválida"))
            }
        }

        post("/api/sync") {
            val user = call.authUser(db) ?: return@post
            val req = call.receive<SyncRequest>()
            if (req.clientVersionCode in 1 until MIN_CLIENT_VERSION_CODE) {
                call.respond(HttpStatusCode.UpgradeRequired, ErrorResponse("Atualize o app para sincronizar"))
                return@post
            }

            val applied = mutableListOf<String>()
            val rejected = mutableListOf<Rejected>()
            for (change in req.changes) {
                val existing = db.getRecord(change.id)
                if (existing != null && existing.type != change.type) {
                    rejected.add(Rejected(change.id, "Tipo de registro não confere", existing))
                    continue
                }
                val deleting = change.deleted && existing?.deleted != true
                val reason = WriteRules.check(
                    user = user,
                    type = change.type,
                    isNew = existing == null,
                    existingCreatedBy = existing?.createdBy,
                    deleting = deleting,
                )
                if (reason != null) {
                    rejected.add(Rejected(change.id, reason, existing))
                } else {
                    db.applyChange(change, user)
                    applied.add(change.id)
                }
            }

            val page = db.changesSince(req.sinceSeq, PAGE_SIZE)
            val visible = page.filter { canSee(user, it) }
            val maxSeq = page.lastOrNull()?.seq ?: maxOf(req.sinceSeq, 0L)
            call.respond(
                SyncResponse(
                    applied = applied,
                    rejected = rejected,
                    changes = visible,
                    maxSeq = maxSeq,
                    hasMore = page.size >= PAGE_SIZE,
                    user = user,
                    serverTime = System.currentTimeMillis(),
                )
            )
        }
    }
}
