# Histórico de versões

## 0.1.0 — Base

**App servidor**
- Servidor sempre ligado (serviço em primeiro plano), religa sozinho quando o celular reinicia.
- Cadastro de usuários com número, senha, perfis prontos e permissões individuais.
- Bloqueio de usuário (encerra as sessões dele na hora).
- Histórico de alterações: quem criou, alterou ou excluiu cada registro, e quando.
- Tela de status com endereço local, endereço pela internet (Cloudflare) e contadores.

**App cliente**
- Tela de login com engrenagem para configurar e testar a conexão com o servidor.
- Funciona sem internet: tudo é salvo no celular e enviado depois.
- Sincronização por diferença, em segundo plano a cada 15 minutos e logo depois de salvar.
- Cadastro de clientes (primeiro dado sincronizado, para testar o sistema).
- Troca de senha pelo próprio usuário.
