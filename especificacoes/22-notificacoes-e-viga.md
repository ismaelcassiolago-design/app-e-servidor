# Notificações e viga Benkelman

## Menu de notificações (o usuário escolhe)
- Novo ensaio (por tipo)
- Novo segmento
- Ensaio ou segmento alterado
- Ensaio fora dos parâmetros
- Só de determinados usuários, clientes ou rodovias
- Viga Benkelman pendente

Push pelo Firebase Cloud Messaging (gratuito). O push leva só o aviso de novidade; os dados vêm do servidor próprio.

## Viga Benkelman (o app só controla o prazo; não lança leituras)
1. Todo dia com segmento lançado é dia de produção e gera pendência de viga para 7 dias depois.
2. No dia anterior ao prazo: push "ensaio de viga tem que ser feito", com a data da produção e os segmentos (rodovia e posições). Recebe quem ativou esse aviso.
3. Os dias de produção mostram "Viga de 7 dias pendente" até ser marcada como concluída.
4. Só marca como concluída quem tem a permissão `concluir_viga`.
