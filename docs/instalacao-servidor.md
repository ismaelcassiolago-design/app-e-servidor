# Instalação do servidor e da conexão pela internet

Tempo estimado: 1 hora, feito uma única vez. Custo: R$ 40 por ano (domínio). O túnel da Cloudflare é gratuito.

## Parte 1 — App servidor

1. No celular servidor, instale o APK `ensaios-servidor-x.y.z.apk` (página **Releases** do repositório).
2. Abra o app e permita as notificações.
3. Toque em **Abrir economia de bateria** e marque o app como **Não otimizar** (ou "Sem restrições").
4. Na aba **Usuários**, crie os usuários (nome, usuário, senha e perfil).
5. Deixe o celular sempre na tomada e no Wi-Fi (ou com chip de dados).

Teste na mesma rede: no app cliente, engrenagem → endereço `http://<IP mostrado no servidor>:8080` → **Testar conexão**.

## Parte 2 — Domínio

1. Registre um domínio `.com.br` em registro.br (ex.: `suaempresaensaios.com.br`).
2. Crie uma conta gratuita em cloudflare.com e adicione o domínio (plano **Free**).
3. A Cloudflare mostra dois *nameservers*. No registro.br, em **DNS** do domínio, troque os servidores DNS pelos dois da Cloudflare.
4. Aguarde a Cloudflare indicar o domínio como **Ativo** (de minutos a algumas horas).

## Parte 3 — Túnel na Cloudflare

1. No painel da Cloudflare, abra **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel**.
2. Escolha **Cloudflared**, dê o nome `ensaios` e avance.
3. A tela mostra um comando com um **token** longo (começa com `eyJ`). Copie só o token e guarde.
4. Na etapa de rota pública (*Public hostname*), preencha:
   - Subdomain: `ensaios`
   - Domain: o seu domínio
   - Service: tipo **HTTP**, URL `localhost:8080`
5. Salve. O endereço dos clientes será `https://ensaios.<seu domínio>`.

## Parte 4 — Túnel no celular servidor (Termux)

1. Instale o **F-Droid** (f-droid.org) e, por ele, os apps **Termux** e **Termux:Boot**. (Não use o Termux da Play Store, que está desatualizado.)
2. Abra o Termux e digite:

```
pkg update -y
pkg install -y cloudflared termux-api
```

3. Crie o script que liga o túnel (troque `SEU_TOKEN` pelo token copiado):

```
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/tunel.sh << 'FIM'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
cloudflared tunnel --no-autoupdate run --token SEU_TOKEN
FIM
chmod +x ~/.termux/boot/tunel.sh
```

4. Abra o app **Termux:Boot** uma vez (isso ativa o início automático).
5. Ligue o túnel agora: `~/.termux/boot/tunel.sh` (deixe o Termux aberto em segundo plano).
6. Tire o **Termux** e o **Termux:Boot** da economia de bateria, como fez com o app servidor.
7. No app servidor, aba **Servidor** → campo **Endereço pela internet**: `https://ensaios.<seu domínio>` → **Salvar**.

## Parte 5 — Teste final

1. Num celular de campo **com dados móveis (Wi-Fi desligado)**, instale o app cliente.
2. Engrenagem → endereço `https://ensaios.<seu domínio>` → **Testar conexão** → deve aparecer "Conectado".
3. Entre com um usuário criado na Parte 1.
4. Cadastre um cliente de teste e confira na aba **Histórico** do servidor.

## Se algo não funcionar
- "Sem conexão": confira se o servidor está LIGADO e se o Termux está aberto com o túnel rodando.
- Painel da Cloudflare mostra o túnel como **Healthy** quando o celular está conectado.
- Reiniciou o celular: o servidor e o túnel ligam sozinhos (se a Parte 4, passo 4, foi feita).
