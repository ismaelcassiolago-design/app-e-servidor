# Histórico de versões

## 0.2.0 — Cadastros e segmentos

**App cliente**
- Tela de Cadastros com: clientes (com os parâmetros mínimos e máximos padrão de cada cliente), rodovias, faixas, frascos e areia, cilindros de Proctor, bandejas e lonas, veículos.
- Segmentos: data, cliente, rodovia, serviço/camada, posição inicial e final em km+m (o "+" entra sozinho), lado, faixa e largura.
- Extensão e área calculadas na hora; lista agrupada por dia com o total de extensão do dia.
- Tela de detalhes do segmento, com edição e exclusão conforme as permissões.
- Campos numéricos abrem o teclado numérico; datas com calendário; listas de escolha com busca.

**App servidor**
- Histórico mostra o tipo e um resumo do registro (ex.: "BR-163 · 168+340 a 168+760 · Faixa 1").

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
