package br.ensaios.cliente.data

import br.ensaios.shared.Calc
import br.ensaios.shared.Km
import br.ensaios.shared.Num
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.TipoEnsaio
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import br.ensaios.shared.UserInfo

private val MESES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)

/** "2026-10" → "Outubro de 2026" */
fun nomeMes(ym: String): String {
    val m = ym.substringAfter("-").toIntOrNull() ?: return ym
    return "${MESES.getOrElse(m - 1) { "" }} de ${ym.substringBefore("-")}"
}

fun mesesDoAno(): List<String> = MESES

/** Mês de produção (adicionado à mão). */
data class Mes(val id: String, val ym: String, val pending: Boolean)

/** Dia de produção (adicionado à mão dentro do mês). */
data class Dia(val id: String, val data: String, val mesId: String, val pending: Boolean)

object Producao {
    fun meses(db: LocalDb): List<Mes> =
        db.listByType(RecordTypes.MES)
            .map { Mes(it.record.id, it.record.data.text("mes"), it.pending) }
            .sortedByDescending { it.ym }

    fun dias(db: LocalDb, mes: Mes): List<Dia> =
        db.listByType(RecordTypes.DIA)
            .map { Dia(it.record.id, it.record.data.text("data"), it.record.data.text("mes_id"), it.pending) }
            .filter { it.mesId == mes.id || (it.mesId.isEmpty() && it.data.startsWith(mes.ym)) }
            .sortedByDescending { it.data }

    fun dia(db: LocalDb, id: String): Dia? = db.get(id)?.takeIf { !it.record.deleted }?.let {
        Dia(it.record.id, it.record.data.text("data"), it.record.data.text("mes_id"), it.pending)
    }

    fun mes(db: LocalDb, id: String): Mes? = db.get(id)?.takeIf { !it.record.deleted }?.let {
        Mes(it.record.id, it.record.data.text("mes"), it.pending)
    }

    /** Segmentos do dia (pelo vínculo com o dia ou, nos antigos, pela data). */
    fun segmentos(db: LocalDb, dia: Dia, all: List<Segmento>? = null): List<Segmento> =
        (all ?: todosSegmentos(db))
            .filter { it.diaId == dia.id || (it.diaId.isEmpty() && it.data == dia.data) }
            .sortedBy { it.kmIni ?: 0L }

    fun todosSegmentos(db: LocalDb): List<Segmento> =
        db.listByType(RecordTypes.SEGMENTO).map { Segmento.from(it) }

    private fun todosDias(db: LocalDb): List<Dia> =
        db.listByType(RecordTypes.DIA).map { Dia(it.record.id, it.record.data.text("data"), it.record.data.text("mes_id"), it.pending) }

    /** Segmentos que não aparecem em nenhum dia (ex.: lançados antes da versão 0.3). */
    fun semDia(db: LocalDb): List<Segmento> {
        val dias = todosDias(db)
        val ids = dias.map { it.id }.toSet()
        val datas = dias.map { it.data }.toSet()
        return todosSegmentos(db).filter { s ->
            if (s.diaId.isNotEmpty()) s.diaId !in ids else s.data !in datas
        }
    }

    /**
     * Coloca cada segmento sem dia no mês e no dia da data dele,
     * criando o mês e o dia quando ainda não existem. Retorna quantos foram organizados.
     */
    fun organizar(db: LocalDb, user: UserInfo): Int {
        val orfaos = semDia(db).filter { it.data.length >= 10 && it.id != null }
        for (s in orfaos) {
            val ym = s.data.substring(0, 7)
            val mesId = meses(db).firstOrNull { it.ym == ym }?.id
                ?: db.saveLocal(RecordTypes.MES, null, buildJsonObject {
                    put("mes", JsonPrimitive(ym))
                    put("resumo", JsonPrimitive(nomeMes(ym)))
                }, user)
            val diaId = todosDias(db).firstOrNull { it.data == s.data }?.id
                ?: db.saveLocal(RecordTypes.DIA, null, buildJsonObject {
                    put("data", JsonPrimitive(s.data))
                    put("mes_id", JsonPrimitive(mesId))
                    put("resumo", JsonPrimitive(Num.date(s.data)))
                }, user)
            val rec = db.get(s.id!!) ?: continue
            val novo = JsonObject(rec.record.data + ("dia_id" to JsonPrimitive(diaId)))
            db.saveLocal(RecordTypes.SEGMENTO, s.id, novo, user)
        }
        return orfaos.size
    }
}

// ------------------------------------------------------------------ ensaios

/** Resumo de um ensaio para as listas. */
data class EnsaioItem(
    val id: String,
    val tipo: TipoEnsaio,
    val serie: String,
    val posicao: String,
    val posM: Long?,
    val titulo: String,
    val detalhe: String,
    /** "ok", "bad", "ref" ou "" */
    val status: String,
    val pending: Boolean,
    val draft: Boolean,
)

object Ensaios {
    fun doSegmento(db: LocalDb, segmentoId: String, casasGc: Int): List<EnsaioItem> =
        TipoEnsaio.values().filter { it.disponivel }.flatMap { tipo ->
            db.listByType(tipo.type)
                .filter { it.record.data.text("segmento_id") == segmentoId }
                .map { item(it, tipo, casasGc) }
        }.sortedBy { it.posM ?: 0L }

    fun item(r: LocalRecord, tipo: TipoEnsaio, casasGc: Int): EnsaioItem {
        val d = r.record.data
        val (titulo, detalhe) = when (tipo) {
            TipoEnsaio.IN_SITU -> {
                val gc = d.num("gc")?.let { "GC ${Num.fmt(it, casasGc)}%" } ?: "sem resultado"
                val gs = d.num("gs")?.let { "γs ${Num.density(it)}" } ?: ""
                "In situ · $gc" to gs
            }
            TipoEnsaio.PROCTOR -> {
                val gs = d.num("gs")?.let { "γs máx ${Num.density(it)}" } ?: "sem resultado"
                val h = d.num("h")?.let { "h ót ${Num.fmt(it, 1)}%" } ?: ""
                "Proctor · $gs" to h
            }
            TipoEnsaio.TAXA -> {
                val casas = d.long("casas_taxa")?.toInt() ?: 3
                val t = d.num("kg_m2")?.let { "${Num.fmt(it, casas)} kg/m²" } ?: "sem resultado"
                val p = d.num("projeto")?.let { "projeto ${Num.fmt(it, casas)}" } ?: ""
                "Taxa ${d.text("tipo").lowercase()} · $t" to p
            }
            TipoEnsaio.UMIDADE -> {
                val h = d.num("h")?.let { "${Num.fmt(it, 1)}%" } ?: "sem resultado"
                "Umidade inicial · $h" to d.text("lado")
            }
            TipoEnsaio.RESIDUO -> {
                val r = d.num("residuo")?.let { "${Num.fmt(it, 1)}%" } ?: "sem resultado"
                val carga = d.num("carga")?.let { "${Num.plain(it)} t" } ?: ""
                "Resíduo · $r" to listOf(d.text("placa"), carga).filter { it.isNotEmpty() }.joinToString(" · ")
            }
        }
        return EnsaioItem(
            id = r.record.id,
            tipo = tipo,
            serie = d.text("serie"),
            posicao = d.long("posicao")?.let { Km.format(it) } ?: "",
            posM = d.long("posicao"),
            titulo = titulo,
            detalhe = detalhe,
            status = d.text("status"),
            pending = r.pending,
            draft = r.draft,
        )
    }

    /** Proctors candidatos para um in situ: os do segmento; se não houver, todos. Ordenados pela distância. */
    fun proctorsPara(db: LocalDb, segmentoId: String, posicao: Long?): List<LocalRecord> {
        val todos = db.listByType(RecordTypes.PROCTOR).filter { !it.draft && it.record.data.num("gs") != null }
        val doSeg = todos.filter { it.record.data.text("segmento_id") == segmentoId }
        val base = doSeg.ifEmpty { todos }
        return if (posicao == null) base.sortedByDescending { it.record.createdAt }
        else base.sortedBy { kotlin.math.abs((it.record.data.long("posicao") ?: Long.MAX_VALUE / 2) - posicao) }
    }
}

// ------------------------------------------------------------------ formulários dos ensaios

/** Lê os campos do registro para o formulário (texto). */
fun formFrom(data: JsonObject?, numKeys: List<String>, kmKeys: List<String>, textKeys: List<String>): Map<String, String> {
    val m = mutableMapOf<String, String>()
    numKeys.forEach { k -> m[k] = data?.num(k)?.let { Num.plain(it) } ?: "" }
    kmKeys.forEach { k -> m[k] = data?.long(k)?.let { Km.toDigits(it) } ?: "" }
    textKeys.forEach { k -> m[k] = data?.text(k) ?: "" }
    return m
}

fun JsonObjectBuilder.num(key: String, value: Double?) {
    if (value != null && !value.isNaN() && !value.isInfinite()) put(key, JsonPrimitive(value))
}

fun JsonObjectBuilder.text(key: String, value: String) {
    if (value.isNotEmpty()) put(key, JsonPrimitive(value))
}

/** Status a partir das conferências (null = sem limite). */
fun statusDe(vararg checks: Boolean?): String = when {
    checks.any { it == false } -> "bad"
    checks.any { it == true } -> "ok"
    else -> ""
}

/** Atalho para o cálculo do status do in situ. */
fun statusInSitu(gc: Double?, h: Double?, hOt: Double?, lim: JsonObject?, casasGc: Int): String {
    val gcOk = Calc.dentro(gc, lim?.num("gc_min"), lim?.num("gc_max"), casasGc)
    val desvio = if (h != null && hOt != null) h - hOt else null
    val hOk = Calc.dentro(desvio, lim?.num("umid_desvio_min"), lim?.num("umid_desvio_max"), 1)
    return statusDe(gcOk, hOk)
}
