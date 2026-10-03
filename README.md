# Controle de Ensaios

Dois apps Android para controle de ensaios de campo e laboratório:

- **server/** — app servidor. Fica num celular sempre ligado e guarda o banco de dados.
- **client/** — app cliente. Usado no campo e no laboratório para lançar ensaios.
- **shared/** — código comum aos dois (formato dos dados, permissões, cálculos).

## Onde está cada coisa

| Pasta | Conteúdo |
| --- | --- |
| `especificacoes/` | Um arquivo de texto por funcionalidade: campos, fórmulas, regras. **Leia antes de mudar algo.** |
| `docs/instalacao-servidor.md` | Passo a passo para instalar o servidor e ligar o Cloudflare Tunnel. |
| `CHANGELOG.md` | O que mudou em cada versão. |
| `gradle.properties` | Número da versão (`appVersionName` e `appVersionCode`). |
| `.github/workflows/build.yml` | Compila os APKs no GitHub a cada envio. |

## Como gerar uma nova versão

1. Atualize o arquivo de especificação da funcionalidade em `especificacoes/`.
2. Faça a mudança no código.
3. Aumente `appVersionName` e `appVersionCode` em `gradle.properties`.
4. Registre a mudança no `CHANGELOG.md`.
5. Envie para o GitHub. Os APKs aparecem em **Releases** em alguns minutos.

## Regras que não podem ser quebradas

- **Mesma chave de assinatura sempre** (`keystore/ensaios.jks`). Com outra chave, o Android não atualiza o app e é preciso desinstalar, perdendo os dados do celular.
- **Banco de dados só cresce:** mudança de estrutura entra como migração em `onUpgrade`, nunca apagando dados.
- **Novos tipos de registro** usam a tabela genérica `records` (conteúdo em JSON). Cadastros começam com `cad_`.
