package br.ensaios.cliente.data

import android.content.Context
import br.ensaios.cliente.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Padrões do app (neste celular): cor, casas decimais. */
object AppSettings {
    private const val FILE = "padroes"

    private val _palette = MutableStateFlow(Palette.VERDE)
    val palette: StateFlow<Palette> = _palette

    /** Sol forte: alto contraste, ligado e desligado pelo botão no cabeçalho. */
    private val _solForte = MutableStateFlow(false)
    val solForte: StateFlow<Boolean> = _solForte

    private val _casasTaxa = MutableStateFlow(3)
    val casasTaxa: StateFlow<Int> = _casasTaxa

    private val _casasGc = MutableStateFlow(1)
    val casasGc: StateFlow<Int> = _casasGc

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val sp = sp(context)
        _palette.value = try {
            Palette.valueOf(sp.getString("palette", Palette.VERDE.name) ?: Palette.VERDE.name)
        } catch (e: Exception) {
            Palette.VERDE
        }
        if (_palette.value == Palette.SOL_FORTE) _palette.value = Palette.VERDE
        _solForte.value = sp.getBoolean("sol_forte", false)
        _casasTaxa.value = sp.getInt("casas_taxa", 3)
        _casasGc.value = sp.getInt("casas_gc", 1)
    }

    fun setPalette(context: Context, p: Palette) {
        sp(context).edit().putString("palette", p.name).apply()
        _palette.value = p
    }

    fun toggleSolForte(context: Context) {
        val v = !_solForte.value
        sp(context).edit().putBoolean("sol_forte", v).apply()
        _solForte.value = v
    }

    fun setCasasTaxa(context: Context, n: Int) {
        sp(context).edit().putInt("casas_taxa", n).apply()
        _casasTaxa.value = n
    }

    fun setCasasGc(context: Context, n: Int) {
        sp(context).edit().putInt("casas_gc", n).apply()
        _casasGc.value = n
    }
}
