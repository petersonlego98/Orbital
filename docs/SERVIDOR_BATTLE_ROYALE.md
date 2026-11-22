# Battle Royale 8x8 multiplayer

O modo 8x8 pode ser jogado online com **8 jogadores humanos**, um por time, sem IAs e sem recurso gratuito. Usa a pilha ArcNet já existente (conexão, mundo e autoridade de input) — não precisa do relay Cloudflare.

## Subir o servidor

No PC que vai hospedar:

```bash
# com as flags autoritativas (recomendado)
java -Dorbital.authoritative=1 -Dorbital.eventlog=1 -jar server.jar
```

No console do servidor:

```
host-br
```

O servidor carrega o mapa 8x8 (512x512, 64 cores, 8 times), aplica regras sem IA e **pausa a partida até 8 jogadores conectarem**. O 9º jogador é recusado.

## Entrar na partida

Cada jogador usa **Juntar-se ao Jogo** no menu e informa o IP do servidor. O time é atribuído automaticamente na ordem de conexão (um dos oito: `sharded`, `crux`, `malis`, `green`, `blue`, `neoplastic`, `Team 7`, `Team 8`). Reconectar com o mesmo UUID devolve o mesmo time.

## Regras

- `attackMode = true` → vitória do último time com core vivo.
- `pvp = true` → nenhum time é tratado como IA pelo motor.
- `rtsAi = false` e `infiniteResources = false` para os 8 times.
- O mapa é gerado no servidor e enviado aos clientes; todos jogam o mesmo terreno (seed fixa `20261120`).

## Diferença do modo local

O botão do menu **Battle Royale 8x8** continua funcionando como antes: 1 jogador + 7 IAs, sem servidor. O multiplayer só é ativado pelo comando `host-br`.
