# Relatório — muros e muralhas com atributos de combate

**Tipo:** mapeamento técnico de defesa passiva  
**Data:** 10 de novembro de 2026  
**Fonte primária:** `core/src/orbital/content/Blocks.java` (região `defense`)  
**Mecânicas:** `core/src/orbital/world/blocks/defense/Wall.java`, `ShieldWall.java`, `core/src/orbital/entities/Damage.java`, `entities/Lightning.java`, `entities/comp/BuildingComp.java`  
**Árvores de pesquisa:** `SerpuloTechTree.java`, `ErekirTechTree.java`  
**Escopo:** todas as definições de `Wall` do jogo; entram no relatório apenas as que possuem atributo especial contra armas.

---

## 1. Critério de inclusão

O jogo define **25 blocos da classe `Wall`** (muros 1x1 e muralhas 2x2). A grande maioria é só “vida extra”: cobre, titânio, tório e sucata não têm nenhuma propriedade além de `health`.

Este relatório lista **somente os muros/muralhas que possuem pelo menos um atributo que altera o dano recebido ou reage ao ataque**:

| Atributo | Campo no código | O que faz |
|---|---|---|
| Armadura | `armor` | Reduz o dano de cada projétil direto |
| Deflexão | `chanceDeflect` | Reflete projéteis de volta |
| Raio de retaliação | `lightningChance` / `lightningDamage` | Dispara um raio ao ser atingido |
| Absorção de laser | `absorbLasers` | Interrompe lasers e feixes no bloco |
| Isolamento | `insulated` | Interrompe correntes de raio e conexões automáticas de energia |
| Escudo | `shieldHealth` (classe `ShieldWall`) | Absorve dano antes da vida do bloco |

**Resultado: 15 blocos** (8 famílias, contando as versões 1x1 e 2x2) atendem ao critério.

> Todos os muros usam o multiplicador global `wallHealthMultiplier = 4` (`Blocks.java:1706`), então a vida final é `vida base × 4` (e `× 4` novamente na versão 2x2).

---

## 2. Como cada atributo funciona

### 2.1 Armadura — `armor`

Aplicada apenas no **impacto direto de projétil** (`BuildingComp.collision`, linha 1767):

```java
damage = Damage.applyArmor(damage, block.armor * t.armorMultiplier * t.blockArmorMultiplier);
// applyArmor = max(dano - armadura, 0.1 * dano)
```

- Reduz dano **por acerto**, não por segundo.
- Piso de **10% de dano** sempre passa (redução máxima de 90%).
- Projéteis com `pierceArmor = true` ignoram a armadura.
- Multiplicadores do projétil (`armorMultiplier`, `blockArmorMultiplier`) podem aumentar ou diminuir a armadura efetiva.
- **Dano de área (splash) e explosões não passam por essa conta** — entram direto em `damage(damage)`. Ou seja, armadura protege contra tiro direto, não contra explosão.

Consequência prática: armadura alta é excelente contra metralhadoras e projéteis fracos e rápida, e fraca contra um único golpe grande. O piso de 10% também garante que ninguém fica imune.

### 2.2 Deflexão — `chanceDeflect`

Executada em `Wall.WallBuild.collision` (linhas 178–205):

```java
if(!Mathf.chance(chanceDeflect / bullet.damage())) return true;
```

- A chance **cai conforme o dano do projétil sobe**. O valor `chanceDeflect` não é uma porcentagem: é o “orçamento de dano” que a parede reflete de graça.
- Um muro de fase (`chanceDeflect = 10`) reflete **sempre** projéteis de até 10 de dano; um de 20 de dano tem 50%; um de 100 tem 10%.
- Só funciona em projéteis `reflectable` e com velocidade acima de `0.1`.
- O projétil refletido muda de time e de dono para a parede — ou seja, **pode acertar o inimigo**.
- A parede não sofre dano daquele impacto.

### 2.3 Raio de retaliação — `lightningChance`

Executado no mesmo `collision` (linhas 169–175):

```java
if(Mathf.chance(lightningChance)){
    Lightning.create(team, lightningColor, lightningDamage, x, y, bullet.rotation() + 180f, lightningLength);
}
```

- Chance **fixa por projétil**, independente do dano.
- O raio nasce na parede e segue na direção oposta à do projétil.
- O raio encadeia até 8 alvos num raio de 30 unidades e para ao encontrar um muro isolado inimigo (`Lightning.java:61`).
- Valores padrão da classe `Wall`: `lightningDamage = 20`, `lightningLength = 17`, cor `Pal.surge`.

### 2.4 Absorção de laser — `absorbLasers`

- Lasers e feixes que consultam `absorbLasers()` param no bloco em vez de atravessá-lo (`Damage.findLaserLength`, `Damage.findAbsorber`, `BulletComp`).
- É a única defesa de muro que anula **lasers de perfuração**.

### 2.5 Isolamento — `insulated`

- Interrompe correntes de raio: o raio “gruda” no bloco e para (`Lightning.java:61`).
- Impede conexões automáticas de energia e passagem por `BeamNode`.
- Não reduz dano de bala; é uma defesa contra **eletricidade**, não contra projétil comum.

### 2.6 Escudo — `ShieldWall`

- O dano é desviado para um escudo separado antes de atingir a vida (`ShieldWall.java:101–117`).
- Quando o escudo chega a zero, o bloco fica quebrado por `breakCooldown = 600 ticks = 10 s`.
- Fora do cooldown, regenera `regenSpeed = 2` por tick, escalado por `edelta()` (ou seja, depende da eficiência e do fornecimento de energia).
- Se a energia acabar, `broken()` retorna `true` e o escudo desce.
- O escudo é **pessoal e quadrado**, cobrindo apenas a área do bloco 2x2 — não protege construções vizinhas.

---

## 3. Tabela-resumo dos 15 blocos

Vida já com o multiplicador global. Custo é o custo de construção da versão indicada.

| Bloco (pt-BR) | ID | Tam. | Vida | Custo | Atributo(s) de combate |
|---|---|---:|---:|---|---|
| Muro de Plastânio | `plastanium-wall` | 1x1 | 500 | 5 plastânio + 2 metavídro | `insulated`, `absorbLasers` |
| Muralha de Plastânio | `plastanium-wall-large` | 2x2 | 2.000 | 20 plastânio + 8 metavídro | `insulated`, `absorbLasers` |
| Muro de Fase | `phase-wall` | 1x1 | 600 | 6 tecido de fase | `chanceDeflect = 10`, `flashHit` |
| Muralha de Fase | `phase-wall-large` | 2x2 | 2.400 | 24 tecido de fase | `chanceDeflect = 10`, `flashHit` |
| Muro de Liga de Surto | `surge-wall` | 1x1 | 920 | 6 liga de surto | `lightningChance = 5%`, `lightningDamage = 20` |
| Muralha de Liga de Surto | `surge-wall-large` | 2x2 | 3.680 | 24 liga de surto | `lightningChance = 5%`, `lightningDamage = 20` |
| Muro de Berílio | `beryllium-wall` | 1x1 | 520 | 6 berílio | `armor = 2` |
| Muralha de Berílio | `beryllium-wall-large` | 2x2 | 2.080 | 24 berílio | `armor = 2` |
| Muro de Tungstênio | `tungsten-wall` | 1x1 | 720 | 6 tungstênio | `armor = 14` |
| Muralha de Tungstênio | `tungsten-wall-large` | 2x2 | 2.880 | 24 tungstênio | `armor = 14` |
| Muro de Carboneto | `carbide-wall` | 1x1 | 1.080 | 6 tório + 6 carboneto | `armor = 16` |
| Muralha de Carboneto | `carbide-wall-large` | 2x2 | 4.320 | 24 tório + 24 carboneto | `armor = 16` |
| Muro de Liga de Surto Reforçada | `reinforced-surge-wall` | 1x1 | 1.000 | 6 liga de surto + 2 tungstênio | `armor = 20`, `lightningChance = 5%`, `lightningDamage = 30` |
| Muralha de Liga de Surto Reforçada | `reinforced-surge-wall-large` | 2x2 | 4.000 | 24 liga de surto + 8 tungstênio | `armor = 20`, `lightningChance = 5%`, `lightningDamage = 30` |
| Muro Blindado | `shielded-wall` | 2x2 | 4.160 + 900 de escudo | 20 tecido de fase + 12 liga de surto + 12 berílio + 3 energia/s | `shieldHealth = 900`, `armor = 15`, `chanceDeflect = 8` |

---

## 4. Análise por família

### 4.1 Plastânio — o único anti-laser (Serpulo)

- **Atributos:** `insulated = true`, `absorbLasers = true`.
- **Contra o quê:** lasers, feixes contínuos e correntes de raio. É a resposta direta a Corvus, Quasar e similares.
- **Não tem:** armadura, deflexão ou retaliação. Contra bala comum é só um muro de 500/2.000 de vida.
- **Posicionamento:** vale como “escudo de corredor” na frente de núcleos e torres contra composições de laser. Contra ataque físico puro, é custo perdido.
- **Observação:** as duas versões são idênticas em atributo; a 2x2 só multiplica vida e custo.

### 4.2 Fase — o muro anti-projétil (Serpulo)

- **Atributos:** `chanceDeflect = 10`, `flashHit = true`.
- **Contra o quê:** enxurrada de projéteis fracos. Qualquer projétil de dano ≤ 10 é refletido de forma garantida; o reflexo ainda passa a pertencer à sua equipe.
- **Fraqueza:** projéteis de dano alto e explosões. Um tiro de 100 de dano só é refletido em 10% das vezes.
- **Efeito visual:** `flashHit` acende a parede a cada impacto — bom indicador de que a deflexão está funcionando.
- **Comparação:** vida baixa (600/2.400) para um muro de fim de linha; o valor está no atributo, não no HP.

### 4.3 Liga de Surto — retaliação elétrica (Serpulo)

- **Atributos:** `lightningChance = 0,05` (5% por impacto), `lightningDamage = 20`, `lightningLength = 17`.
- **Contra o quê:** saturação de projéteis. Cada impacto tem 5% de devolver um raio que encadeia até 8 alvos.
- **Fraqueza:** a probabilidade não escala com o dano nem com a cadência de forma garantida; contra poucos tiros por segundo o retorno é irregular.
- **Sinergia:** funciona melhor em linhas longas de muro, onde muitos projéteis colidem por segundo.
- **Nota:** o dano é fixo em 20 e não é multiplicado pela vida ou pelo tamanho da muralha.

### 4.4 Berílio — armadura de entrada (Erekir)

- **Atributo:** `armor = 2`.
- **Contra o quê:** projéteis muito fracos. Com o piso de 10%, a redução máxima é de 2 pontos por acerto.
- **Leitura:** é a armadura mais fraca do jogo; existe para ensinar o sistema, não para segurar linha de frente.
- **Custo de construção:** `buildCostMultiplier = 8f` (1x1) e `5f` (2x2), ou seja, demora mais para construir que os muros equivalentes.

### 4.5 Tungstênio — armadura intermediária (Erekir)

- **Atributo:** `armor = 14`.
- **Contra o quê:** metralhadoras e projéteis de dano médio. Um tiro de 20 vira 6; um de 15 vira 1,5.
- **Fraqueza:** dano de área e projéteis com `pierceArmor`.
- **Custo:** mesmo `buildCostMultiplier` alto do berílio (8f/5f).

### 4.6 Carboneto — armadura pesada (Erekir)

- **Atributo:** `armor = 16`.
- **Contra o quê:** linha de frente contra dano direto. É a segunda maior armadura do jogo.
- **Vida:** 1.080/4.320, a maior entre os muros com armadura pura.
- **Custo:** tório + carboneto, sem energia.
- **Comparação:** contra tiro direto, é mais eficiente que a Surto Reforçada em vida por material; perde em utilidade por não retaliar.

### 4.7 Surto Reforçada — armadura + retaliação (Erekir)

- **Atributos:** `armor = 20`, `lightningChance = 5%`, `lightningDamage = 30`.
- **Contra o quê:** a combinação mais completa contra projéteis: reduz cada acerto em 20 e ainda devolve raios de 30.
- **Vida:** 1.000/4.000.
- **Uso:** muro de escolta de torres e portões, onde o inimigo precisa atirar de perto.
- **Nota:** é a maior armadura do jogo; o raio maior compensa a vida um pouco menor que a Muralha de Carboneto.

### 4.8 Muro Blindado — escudo, armadura e deflexão (Erekir)

- **Atributos:** `shieldHealth = 900`, `armor = 15`, `chanceDeflect = 8`, consumo de 3 energia/s.
- **Contra o quê:** burst e pressão constante. O escudo absorve até 900 de dano antes da vida; a armadura reduz o que passa; a deflexão devolve projéteis de até 8 de dano.
- **Vida efetiva:** 4.160 de vida + 900 de escudo = **5.060** contra dano direto, sem contar deflexão.
- **Fraqueza:** depende de energia. Sem rede elétrica, o escudo cai e sobra só a armadura. O escudo é pessoal (não cobre vizinhos), então não substitui um Projetor de Força.
- **Recarga:** após quebrar, 10 s sem escudo; depois regenera 2 por tick.
- **Custo:** o mais caro da lista (20 tecido de fase + 12 liga de surto + 12 berílio).

---

## 5. Comparativo rápido

**Contra dano direto de projétil:**

| Pergunta | Resposta |
|---|---|
| Maior armadura | Muro/Muralha de Liga de Surto Reforçada — **20** |
| Maior vida bruta | Muralha de Carboneto — **4.320** |
| Maior vida efetiva | Muro Blindado — **5.060** (com escudo) |
| Melhor contra lasers | Muro/Muralha de Plastânio — **absorção total** |
| Melhor contra projéteis fracos | Muro/Muralha de Fase — **deflexão garantida até 10 de dano** |
| Melhor retaliação | Muro/Muralha de Surto Reforçada — **30 de dano por raio** |
| Melhor contra raios | Muro/Muralha de Plastânio — **isolamento** |
| Único que exige energia | Muro Blindado |

**Perfil de resistência, em uma frase:**

- **Plastânio** → anti-laser/anti-raio.
- **Fase** → anti-enxurrada de projéteis fracos.
- **Surto** → retaliação barata.
- **Berílio** → armadura de aprendizado.
- **Tungstênio** → armadura intermediária.
- **Carboneto** → parede de vida alta com boa armadura.
- **Surto Reforçada** → armadura máxima com retaliação.
- **Blindado** → tanque de escudo com armadura e deflexão, dependente de energia.

---

## 6. Fora do escopo (e por quê)

| Bloco | Motivo da exclusão |
|---|---|
| Muro/Muralha de Cobre, Titânio, Tório | Só vida; nenhum atributo especial |
| Muro Grande/Enorme/Gigante de Sucata | Só vida (e variantes visuais) |
| Porta / Porta Grande | Só vida; abre/fecha, não reage a armas |
| Porta de Segurança (`blast-door`) | Tem `armor = 14`, mas é `AutoDoor`, não `Wall`; fica como observação |
| Projetor de Força (`force-projector`) | Gera escudo de área, mas não é muro/muralha |
| Muros de ambiente (areia, gelo, pedra etc.) | `StaticWall`/`TiledWall`, não são construções defensivas |

> Se a pergunta for “toda defesa passiva que reage a arma”, a Porta de Segurança entra como **16º item** (armadura 14, 2x2, 2.800 de vida). Como o pedido foi especificamente muros e muralhas, ela ficou de fora da contagem principal.

---

## 7. Anexo técnico — onde está cada coisa

| Conteúdo | Arquivo / linha |
|---|---|
| Multiplicador global de vida | `core/src/orbital/content/Blocks.java:1706` |
| Plastânio (1x1 / 2x2) | `Blocks.java:1731–1746` |
| Fase (1x1 / 2x2) | `Blocks.java:1759–1772` |
| Surto (1x1 / 2x2) | `Blocks.java:1774–1785` |
| Berílio (1x1 / 2x2) | `Blocks.java:1836–1849` |
| Tungstênio (1x1 / 2x2) | `Blocks.java:1851–1864` |
| Porta de Segurança (armadura 14) | `Blocks.java:1866–1871` |
| Surto Reforçada (1x1 / 2x2) | `Blocks.java:1873–1890` |
| Carboneto (1x1 / 2x2) | `Blocks.java:1892–1903` |
| Muro Blindado | `Blocks.java:1905–1919` |
| Campos de `Wall` | `core/src/orbital/world/blocks/defense/Wall.java:19–50` |
| Lógica de deflexão e raio | `Wall.java:163–208` |
| Escudo do Muro Blindado | `core/src/orbital/world/blocks/defense/ShieldWall.java` |
| Cálculo de armadura | `core/src/orbital/entities/Damage.java:662–665` |
| Aplicação da armadura em blocos | `core/src/orbital/entities/comp/BuildingComp.java:1761–1770` |
| Cadeia do raio e isolamento | `core/src/orbital/entities/Lightning.java:42–100` |
| Absorção de laser | `Damage.java:146–219`, `entities/comp/BulletComp.java:295` |
| Pesquisa Serpulo | `SerpuloTechTree.java:281–293` |
| Pesquisa Erekir | `ErekirTechTree.java:251–277` |

---

## 8. Conclusões para balanceamento

1. **A armadura é o atributo mais “invisível” para o jogador.** O jogo mostra `armor` no painel, mas não mostra a redução efetiva. Contra um projétil de 10 de dano, 20 de armadura anula 90%; contra um de 200, anula 10%. O número isolado engana.
2. **O piso de 10% é o que impede o Muro Blindado de ser invencível.** Sem ele, 20 de armadura + 900 de escudo tornaria a parede imune a boa parte do conteúdo.
3. **Splash ignora armadura.** Hoje, a melhor resposta contra Carboneto/Surto Reforçada é artilharia e explosão, não tiro direto. Isso dá variedade saudável ao combate, mas não está comunicado em lugar nenhum da interface.
4. **Plastânio é binário:** se o inimigo usa laser, ele é obrigatório; se não usa, é um muro caro e comum. Isso é bom para composição de inimigos, mas pede leitura clara do inimigo antes da onda.
5. **A Fase tem um corte brusco em 10 de dano.** Projéteis abaixo disso são 100% refletidos, acima caem rápido. É um atributo fácil de entender na prática e difícil de explicar no papel.
6. **O Muro Blindado é o único que introduz custo operacional contínuo** (energia). É o candidato natural a “muro de elite” e o mais interessante para o design de setores, porque obriga a proteger a rede elétrica junto com a parede.
7. **As versões 2x2 são puramente multiplicativas** em todos os casos: mesma propriedade, 4x vida e 4x custo. Não há ganho de atributo por tamanho — apenas economia de espaço.

---

*Documento gerado a partir da leitura direta do código-fonte; valores numéricos conferidos contra as definições de `Blocks.java` e as classes de mecânica citadas.*
