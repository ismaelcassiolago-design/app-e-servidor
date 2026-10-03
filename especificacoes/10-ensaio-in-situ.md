# Ensaio in situ — frasco de areia (IS)

Base: DNER-ME 092/94, ficha Neovia CQ 06. Até 6 furos por folha no PDF padrão Neovia.

## Cabeçalho
Rodovia, trecho, camada, segmento, operador, data, pista, faixa (vêm do segmento).

## Por furo
Furo, posição (km+m), camada, posição (LD/LE/eixo), frasco (do cadastro), Proctor de referência.

| Linha | Campo | Origem |
| --- | --- | --- |
| 1 | Peso do frasco antes | Digitado |
| 2 | Peso do frasco depois | Digitado |
| 3 | Peso da areia deslocada | 1 − 2 |
| 4 | Peso da areia no funil e placa | Cadastro do frasco |
| 5 | Peso da areia na cavidade | 3 − 4 |
| 6 | Massa específica aparente da areia | Cadastro do frasco |
| 7 | Volume do solo | 5 / 6 |
| 8 | Peso do solo e recipiente | Digitado |
| 9 | Peso do recipiente | Digitado |
| 10 | Peso total do solo do furo | **(8 − 9) + (U1 − U3)**: o solo do recipiente mais a amostra úmida que foi para a cápsula da umidade (a ficha impressa diz "9 − 8") |

## Umidade de campo (só cápsula)
| Linha | Campo | Origem |
| --- | --- | --- |
| U1 | Cápsula + solo úmido | Digitado |
| U2 | Cápsula + solo seco | Digitado |
| U3 | Cápsula | Digitado |
| U4 | Água | U1 − U2 |
| U5 | Solo seco | U2 − U3 |
| U6 | Umidade (%) | U4 / U5 × 100 |

## Resultados
- γh = linha 10 / linha 7
- γs = γh / (1 + U6/100)
- GC = γs / γs,lab × 100
- γs,lab e umidade ótima vêm do Proctor do segmento; "Registro (laboratório)" = número de série do Proctor.

## Escolha do Proctor
1. Proctor do mesmo segmento na mesma posição; senão,
2. o da posição mais próxima;
3. a tela lista os Proctors do segmento (série, posição, γs) para conferir ou trocar.

## Massa-alvo da umidade
Tara da cápsula (U3) vazia conta como zero (vale para todas as umidades).
Assim que U1 e U3 são digitados, mostrar o alvo de U2 para a umidade = ótima − 2 pontos:

U2_alvo = U3 + (U1 − U3) / (1 + (h_ót − 2)/100)

Exemplo: U3 = 50,00 g; U1 = 550,00 g; h_ót = 10,0% → alvo 8,0% (ótima 8% → alvo 6%) → U2_alvo = 50,00 + 500,00/1,08 = 512,96 g.
Balança acima do alvo = umidade ainda acima de 8,0%.

## Status
Verde/vermelho conforme os parâmetros do cliente (ex.: GC mínimo).
