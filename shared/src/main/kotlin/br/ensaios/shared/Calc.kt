package br.ensaios.shared

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Operações que aceitam valor vazio (null): se faltar um dado, o resultado fica vazio. */
private fun sub(a: Double?, b: Double?): Double? = if (a != null && b != null) a - b else null
private fun div(a: Double?, b: Double?): Double? = if (a != null && b != null && abs(b) > 1e-12) a / b else null

/** Arredonda para comparar com limites do mesmo jeito que aparece na tela. */
fun Double.roundTo(decimals: Int): Double {
    val f = 10.0.pow(decimals)
    return (this * f).roundToLong() / f
}

/** Umidade por cápsula: U1 cápsula + solo úmido, U2 cápsula + solo seco, U3 cápsula. */
data class Umidade(val u4Agua: Double?, val u5SoloSeco: Double?, val h: Double?)

object Calc {

    /** A tara da cápsula (U3) vazia conta como zero. */
    fun umidade(u1: Double?, u2: Double?, u3: Double?): Umidade {
        val tara = u3 ?: 0.0
        val u4 = sub(u1, u2)
        val u5 = sub(u2, tara)
        val h = div(u4, u5)?.times(100)
        return Umidade(u4, u5, h)
    }

    /** γs = γh / (1 + h/100) */
    fun seca(gh: Double?, h: Double?): Double? = if (gh != null && h != null) gh / (1 + h / 100) else null

    // ------------------------------------------------------------ in situ (DNER-ME 092/94, ficha CQ 06)

    data class InSitu(
        val l3AreiaDeslocada: Double?,
        val l5AreiaCavidade: Double?,
        val l7Volume: Double?,
        /** Solo que ficou no recipiente (8 − 9). */
        val soloRecipiente: Double?,
        /** Amostra úmida tirada do furo para a umidade (U1 − U3). */
        val amostraUmidade: Double?,
        /** 10 · Peso total do solo retirado do furo = (8 − 9) + (U1 − U3). */
        val l10PesoSolo: Double?,
        val umidade: Umidade,
        val gh: Double?,
        val gs: Double?,
        val gc: Double?,
        /** Alvo da linha U2 para a umidade ficar 2 pontos abaixo da ótima. */
        val u2Alvo: Double?,
    )

    fun inSitu(
        l1: Double?, l2: Double?, l4: Double?, l6: Double?, l8: Double?, l9: Double?,
        u1: Double?, u2: Double?, u3: Double?,
        gsLab: Double?, hOtima: Double?,
    ): InSitu {
        val l3 = sub(l1, l2)
        val l5 = sub(l3, l4)
        val l7 = div(l5, l6)
        val noRecipiente = sub(l8, l9)
        val amostra = sub(u1, u3 ?: 0.0)
        // O material da cápsula também saiu do furo: soma ao peso do solo.
        val l10 = if (noRecipiente != null && amostra != null) noRecipiente + amostra else null
        val um = umidade(u1, u2, u3)
        val gh = div(l10, l7)
        val gs = seca(gh, um.h)
        val gc = div(gs, gsLab)?.times(100)
        val alvo = massaAlvo(u1, u3, hOtima)
        return InSitu(l3, l5, l7, noRecipiente, amostra, l10, um, gh, gs, gc, alvo)
    }

    /**
     * Peso que a balança deve marcar (cápsula + solo seco) para a umidade ficar
     * 2 pontos abaixo da ótima do Proctor vinculado (ex.: ótima 8% → alvo 6%).
     * U2 alvo = U3 + (U1 − U3) / (1 + (h_ót − 2)/100). Tara (U3) vazia = 0.
     */
    fun massaAlvo(u1: Double?, u3: Double?, hOtima: Double?): Double? {
        if (u1 == null || hOtima == null) return null
        val tara = u3 ?: 0.0
        return tara + (u1 - tara) / (1 + (hOtima - 2) / 100)
    }

    // ------------------------------------------------------------ Proctor de um ponto (NBR 7182)

    data class Proctor(
        val umidade: Umidade,
        val soloUmido: Double?,
        val gh: Double?,
        val gs: Double?,
    )

    fun proctor(
        u1: Double?, u2: Double?, u3: Double?,
        pesoBrutoUmido: Double?, pesoCilindro: Double?, volumeCilindro: Double?,
    ): Proctor {
        val um = umidade(u1, u2, u3)
        val solo = sub(pesoBrutoUmido, pesoCilindro)
        val gh = div(solo, volumeCilindro)
        return Proctor(um, solo, gh, seca(gh, um.h))
    }

    // ------------------------------------------------------------ taxa de aplicação (ficha CQ 05)

    data class Taxa(
        /** D · peso do material (g) = (tara + material) − tara. */
        val pesoMaterial: Double?,
        /** Taxa aplicada em kg/m² = D / 1000 / área. */
        val kgM2: Double?,
        /** Imprimação: taxa em L/m² = kg/m² / densidade do ligante (kg/L). */
        val lM2: Double?,
        /** Imprimação: taxa residual = L/m² × teor de resíduo / 100. */
        val residual: Double?,
        /** Cimento: taxa de projeto (kg/m²) = 1 × 1 × e × γs / 100 × %. */
        val projeto: Double?,
        /** Diferença da aplicada para a de projeto (%). */
        val variacao: Double?,
    )

    fun taxa(
        tara: Double?, taraMaterial: Double?, area: Double?,
        densidadeLigante: Double?, teorResiduo: Double?,
        espessuraCm: Double?, gsProctor: Double?, pctCimento: Double?,
    ): Taxa {
        val material = sub(taraMaterial, tara ?: 0.0)
        val kg = div(material?.div(1000), area)
        val l = div(kg, densidadeLigante)
        val res = if (l != null && teorResiduo != null) l * teorResiduo / 100 else null
        val proj = if (espessuraCm != null && gsProctor != null && pctCimento != null)
            (1.0 * 1.0 * (espessuraCm / 100) * (gsProctor * 1000)) / 100 * pctCimento else null
        val varia = if (kg != null && proj != null && proj > 0) (kg - proj) / proj * 100 else null
        return Taxa(material, kg, l, res, proj, varia)
    }

    // ------------------------------------------------------------ resíduo por evaporação (NBR 14376)

    /** Resíduo (%) = (recipiente com resíduo − recipiente) / (recipiente com amostra − recipiente) × 100 */
    fun residuo(recipiente: Double?, comAmostra: Double?, comResiduo: Double?): Double? {
        val tara = recipiente ?: 0.0
        return div(sub(comResiduo, tara), sub(comAmostra, tara))?.times(100)
    }

    // ------------------------------------------------------------ status

    /**
     * Confere um valor contra mínimo e máximo (comparando com o valor arredondado como aparece).
     * Retorna null quando não há limite ou valor.
     */
    fun dentro(valor: Double?, min: Double?, max: Double?, casas: Int): Boolean? {
        if (valor == null || (min == null && max == null)) return null
        val v = valor.roundTo(casas)
        if (min != null && v < min) return false
        if (max != null && v > max) return false
        return true
    }
}

/** Número de série: TIPO-ANO-USUÁRIO-SEQUÊNCIA (ex.: IS-2026-03-0127). A sequência nunca reinicia. */
object Serie {
    fun format(prefixo: String, ano: Int, usuario: Int, numero: Int): String =
        "%s-%04d-%02d-%04d".format(java.util.Locale.ROOT, prefixo, ano, usuario, numero)
}

/** Tipos de ensaio, com prefixo do número de série. */
enum class TipoEnsaio(
    val type: String, val prefixo: String, val nome: String, val disponivel: Boolean,
    /** Taxa e resíduo podem ser no lado inteiro da pista. */
    val aceitaInteiro: Boolean = false,
) {
    IN_SITU(RecordTypes.INSITU, "IS", "In situ", true),
    PROCTOR(RecordTypes.PROCTOR, "PR", "Proctor", true),
    TAXA(RecordTypes.TAXA, "TA", "Taxa de aplicação", true, aceitaInteiro = true),
    UMIDADE(RecordTypes.UMIDADE, "UM", "Umidade inicial", true),
    RESIDUO(RecordTypes.RESIDUO, "RE", "Resíduo emulsão", true, aceitaInteiro = true);

    /** Opções de lado do ensaio. */
    val lados: List<String> get() = if (aceitaInteiro) Lados.base + "Inteiro" else Lados.base

    companion object {
        fun byType(type: String): TipoEnsaio? = values().firstOrNull { it.type == type }
    }
}

/** Lados do ensaio. B.D e B.E = bordo direito e bordo esquerdo. */
object Lados {
    val base = listOf("LD", "LE", "B.D", "B.E", "Eixo")

    private val nomes = mapOf(
        "LD" to "LD · lado direito",
        "LE" to "LE · lado esquerdo",
        "B.D" to "B.D · bordo direito",
        "B.E" to "B.E · bordo esquerdo",
        "Eixo" to "Eixo",
        "Inteiro" to "Inteiro (largura toda)",
    )

    fun label(lado: String): String = nomes[lado] ?: lado
}
