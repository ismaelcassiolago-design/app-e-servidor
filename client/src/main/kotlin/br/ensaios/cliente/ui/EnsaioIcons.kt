package br.ensaios.cliente.ui

import androidx.annotation.DrawableRes
import br.ensaios.cliente.R
import br.ensaios.shared.TipoEnsaio

/** Ícone desenhado de cada tipo de ensaio (arquivos em res/drawable/ic_ensaio_*.xml). */
@DrawableRes
fun TipoEnsaio.iconRes(): Int = when (this) {
    TipoEnsaio.IN_SITU -> R.drawable.ic_ensaio_insitu
    TipoEnsaio.PROCTOR -> R.drawable.ic_ensaio_proctor
    TipoEnsaio.TAXA -> R.drawable.ic_ensaio_taxa
    TipoEnsaio.UMIDADE -> R.drawable.ic_ensaio_umidade
    TipoEnsaio.RESIDUO -> R.drawable.ic_ensaio_residuo
}
