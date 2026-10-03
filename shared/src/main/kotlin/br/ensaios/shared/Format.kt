package br.ensaios.shared

import java.util.Locale
import kotlin.math.abs

/** Posição em km + metros (estaqueamento). Guardada em metros: 168+340 = 168340. */
object Km {
    fun format(meters: Long): String = "%d+%03d".format(Locale.ROOT, meters / 1000, abs(meters % 1000))

    /** Dígitos digitados (ex.: "168340") → metros. */
    fun fromDigits(digits: String): Long? = digits.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLongOrNull()

    /** Metros → dígitos para edição, sempre com 3 dígitos de metros (340 → "0340"). */
    fun toDigits(meters: Long): String = "${meters / 1000}" + "%03d".format(Locale.ROOT, meters % 1000)

    fun extension(start: Long, end: Long): Long = abs(end - start)
}

/**
 * Números do app: vírgula como separador decimal, exceto massas específicas (ponto: 2.100).
 */
object Num {
    fun parse(text: String?): Double? = text?.trim()?.replace(',', '.')?.toDoubleOrNull()

    /** Número com vírgula e [decimals] casas (ex.: 8,4). */
    fun fmt(value: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value).replace('.', ',')

    /** Massa específica: ponto após o primeiro dígito, 3 casas (ex.: 2.100). */
    fun density(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    /** Valor sem zeros inúteis, com vírgula (para mostrar o que foi digitado). */
    fun plain(value: Double): String {
        val s = if (value == Math.floor(value) && abs(value) < 1e12) value.toLong().toString()
        else value.toBigDecimal().stripTrailingZeros().toPlainString()
        return s.replace('.', ',')
    }

    /** Data ISO (2026-10-03) → 03/10/2026. */
    fun date(iso: String?): String {
        if (iso == null || iso.length < 10) return iso ?: ""
        return "${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)}"
    }
}
