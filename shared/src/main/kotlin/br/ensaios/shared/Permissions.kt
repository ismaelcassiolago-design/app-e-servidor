package br.ensaios.shared

/** Permissões que o administrador do servidor dá a cada usuário. */
object Perm {
    const val VER = "ver"
    const val CRIAR = "criar"
    const val EDITAR_PROPRIOS = "editar_proprios"
    const val EDITAR_OUTROS = "editar_outros"
    const val EXCLUIR = "excluir"
    const val LIXEIRA = "lixeira"
    const val CADASTROS = "editar_cadastros"
    const val CONCLUIR_VIGA = "concluir_viga"
    const val RELATORIOS = "relatorios"

    val all: List<String> = listOf(
        VER, CRIAR, EDITAR_PROPRIOS, EDITAR_OUTROS, EXCLUIR,
        LIXEIRA, CADASTROS, CONCLUIR_VIGA, RELATORIOS,
    )

    val labels: Map<String, String> = mapOf(
        VER to "Ver ensaios de todos",
        CRIAR to "Criar ensaios e segmentos",
        EDITAR_PROPRIOS to "Editar os próprios",
        EDITAR_OUTROS to "Editar de outros usuários",
        EXCLUIR to "Excluir (mandar para a lixeira)",
        LIXEIRA to "Acessar a lixeira",
        CADASTROS to "Editar cadastros",
        CONCLUIR_VIGA to "Concluir viga Benkelman",
        RELATORIOS to "Gerar relatórios",
    )

    /** Perfis prontos para facilitar o cadastro de usuários. */
    val profiles: Map<String, Set<String>> = linkedMapOf(
        "Laboratorista" to setOf(VER, CRIAR, EDITAR_PROPRIOS, RELATORIOS),
        "Encarregado" to setOf(VER, CRIAR, EDITAR_PROPRIOS, EDITAR_OUTROS, EXCLUIR, CADASTROS, CONCLUIR_VIGA, RELATORIOS),
        "Administrador" to all.toSet(),
    )

    fun has(user: UserInfo, perm: String): Boolean = user.isAdmin || perm in user.permissions
}

/** Tipos de registro. Cadastros começam com "cad_". */
object RecordTypes {
    const val CLIENTE = "cad_cliente"

    fun isCadastro(type: String): Boolean = type.startsWith("cad_")
}

object WriteRules {
    /**
     * Confere se o usuário pode gravar a alteração. Retorna null se pode,
     * ou o motivo da recusa. Usado no servidor (regra oficial) e no cliente (para esconder botões).
     */
    fun check(
        user: UserInfo,
        type: String,
        isNew: Boolean,
        existingCreatedBy: String?,
        deleting: Boolean,
    ): String? {
        if (!user.active) return "Usuário bloqueado"
        if (user.isAdmin) return null
        if (RecordTypes.isCadastro(type)) {
            return if (Perm.CADASTROS in user.permissions) null else "Sem permissão para editar cadastros"
        }
        return when {
            deleting -> if (Perm.EXCLUIR in user.permissions) null else "Sem permissão para excluir"
            isNew -> if (Perm.CRIAR in user.permissions) null else "Sem permissão para criar"
            existingCreatedBy == user.id ->
                if (Perm.EDITAR_PROPRIOS in user.permissions) null else "Sem permissão para editar"
            else ->
                if (Perm.EDITAR_OUTROS in user.permissions) null else "Sem permissão para editar lançamentos de outros usuários"
        }
    }
}
