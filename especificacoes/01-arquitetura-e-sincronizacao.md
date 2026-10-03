# Arquitetura e sincronização

## Apps
- **Servidor** (Android, `br.ensaios.servidor`): celular sempre na tomada. Roda um servidor HTTP (Ktor) na porta 8080 dentro de um serviço em primeiro plano. Religa sozinho ao reiniciar o celular.
- **Cliente** (Android, `br.ensaios.cliente`): cerca de 7 usuários. Funciona offline; tudo é salvo no celular (SQLite) e sincronizado depois.
- Só Android. Não há versão para iPhone.

## Banco de dados
- Tabela genérica `records`: `id` (UUID), `type`, `data` (JSON), `deleted`, `seq`, `version`, `created_by/at`, `updated_by/at`.
- Novos tipos de ensaio não exigem mudar a estrutura do banco.
- Servidor tem também `users`, `tokens`, `history`, `meta`.

## Sincronização por diferença (`POST /api/sync`)
1. Cliente envia `sinceSeq` (último número recebido) e as alterações locais pendentes.
2. Servidor confere permissão de cada alteração, grava, dá um novo `seq` e registra no histórico.
3. Servidor devolve as alterações com `seq > sinceSeq` (no máximo 500 por vez, `hasMore` indica se há mais).
4. Alteração recusada volta com a versão atual do servidor, e o cliente desfaz a dele.
5. Conflito: vale a última alteração que chega ao servidor; a anterior fica no histórico.
6. Alteração local ainda não enviada nunca é sobrescrita pelo que vem do servidor.
- Respostas comprimidas (gzip).
- Fotos de outros usuários: só os dados da foto sincronizam; a imagem baixa sob pedido (v0.5).
- Em segundo plano: WorkManager a cada 15 min com internet, e logo depois de salvar algo.

## Conexão pela internet
- Cloudflare Tunnel com nome fixo + domínio `.com.br` (R$ 40/ano no Registro.br). Túnel gratuito.
- `cloudflared` roda no Termux do celular servidor apontando para `http://localhost:8080`.
- Clientes usam `https://ensaios.<dominio>`; na mesma rede Wi-Fi também funciona `http://<ip>:8080`.
- Guia: `docs/instalacao-servidor.md`.

## Endpoints
| Método | Caminho | Uso |
| --- | --- | --- |
| GET | /api/ping | Teste de conexão (nome e versão do servidor) |
| POST | /api/login | Usuário e senha → token |
| POST | /api/sync | Sincronização (exige token) |
| POST | /api/senha | Troca de senha pelo usuário (exige token) |
