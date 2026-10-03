# Histórico de versões

## 0.3.0 — Visual novo, in situ e Proctor

**App cliente**
- Visual novo: barra colorida no topo, barra de abas embaixo (Início, Produção, Relatórios, Cadastros), cartões e etiquetas de status.
- Três paletas de cor nas Configurações (verde, laranja, vermelho e grafite) e botão de sol forte (☀) no cabeçalho de todas as telas.
- Tela inicial com produção e ensaios do dia e os botões Dias de produção, Relatórios gerados, Últimos resumos e Cadastros.
- Produção organizada em Mês → Dias → Segmentos → Ensaios. Mês e dia são adicionados à mão.
- Segmento passa a ter pista e faixa (ou acostamento). O lado (LD, LE, eixo) fica em cada ensaio; taxa e resíduo terão também "Inteiro".
- Novo cadastro de pistas.
- Ensaio in situ (frasco de areia) com a numeração da ficha CQ 06, umidade por cápsula, massa-alvo de U2, escolha automática do Proctor mais próximo, grau de compactação e APROVADO/REPROVADO pelos limites do cliente.
- Ensaio Proctor de um ponto com cilindro do cadastro.
- Número de série automático (ex.: IS-2026-01-0001), que nunca reinicia.
- Ícones desenhados para cada ensaio: frasco de areia (in situ), cilindro e soquete (Proctor), caminhão (taxa), gota na cápsula (umidade) e panela com espátula (resíduo).
- Rascunho salvo sozinho enquanto digita; só é enviado ao servidor ao tocar em "Concluir e enviar".
- Configurações: casas decimais da taxa (3 ou 4) e do grau de compactação (1, 2 ou 3).

**App servidor**
- Mesmo visual novo e escolha da cor do app.

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
