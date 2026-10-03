# Números, arredondamento e número de série

## Separador decimal
- **Ponto** só nas massas específicas (densidades): `2.100`.
- **Vírgula** em todos os outros números: `8,4%`, `512,96 g`, `5,419 kg/m²`.

## Arredondamento (só na exibição e nos relatórios; cálculos usam o valor completo)
| Valor | Formato |
| --- | --- |
| Massas digitadas | Como digitado |
| Massas calculadas | Precisão total |
| Umidade | 1 casa |
| Volume | 0 casas (cm³) |
| Massa específica | 3 casas, ponto após o primeiro dígito (2.100) |
| Taxa de aplicação | 3 casas (padrão) ou 4 casas — escolhido nos padrões do app |
| Grau de compactação | 1 casa (padrão), 2 ou 3 — escolhido nos padrões do app |

O servidor guarda o valor completo e as casas usadas em cada ensaio; aceita qualquer uma das opções.

## Entrada de dados
- Salvamento automático a cada campo (rascunho até concluir; sincroniza depois de concluído).
- Teclado numérico abre sozinho nos campos de número.
- Botão "próximo" do teclado pula para o campo seguinte.

## Número de série
- Formato `TIPO-ANO-USUÁRIO-SEQUÊNCIA`, ex.: `IS-2026-03-0127`.
- Prefixos: IS (in situ), PR (Proctor), TA (taxa de aplicação), UM (umidade inicial), RE (resíduo).
- Gerado no celular (funciona offline, não repete entre usuários).
- **A contagem nunca reinicia**: só o ano muda (último de 2026 = 0127 → primeiro de 2027 = 0128).
- Por dentro, cada registro também tem um UUID.
