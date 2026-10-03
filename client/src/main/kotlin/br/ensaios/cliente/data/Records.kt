package br.ensaios.cliente.data

import br.ensaios.shared.CadastroSpec
import br.ensaios.shared.Cadastros
import br.ensaios.shared.FieldKind
import br.ensaios.shared.FieldSpec
import br.ensaios.shared.Num
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Leitura simples dos campos JSON de um registro. */
fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""
fun JsonObject.num(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

/** Valor do campo formatado para mostrar (com unidade). */
fun JsonObject.display(field: FieldSpec, refNames: Map<String, String> = emptyMap()): String {
    val v = when (field.kind) {
        FieldKind.DECIMAL -> num(field.key)?.let { Num.plain(it) } ?: ""
        FieldKind.DENSITY -> num(field.key)?.let { Num.density(it) } ?: ""
        FieldKind.INTEGER -> long(field.key)?.toString() ?: ""
        FieldKind.DATE -> Num.date(text(field.key))
        FieldKind.REF -> refNames[text(field.key)] ?: ""
        else -> text(field.key)
    }
    if (v.isEmpty()) return ""
    return if (field.unit.isEmpty()) v else "$v ${field.unit}"
}

/** Converte o registro para os valores do formulário (texto). */
fun formValues(spec: CadastroSpec, data: JsonObject?): Map<String, String> =
    spec.fields.associate { f ->
        val v = if (data == null) f.default else when (f.kind) {
            FieldKind.DECIMAL -> data.num(f.key)?.let { Num.plain(it) } ?: ""
            FieldKind.DENSITY -> data.num(f.key)?.let { Num.density(it) } ?: ""
            FieldKind.INTEGER -> data.long(f.key)?.toString() ?: ""
            else -> data.text(f.key)
        }
        f.key to v
    }

/** Confere os campos. Retorna a mensagem de erro ou null. */
fun validateForm(spec: CadastroSpec, values: Map<String, String>): String? {
    for (f in spec.fields) {
        val v = values[f.key]?.trim() ?: ""
        if (f.required && v.isEmpty()) return "Preencha: ${f.label}"
        if (v.isNotEmpty() && f.kind in setOf(FieldKind.DECIMAL, FieldKind.DENSITY, FieldKind.INTEGER) && Num.parse(v) == null) {
            return "Número inválido em: ${f.label}"
        }
    }
    return null
}

/** Monta o JSON do registro a partir do formulário. */
fun buildData(spec: CadastroSpec, values: Map<String, String>): JsonObject = buildJsonObject {
    for (f in spec.fields) {
        val v = values[f.key]?.trim() ?: ""
        if (v.isEmpty()) continue
        when (f.kind) {
            FieldKind.DECIMAL, FieldKind.DENSITY -> Num.parse(v)?.let { put(f.key, JsonPrimitive(it)) }
            FieldKind.INTEGER -> Num.parse(v)?.let { put(f.key, JsonPrimitive(it.toLong())) }
            else -> put(f.key, JsonPrimitive(v))
        }
    }
}

/** Nomes dos registros de um cadastro (id → nome), para listas de escolha. */
fun refNames(db: LocalDb, type: String): Map<String, String> {
    val spec = Cadastros.byType(type)
    val key = spec?.titleKey ?: "nome"
    return db.listByType(type).associate { it.record.id to it.record.data.text(key) }
}
