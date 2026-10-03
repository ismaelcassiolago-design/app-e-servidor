# Taxa de aplicação (TA)

Base: ficha Neovia CQ 05. Tipos: cimento, cal, agregado, imprimação (escolhido no ensaio).

## Cabeçalho
Obra, trecho, posição, camada, medição, período, pista/faixa.

## Por linha
Data, km inicial, km final, faixa, bandeja (cadastro).

| Coluna | Campo | Origem |
| --- | --- | --- |
| A | Tara da bandeja | Digitado |
| C | Tara + material | Digitado |
| D | Peso do material | C − A |
| Área | Área da bandeja/lona (m²) | Cadastro |
| kg/m² | Taxa | D (kg) / área |
| L/m² | Taxa (imprimação) | kg/m² / densidade do ligante |
| Resíduo | Taxa residual (imprimação) | L/m² × teor de resíduo / 100 |

## Taxa de projeto (cimento)
Do Proctor do segmento (mesmo critério de posição do in situ):

Taxa de projeto (kg/m²) = (1 × 1 × e × γs) / 100 × %cimento
- e = espessura da camada (m); γs em kg/m³ (2.100 g/cm³ = 2100 kg/m³).
- Exemplo: e = 0,20; γs = 2100; 3% → 420 / 100 × 3 = 12,600 kg/m².

## Casas decimais
3 por padrão (5,419); opção de 4 (5,4190) nos padrões do app.

## Status
Diferença entre taxa aplicada e de projeto conforme os parâmetros do cliente.
