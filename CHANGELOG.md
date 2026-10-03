# Histórico de versões

## 0.4.2 — Totais do mês

- Botão "Dias de produção" passa a se chamar "Produção".
- Dentro do mês: extensão total produzida (m), área total (m²) e número de segmentos, atualizados a cada segmento lançado.
- Lista de meses e de dias mostra também a área em m².

## 0.4.1 — Bordos

- Lado do ensaio com as opções B.D (bordo direito) e B.E (bordo esquerdo), em todos os ensaios.

## 0.4.0 — Taxa, umidade, resíduo e PDFs

**App cliente**
- Ensaio de taxa de aplicação (cimento, cal, agregado ou imprimação): bandeja ou lona do cadastro com área e tara, pesos em gramas, taxa em kg/m². Cimento: taxa de projeto pelo Proctor (espessura em cm e % de cimento) e diferença com APROVADO/REPROVADO pelos limites do cliente. Imprimação: L/m² e taxa residual.
- Ensaio de umidade inicial para reciclagem (cápsula), com limites mínimo e máximo do cliente.
- Ensaio de resíduo por evaporação da emulsão (NBR 14376): veículo do cadastro ou placa digitada, peso da carga em toneladas, resíduo mínimo do cliente.
- Lado "Inteiro" na taxa e no resíduo.
- PDF do dia de produção (botão PDF no dia), em dois modelos:
  - Padrão do app: resumo do dia e tabela de ensaios por segmento, com APROVADO/REPROVADO.
  - Padrão Neovia: fichas CQ 06 (in situ, até 6 furos por folha), compactação (Proctor), CQ 05 (taxa) e tabelas de umidade e resíduo com o total de emulsão do dia.
- Aba Relatórios: lista dos PDFs gerados, com abrir, compartilhar (WhatsApp, e-mail) e excluir.

## 0.3.4 — Tara em gramas

- Tara das bandejas e lonas em gramas. Na taxa, o peso do material (g) é convertido para kg no cálculo da taxa em kg/m².

## 0.3.3 — Ajustes na umidade e nas bandejas

- Alvo da umidade do in situ: peso que a balança deve marcar para a umidade ficar 2 pontos abaixo da ótima do Proctor vinculado (ex.: ótima 8% → 6%). A tela mostra a ótima usada.
- Tara da cápsula vazia conta como zero (in situ e Proctor); o alvo aparece mesmo sem tara.
- Cadastro de bandejas e lonas com tara (kg), que será descontada no ensaio de taxa.

## 0.3.2 — Correção no in situ

- Linha 10 (peso do solo do furo) passa a somar a amostra úmida da umidade: (8 − 9) + (U1 − U3). A tela mostra as duas parcelas e o total.

## 0.3.1 — Correção

- Segmentos lançados antes da versão 0.3 (sem dia de produção) aparecem num aviso em "Dias de produção", com o botão "Organizar automaticamente", que cria o mês e o dia de cada um e coloca os segmentos dentro.

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
