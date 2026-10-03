package br.ensaios.cliente.report

import android.content.Context
import android.graphics.Color
import br.ensaios.cliente.data.Dia
import br.ensaios.cliente.data.LocalDb
import br.ensaios.cliente.data.LocalRecord
import br.ensaios.cliente.data.Producao
import br.ensaios.cliente.data.Segmento
import br.ensaios.cliente.data.long
import br.ensaios.cliente.data.num
import br.ensaios.cliente.data.refNames
import br.ensaios.cliente.data.text
import br.ensaios.shared.Km
import br.ensaios.shared.Num
import br.ensaios.shared.RecordTypes
import br.ensaios.shared.TipoEnsaio
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Modelos de PDF. */
enum class ModeloPdf(val label: String, val sufixo: String) {
    APP("Padrão do app", "app"),
    NEOVIA("Padrão Neovia (fichas CQ)", "neovia"),
}

private val VERDE = Color.rgb(31, 138, 76)
private val VERMELHO = Color.rgb(198, 40, 40)
private val CINZA = Color.rgb(235, 237, 240)

/** Pasta onde ficam os PDFs gerados. */
fun pastaRelatorios(context: Context): File = File(context.filesDir, "relatorios").apply { mkdirs() }

private fun JsonObject.mass(k: String) = num(k)?.let { Num.mass(it) } ?: ""
private fun JsonObject.dens(k: String) = num(k)?.let { Num.density(it) } ?: ""
private fun JsonObject.pct(k: String, casas: Int = 1) = num(k)?.let { Num.fmt(it, casas) } ?: ""
private fun JsonObject.km(k: String) = long(k)?.let { Km.format(it) } ?: ""
private fun sub(a: Double?, b: Double?): Double? = if (a != null && b != null) a - b else null

/** Tudo que o relatório de um dia precisa, já buscado do banco. */
private class DadosDia(context: Context, val dia: Dia) {
    val db = LocalDb.get(context)
    val segs: List<Segmento> = Producao.segmentos(db, dia)
    val rodovias = refNames(db, RecordTypes.RODOVIA)
    val pistas = refNames(db, RecordTypes.PISTA)
    val faixas = refNames(db, RecordTypes.FAIXA)
    val clientes = refNames(db, RecordTypes.CLIENTE)
    val cilindros = refNames(db, RecordTypes.CILINDRO)
    val segIds = segs.mapNotNull { it.id }.toSet()

    /** Ensaios concluídos do dia, por tipo (rascunhos ficam de fora). */
    fun ensaios(tipo: TipoEnsaio): List<LocalRecord> =
        db.listByType(tipo.type)
            .filter { !it.draft && it.record.data.text("segmento_id") in segIds }
            .sortedBy { it.record.data.long("posicao") ?: 0L }

    fun seg(id: String): Segmento? = segs.firstOrNull { it.id == id }

    fun descricao(s: Segmento): String = listOf(
        rodovias[s.rodoviaId] ?: "", s.intervalo, pistas[s.pistaId] ?: "", faixas[s.faixaId] ?: "",
        s.extensao?.let { "$it m" } ?: "", s.servico,
    ).filter { it.isNotEmpty() }.joinToString(" · ")
}

private fun situacao(status: String): Cell = when (status) {
    "ok" -> c("APROVADO", bold = true, color = VERDE)
    "bad" -> c("REPROVADO", bold = true, color = VERMELHO)
    "ref" -> c("REFERÊNCIA", bold = true)
    else -> c("—")
}

/** Resultado e limite de cada ensaio, para o relatório no padrão do app. */
private fun resultadoELimite(tipo: TipoEnsaio, d: JsonObject): Pair<String, String> {
    val lim = d["lim"] as? JsonObject
    return when (tipo) {
        TipoEnsaio.IN_SITU -> {
            val casas = d.long("casas_gc")?.toInt() ?: 1
            "GC ${d.pct("gc", casas)}% · γs ${d.dens("gs")} · h ${d.pct("h")}%" to
                listOfNotNull(lim?.num("gc_min")?.let { "GC ≥ ${Num.plain(it)}" }, lim?.num("gc_max")?.let { "≤ ${Num.plain(it)}" }).joinToString(" ")
        }
        TipoEnsaio.PROCTOR -> "γs máx ${d.dens("gs")} · h ót ${d.pct("h")}%" to ""
        TipoEnsaio.TAXA -> {
            val casas = d.long("casas_taxa")?.toInt() ?: 3
            val proj = d.num("projeto")?.let { " (projeto ${Num.fmt(it, casas)})" } ?: ""
            "${d.text("tipo")} ${d.num("kg_m2")?.let { Num.fmt(it, casas) } ?: ""} kg/m²$proj" to
                listOfNotNull(lim?.num("taxa_var_min")?.let { "${Num.plain(it)}%" }, lim?.num("taxa_var_max")?.let { "${Num.plain(it)}%" }).joinToString(" a ")
        }
        TipoEnsaio.UMIDADE -> "h ${d.pct("h")}%" to
            listOfNotNull(lim?.num("umid_inicial_min")?.let { "${Num.plain(it)}%" }, lim?.num("umid_inicial_max")?.let { "${Num.plain(it)}%" }).joinToString(" a ")
        TipoEnsaio.RESIDUO -> "Resíduo ${d.pct("residuo")}% · ${d.text("placa")} · ${d.num("carga")?.let { Num.plain(it) } ?: ""} t" to
            (lim?.num("residuo_min")?.let { "≥ ${Num.plain(it)}%" } ?: "")
    }
}

/** Gera o PDF do dia de produção no modelo escolhido. Retorna o arquivo. */
fun gerarRelatorioDia(context: Context, dia: Dia, modelo: ModeloPdf, autor: String): File {
    val dados = DadosDia(context, dia)
    val pdf = PdfKit()
    when (modelo) {
        ModeloPdf.APP -> modeloApp(pdf, dados, autor)
        ModeloPdf.NEOVIA -> modeloNeovia(pdf, dados, autor)
    }
    val nome = "Relatorio_${dia.data}_${modelo.sufixo}.pdf"
    return pdf.save(File(pastaRelatorios(context), nome))
}

// =================================================================== padrão do app

private fun modeloApp(pdf: PdfKit, d: DadosDia, autor: String) {
    val gerado = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
    pdf.onNewPage = { p ->
        p.text(p.margin, p.margin + 8, "Controle de Ensaios · Relatório diário", 8f, color = Color.GRAY)
        p.text(p.width - p.margin, p.margin + 8, "Dia ${Num.date(d.dia.data)} · gerado em $gerado", 8f, align = Align.RIGHT, color = Color.GRAY)
        p.y = p.margin + 18
    }
    pdf.newPage()
    pdf.title("RELATÓRIO DIÁRIO DE ENSAIOS — ${Num.date(d.dia.data)}", 14f)

    val todos = TipoEnsaio.values().flatMap { t -> d.ensaios(t).map { t to it } }
    val clientes = d.segs.map { d.clientes[it.clienteId] ?: "" }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
    val rodovias = d.segs.map { d.rodovias[it.rodoviaId] ?: "" }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
    pdf.line("Cliente: ${clientes.ifEmpty { "—" }}    Rodovia: ${rodovias.ifEmpty { "—" }}    Responsável: $autor", 9f)
    pdf.space(4f)

    pdf.table(
        listOf(1f, 1f, 1f, 1f, 1f, 1f),
        listOf(c("Segmentos"), c("Extensão total"), c("Área total"), c("Ensaios"), c("Aprovados"), c("Reprovados")),
        listOf(
            listOf(
                c("${d.segs.size}", bold = true),
                c("${d.segs.sumOf { it.extensao ?: 0L }} m", bold = true),
                c("${Num.fmt(d.segs.sumOf { it.area ?: 0.0 }, 2)} m²", bold = true),
                c("${todos.size}", bold = true),
                c("${todos.count { it.second.record.data.text("status") == "ok" }}", bold = true, color = VERDE),
                c("${todos.count { it.second.record.data.text("status") == "bad" }}", bold = true, color = VERMELHO),
            )
        ),
        rowH = 18f, size = 9f,
    )

    d.segs.forEach { s ->
        pdf.space(10f)
        pdf.ensure(60f)
        pdf.line(d.descricao(s), 10f, bold = true)
        val linhas = todos.filter { it.second.record.data.text("segmento_id") == s.id }.map { (tipo, r) ->
            val data = r.record.data
            val (res, lim) = resultadoELimite(tipo, data)
            listOf(
                c(data.text("serie")), c(tipo.nome), c(data.km("posicao")), c(data.text("lado")),
                c(res, align = Align.LEFT), c(lim), situacao(data.text("status")),
            )
        }
        if (linhas.isEmpty()) {
            pdf.line("Nenhum ensaio concluído neste segmento.", 8.5f, color = Color.GRAY)
        } else {
            pdf.table(
                listOf(1.6f, 1.2f, 0.9f, 0.6f, 3.2f, 1.1f, 1.1f),
                listOf(c("Série"), c("Ensaio"), c("Posição"), c("Lado"), c("Resultado"), c("Limite"), c("Situação")),
                linhas,
            )
        }
    }
    pdf.signatures("Laboratorista", "Fiscalização")
}

// =================================================================== padrão Neovia (fichas CQ)

private fun modeloNeovia(pdf: PdfKit, d: DadosDia, autor: String) {
    pdf.onNewPage = null
    val inSitu = d.ensaios(TipoEnsaio.IN_SITU)
    val proctors = d.ensaios(TipoEnsaio.PROCTOR)
    val taxas = d.ensaios(TipoEnsaio.TAXA)
    val umidades = d.ensaios(TipoEnsaio.UMIDADE)
    val residuos = d.ensaios(TipoEnsaio.RESIDUO)

    // In situ: uma ficha CQ 06 por segmento, até 6 furos por folha.
    inSitu.groupBy { it.record.data.text("segmento_id") }.forEach { (segId, lista) ->
        lista.chunked(6).forEach { furos -> fichaCq06(pdf, d, d.seg(segId), furos, autor) }
    }
    // Proctor: ficha de compactação, até 6 pontos por folha.
    proctors.chunked(6).forEach { fichaCompactacao(pdf, d, it) }
    // Taxa: ficha CQ 05 (paisagem).
    if (taxas.isNotEmpty()) fichaCq05(pdf, d, taxas)
    // Umidade e resíduo não têm ficha: tabela simples.
    if (umidades.isNotEmpty() || residuos.isNotEmpty()) tabelasSimples(pdf, d, umidades, residuos)

    if (inSitu.isEmpty() && proctors.isEmpty() && taxas.isEmpty() && umidades.isEmpty() && residuos.isEmpty()) {
        pdf.newPage(false)
        pdf.title("Dia ${Num.date(d.dia.data)}: nenhum ensaio concluído.")
    }
}

/** Cabeçalho de ficha: título no centro e código à direita. */
private fun cabecalhoFicha(pdf: PdfKit, linha1: String, linha2: String, codigo: String) {
    val x = pdf.margin
    val w = pdf.width - 2 * pdf.margin
    pdf.rect(x, pdf.y, w, 40f)
    pdf.text(x + w / 2, pdf.y + 16, linha1, 11f, bold = true, align = Align.CENTER)
    pdf.text(x + w / 2, pdf.y + 31, linha2, 10f, bold = true, align = Align.CENTER)
    pdf.text(x + w - 8, pdf.y + 16, codigo, 11f, bold = true, align = Align.RIGHT)
    pdf.y += 40f
}

/** Quadro de campos "RÓTULO: valor" em pares, 2 por linha. */
private fun quadroCampos(pdf: PdfKit, campos: List<Pair<String, String>>) {
    val linhas = campos.chunked(2).map { par ->
        par.flatMap { (k, v) -> listOf(c("$k:", bold = true, align = Align.LEFT, fill = CINZA), c(v, align = Align.LEFT)) } +
            if (par.size == 1) listOf(c(""), c("")) else emptyList()
    }
    pdf.table(listOf(1f, 2f, 1f, 2f), null, linhas, rowH = 15f, size = 8.5f)
}

private fun fichaCq06(pdf: PdfKit, d: DadosDia, s: Segmento?, furos: List<LocalRecord>, autor: String) {
    pdf.newPage(false)
    cabecalhoFicha(pdf, "CONTROLE DE COMPACTAÇÃO", "DENSIDADE \"IN SITU\" MÉT. FRASCO DE AREIA", "CQ 06")
    quadroCampos(
        pdf,
        listOf(
            "RODOVIA" to (s?.let { d.rodovias[it.rodoviaId] } ?: ""),
            "OPERADOR" to autor,
            "TRECHO" to (s?.intervalo ?: ""),
            "DATA" to Num.date(d.dia.data),
            "CAMADA" to (s?.servico ?: ""),
            "PISTA" to (s?.let { d.pistas[it.pistaId] } ?: ""),
            "SEGMENTO" to (s?.intervalo ?: ""),
            "FAIXA" to (s?.let { d.faixas[it.faixaId] } ?: ""),
        ),
    )
    pdf.space(8f)
    pdf.title("DETERMINAÇÃO DA DENSIDADE \"IN SITU\" (DNER-ME 092/94)", 10f)

    val dados = furos.map { it.record.data }
    fun linha(label: String, f: (JsonObject) -> String) = listOf(c(label, align = Align.LEFT)) + (0 until 6).map { i -> c(dados.getOrNull(i)?.let(f) ?: "") }
    fun secao(label: String) = listOf(c(label, bold = true, align = Align.LEFT, fill = CINZA)) + (0 until 6).map { c("", fill = CINZA) }
    val pesos = listOf(3.4f) + List(6) { 1f }

    val rows = listOf(
        linha("FURO") { it.text("furo").ifEmpty { it.text("serie").takeLast(4) } },
        linha("KM") { it.km("posicao") },
        linha("CAMADA") { it.text("camada") },
        linha("POSIÇÃO") { it.text("lado") },
        linha("PESO DO FRASCO ANTES - 1") { it.mass("l1") },
        linha("PESO DO FRASCO DEPOIS - 2") { it.mass("l2") },
        linha("PESO AREIA DESLOCADA - 3 = 1 - 2") { j -> sub(j.num("l1"), j.num("l2"))?.let { Num.mass(it) } ?: "" },
        linha("PESO AREIA FUNIL E PLACA - 4") { it.mass("l4") },
        linha("PESO DA AREIA NA CAVIDADE - 5 = 3 - 4") { j -> sub(sub(j.num("l1"), j.num("l2")), j.num("l4"))?.let { Num.mass(it) } ?: "" },
        linha("MASSA ESPEC. APAR. AREIA - 6") { it.dens("l6") },
        linha("VOLUME DO SOLO - 7 = 5 / 6") { j -> j.num("volume")?.let { Num.fmt(it, 0) } ?: "" },
        linha("PESO DO SOLO E RECIPIENTE - 8") { it.mass("l8") },
        linha("PESO DO RECIPIENTE - 9") { it.mass("l9") },
        linha("PESO SOLO - 10 = (8 - 9) + AMOSTRA") { it.mass("peso_solo") },
        secao("UMIDADES DE CAMPO"),
        linha("CÁPSULA NÚMERO") { it.text("capsula") },
        linha("PESO CÁPSULA E SOLO ÚMIDO - 1") { it.mass("u1") },
        linha("PESO CÁPSULA E SOLO SECO - 2") { it.mass("u2") },
        linha("PESO CÁPSULA - 3") { j -> Num.mass(j.num("u3") ?: 0.0) },
        linha("PESO ÁGUA - 4 = 1 - 2") { j -> sub(j.num("u1"), j.num("u2"))?.let { Num.mass(it) } ?: "" },
        linha("PESO SOLO SECO - 5 = 2 - 3") { j -> sub(j.num("u2"), j.num("u3") ?: 0.0)?.let { Num.mass(it) } ?: "" },
        linha("TEOR UMIDADE - 6 = (4 / 5)*100") { it.pct("h") },
        secao("ENSAIO DE COMPACTAÇÃO EM LABORATÓRIO"),
        linha("MASSA ESP. APARENTE SOLO ÚMIDO") { it.dens("gh") },
        linha("MASSA ESP. APARENTE SOLO SECO") { it.dens("gs") },
        linha("MASSA ESPEC. SECO DO LABORATÓRIO") { it.dens("gs_lab") },
        linha("UMIDADE ÓTIMA") { it.pct("h_ot") },
        listOf(c("GRAU DE COMPACTAÇÃO", align = Align.LEFT)) + (0 until 6).map { i ->
            val j = dados.getOrNull(i)
            val casas = j?.long("casas_gc")?.toInt() ?: 1
            val cor = when (j?.text("status")) { "ok" -> VERDE; "bad" -> VERMELHO; else -> Color.BLACK }
            c(j?.pct("gc", casas)?.let { if (it.isEmpty()) "" else "$it%" } ?: "", bold = true, color = cor)
        },
        linha("REGISTRO (LABORATÓRIO)") { it.text("proctor_serie") },
    )
    pdf.table(pesos, null, rows, rowH = 15.5f, size = 7.6f)
    pdf.space(6f)
    val obs = dados.mapNotNull { j -> j.text("obs").takeIf { it.isNotEmpty() }?.let { "${j.text("serie")}: $it" } }.joinToString("  ·  ")
    pdf.table(listOf(1f), null, listOf(listOf(c("OBS: $obs", align = Align.LEFT))), rowH = 30f, size = 8f)
    pdf.signatures("Laboratorista", "Fiscalização")
}

private fun fichaCompactacao(pdf: PdfKit, d: DadosDia, lista: List<LocalRecord>) {
    pdf.newPage(false)
    cabecalhoFicha(pdf, "SISTEMA DE GESTÃO DA QUALIDADE", "ENSAIO DE COMPACTAÇÃO — NBR 7182:2016", "LAB. OBRA")
    val dados = lista.map { it.record.data }
    val s = dados.firstOrNull()?.let { d.seg(it.text("segmento_id")) }
    quadroCampos(
        pdf,
        listOf(
            "INTERESSADO" to (s?.let { d.clientes[it.clienteId] } ?: ""),
            "DATA" to Num.date(d.dia.data),
            "SEGMENTO" to (s?.intervalo ?: ""),
            "PROCTOR/ENERGIA" to (dados.firstOrNull()?.text("energia") ?: ""),
            "OBRA" to (s?.let { d.rodovias[it.rodoviaId] } ?: ""),
            "Nº DE CAMADAS" to (dados.firstOrNull()?.text("camadas") ?: ""),
            "CAMADA" to (dados.firstOrNull()?.text("camada") ?: ""),
            "Nº DE GOLPES" to (dados.firstOrNull()?.text("golpes") ?: ""),
        ),
    )
    pdf.space(8f)
    fun linha(label: String, un: String, f: (JsonObject) -> String) =
        listOf(c(label, align = Align.LEFT), c(un)) + (0 until 6).map { i -> c(dados.getOrNull(i)?.let(f) ?: "") }
    val rows = listOf(
        linha("Estaca / posição", "") { it.km("posicao") },
        linha("Cápsula", "nº") { it.text("capsula") },
        linha("Peso bruto úmido", "g") { it.mass("u1") },
        linha("Peso bruto seco", "g") { it.mass("u2") },
        linha("Peso da água", "g") { j -> sub(j.num("u1"), j.num("u2"))?.let { Num.mass(it) } ?: "" },
        linha("Peso da cápsula", "g") { j -> Num.mass(j.num("u3") ?: 0.0) },
        linha("Peso do solo seco", "g") { j -> sub(j.num("u2"), j.num("u3") ?: 0.0)?.let { Num.mass(it) } ?: "" },
        linha("Umidade", "%") { it.pct("h") },
        linha("Cilindro", "nº") { j -> d.cilindros[j.text("cilindro_id")] ?: "" },
        linha("Peso bruto úmido", "g") { it.mass("bruto") },
        linha("Peso do cilindro", "g") { it.mass("cil_massa") },
        linha("Volume do cilindro", "cm³") { j -> j.num("cil_volume")?.let { Num.plain(it) } ?: "" },
        linha("Peso do solo úmido", "g") { it.mass("solo_umido") },
        linha("Massa do solo úmido", "g/cm³") { it.dens("gh") },
        linha("Massa do solo seco", "g/cm³") { it.dens("gs") },
    )
    pdf.table(listOf(3f, 0.9f) + List(6) { 1f }, listOf(c("Item"), c("Unidade")) + (1..6).map { c("$it") }, rows, rowH = 16f, size = 8f)
    pdf.space(8f)
    pdf.title("RESULTADOS", 10f)
    pdf.table(
        listOf(1.4f, 1f, 1f),
        listOf(c("Ponto (série)"), c("Massa específica máxima (g/cm³)"), c("Umidade ótima (%)")),
        dados.map { listOf(c(it.text("serie")), c(it.dens("gs"), bold = true), c(it.pct("h"), bold = true)) },
        rowH = 16f, size = 8.5f,
    )
    pdf.signatures("Laboratorista", "Enc. Laboratório")
}

private fun fichaCq05(pdf: PdfKit, d: DadosDia, taxas: List<LocalRecord>) {
    pdf.newPage(true)
    cabecalhoFicha(pdf, "CONTROLE DE QUALIDADE", "CONTROLE DA TAXA DE APLICAÇÃO", "CQ 05")
    val dados = taxas.map { it.record.data }
    val tipos = dados.map { it.text("tipo") }.toSet()
    fun marca(t: String) = if (t in tipos) "X" else " "
    val s = dados.firstOrNull()?.let { d.seg(it.text("segmento_id")) }
    quadroCampos(
        pdf,
        listOf(
            "OBRA" to (s?.let { d.rodovias[it.rodoviaId] } ?: ""),
            "ENSAIO" to "Imprimação (${marca("Imprimação")})  Cimento (${marca("Cimento")})  Agregado (${marca("Agregado")})  Cal (${marca("Cal")})",
            "TRECHO" to (s?.intervalo ?: ""),
            "PERÍODO" to Num.date(d.dia.data),
            "CAMADA" to (s?.servico ?: ""),
            "PISTA/FAIXA" to (s?.let { listOfNotNull(d.pistas[it.pistaId], d.faixas[it.faixaId]).joinToString(" / ") } ?: ""),
        ),
    )
    pdf.space(8f)
    val rows = taxas.map { r ->
        val j = r.record.data
        val casas = j.long("casas_taxa")?.toInt() ?: 3
        val seg = d.seg(j.text("segmento_id"))
        val cor = when (j.text("status")) { "ok" -> VERDE; "bad" -> VERMELHO; else -> Color.BLACK }
        listOf(
            c(Num.date(d.dia.data)), c(j.km("posicao")), c(j.km("posicao_fim")),
            c(seg?.let { d.faixas[it.faixaId] } ?: ""),
            c(Num.mass(j.num("tara") ?: 0.0)), c(j.mass("tara_material")), c(j.mass("material")),
            c(j.num("area")?.let { Num.plain(it) } ?: ""),
            c(j.num("kg_m2")?.let { Num.fmt(it, casas) } ?: "", bold = true, color = cor),
            c(j.num("l_m2")?.let { Num.fmt(it, casas) } ?: ""),
            c(j.num("residual")?.let { Num.fmt(it, casas) } ?: ""),
            c(listOfNotNull(
                j.num("projeto")?.let { "proj. ${Num.fmt(it, casas)}" },
                j.num("dens_ligante")?.let { "dens. ${Num.plain(it)}" },
                j.text("obs").takeIf { it.isNotEmpty() },
            ).joinToString(" · "), align = Align.LEFT),
        )
    }
    pdf.table(
        listOf(0.9f, 0.9f, 0.9f, 0.7f, 0.8f, 1f, 1f, 0.6f, 1f, 1f, 0.8f, 2.4f),
        listOf(
            c("Data"), c("KM inicial"), c("KM final"), c("Faixa"), c("Tara A (g)"), c("Tara+Mat. (g)"),
            c("Material D (g)"), c("Área (m²)"), c("Taxa kg/m²"), c("Taxa L/m²"), c("Resíduo"), c("Observação"),
        ),
        rows, rowH = 17f, size = 7.6f,
    )
    pdf.signatures("Laboratorista", "Fiscal")
}

private fun tabelasSimples(pdf: PdfKit, d: DadosDia, umidades: List<LocalRecord>, residuos: List<LocalRecord>) {
    pdf.newPage(false)
    if (umidades.isNotEmpty()) {
        pdf.title("UMIDADE INICIAL PARA RECICLAGEM — ${Num.date(d.dia.data)}", 11f)
        pdf.table(
            listOf(1.6f, 1f, 0.7f, 0.8f, 1f, 1f, 1f, 0.9f, 1.1f),
            listOf(c("Série"), c("Posição"), c("Lado"), c("Cápsula"), c("Úmido (g)"), c("Seco (g)"), c("Tara (g)"), c("Umidade"), c("Situação")),
            umidades.map { r ->
                val j = r.record.data
                listOf(c(j.text("serie")), c(j.km("posicao")), c(j.text("lado")), c(j.text("capsula")), c(j.mass("u1")), c(j.mass("u2")),
                    c(Num.mass(j.num("u3") ?: 0.0)), c("${j.pct("h")}%", bold = true), situacao(j.text("status")))
            },
        )
        pdf.space(14f)
    }
    if (residuos.isNotEmpty()) {
        pdf.title("RESÍDUO POR EVAPORAÇÃO DA EMULSÃO (NBR 14376) — ${Num.date(d.dia.data)}", 11f)
        pdf.table(
            listOf(1.6f, 0.9f, 0.7f, 0.8f, 1f, 1f, 1f, 0.9f, 1.1f),
            listOf(c("Série"), c("Placa"), c("Carga (t)"), c("Emulsão"), c("Recip. (g)"), c("+ amostra (g)"), c("+ resíduo (g)"), c("Resíduo"), c("Situação")),
            residuos.map { r ->
                val j = r.record.data
                listOf(c(j.text("serie")), c(j.text("placa")), c(j.num("carga")?.let { Num.plain(it) } ?: ""), c(j.text("emulsao")),
                    c(Num.mass(j.num("recipiente") ?: 0.0)), c(j.mass("com_amostra")), c(j.mass("com_residuo")),
                    c("${j.pct("residuo")}%", bold = true), situacao(j.text("status")))
            },
        )
        pdf.line("Total de emulsão recebida no dia: ${Num.plain(residuos.sumOf { it.record.data.num("carga") ?: 0.0 })} t", 9f, bold = true)
    }
    pdf.signatures("Laboratorista", "Fiscalização")
}
