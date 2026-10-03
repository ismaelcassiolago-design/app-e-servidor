# Segmentos

O segmento é o trecho de serviço do dia; os ensaios são lançados dentro dele.

## Campos
data (vem do dia), cliente, rodovia, serviço ou camada, posição inicial, posição final, pista, faixa ou acostamento, largura da faixa (m), responsável, observações.
O segmento **não tem lado**: LD, LE, B.D (bordo direito), B.E (bordo esquerdo) e eixo ficam em cada ensaio (taxa e resíduo têm também "Inteiro").

## Organização
Produção → **Mês** (adicionado à mão) → **Dias** (adicionados à mão, dentro do mês) → **Segmentos** → **Ensaios**.
Tipos de registro: `producao_mes` (mes = "2026-10"), `producao_dia` (data, mes_id), `segmento` (dia_id).

## Posição (estaqueamento por km)
- Formato **km + metros**: `168+340` = km 168 e 340 m. Guardar internamente em metros (168340).
- Digitação com teclado numérico; o "+" entra sozinho depois do km.

## Cálculos automáticos
- Extensão = posição final − posição inicial (ex.: 168+340 a 168+760 = 420 m).
- Total do dia = soma das extensões de todos os segmentos do dia de produção.
- Área = extensão × largura da faixa.
- Cada segmento tem **uma só faixa**; se a faixa mudar, o usuário lança outro segmento.

## Tela do segmento
Todos os ensaios do segmento numa tela, cada um com número de série, posição, faixa, resultado e status (verde/vermelho).

## Validação
Aviso quando a posição de um ensaio cai fora do intervalo do segmento.

## Dia de produção
Todo dia com pelo menos um segmento lançado é dia de produção (gera a pendência de viga Benkelman — ver 22).
