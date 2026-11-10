# Catálogo completo — muros, muralhas e portas

**Tipo:** catálogo técnico de blocos defensivos  
**Data:** 10 de novembro de 2026  
**Fonte primária:** `core/src/orbital/content/Blocks.java` (região `defense`)  
**Classes:** `core/src/orbital/world/blocks/defense/Wall.java`, `Door.java`, `AutoDoor.java`, `ShieldWall.java`  
**Árvores de pesquisa:** `SerpuloTechTree.java`, `ErekirTechTree.java`  
**Escopo:** todos os blocos construíveis da categoria `defense` que funcionam como muro, muralha ou porta.

---

## 1. O que este catálogo cobre

O jogo possui **25 definições de `Wall`** e **3 definições de porta** (`Door` / `AutoDoor`, que herdam de `Wall`). Este documento lista **todos os 28 blocos**, com:

- nome localizado e ID interno;
- tamanho, vida e armadura;
- requisitos de construção;
- tempo de construção calculado;
- atributos especiais de combate;
- posição na árvore de pesquisa;
- descrição oficial em português;
- observações de uso.

Os **muros de terreno** (`StaticWall`, como pedra, gelo e areia) ficam de fora: são cenário indestrutível, sem vida, custo ou atributo de combate. Há uma nota sobre eles no fim do documento.

---

## 2. Características comuns a todo muro

Todo bloco que herda de `Wall` recebe automaticamente:

| Característica | Valor | Efeito |
|---|---|---|
| Categoria | `Category.defense` | Aparece na aba de defesa |
| Grupo | `BlockGroup.walls` | Conta como muro para lógica e IA |
| Sólido | `solid = true` | Bloqueia unidades e projéteis |
| Destrutível | `destructible = true` | Pode ser destruído |
| Prioridade de alvo | `TargetPriority.wall` | Unidades inimigas tratam como obstáculo |
| Dano por esmagamento | `crushDamageMultiplier = 5f` | Tanques que atropelam causam 5x dano |
| Multiplicador de construção | `buildCostMultiplier = 6f` | Tempo de construção 6x o custo dos itens |
| Sobrecarga | `canOverdrive = false` | Não recebe bônus de Overdrive |
| Ambiente | `Env.any` | Pode ser colocado em qualquer terreno |
| Desenho | `drawCached = true` | Renderização em cache |

> Exceções: as portas sobrescrevem `solid = false`; a sucata usa `buildCostMultiplier = 4f`; berílio e tungstênio usam `8f` (1x1) e `5f` (2x2).

**Fórmula da vida:** todos usam `wallHealthMultiplier = 4` (`Blocks.java:1706`). A vida final é `vida base × 4`; a versão 2x2 multiplica novamente por 4.

**Fórmula do tempo de construção:**

```text
buildTime = Σ (quantidade × custo do item) × buildCostMultiplier
tempo (s) = buildTime ÷ 60
```

Os custos de item usados são: cobre `0,5`, titânio `1,0`, tório `1,1`, sucata `0,5`, silício `0,8`, plastânio `1,3`, tecido de fase `1,3`, liga de surto `1,2`, berílio `1,2`, tungstênio `1,5`, carboneto `1,4` e metavidro `1,5`. Eles vêm de `Items.java` e determinam apenas o **tempo**, não a quantidade de itens.

---

## 3. Muros básicos (Serpulo)

Sem atributo especial: são a linha de vida pura.

### 3.1 Muro de Cobre / Muralha de Cobre

| Campo | Muro de Cobre | Muralha de Cobre |
|---|---|---|
| ID | `copper-wall` | `copper-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 320 | 1.280 |
| Armadura | 0 | 0 |
| Custo | 6 cobre | 24 cobre |
| Tempo de construção | 0,30 s | 1,20 s |
| Atributos especiais | — | — |
| Pesquisa | após Duo | após Muro de Cobre |
| Descrição | “Um bloco defensivo barato. Útil para proteger o núcleo e torretas no começo.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** a primeira parede do jogo. Barata e rápida, serve para desviar tiro de torres iniciais e fechar gargalos. Não segura composições pesadas.

### 3.2 Muro de Titânio / Muralha de Titânio

| Campo | Muro de Titânio | Muralha de Titânio |
|---|---|---|
| ID | `titanium-wall` | `titanium-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 440 | 1.760 |
| Armadura | 0 | 0 |
| Custo | 6 titânio | 24 titânio |
| Tempo de construção | 0,60 s | 2,40 s |
| Atributos especiais | — | — |
| Pesquisa | após Muralha de Cobre | após Muro de Titânio |
| Descrição | “Um bloco defensivo moderadamente forte. Providencia defesa moderada contra inimigos.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** salto de 37,5% em vida sobre o cobre, com o dobro do tempo de construção. É a parede “padrão” do meio de jogo.

### 3.3 Muro de Tório / Muralha de Tório

| Campo | Muro de Tório | Muralha de Tório |
|---|---|---|
| ID | `thorium-wall` | `thorium-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 800 | 3.200 |
| Armadura | 0 | 0 |
| Custo | 6 tório | 24 tório |
| Tempo de construção | 0,66 s | 2,64 s |
| Atributos especiais | — | — |
| Pesquisa | após Muro de Titânio | após Muro de Tório |
| Descrição | “Um bloco defensivo forte. Boa proteção contra inimigos.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** a maior vida entre os muros sem atributo especial. Ainda assim, perde para Carboneto e Surto Reforçada em eficiência por material quando o assunto é resistência real.

---

## 4. Muros de sucata (Serpulo)

Linha barata e descartável, com variantes visuais. Todos usam `buildCostMultiplier = 4f` (construção mais rápida que o padrão 6x).

| Bloco | ID | Tam. | Vida | Custo | Tempo | Variantes |
|---|---|---:|---:|---|---:|---:|
| Muro de Sucata | `scrap-wall` | 1x1 | 240 | 6 sucata | 0,20 s | 5 |
| Muro Grande de Sucata | `scrap-wall-large` | 2x2 | 960 | 24 sucata | 0,80 s | 4 |
| Muro Enorme de Sucata | `scrap-wall-huge` | 3x3 | 2.160 | 54 sucata | 1,80 s | 3 |
| Muro Gigante de Sucata | `scrap-wall-gigantic` | 4x4 | 3.840 | 96 sucata | 3,20 s | — |

- **Pesquisa:** Muro de Sucata → Grande → Enorme → Gigante, logo após a Muralha de Cobre.
- **Descrição:** “Protege estruturas de projéteis inimigos.”
- **Atributos especiais:** nenhum. O valor está no custo baixíssimo e na construção rápida.
- **Leitura:** a pior vida por tile da categoria (240 por 1x1), mas também a mais barata. Serve para tampão, isca e reparo rápido, não para linha de frente.

---

## 5. Muro de Plastânio / Muralha de Plastânio

| Campo | Muro | Muralha |
|---|---|---|
| ID | `plastanium-wall` | `plastanium-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 500 | 2.000 |
| Armadura | 0 | 0 |
| Custo | 5 plastânio + 2 metavidro | 20 plastânio + 8 metavidro |
| Tempo de construção | 0,95 s | 3,80 s |
| Atributos | `insulated = true`, `absorbLasers = true` | Idem |
| Extra | `schematicPriority = 10` | Idem |
| Pesquisa | após Muro de Titânio | após Muro de Plastânio |
| Descrição | “Um tipo especial de muro que absorve arcos elétricos e bloqueia conexões automáticas de células de energia.” | Mesma descrição; ocupa múltiplos blocos. |

**Atributos em detalhe:**

- **`absorbLasers`** — lasers e feixes param no bloco em vez de atravessá-lo (`Damage.findLaserLength`, `BulletComp`).
- **`insulated`** — correntes de raio terminam no bloco (`Lightning.java:61`) e conexões automáticas de energia não o atravessam.
- **`schematicPriority = 10`** — prioridade alta em schematics, para o muro ser colocado por último.

**Leitura:** o único muro anti-laser do jogo. Contra Corvus, Quasar e feixes em geral, é obrigatório. Contra dano físico, é só um muro de 500/2.000 com custo alto.

---

## 6. Muro de Fase / Muralha de Fase

| Campo | Muro | Muralha |
|---|---|---|
| ID | `phase-wall` | `phase-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 600 | 2.400 |
| Armadura | 0 | 0 |
| Custo | 6 tecido de fase | 24 tecido de fase |
| Tempo de construção | 0,78 s | 3,12 s |
| Atributos | `chanceDeflect = 10`, `flashHit = true` | Idem |
| Pesquisa | após Muro de Liga de Surto | após Muro de Fase |
| Descrição | “Um muro revestido com tecido de fase. Reflete a maioria das balas no impacto.” | Mesma descrição; ocupa múltiplos blocos. |

**Como funciona a deflexão:**

```text
chance = chanceDeflect ÷ dano do projétil
```

- Projétil de até 10 de dano: **100%** de chance.
- Projétil de 20: **50%**.
- Projétil de 100: **10%**.
- Só vale para projéteis `reflectable` e com velocidade acima de `0,1`.
- O projétil refletido muda de time e passa a pertencer à parede — pode acertar o inimigo.

**Leitura:** excelente contra enxurrada de projéteis fracos; fraca contra dano alto e explosões. O `flashHit` acende a parede a cada impacto, servindo de feedback visual.

---

## 7. Muro de Liga de Surto / Muralha de Liga de Surto

| Campo | Muro | Muralha |
|---|---|---|
| ID | `surge-wall` | `surge-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 920 | 3.680 |
| Armadura | 0 | 0 |
| Custo | 6 liga de surto | 24 liga de surto |
| Tempo de construção | 0,72 s | 2,88 s |
| Atributos | `lightningChance = 5%`, `lightningDamage = 20` | Idem |
| Pesquisa | após Muro de Tório | após Muro de Liga de Surto |
| Descrição | “Um bloco defensivo extremamente durável. Se carrega com eletricidade no contato com as balas, soltando-as aleatoriamente.” | Mesma descrição; ocupa múltiplos blocos. |

**Como funciona a retaliação:**

- A cada impacto de projétil, **5%** de chance de criar um raio.
- O raio sai na direção oposta à do projétil (`bullet.rotation() + 180`).
- Dano fixo de **20**, comprimento **17**, cor `Pal.surge`.
- Encadeia até **8 alvos** num raio de **30 unidades** e para em muro isolado inimigo.
- A chance é fixa por impacto: não escala com o dano recebido.

**Leitura:** muro de saturação. Funciona melhor em linhas longas, onde muitos projéteis colidem por segundo. Contra poucos tiros, o retorno é irregular.

---

## 8. Muros de Berílio (Erekir)

| Campo | Muro de Berílio | Muralha de Berílio |
|---|---|---|
| ID | `beryllium-wall` | `beryllium-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 520 | 2.080 |
| Armadura | 2 | 2 |
| Custo | 6 berílio | 24 berílio |
| Tempo de construção | 0,96 s | 2,40 s |
| Multiplicador de construção | 8x | 5x |
| Pesquisa | após Breach | após Muro de Berílio |
| Descrição | “Protege estruturas contra projéteis inimigos.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** a armadura mais baixa do jogo. Com o piso de 10% de dano, reduz no máximo 2 pontos por acerto. Existe para introduzir o conceito de armadura, não para segurar linha de frente. O multiplicador 8x faz o muro 1x1 demorar mais que o normal para construir.

---

## 9. Muros de Tungstênio (Erekir)

| Campo | Muro de Tungstênio | Muralha de Tungstênio |
|---|---|---|
| ID | `tungsten-wall` | `tungsten-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 720 | 2.880 |
| Armadura | 14 | 14 |
| Custo | 6 tungstênio | 24 tungstênio |
| Tempo de construção | 1,20 s | 3,00 s |
| Multiplicador de construção | 8x | 5x |
| Pesquisa | após Muro de Berílio | após Muro de Tungstênio |
| Descrição | “Protege estruturas contra projéteis inimigos.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** contra um tiro de 20 de dano, sobram 6; contra um de 15, sobram 1,5. É a armadura intermediária de Erekir. Fraqueza clara: dano de área e projéteis com `pierceArmor`.

---

## 10. Muros de Carboneto (Erekir)

| Campo | Muro de Carboneto | Muralha de Carboneto |
|---|---|---|
| ID | `carbide-wall` | `carbide-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 1.080 | 4.320 |
| Armadura | 16 | 16 |
| Custo | 6 tório + 6 carboneto | 24 tório + 24 carboneto |
| Tempo de construção | 1,50 s | 6,00 s |
| Multiplicador de construção | 6x | 6x |
| Pesquisa | após Muro de Tungstênio | após Muro de Carboneto |
| Descrição | “Protege estruturas contra projéteis inimigos.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** maior vida bruta entre os muros com armadura. A Muralha de Carboneto tem **4.320 de vida**, contra 4.000 da Surto Reforçada. Em compensação, não retalia e tem 4 pontos menos de armadura. É a parede “tanque” pura de Erekir.

---

## 11. Muro de Liga de Surto Reforçada / Muralha

| Campo | Muro | Muralha |
|---|---|---|
| ID | `reinforced-surge-wall` | `reinforced-surge-wall-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 1.000 | 4.000 |
| Armadura | 20 | 20 |
| Custo | 6 liga de surto + 2 tungstênio | 24 liga de surto + 8 tungstênio |
| Tempo de construção | 1,02 s | 4,08 s |
| Atributos | `armor = 20`, `lightningChance = 5%`, `lightningDamage = 30` | Idem |
| Pesquisa | após Muro de Tungstênio | após Muro de Liga de Surto Reforçada |
| Custo de pesquisa | 20 liga de surto + 100 tungstênio | 40 liga de surto + 200 tungstênio |
| Descrição | “Protege estruturas contra projéteis inimigos, lançando periodicamente arcos elétricos em contato com o projétil.” | Mesma descrição; ocupa múltiplos blocos. |

**Leitura:** a maior armadura do jogo (**20**) combinada com retaliação de **30** de dano por raio. Perde 320 de vida para a Muralha de Carboneto, mas ganha 4 de armadura e o raio. É o muro mais completo contra dano direto.

---

## 12. Muro Blindado (Erekir)

| Campo | Valor |
|---|---|
| ID | `shielded-wall` |
| Tamanho | 2x2 |
| Vida | 4.160 |
| Armadura | 15 |
| Escudo | 900 |
| Deflexão | `chanceDeflect = 8` |
| Custo | 20 tecido de fase + 12 liga de surto + 12 berílio |
| Tempo de construção | 5,48 s |
| Energia | 3 por segundo (`consumePower(3f / 60f)`) |
| Pesquisa | após Muralha de Liga de Surto Reforçada |
| Descrição | “Protege estruturas contra projéteis inimigos. Implanta um escudo que absorve a maioria dos projéteis quando energia é fornecida. Conduz energia.” |

**Funcionamento do escudo (`ShieldWall.java`):**

- O dano é desviado para o escudo antes de atingir a vida (`shieldTaken = min(shield, damage)`).
- Escudo quebrado: `breakCooldown = 600 ticks = 10 s` sem escudo.
- Fora do cooldown, regenera `regenSpeed = 2` por tick, escalado por `edelta()` (depende da eficiência e da energia).
- Sem energia, `broken()` retorna `true` e o escudo desce.
- O escudo é **pessoal e quadrado**, cobrindo apenas a área do bloco 2x2 — não protege construções vizinhas.
- `conductivePower = true`, `hasPower = true`, `consumesPower = true`, `outputsPower = false`.

**Leitura:** maior vida efetiva do jogo (**4.160 + 900 = 5.060**, antes de armadura e deflexão), mas com custo operacional contínuo de energia e tempo de construção de 5,48 s — só a Muralha de Carboneto demora mais (6,00 s). É o “muro de elite” de Erekir.

---

## 13. Portas (estruturas relacionadas)

Portas herdam de `Wall`, mas não são muros fechados: `solid = false`, `solidifes = true`, `consumesTap = true`. Elas ficam sólidas apenas quando fechadas (`checkSolid()`).

### 13.1 Porta / Porta Grande

| Campo | Porta | Porta Grande |
|---|---|---|
| ID | `door` | `door-large` |
| Tamanho | 1x1 | 2x2 |
| Vida | 400 | 1.600 |
| Armadura | 0 | 0 |
| Custo | 6 titânio + 4 silício | 24 titânio + 16 silício |
| Tempo de construção | 0,92 s | 3,68 s |
| Atributos | abre/fecha ao toque; portas encadeadas abrem juntas | Idem + efeitos próprios (`dooropenlarge` / `doorcloselarge`) |
| Pesquisa | após Muro de Titânio | após Porta |
| Descrição | “Uma pequena porta. Pode ser aberta e fechada ao tocar.” | “Uma grande porta. Pode ser aberta e fechada ao tocar. Ocupa múltiplos blocos.” |

**Comportamento:**

- Toque abre/fecha; há cooldown de **60 ticks (1 s)** por porta de origem.
- Portas adjacentes ficam “encadeadas” e abrem/fecham juntas.
- A porta **não fecha** se houver unidades dentro dela.
- Configurável por lógica (`enabled`) e por sensores.

### 13.2 Porta de Segurança (`blast-door`)

| Campo | Valor |
|---|---|
| ID | `blast-door` |
| Classe | `AutoDoor` (herda de `Wall`) |
| Tamanho | 2x2 |
| Vida | 2.800 |
| Armadura | 14 |
| Custo | 24 tungstênio + 24 silício |
| Tempo de construção | 5,52 s |
| Atributos | abre automaticamente para unidades terrestres aliadas; armadura 14 |
| Pesquisa | após Muralha de Tungstênio |
| Descrição | “Uma parede que se abre quando as unidades terrestres aliadas estão no alcance. Não pode ser controlada manualmente.” |

**Comportamento:**

- Verifica a cada **20 ticks** se há unidade terrestre aliada num raio de `size × tilesize + 24` (margem de `12` para cada lado).
- Só considera unidades **terrestres** (`isGrounded()`) e que **não usam passos** (`!allowLegStep`).
- Não aceita controle manual; `noUpdateDisabled = true` e `drawDisabled = true`.
- `teamPassable = true`.

**Leitura:** é a única porta com atributo de combate (armadura 14). Por ser `AutoDoor`, não entra na contagem de “muros” do relatório de combate, mas é a melhor opção para passagem de tanques em Erekir.

---

## 14. Tabela-resumo geral

### 14.1 Muros e muralhas (25 blocos)

| Nome | ID | Tam. | Vida | Armadura | Atributo especial | Custo | Tempo |
|---|---|---:|---:|---:|---|---|---:|
| Muro de Cobre | `copper-wall` | 1x1 | 320 | 0 | — | 6 cobre | 0,30 s |
| Muralha de Cobre | `copper-wall-large` | 2x2 | 1.280 | 0 | — | 24 cobre | 1,20 s |
| Muro de Titânio | `titanium-wall` | 1x1 | 440 | 0 | — | 6 titânio | 0,60 s |
| Muralha de Titânio | `titanium-wall-large` | 2x2 | 1.760 | 0 | — | 24 titânio | 2,40 s |
| Muro de Tório | `thorium-wall` | 1x1 | 800 | 0 | — | 6 tório | 0,66 s |
| Muralha de Tório | `thorium-wall-large` | 2x2 | 3.200 | 0 | — | 24 tório | 2,64 s |
| Muro de Sucata | `scrap-wall` | 1x1 | 240 | 0 | — | 6 sucata | 0,20 s |
| Muro Grande de Sucata | `scrap-wall-large` | 2x2 | 960 | 0 | — | 24 sucata | 0,80 s |
| Muro Enorme de Sucata | `scrap-wall-huge` | 3x3 | 2.160 | 0 | — | 54 sucata | 1,80 s |
| Muro Gigante de Sucata | `scrap-wall-gigantic` | 4x4 | 3.840 | 0 | — | 96 sucata | 3,20 s |
| Muro de Plastânio | `plastanium-wall` | 1x1 | 500 | 0 | anti-laser + isolado | 5 plastânio + 2 metavidro | 0,95 s |
| Muralha de Plastânio | `plastanium-wall-large` | 2x2 | 2.000 | 0 | anti-laser + isolado | 20 plastânio + 8 metavidro | 3,80 s |
| Muro de Fase | `phase-wall` | 1x1 | 600 | 0 | deflexão 10 | 6 tecido de fase | 0,78 s |
| Muralha de Fase | `phase-wall-large` | 2x2 | 2.400 | 0 | deflexão 10 | 24 tecido de fase | 3,12 s |
| Muro de Liga de Surto | `surge-wall` | 1x1 | 920 | 0 | raio 5% / 20 | 6 liga de surto | 0,72 s |
| Muralha de Liga de Surto | `surge-wall-large` | 2x2 | 3.680 | 0 | raio 5% / 20 | 24 liga de surto | 2,88 s |
| Muro de Berílio | `beryllium-wall` | 1x1 | 520 | 2 | — | 6 berílio | 0,96 s |
| Muralha de Berílio | `beryllium-wall-large` | 2x2 | 2.080 | 2 | — | 24 berílio | 2,40 s |
| Muro de Tungstênio | `tungsten-wall` | 1x1 | 720 | 14 | — | 6 tungstênio | 1,20 s |
| Muralha de Tungstênio | `tungsten-wall-large` | 2x2 | 2.880 | 14 | — | 24 tungstênio | 3,00 s |
| Muro de Carboneto | `carbide-wall` | 1x1 | 1.080 | 16 | — | 6 tório + 6 carboneto | 1,50 s |
| Muralha de Carboneto | `carbide-wall-large` | 2x2 | 4.320 | 16 | — | 24 tório + 24 carboneto | 6,00 s |
| Muro de Liga de Surto Reforçada | `reinforced-surge-wall` | 1x1 | 1.000 | 20 | raio 5% / 30 | 6 liga de surto + 2 tungstênio | 1,02 s |
| Muralha de Liga de Surto Reforçada | `reinforced-surge-wall-large` | 2x2 | 4.000 | 20 | raio 5% / 30 | 24 liga de surto + 8 tungstênio | 4,08 s |
| Muro Blindado | `shielded-wall` | 2x2 | 4.160 + 900 escudo | 15 | escudo + deflexão 8 | 20 tecido de fase + 12 liga de surto + 12 berílio + 3 energia/s | 5,48 s |

### 14.2 Portas (3 blocos)

| Nome | ID | Tam. | Vida | Armadura | Atributo | Custo | Tempo |
|---|---|---:|---:|---:|---|---|---:|
| Porta | `door` | 1x1 | 400 | 0 | abre ao toque | 6 titânio + 4 silício | 0,92 s |
| Porta Grande | `door-large` | 2x2 | 1.600 | 0 | abre ao toque | 24 titânio + 16 silício | 3,68 s |
| Porta de Segurança | `blast-door` | 2x2 | 2.800 | 14 | abre para unidades terrestres aliadas | 24 tungstênio + 24 silício | 5,52 s |

---

## 15. Rankings

| Pergunta | Resposta |
|---|---|
| Maior vida bruta | Muralha de Carboneto — **4.320** |
| Maior vida efetiva | Muro Blindado — **5.060** (com escudo) |
| Maior armadura | Surto Reforçada — **20** |
| Maior deflexão | Fase — **10** |
| Maior retaliação | Surto Reforçada — **30** por raio |
| Único anti-laser | Plastânio |
| Único anti-raio | Plastânio |
| Único com escudo | Muro Blindado |
| Único com custo de energia | Muro Blindado |
| Mais barato | Muro de Sucata — **6 sucata / 0,20 s** |
| Maior tempo de construção | Muralha de Carboneto — **6,00 s** |
| Mais complexo | Muro Blindado — 3 recursos de alto tier + energia contínua |
| Maior alcance de utilidade | Porta de Segurança (passagem automática) |

---

## 16. Observações técnicas importantes

### 16.1 Armadura não protege contra splash

`BuildingComp.collision` aplica `Damage.applyArmor` apenas no impacto direto de projétil. Dano de área e explosões entram por `damage(damage)` e **ignoram a armadura**. Por isso Carboneto e Surto Reforçada são fortes contra tiro direto e vulneráveis a artilharia.

### 16.2 Piso de 10% de dano

`Damage.applyArmor` retorna `max(dano - armadura, 0,1 × dano)`. Nenhum muro fica imune: no mínimo 10% do dano sempre passa.

### 16.3 Deflexão depende do dano

`chanceDeflect ÷ dano` faz o atributo ser muito mais forte contra projéteis fracos. Um muro de Fase reflete 100% dos tiros de até 10 de dano, mas só 10% de um tiro de 100.

### 16.4 Nomenclatura

- **Muro** = versão 1x1.
- **Muralha** = versão 2x2.
- **Muro Grande/Enorme/Gigante** = versões 2x2/3x3/4x4 de sucata.
- A vida cresce com `tamanho²`, mas os atributos especiais **não** escalam com o tamanho: uma Muralha de Fase tem a mesma deflexão 10 do Muro de Fase.

### 16.5 Versões 2x2 são multiplicativas

Em todas as famílias, a versão 2x2 tem exatamente 4x a vida e 4x o custo, mantendo o mesmo atributo. O ganho é economizar espaço e tempo de colocação, não obter um atributo melhor.

### 16.6 Muros de terreno

Existem cerca de 24 `StaticWall` no jogo (pedra, gelo, areia, dacito, regolito, carboneto, metal, etc.). Eles são **cenário indestrutível**: `breakable = false`, `unitMoveBreakable = false`, sem vida, custo ou atributo de combate. Não entram neste catálogo porque não são construíveis nem defensivos no sentido de jogo.

---

## 17. Referências de código

| Conteúdo | Arquivo / linha |
|---|---|
| Multiplicador global de vida | `core/src/orbital/content/Blocks.java:1706` |
| Muros de cobre e titânio | `Blocks.java:1708–1729` |
| Plastânio | `Blocks.java:1731–1746` |
| Tório | `Blocks.java:1748–1757` |
| Fase | `Blocks.java:1759–1772` |
| Liga de Surto | `Blocks.java:1774–1785` |
| Porta / Porta Grande | `Blocks.java:1787–1798` |
| Sucata (4 tamanhos) | `Blocks.java:1800–1828` |
| Berílio | `Blocks.java:1836–1849` |
| Tungstênio | `Blocks.java:1851–1864` |
| Porta de Segurança | `Blocks.java:1866–1871` |
| Surto Reforçada | `Blocks.java:1873–1890` |
| Carboneto | `Blocks.java:1892–1903` |
| Muro Blindado | `Blocks.java:1905–1919` |
| Classe `Wall` e atributos | `core/src/orbital/world/blocks/defense/Wall.java` |
| Deflexão e raio | `Wall.java:163–208` |
| Escudo | `core/src/orbital/world/blocks/defense/ShieldWall.java` |
| Porta manual | `core/src/orbital/world/blocks/defense/Door.java` |
| Porta automática | `core/src/orbital/world/blocks/defense/AutoDoor.java` |
| Cálculo de armadura | `core/src/orbital/entities/Damage.java:662–665` |
| Aplicação em blocos | `core/src/orbital/entities/comp/BuildingComp.java:1761–1770` |
| Raio e isolamento | `core/src/orbital/entities/Lightning.java:42–100` |
| Absorção de laser | `Damage.java:146–219`, `BulletComp.java:295` |
| Tempo de construção | `core/src/orbital/world/Block.java:1460–1471` |
| Custos dos itens | `core/src/orbital/content/Items.java` |
| Pesquisa Serpulo | `SerpuloTechTree.java:263–295` |
| Pesquisa Erekir | `ErekirTechTree.java:251–277` |

---

*Documento gerado por leitura direta do código-fonte. Os tempos de construção são calculados pela fórmula do motor e podem variar com regras de partida e velocidade de construção.*
