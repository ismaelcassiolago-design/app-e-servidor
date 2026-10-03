package br.ensaios.cliente.data

import br.ensaios.shared.Km
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Segmento: trecho de serviço do dia. Uma faixa por segmento. */
data class Segmento(
    val id: String?,
    val data: String,
    val clienteId: String,
    val rodoviaId: String,
    val servico: String,
    val kmIni: Long?,
    val kmFim: Long?,
    val lado: String,
    val faixaId: String,
    val largura: Double?,
    val responsavel: String,
    val obs: String,
    val pending: Boolean = false,
    val createdBy: String? = null,
) {
    /** Extensão em metros (fim − início). */
    val extensao: Long? get() = if (kmIni != null && kmFim != null) Km.extension(kmIni, kmFim) else null

    /** Área em m² (extensão × largura). */
    val area: Double? get() = extensao?.let { e -> largura?.let { it * e } }

    val intervalo: String get() = listOfNotNull(kmIni?.let { Km.format(it) }, kmFim?.let { Km.format(it) }).joinToString(" a ")

    fun toJson(rodoviaNome: String, faixaNome: String): JsonObject = buildJsonObject {
        put("data", JsonPrimitive(data))
        put("cliente_id", JsonPrimitive(clienteId))
        put("rodovia_id", JsonPrimitive(rodoviaId))
        put("servico", JsonPrimitive(servico))
        kmIni?.let { put("km_ini", JsonPrimitive(it)) }
        kmFim?.let { put("km_fim", JsonPrimitive(it)) }
        put("lado", JsonPrimitive(lado))
        put("faixa_id", JsonPrimitive(faixaId))
        largura?.let { put("largura", JsonPrimitive(it)) }
        put("responsavel", JsonPrimitive(responsavel))
        put("obs", JsonPrimitive(obs))
        // Texto curto para o histórico do servidor.
        put("resumo", JsonPrimitive(listOf(rodoviaNome, intervalo, faixaNome).filter { it.isNotEmpty() }.joinToString(" · ")))
    }

    companion object {
        val LADOS = listOf("LD", "LE", "Eixo")

        fun from(r: LocalRecord): Segmento {
            val d = r.record.data
            return Segmento(
                id = r.record.id,
                data = d.text("data"),
                clienteId = d.text("cliente_id"),
                rodoviaId = d.text("rodovia_id"),
                servico = d.text("servico"),
                kmIni = d.long("km_ini"),
                kmFim = d.long("km_fim"),
                lado = d.text("lado"),
                faixaId = d.text("faixa_id"),
                largura = d.num("largura"),
                responsavel = d.text("responsavel"),
                obs = d.text("obs"),
                pending = r.pending,
                createdBy = r.record.createdBy,
            )
        }
    }
}
