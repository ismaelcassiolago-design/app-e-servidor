# Usuários, permissões e histórico

## Usuários
- Criados, editados e bloqueados só no app servidor.
- Campos: nome, usuário (login), senha, número (automático: 01, 02...), administrador, ativo, permissões.
- O número do usuário entra no número de série dos ensaios.
- Senhas guardadas com PBKDF2-SHA256 (nunca em texto).
- Bloquear um usuário encerra as sessões dele na hora.
- O usuário pode trocar a própria senha no app cliente.
- Quem já entrou uma vez continua usando o app sem internet.
- Se outro usuário entrar no mesmo celular, os dados locais são limpos e baixados de novo.

## Permissões
| Código | O que libera |
| --- | --- |
| ver | Ver ensaios e segmentos de todos (sem ela, só os próprios e os cadastros) |
| criar | Lançar ensaios e segmentos |
| editar_proprios | Alterar o que o próprio usuário lançou |
| editar_outros | Alterar lançamentos de outros |
| excluir | Mandar para a lixeira |
| lixeira | Ver e restaurar da lixeira |
| editar_cadastros | Clientes, rodovias, faixas, calibrações, veículos, parâmetros |
| concluir_viga | Marcar a viga Benkelman de 7 dias como concluída |
| relatorios | Gerar e compartilhar PDF, imagem, texto e Excel |

Administrador tem todas. Perfis prontos: Laboratorista, Encarregado, Administrador (ver `shared/.../Permissions.kt`).
A regra oficial é conferida no servidor (`WriteRules.check`); o cliente usa a mesma regra só para esconder botões.

## Histórico
- Toda alteração aceita gera uma linha: registro, tipo, usuário, ação (criou, alterou, excluiu, restaurou), conteúdo antes e depois, data e hora.
- Visível na aba Histórico do servidor.
