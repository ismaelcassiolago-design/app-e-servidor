package br.ensaios.shared

/** Tipos de campo dos formulários genéricos. */
enum class FieldKind {
    TEXT,

    /** Número com vírgula (ex.: largura 3,5). */
    DECIMAL,

    /** Massa específica, mostrada com ponto (ex.: 1.456). */
    DENSITY,

    INTEGER,

    /** Data, guardada como 2026-10-03. */
    DATE,

    /** Escolha de outro cadastro (guarda o id). */
    REF,

    /** Escolha numa lista fixa. */
    CHOICE,
}

data class FieldSpec(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val unit: String = "",
    val refType: String? = null,
    val options: List<String> = emptyList(),
    val required: Boolean = false,
    val default: String = "",
    /** Título de seção acima do campo (ex.: "Parâmetros"). */
    val section: String = "",
)

data class CadastroSpec(
    val type: String,
    val title: String,
    val singular: String,
    /** Campo que identifica o registro nas listas. */
    val titleKey: String,
    val fields: List<FieldSpec>,
)

/**
 * Definição de todos os cadastros. Para criar um cadastro novo, basta acrescentar aqui
 * (a tela de lista e o formulário são montados a partir desta definição).
 */
object Cadastros {
    val clientes = CadastroSpec(
        type = RecordTypes.CLIENTE, title = "Clientes", singular = "cliente", titleKey = "nome",
        fields = listOf(
            FieldSpec("nome", "Nome", required = true),
            FieldSpec("contrato", "Contrato"),
            FieldSpec("gc_min", "Grau de compactação mínimo", FieldKind.DECIMAL, "%", section = "Parâmetros padrão deste cliente"),
            FieldSpec("gc_max", "Grau de compactação máximo", FieldKind.DECIMAL, "%"),
            FieldSpec("umid_desvio_min", "Umidade: desvio mínimo da ótima", FieldKind.DECIMAL, "pontos"),
            FieldSpec("umid_desvio_max", "Umidade: desvio máximo da ótima", FieldKind.DECIMAL, "pontos"),
            FieldSpec("umid_inicial_min", "Umidade inicial (reciclagem) mínima", FieldKind.DECIMAL, "%"),
            FieldSpec("umid_inicial_max", "Umidade inicial (reciclagem) máxima", FieldKind.DECIMAL, "%"),
            FieldSpec("taxa_var_min", "Taxa: variação mínima sobre o projeto", FieldKind.DECIMAL, "%"),
            FieldSpec("taxa_var_max", "Taxa: variação máxima sobre o projeto", FieldKind.DECIMAL, "%"),
            FieldSpec("residuo_min", "Resíduo mínimo da emulsão", FieldKind.DECIMAL, "%", default = "67"),
        ),
    )

    val rodovias = CadastroSpec(
        type = RecordTypes.RODOVIA, title = "Rodovias", singular = "rodovia", titleKey = "nome",
        fields = listOf(
            FieldSpec("nome", "Rodovia (ex.: BR-163)", required = true),
            FieldSpec("trecho", "Trecho"),
            FieldSpec("cliente_id", "Cliente", FieldKind.REF, refType = RecordTypes.CLIENTE),
        ),
    )

    val pistas = CadastroSpec(
        type = RecordTypes.PISTA, title = "Pistas", singular = "pista", titleKey = "nome",
        fields = listOf(FieldSpec("nome", "Pista (ex.: Pista Norte, Pista Sul)", required = true)),
    )

    val faixas = CadastroSpec(
        type = RecordTypes.FAIXA, title = "Faixas", singular = "faixa", titleKey = "nome",
        fields = listOf(FieldSpec("nome", "Faixa (ex.: Faixa 1, Acostamento)", required = true)),
    )

    val frascos = CadastroSpec(
        type = RecordTypes.FRASCO, title = "Frascos e areia", singular = "frasco", titleKey = "identificacao",
        fields = listOf(
            FieldSpec("identificacao", "Identificação do frasco", required = true),
            FieldSpec("massa_esp_areia", "Massa específica aparente da areia", FieldKind.DENSITY, "g/cm³", required = true),
            FieldSpec("peso_funil_placa", "Peso da areia no funil e placa", FieldKind.DECIMAL, "g", required = true),
            FieldSpec("data_calibracao", "Data da calibração", FieldKind.DATE),
        ),
    )

    val cilindros = CadastroSpec(
        type = RecordTypes.CILINDRO, title = "Cilindros de Proctor", singular = "cilindro", titleKey = "identificacao",
        fields = listOf(
            FieldSpec("identificacao", "Identificação do cilindro", required = true),
            FieldSpec("massa", "Massa do cilindro", FieldKind.DECIMAL, "g", required = true),
            FieldSpec("volume", "Volume do cilindro", FieldKind.DECIMAL, "cm³", required = true),
        ),
    )

    val bandejas = CadastroSpec(
        type = RecordTypes.BANDEJA, title = "Bandejas e lonas", singular = "bandeja", titleKey = "identificacao",
        fields = listOf(
            FieldSpec("identificacao", "Identificação", required = true),
            FieldSpec("area", "Área", FieldKind.DECIMAL, "m²", required = true),
        ),
    )

    val veiculos = CadastroSpec(
        type = RecordTypes.VEICULO, title = "Veículos", singular = "veículo", titleKey = "placa",
        fields = listOf(FieldSpec("placa", "Placa", required = true)),
    )

    val all: List<CadastroSpec> = listOf(clientes, rodovias, pistas, faixas, frascos, cilindros, bandejas, veiculos)

    fun byType(type: String): CadastroSpec? = all.firstOrNull { it.type == type }
}
