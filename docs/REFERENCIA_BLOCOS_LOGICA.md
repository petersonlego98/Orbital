# Referência dos Blocos de Lógica — Orbital

Este documento descreve **todo o sistema de lógica** do Orbital: os blocos que podem
ser construídos (processadores, memórias, displays, etc.) e os **blocos de instrução**
usados dentro deles, com sintaxe, parâmetros e função.

> **Escopo:** lógica de jogo (herdada do Mindustry e mantida no fork). Não cobre
> detalhes internos de renderização, rede ou serialização além do necessário.
>
> **Fontes principais:**
> - `core/src/orbital/logic/LStatements.java` — definição das instruções
> - `core/src/orbital/logic/LExecutor.java` — execução das instruções
> - `core/src/orbital/logic/LAssembler.java` / `LParser.java` — compilação do código
> - `core/src/orbital/logic/GlobalVars.java` — variáveis globais e constantes
> - `core/src/orbital/logic/LCategory.java` — categorias do editor
> - `core/src/orbital/world/blocks/logic/*` — blocos de lógica construíveis
> - `core/src/orbital/content/Blocks.java` — atributos dos blocos (linhas ~6890–7040)

---

## 1. Como o sistema funciona

1. Um **processador** contém uma lista de instruções (o "código").
2. Cada instrução é executada em sequência; `@counter` guarda o índice da próxima.
3. O processador executa `instructionsPerTick` instruções por tick (com acúmulo de até
   5× esse valor — `maxInstructionScale = 5`).
4. Ao chegar ao fim do código, a execução reinicia do topo (loop), a menos que se use
   `stop`, `wait` ou saltos.
5. O processador pode ser **linkado** a blocos próximos, que recebem nomes automáticos
   (`cell1`, `message1`, `display1`, `switch1`, ...) e viram variáveis no código.
6. Loops podem ser controlados com `jump` e rótulos (`meuRotulo:`), ou pelo índice
   da instrução.
7. Instruções **privilegiadas** só funcionam em processadores de mundo
   (`world-processor`) e exigem editor/`allowEditWorldProcessors`.

### Limites gerais (`LogicBlock`)

| Limite | Valor |
|---|---:|
| Código não comprimido | 100 KiB (`maxByteLen = 1024 * 100`) |
| Código comprimido | 16.000 bytes (`maxCompressedLen`) |
| Links por processador | 6.000 (`maxLinks`) |
| Nome/tag do processador | 32 caracteres |
| Acúmulo máximo de instruções | 5× o IPT (`maxInstructionScale = 5`) |
| Rótulos de salto | 500 (`LParser.maxJumps`) |
| Instruções por linha | 16 tokens (`LParser.tokens`) |

---

## 2. Blocos de lógica construíveis

### 2.1 Resumo

| Bloco | Tamanho | IPT | Alcance de link | Extra |
|---|---:|---:|---:|---|
| `micro-processor` | 1×1 | 2 | 10 blocos | Versão barata |
| `logic-processor` | 2×2 | 8 | 22 blocos | Processador padrão |
| `hyper-processor` | 3×3 | 25 | 42 blocos | Consome criogênio |
| `world-processor` | 1×1 | 8 | ∞ | Privilegiado; até 1.000 IPT |
| `memory-cell` | 1×1 | — | — | 64 posições |
| `memory-bank` | 2×2 | — | — | 512 posições |
| `world-cell` | 1×1 | — | — | 512 posições, privilegiado |
| `switch` | 1×1 | — | — | Alterna ligado/desligado |
| `world-switch` | 1×1 | — | — | Privilegiado |
| `message` | 1×1 | — | — | Texto de até 400 caracteres |
| `reinforced-message` | 1×1 | — | — | 100 de vida, não é esmagado |
| `world-message` | 1×1 | — | — | Privilegiado |
| `logic-display` | 3×3 | — | — | Tela de 80 unidades |
| `large-logic-display` | 6×6 | — | — | Tela de 176 unidades |
| `tile-logic-display` | 1×1 | — | — | Tela modular |
| `canvas` | 2×2 | — | — | 12×12 pixels, 8 cores |
| `large-canvas` | 3×3 | — | — | 24×24 pixels, 16 cores |

> IPT = instruções por tick. O alcance em blocos é `range / 8`, onde o padrão de
> `LogicBlock` é 80 px (10 blocos). `world-processor` usa `Float.MAX_VALUE`.

### 2.2 Processadores

#### `micro-processor`
- Executa 2 instruções/tick.
- 1×1, barato, bom para automações simples.
- Alcance de link de 10 blocos.
- Receita: 90 cobre, 50 chumbo, 50 silício.

#### `logic-processor`
- Executa 8 instruções/tick.
- 2×2, alcance de 22 blocos.
- Receita: 320 chumbo, 80 silício, 60 grafite, 50 tório.

#### `hyper-processor`
- Executa 25 instruções/tick.
- 3×3, alcance de 42 blocos.
- Consome 0,08 criogênio/s.
- Receita: 450 chumbo, 150 silício, 75 tório, 50 liga de surto.

#### `world-processor`
- Processador privilegiado para mapas/editor.
- Começa com 8 IPT e pode chegar a **1.000 IPT** (`maxInstructionsPerTick`).
- Alcance infinito, não pode ser destruído, não é alvo.
- Só é editável/colocável conforme `accessible()`:
  editor, playtest ou `allowEditWorldProcessors`.
- Receita: apenas no editor (`BuildVisibility.worldProcessorOnly`).

**Funções comuns a todos os processadores**
- `control` blocos linkados (ligar/desligar, atirar, configurar).
- `read`/`write` em memórias e variáveis de outros processadores.
- `draw`/`drawflush` em displays.
- `print`/`printflush` em message blocks.
- Controlar unidades com `ubind`/`ucontrol`.
- Receber links como variáveis (`cell1`, `turret1`, ...) e por índice (`getlink`).

### 2.3 Memórias

#### `memory-cell` / `memory-bank` / `world-cell`
- Armazenam números e objetos em posições indexadas a partir de `0`.
- `memory-cell`: 64 posições, 1×1.
- `memory-bank`: 512 posições, 2×2.
- `world-cell`: 512 posições, versão privilegiada de mundo.
- Todas as posições começam com `0`.
- Acesso pela instrução `read`/`write` ou pelo nome do link (`cell1`, `bank1`).
- `memoryCapacity` pode ser lido com `sensor`.
- Não podem ser carregadas/derrubadas (pickup) por causa do volume de dados.

### 2.4 Interruptores

#### `switch` / `world-switch`
- Bloco configurável que alterna `enabled` (ligado/desligado).
- O estado pode ser lido com `sensor ... @enabled` e alterado com
  `control enabled switch1 1/0`.
- `world-switch` é a variante privilegiada, indestrutível e não-alvo.
- Toca som ao ser clicado.

### 2.5 Blocos de mensagem

#### `message` / `reinforced-message` / `world-message`
- Guardam um texto exibido no mapa e servem de destino para `printflush`.
- Limite: 400 caracteres e 24 quebras de linha (`maxTextLength`/`maxNewlines`).
- `print`/`printchar`/`format`/`localeprint` alimentam o buffer de texto;
  `printflush message1` envia o buffer para o bloco.
- `reinforced-message` tem 100 de vida e `crushFragile = true`.
- `world-message` é a variante privilegiada.

### 2.6 Displays

#### `logic-display` / `large-logic-display` / `tile-logic-display`
- Recebem comandos de desenho via `draw` e `drawflush display1`.
- Resolução lógica: `displaySize` (80 no normal, 176 no grande).
- Suportam polígonos de até 25 lados (`maxSides = 25`).
- Escala de `scale` usa passo de 0,05 (`scaleStep`).
- Podem desenhar outras telas (tipo de conteúdo `displayDrawType = 30`).
- O buffer de desenho é enviado apenas no `drawflush`.

#### `canvas` / `large-canvas`
- Exibem uma imagem estática com paleta fixa, editável na interface.
- `canvas`: 12×12 pixels e 8 cores.
- `large-canvas`: 24×24 pixels e 16 cores (paleta Woodspark).
- Não são controlados por instruções; são editáveis como um bloco configurável.

---

## 3. Variáveis e constantes globais

### 3.1 Variáveis do processador

| Variável | Função |
|---|---|
| `@counter` | Índice da próxima instrução (pode ser lido/escrito) |
| `@unit` | Unidade atualmente vinculada por `ubind` |
| `@this` | O próprio processador |
| `@thisx`, `@thisy` | Coordenadas do processador |
| `@links` | Quantidade de links válidos |
| `@ipt` | Instruções por tick atuais |
| `@queries` | Resultado da última instrução `query` (privilegiado) |
| `@wait` | Usado por `message` para aguardar a mensagem anterior |

### 3.2 Tempo e mapa

| Variável | Função |
|---|---|
| `@time` | Tempo desde o início, em milissegundos (`tick / 60 * 1000`) |
| `@tick` | Tick atual |
| `@second` | Segundos desde o início |
| `@minute` | Minutos desde o início |
| `@waveNumber` | Número da onda atual |
| `@waveTime` | Tempo até a próxima onda, em segundos |
| `@mapw`, `@maph` | Largura e altura do mapa em tiles |

### 3.3 Rede e cliente

| Variável | Função |
|---|---|
| `@server` | `1` se for servidor ou single-player |
| `@client` | `1` se for cliente conectado |
| `@clientLocale` | Idioma do cliente (privilegiado) |
| `@clientUnit` | Unidade do jogador (privilegiado) |
| `@clientName` | Nome do jogador (privilegiado) |
| `@clientTeam` | ID do time do jogador (privilegiado) |
| `@clientMobile` | `1` se o cliente for móvel (privilegiado) |
| `@clientMusicPlaying` | `1` se há música tocando (privilegiado) |
| `@clientCurrentMusic` | Nome da música atual (privilegiado) |

### 3.4 Constantes

| Constante | Valor |
|---|---|
| `true` / `false` | `1` / `0` |
| `null` | valor nulo |
| `@pi`, `π` | π |
| `@e` | número de Euler |
| `@degToRad`, `@radToDeg` | fatores de conversão |
| `@ctrlProcessor` | controlador = processador (`1`) |
| `@ctrlPlayer` | controlador = jogador (`2`) |
| `@ctrlCommand` | controlador = comando (`3`) |

Também são constantes:
- conteúdo: `@copper`, `@conveyor`, `@dagger`, `@sharded`, `@rain`, `@status-wet`, ...
- sensores: `@health`, `@x`, `@totalItems`, ... (todos os nomes de `LAccess`);
- cores: `@colorRed`, `@colorBlue`, ...
- sons: `@sfx-shoot`, ...
- alinhamentos: `@center`, `@topLeft`, `@bottomRight`, ...
- contagens: `@itemCount`, `@liquidCount`, `@unitCount`, `@blockCount`.

### 3.5 Literais

- Números: `10`, `-5.5`, `0xFF`, `0b1010`, `%ff0000`, `%[red]`.
- Strings: `"texto"` com escapes `\n`, `\"`, `\\`, `\uXXXX`.
- Cores: `%RRGGBB` ou `%RRGGBBAA`.
- Constantes de conteúdo/bloco/unidade: prefixo `@`.
- Comentários: `#` até o fim da linha.
- Separador alternativo de instruções: `;`.

---

## 4. Blocos de instrução (statements)

As instruções são organizadas em 7 categorias (`LCategory`):

| Categoria | Cor/ícone | Uso |
|---|---|---|
| `io` | Entrada e saída | Memória, buffers de texto/desenho |
| `block` | Controle de blocos | Blocos linkados, radar, sensores |
| `operation` | Operações | Variáveis, matemática, cores |
| `control` | Fluxo | Saltos, espera, parada, taxa |
| `unit` | Unidades | Vincular e comandar unidades |
| `world` | Mundo | Regras, spawn, clima, efeitos, áudio |
| `unknown` | Sem categoria | Instruções inválidas |

> Nas tabelas abaixo, campos em **negrito** são fixos; os demais são variáveis,
> números, strings ou nomes de links. Instruções marcadas com 🔒 são privilegiadas
> (somente `world-processor`).

### 4.1 Entrada e saída (`io`)

#### `read` — Ler
```
read <saida> <alvo> <endereco>
```
Lê um número de uma memória linkada, de uma variável de outro processador ou de uma
posição de memória. Também pode ler variáveis internas de outro processador pelo nome.

#### `write` — Escrever
```
write <valor> <alvo> <endereco>
```
Escreve um número em uma memória linkada ou em uma variável de outro processador.

#### `draw` — Desenhar
```
draw <tipo> <x> <y> <p1> <p2> <p3> <p4>
```
Adiciona uma operação ao buffer de desenho. Nada aparece até `drawflush`.
Tipos e parâmetros estão na seção 5.1.

#### `print` — Imprimir
```
print <valor>
```
Adiciona texto ao buffer de impressão. Não exibe nada até `printflush`.
Aceita strings, números e ícones de conteúdo (`@copper`, `@dagger`, ...).

#### `printchar` — Imprimir caractere
```
printchar <codigo>
```
Adiciona um caractere UTF-16 (ou ícone de conteúdo) ao buffer de impressão.
Ex.: `printchar 65` adiciona `A`.

#### `format` — Formatar
```
format <valor>
```
Substitui o próximo marcador `{0}`–`{9}` no buffer de texto pelo valor.
Se o marcador for inválido, não faz nada.

### 4.2 Controle de blocos (`block`)

#### `drawflush` — Enviar desenho
```
drawflush <display>
```
Envia o buffer de desenho para um display linkado.

#### `printflush` — Enviar texto
```
printflush <message>
```
Envia o buffer de texto para um bloco de mensagem linkado.

#### `getlink` — Obter link
```
getlink <saida> <indice>
```
Obtém o bloco linkado pelo índice (começa em `0`) e guarda em uma variável.

#### `control` — Controlar bloco
```
control <tipo> <alvo> <p1> <p2> <p3> <p4>
```
Controla um bloco linkado. Tipos:
- `enabled <to>` — liga/desliga;
- `shoot <x> <y> <shoot>` — mira e atira;
- `shootp <unit> <shoot>` — atira em uma unidade;
- `config <to>` — define a configuração;
- `color <to>` — define a cor (lâmpadas, etc.).

#### `radar` — Radar de bloco
```
radar <alvo1> <alvo2> <alvo3> <ordenacao> <bloco> <sentido> <saida>
```
Procura unidades ao redor de um bloco linkado. Os três primeiros campos são filtros
(`enemy`, `ally`, `player`, `attacker`, `flying`, `boss`, `ground`, `any`), seguidos
do critério de ordenação (`distance`, `health`, `shield`, `armor`, `maxHealth`),
do bloco de origem, do sentido (`1` = menor distância / maior valor, `-1` = inverso)
e da variável de saída.

#### `sensor` — Sensor
```
sensor <saida> <alvo> <propriedade>
```
Obtém uma propriedade de um bloco ou unidade, por exemplo:
```
sensor result turret1 @health
sensor result block1 @totalItems
```
A lista completa de propriedades está na seção 5.2.

### 4.3 Operações (`operation`)

#### `set` — Definir
```
set <destino> <valor>
```
Atribui um valor a uma variável. Strings, números, links e objetos são aceitos.

#### `op` — Operação
```
op <operador> <destino> <a> <b>
```
Realiza uma operação matemática, lógica ou bit a bit. Operadores unários usam apenas
`<a>` (`op abs result a`); operações de função usam dois argumentos
(`op max result a b`). Lista completa na seção 5.3.

#### `select` — Selecionar
```
select <destino> <condicao> <valorA> <valorB> <seVerdadeiro> <seFalso>
```
Atribui um dos dois valores conforme a condição. Ex.:
```
select result lessThan x 10 1 0
```

#### `lookup` — Consultar conteúdo
```
lookup <tipo> <saida> <id>
```
Converte um ID de conteúdo em objeto. Tipos: `item`, `liquid`, `block`, `unit`, `team`.
Para o inverso, use `sensor ... @id`.

#### `packcolor` — Empacotar cor
```
packcolor <saida> <r> <g> <b> <a>
```
Empacota componentes RGBA em `[0, 1]` em um único número para desenho/regras.

#### `unpackcolor` — Desempacotar cor
```
unpackcolor <r> <g> <b> <a> <cor>
```
Extrai os componentes RGBA de uma cor empacotada.

### 4.4 Fluxo de controle (`control`)

#### `wait` — Esperar
```
wait <segundos>
```
Pausa a execução por um tempo. O tempo restante é preservado em saves.

#### `stop` — Parar
```
stop
```
Interrompe a execução do processador até que ele seja reativado/reiniciado.

#### `end` — Fim
```
end
```
Salta para o topo do código.

#### `jump` — Salto condicional
```
jump <destino> <condicao> <valor> <comparacao>
```
Salta para um índice de instrução ou rótulo se a condição for verdadeira.
Rótulos são declarados como `nome:` em uma linha própria. Ex.:
```
inicio:
op add i i 1
jump inicio lessThan i 10
```
Condições: `==`, `not` (≠), `<`, `<=`, `>`, `>=`, `===`, `always`.

#### `setrate` — Definir taxa
```
setrate <instrucoesPorTick>
```
Define o IPT do processador. Em processadores normais só é possível **reduzir**
(limite = IPT base); em `world-processor` pode chegar a 1.000.

### 4.5 Unidades (`unit`)

#### `ubind` — Vincular unidade
```
ubind <tipo>
```
Vincula a próxima unidade do tipo especificado e a guarda em `@unit`.
Ex.: `ubind @poly`.

#### `ucontrol` — Controlar unidade
```
ucontrol <comando> <p1> <p2> <p3> <p4> <p5>
```
Comanda a unidade vinculada. Comandos e parâmetros na seção 5.4.

#### `uradar` — Radar de unidade
```
uradar <alvo1> <alvo2> <alvo3> <ordenacao> <sentido> <saida>
```
Como `radar`, mas usa `@unit` como origem (não recebe bloco de origem).

#### `ulocate` — Localizar
```
ulocate <tipo> <grupo> <inimigo> <minerio> <outX> <outY> <encontrado> <bloco>
```
Localiza uma posição ou construção no mapa usando a unidade vinculada.
Tipos: `ore`, `building`, `spawn`, `damaged`. Para `building`, o grupo é um
`BlockFlag` (core, storage, generator, turret, factory, repair, battery, reactor,
drill, shield) e `inimigo` indica se procura o time inimigo.

### 4.6 Mundo (`world`) — privilegiado 🔒

#### `query` — Consultar área
```
query <forma> <tipo> <time> <x> <y> <w> <h>
```
Consulta unidades/construções em uma área circular ou retangular. O resultado fica em
`@queries`; leia com `read` e use `sensor @size`.

#### `getblock` — Ler tile
```
getblock <camada> <saida> <x> <y>
```
Lê uma camada do tile: `floor`, `ore`, `block` ou `building`.

#### `setblock` — Alterar tile
```
setblock <camada> <bloco> <x> <y> <time> <rotacao>
```
Define chão, minério ou bloco em uma posição. Camadas alteráveis: `floor`, `ore`,
`block`. Time e rotação só se aplicam a `block`.

#### `spawn` — Criar unidade
```
spawn <tipo> <x> <y> <rotacao> <time> <saida> <efeito>
```
Cria uma unidade no mapa. A saída recebe a unidade criada.

#### `bullet` — Criar projétil
```
bullet <saida> <origem> <indice> <x> <y> <rotacao> <time> <dono> <dano> <velScl> <vidaScl> <aimX> <aimY>
```
Cria um projétil a partir de um tipo de arma/unidade. `origem` é o tipo, `indice`
seleciona o projétil, e os fatores de escala permitem ajustar velocidade e vida.

#### `status` — Aplicar status
```
status <limpar> <efeito> <unidade> <duracao>
```
Aplica (`false`) ou remove (`true`) um efeito de status. Ex.:
`status false @status-wet unit 10`.

#### `weathersense` — Ler clima
```
weathersense <saida> <clima>
```
Retorna `1` se o clima estiver ativo.

#### `weatherset` — Definir clima
```
weatherset <clima> <estado>
```
Ativa ou desativa um clima.

#### `spawnwave` — Criar onda
```
spawnwave <x> <y> <natural>
```
Cria uma onda inimiga na posição. `natural` indica se conta como onda natural.

#### `setrule` — Alterar regra
```
setrule <regra> <valor> <p1> <p2> <p3> <p4>
```
Altera uma regra do jogo. Regras disponíveis:
`currentWaveTime`, `waveTimer`, `waves`, `wave`, `waveSpacing`, `waveSending`,
`attackMode`, `enemyCoreBuildRadius`, `dropZoneRadius`, `unitCap`, `mapArea`,
`lighting`, `canGameOver`, `ambientLight`, `unitLight`, `solarMultiplier`,
`dragMultiplier`, `ban`, `unban`, `pauseDisabled`, `musicVolume`, `buildSpeed`,
`unitHealth`, `unitBuildSpeed`, `unitMineSpeed`, `unitCost`, `unitDamage`,
`blockHealth`, `blockDamage`, `rtsMinWeight`, `rtsMinSquad`.
- `mapArea` usa `x y w h`.
- Regras por time usam `of` no primeiro parâmetro.
- `ban`/`unban` bloqueiam/desbloqueiam unidade ou bloco.

#### `message` — Exibir mensagem
```
message <tipo> <duracao> <sucesso>
```
Exibe o buffer de texto na tela. Tipos: `notify`, `announce`, `toast`, `mission`.
Se `sucesso` for `@wait`, aguarda a mensagem anterior terminar; caso contrário,
recebe `1`/`0` indicando se conseguiu exibir.

#### `cutscene` — Controlar câmera
```
cutscene <acao> <p1> <p2> <p3> <p4>
```
Ações: `active`, `pan`, `zoom`, `stop`, `shake`, `getHud`, `setHud`.
- `pan`: `x y velocidade`
- `zoom`: `nivel`
- `shake`: `intensidade duracao`
- `setHud`: `mostrar`
- `active`/`getHud`: `resultado`

#### `effect` — Criar efeito
```
effect <tipo> <x> <y> <tamanhoRotacao> <cor> <dados>
```
Cria um efeito visual. Os campos disponíveis dependem do efeito escolhido.

#### `explosion` — Criar explosão
```
explosion <time> <x> <y> <raio> <dano> <aereo> <terrestre> <perfurar> <efeito>
```
Cria uma explosão com dano em alvos aéreos/terrestres.

#### `fetch` — Buscar por índice
```
fetch <tipo> <saida> <time> <indice> <extra>
```
Busca unidades, núcleos, jogadores ou construções por índice.
Tipos: `unit`, `unitCount`, `player`, `playerCount`, `core`, `coreCount`, `build`,
`buildCount`. `extra` é o tipo de unidade/bloco quando aplicável.

#### `sync` — Sincronizar
```
sync <variavel>
```
Sincroniza uma variável pela rede. Limitado a 20 vezes por segundo por variável.

#### `clientdata` — Dados do cliente
```
clientdata <canal> <valor> <confiavel>
```
Envia dados para o cliente. Oculto do editor e requer `allowLogicData`.

#### `getflag` — Ler flag global
```
getflag <saida> <flag>
```
Lê uma flag global booleana.

#### `setflag` — Definir flag global
```
setflag <flag> <valor>
```
Define uma flag global que todos os processadores podem ler.

#### `setprop` — Definir propriedade
```
setprop <propriedade> <alvo> <valor>
```
Define uma propriedade de unidade/bloco. Propriedades graváveis (seção 5.2):
`x`, `y`, `velocityX`, `velocityY`, `rotation`, `speed`, `armor`, `health`,
`shield`, `team`, `flag`, `totalPower`, `payloadType`, `bulletTime`,
`bulletLifetime`.

#### `playsound` — Tocar som
```
playsound <posicional> <id> <volume> <pitch> <pan> <x> <y> <limite>
```
Toca um som global ou posicional. `id` no formato `@sfx-nome`.

#### `playmusic` — Tocar música
```
playmusic <nome> <interromper>
```
Toca uma faixa pelo nome (ex.: `"game1"`). Com `interromper = true`, para a música
anterior; com `null` e `interromper = true`, para a música atual.

#### `setmarker` — Configurar marcador
```
setmarker <propriedade> <id> <p1> <p2> <p3>
```
Altera uma propriedade de um marcador criado com `makemarker`. Propriedades na
seção 5.6.

#### `makemarker` — Criar marcador
```
makemarker <tipo> <id> <x> <y> <substituir>
```
Cria um marcador lógico no mundo. Limite de 20.000 marcadores por mundo.

#### `localeprint` — Imprimir localizado
```
localeprint <chave>
```
Adiciona ao buffer de texto o valor de uma propriedade de locale do mapa.
Em dispositivos móveis, tenta primeiro a chave terminada em `.mobile`.

### 4.7 Sem categoria (`unknown`)

#### `noop`
Instrução inválida gerada quando o parser não reconhece a linha. Não faz nada.

#### `#` (comentário)
Desativado no momento (`//TODO broken`), mas linhas iniciadas por `#` continuam
funcionando como comentários no parser.

---

## 5. Tabelas de referência

### 5.1 Comandos de desenho (`draw`)

| Tipo | Parâmetros | Função |
|---|---|---|
| `clear` | `r g b` | Limpa a tela com a cor (0–255) |
| `color` | `r g b a` | Define a cor de desenho (0–255) |
| `col` | `cor` | Cor empacotada (virtual, expandida em `color`) |
| `stroke` | `largura` | Define a espessura da linha |
| `line` | `x y x2 y2` | Linha |
| `rect` | `x y largura altura` | Retângulo preenchido |
| `lineRect` | `x y largura altura` | Contorno de retângulo |
| `poly` | `x y lados raio rotacao` | Polígono preenchido |
| `linePoly` | `x y lados raio rotacao` | Contorno de polígono |
| `triangle` | `x y x2 y2 x3 y3` | Triângulo preenchido |
| `image` | `x y imagem tamanho rotacao` | Ícone de conteúdo ou outra tela |
| `print` | `x y alinhamento` | Desenha o buffer de texto (virtual) |
| `translate` | `x y` | Translada o sistema de coordenadas |
| `scale` | `x y` | Escala (passo de 0,05) |
| `rotate` | `angulo` | Rotaciona o sistema de coordenadas |
| `reset` | — | Restaura a transformação |

### 5.2 Propriedades de `sensor` e `setprop`

**Leitura (`sensor`)**
- Inventário/energia: `totalItems`, `firstItem`, `totalLiquids`, `totalPower`,
  `itemCapacity`, `liquidCapacity`, `powerCapacity`, `powerNetStored`,
  `powerNetCapacity`, `powerNetIn`, `powerNetOut`.
- Combate: `ammo`, `ammoCapacity`, `currentAmmoType`, `health`, `maxHealth`,
  `heat`, `shield`, `armor`, `range`, `shooting`, `bulletLifetime`, `bulletTime`.
- Estado: `memoryCapacity`, `efficiency`, `progress`, `timescale`, `rotation`,
  `x`, `y`, `velocityX`, `velocityY`, `shootX`, `shootY`, `size`, `solid`,
  `dead`, `team`, `type`, `flag`, `flying`, `controlled`, `controller`,
  `name`, `id`, `selectedBlock`, `selectedRotation`.
- Unidades: `boosting`, `mineX`, `mineY`, `mining`, `buildX`, `buildY`,
  `pingX`, `pingY`, `pingText`, `building`, `breaking`, `speed`, `payloadCount`,
  `payloadType`, `totalPayload`, `payloadCapacity`, `maxUnits`.
- Displays/buffers: `displayWidth`, `displayHeight`, `bufferSize`, `operations`.
- Câmera (privilegiado): `cameraX`, `cameraY`, `cameraWidth`, `cameraHeight`.

**Escrita (`setprop`)**
`x`, `y`, `velocityX`, `velocityY`, `rotation`, `speed`, `armor`, `health`,
`shield`, `team`, `flag`, `totalPower`, `payloadType`, `bulletTime`,
`bulletLifetime`.

**Controle (`control`)**
`enabled`, `shoot`, `shootp`, `config`, `color`.

### 5.3 Operadores (`op`)

**Aritmética**
`+`, `-`, `*`, `/`, `//` (divisão inteira), `%`, `%%` (módulo positivo), `^` (potência),
`abs`, `sign`, `log`, `logn`, `log10`, `floor`, `ceil`, `round`, `sqrt`, `rand`.

**Comparação/lógica**
`==`, `not` (≠), `and`, `<`, `<=`, `>`, `>=`, `===` (igualdade estrita de tipo/objeto).

**Bit a bit**
`<<`, `>>`, `>>>`, `or` (|), `b-and` (&), `xor` (^), `flip` (~).

**Funções de dois argumentos**
`max`, `min`, `angle` (ângulo de um vetor), `anglediff` (diferença angular),
`len` (distância), `noise` (ruído Simplex).

**Trigonometria (graus)**
`sin`, `cos`, `tan`, `asin`, `acos`, `atan`.

### 5.4 Comandos de `ucontrol`

| Comando | Parâmetros | Função |
|---|---|---|
| `idle` | — | Fica parado |
| `stop` | — | Para a unidade |
| `move` | `x y` | Move para a posição |
| `approach` | `x y raio` | Aproxima-se da posição |
| `pathfind` | `x y` | Navega até a posição |
| `autoPathfind` | — | Navegação automática |
| `boost` | `enable` | Liga/desliga propulsão |
| `target` | `x y shoot` | Mira/atira na posição |
| `targetp` | `unit shoot` | Mira/atira em uma unidade |
| `itemDrop` | `to amount` | Deposita itens |
| `itemTake` | `from item amount` | Pega itens |
| `payDrop` | — | Dispensa carga paga |
| `payTake` | `takeUnits` | Pega carga paga |
| `payEnter` | — | Entra em construção pagando |
| `mine` | `x y` | Minera na posição |
| `flag` | `value` | Define a flag da unidade |
| `build` | `x y block rotation config` | Constrói |
| `deconstruct` | `x y` | Desconstrói |
| `getBlock` | `x y type building floor` | Lê o bloco na posição |
| `within` | `x y radius result` | Verifica se está dentro do raio |
| `unbind` | — | Desvincula a unidade |

> `build` e `deconstruct` só aparecem se as regras `logicUnitBuild` e
> `logicUnitDeconstruct` estiverem habilitadas.

### 5.5 Radar, localização e consulta

**Filtros de radar (`RadarTarget`)**
`any`, `enemy`, `ally`, `player`, `attacker`, `flying`, `boss`, `ground`.

**Ordenação (`RadarSort`)**
`distance`, `health`, `shield`, `armor`, `maxHealth`.

**Localização (`ulocate`)**
`ore`, `building`, `spawn`, `damaged`.

**Grupos de construção (`BlockFlag.allLogic`)**
`core`, `storage`, `generator`, `turret`, `factory`, `repair`, `battery`,
`reactor`, `drill`, `shield`.

**Consulta (`query`)**
- Formas: `circle`, `rect`.
- Tipos: `unit`, `building` (projéteis não são consultáveis).

### 5.6 Marcadores (`setmarker`)

| Propriedade | Parâmetros |
|---|---|
| `remove` | — |
| `world` | `truefalse` |
| `minimap` | `truefalse` |
| `light` | `truefalse` |
| `autoscale` | `truefalse` |
| `pos` | `x y` |
| `endPos` | `x y` |
| `drawLayer` | `layer` |
| `color` | `color` |
| `radius` | `radius` |
| `stroke` | `stroke` |
| `outline` | `outline` |
| `rotation` | `rotation` |
| `shape` | `sides fill outline` |
| `arc` | `start end` |
| `flushText` | `fetch` |
| `fontSize` | `size` |
| `textHeight` | `height` |
| `textAlign` | `align` |
| `lineAlign` | `align` |
| `labelFlags` | `background outline` |
| `texture` | `printFlush name` |
| `textureSize` | `width height` |
| `posi` | `index x y` |
| `uvi` | `index x y` |
| `colori` | `index color` |

### 5.7 Tipos diversos

**Mensagens (`message`)**: `notify`, `announce`, `toast`, `mission`.

**Cutscene (`cutscene`)**: `active`, `pan`, `zoom`, `stop`, `shake`, `getHud`, `setHud`.

**Fetch (`fetch`)**: `unit`, `unitCount`, `player`, `playerCount`, `core`,
`coreCount`, `build`, `buildCount`.

**Camadas de tile (`getblock`/`setblock`)**: `floor`, `ore`, `block`, `building`.

**Regras (`setrule`)**: ver seção 4.6.

---

## 6. Observações e boas práticas

- **Privilegiado**: instruções 🔒 só existem em `world-processor`; em outros
  processadores são descartadas na leitura.
- **Links**: cada link vira uma variável com nome (`cell1`, `turret1`, ...) e pode
  ser acessado por índice com `getlink`.
- **Buffers**: `print`/`draw` apenas enchem buffers; nada é exibido sem `printflush`
  ou `drawflush`.
- **Loop**: o código reinicia automaticamente ao final; use `wait` para poupar
  instruções e `stop` para encerrar.
- **IPT**: `setrate` só reduz a taxa em processadores normais; para aumentar, use um
  `world-processor`.
- **Saves**: o tempo restante de `wait`, o acumulador e as variáveis do processador
  são preservados.
- **Strings**: use aspas e escapes; nomes de links não podem conter espaços.
- **Comentários**: `#` e `;` delimitam instruções/comentários no parser.

---

## 7. Mapa dos arquivos

| Arquivo | Conteúdo |
|---|---|
| `core/src/orbital/logic/LStatements.java` | Todas as instruções e seus campos |
| `core/src/orbital/logic/LExecutor.java` | Implementação/execução das instruções |
| `core/src/orbital/logic/LAssembler.java` | Compilação de statements em instruções |
| `core/src/orbital/logic/LParser.java` | Parser da linguagem (tokens, rótulos, saltos) |
| `core/src/orbital/logic/GlobalVars.java` | Variáveis globais e constantes |
| `core/src/orbital/logic/LAccess.java` | Propriedades de sensor/controle |
| `core/src/orbital/logic/LogicOp.java` | Operadores matemáticos/lógicos |
| `core/src/orbital/logic/ConditionOp.java` | Condições de `jump`/`select` |
| `core/src/orbital/logic/LUnitControl.java` | Comandos de `ucontrol` |
| `core/src/orbital/logic/RadarTarget.java` / `RadarSort.java` | Filtros e ordenação de radar |
| `core/src/orbital/logic/LLocate.java` | Tipos de `ulocate` |
| `core/src/orbital/logic/QueryType.java` / `QueryShape.java` | Tipos/formas de `query` |
| `core/src/orbital/logic/TileLayer.java` | Camadas de tile |
| `core/src/orbital/logic/MessageType.java` | Tipos de mensagem |
| `core/src/orbital/logic/CutsceneAction.java` | Ações de cutscene |
| `core/src/orbital/logic/FetchType.java` | Tipos de `fetch` |
| `core/src/orbital/logic/LogicRule.java` | Regras de `setrule` |
| `core/src/orbital/logic/LMarkerControl.java` | Propriedades de marcador |
| `core/src/orbital/logic/LCategory.java` | Categorias do editor |
| `core/src/orbital/world/blocks/logic/LogicBlock.java` | Processadores, links, serialização |
| `core/src/orbital/world/blocks/logic/MemoryBlock.java` | Memórias |
| `core/src/orbital/world/blocks/logic/SwitchBlock.java` | Interruptores |
| `core/src/orbital/world/blocks/logic/MessageBlock.java` | Blocos de mensagem |
| `core/src/orbital/world/blocks/logic/LogicDisplay.java` | Displays e comandos gráficos |
| `core/src/orbital/world/blocks/logic/CanvasBlock.java` | Canvas |
| `core/src/orbital/content/Blocks.java` | Receitas e atributos dos blocos |
