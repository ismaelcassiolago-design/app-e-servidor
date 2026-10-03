# Cadastros

Tudo que se repete é cadastrado uma vez e escolhido numa lista (com busca e os últimos usados no topo).
Tipos de registro começam com `cad_`. Editar exige a permissão `editar_cadastros`.

| Cadastro | Tipo | Campos (JSON) | Usado em |
| --- | --- | --- | --- |
| Clientes | cad_cliente | nome, contrato, logo, parametros (padrão do cliente) | Segmento, logo nos relatórios, limites |
| Rodovias | cad_rodovia | nome (ex.: BR-163), trecho, cliente_id | Segmento |
| Faixas | cad_faixa | nome (ex.: Faixa 1, Faixa 2, Acostamento) | Segmento |
| Frasco e areia | cad_frasco | identificacao, massa_esp_areia (g/cm³), peso_funil_placa (g), data_calibracao | In situ (linhas 4 e 6) |
| Cilindro de Proctor | cad_cilindro | identificacao, massa (g), volume (cm³) | Proctor |
| Bandeja ou lona | cad_bandeja | identificacao, area (m²) | Taxa de aplicação |
| Veículos | cad_veiculo | placa | Ensaio de emulsão |

## Parâmetros (mínimos e máximos)
- Digitados à mão e salvos como padrão **de cada cliente**; podem ser alterados depois.
- Cada ensaio guarda o limite e a calibração usados no dia. Mudar o padrão ou recalibrar não altera ensaios antigos.
- Resíduo mínimo da RR-2C: padrão sugerido 67% (conferir com o contrato).

## Situação na v0.1
Só **Clientes** (nome e contrato), para testar a sincronização. O resto entra na v0.2.
