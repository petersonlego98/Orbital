# Catálogo — Valores de construção dos módulos

**Data:** 2026-11-10
**Método:** leitura do código-fonte (análise estática). Nenhuma verificação in-game foi feita nesta sessão; toda tabela abaixo cita o arquivo e a linha de origem. Valores numéricos conferidos contra `Blocks.java` e `Items.java` na data do documento.
**Escopo:** o que cada módulo **custaria para ser construído quando conversão em bloco/construção**: receita de itens e tempo de construção (`buildTime`). Módulos de arma de unidade estão na seção 5 porque **não possuem receita** em código.

---

## 1. Como o valor de construção é calculado

1. **[FATO]** A receita vem de `requirements(Category.<categoria>, with(Items.<item>, <qtd>))` dentro da definição do bloco em `core/src/orbital/content/Blocks.java`. O `ItemStack` resultante é a lista de itens que uma construção daquele bloco exige.
2. **[FATO]** O tempo de construção é derivado dos custos unitários dos itens (`Item.cost`, em `core/src/orbital/content/Items.java`), **não dos preços de mercado**: em `Block.init()` (`core/src/orbital/world/Block.java`, linhas 1459–1471)

   ```java
   if(requirements.length > 0 && buildTime < 0){
       buildTime = 0f;
       for(ItemStack stack : requirements){
           buildTime += stack.amount * stack.item.cost;
       }
   }

   if(buildTime < 0) buildTime = 20f;

   buildTime *= buildCostMultiplier;
   ```

   ou seja, **`buildTime = Σ(quantidade × custo do item)`, multiplicado por `buildCostMultiplier` do bloco**. Quando o bloco declara `buildTime = ...` explicitamente, esse valor substitui a soma derivada (acontece com `breach`, linha 4489).
3. **[FATO]** A unidade é **ticks** (60 ticks = 1 s). A mesma grandeza é usada como custo de trabalho da construção: `ConstructBlock.setConstruct()` define `buildCost = block.buildTime * state.rules.buildCostMultiplier` (`core/src/orbital/world/blocks/ConstructBlock.java`, linha 451); a barra de progresso e o tempo exibido usam `buildTime / 60` (`Block.java`, linha 666).
4. **[FATO]** Multiplicadores adicionais entram pelas regras de partida e pela habilidade de construção: `Rules.buildCostMultiplier` (`core/src/orbital/game/Rules.java`, linha 117), `type.buildSpeed`/`buildSpeedMultiplier` (`BuilderComp.java`, linha 211). Os números deste documento são o **valor base**, sem esses multiplicadores.
5. **[ACHADO]** Nenhum desses valores é hoje cobrado pelo **sistema de módulos**: instalar/remover um módulo dorsal é gratuito e instantâneo (`WeaponsComp.mountTurret`, sem consulta a itens). As receitas abaixo são o que o bloco **cobra quando é colocado no mundo** — usadas aqui como referência de valores caso os módulos passem a ser construções (pendência da etapa A3 de `PLANO_ETAPAS_MODULOS.md`).

---

## 2. Custos unitários dos itens (`Items.java`)

| Item | `cost` | Linha |
|---|---:|---:|
| Cobre (`copper`) | 0,5 | 17–18 |
| Chumbo (`lead`) | 0,7 | 23–24 |
| Metavido (`metaglass`) | 1,5 | 27–28 |
| Grafite (`graphite`) | 1,0 | 31–32 |
| Titânio (`titanium`) | 1,0 | 49–51 |
| Tório (`thorium`) | 1,1 | 54–58 |
| Sucata (`scrap`) | 0,5 | 62–63 |
| Silício (`silicon`) | 0,8 | 66–67 |
| Plastânio (`plastanium`) | 1,3 | 70–73 |
| Tecido de fase (`phase-fabric`) | 1,3 | 77–78 |
| Liga de surto (`surge-alloy`) | 1,2 | 83–84 |
| Berílio (`beryllium`) | 1,2 | 106–108 |
| Tungstênio (`tungsten`) | 1,5 | 112–114 |
| Óxido (`oxide`) | 1,2 | 118–119 |
| Carboneto (`carbide`) | 1,4 | 123–124 |

> O custo afeta apenas o **tempo** de construção, nunca a quantidade de itens (regra de `Item.cost`, confirmada em `docs/CATALOGO_MUROS_E_MURALHAS.md`, seção de custos).

---

## 3. Módulos de torre (30 blocos, hoje ocultos do menu de construção)

**[FATO]** Os 28 blocos de combate e as duas torres de reparo estão marcados como **somente módulo** (`moduleOnly`) e escondidos de toda construção: `Block.buildVisibility = hidden`, `inEditor = false` em `Blocks.hideModuleOnlyTurrets()` (`core/src/orbital/content/Blocks.java`, linhas 7044–7078). As receitas continuam declaradas porque o catálogo de loadout as lê.

**Colunas:** `Base` = tempo derivado da fórmula da seção 1 antes do multiplicador; `Final` = `Base × buildCostMultiplier` do bloco.

| Módulo | ID | Classe | Tamanho | Requisitos (itens) | Base | Mult. | Final (ticks) | Segundos | Linha (`Blocks.java`) |
|---|---|---|---:|---|---:|---:|---:|---:|---:|
| Duo | `duo` | `ItemTurret` | 1×1 | cobre 35 | 17,5 | 1 | 17,5 | 0,29 | 3277 |
| Scatter | `scatter` | `ItemTurret` | 2×2 | cobre 85, chumbo 45 | 74,0 | 1 | 74,0 | 1,23 | 3357 |
| Scorch | `scorch` | `ItemTurret` | 1×1 | cobre 25, grafite 22 | 34,5 | 1 | 34,5 | 0,58 | 3443 |
| Hail | `hail` | `ItemTurret` | 1×1 | cobre 40, grafite 17 | 37,0 | 1 | 37,0 | 0,62 | 3487 |
| Wave | `wave` | `LiquidTurret` | 2×2 | metavido 45, chumbo 75, cobre 25 | 132,5 | 1 | 132,5 | 2,21 | 3553 |
| Lancer | `lancer` | `PowerTurret` | 2×2 | cobre 60, chumbo 70, silício 60, titânio 30 | 157,0 | 1 | 157,0 | 2,62 | 3585 |
| Arc | `arc` | `PowerTurret` | 1×1 | cobre 50, chumbo 50 | 60,0 | 1 | 60,0 | 1,00 | 3626 |
| Parallax | `parallax` | `TractorBeamTurret` | 2×2 | silício 160, titânio 110, grafite 50 | 288,0 | 1 | 288,0 | 4,80 | 3668 |
| Swarmer | `swarmer` | `ItemTurret` | 2×2 | grafite 35, titânio 35, plastânio 45, silício 30 | 152,5 | 1 | 152,5 | 2,54 | 3683 |
| Salvo | `salvo` | `ItemTurret` | 2×2 | cobre 100, grafite 80, titânio 50 | 180,0 | 1 | 180,0 | 3,00 | 3759 |
| Segment | `segment` | `PointDefenseTurret` | 2×2 | silício 130, tório 80, tecido de fase 40, titânio 40 | 284,0 | 1 | 284,0 | 4,73 | 3864 |
| Tsunami | `tsunami` | `LiquidTurret` | 3×3 | metavido 100, chumbo 400, titânio 250, tório 100 | 790,0 | 1 | 790,0 | 13,17 | 3878 |
| Fuse | `fuse` | `ItemTurret` | 3×3 | cobre 225, grafite 225, tório 100 | 447,5 | 1 | 447,5 | 7,46 | 3943 |
| Ripple | `ripple` | `ItemTurret` | 3×3 | cobre 150, grafite 135, titânio 60 | 270,0 | 1 | 270,0 | 4,50 | 3983 |
| Cyclone | `cyclone` | `ItemTurret` | 3×3 | cobre 200, titânio 125, plastânio 80 | 329,0 | 1 | 329,0 | 5,48 | 4116 |
| Foreshadow | `foreshadow` | `ItemTurret` | 4×4 | cobre 1.000, metavido 600, liga de surto 300, plastânio 200, silício 600 | 2.500,0 | 1 | 2.500,0 | 41,67 | 4236 |
| Spectre | `spectre` | `ItemTurret` | 4×4 | cobre 900, grafite 300, liga de surto 250, plastânio 175, tório 250 | 1.552,5 | 1 | 1.552,5 | 25,88 | 4279 |
| Meltdown | `meltdown` | `LaserTurret` | 4×4 | cobre 1.200, chumbo 350, grafite 300, liga de surto 325, silício 325 | 1.795,0 | 1 | 1.795,0 | 29,92 | 4347 |
| Breach | `breach` | `ItemTurret` | 3×3 | berílio 150, silício 150, grafite 125 | **540,0 (explícito)** | 1 | 540,0 | 9,00 | 4384 (buildTime 4489) |
| Diffuse | `diffuse` | `ItemTurret` | 3×3 | berílio 150, silício 200, grafite 200, tungstênio 50 | 615,0 | 1 | 615,0 | 10,25 | 4496 |
| Sublimate | `sublimate` | `ContinuousLiquidTurret` | 3×3 | tungstênio 150, silício 200, óxido 40, berílio 400 | 913,0 | 1 | 913,0 | 15,22 | 4586 |
| Titan | `titan` | `ItemTurret` | 4×4 | tungstênio 250, silício 300, tório 400 | 1.055,0 | 1 | 1.055,0 | 17,58 | 4672 |
| Disperse | `disperse` | `ItemTurret` | 4×4 | tório 50, óxido 50, silício 200, berílio 350 | 695,0 | 1,5 | 1.042,5 | 17,38 | 4879 (mult. 5180) |
| Afflict | `afflict` | `PowerTurret` | 4×4 | liga de surto 100, silício 200, grafite 250, óxido 40 | 578,0 | 1,5 | 867,0 | 14,45 | 5063 (mult. 5180) |
| Lustre | `lustre` | `ContinuousTurret` | 4×4 | silício 250, grafite 200, óxido 50, carboneto 90 | 586,0 | 1 | 586,0 | 9,77 | 5186 |
| Scathe | `scathe` | `ItemTurret` | 4×4 | silício 450, grafite 400, tungstênio 500, óxido 100, carboneto 200 | 1.910,0 | 1 | 1.910,0 | 31,83 | 5251 |
| Smite | `smite` | `ItemTurret` | 5×5 | óxido 200, liga de surto 400, silício 800, carboneto 500, tecido de fase 300 | 2.450,0 | 1 | 2.450,0 | 40,83 | 5695 |
| Malign | `malign` | `PowerTurret` | 5×5 | carboneto 200, berílio 1.000, silício 500, grafite 500, tecido de fase 200 | 2.640,0 | 1 | 2.640,0 | 44,00 | 5965 |
| Repair Point | `repair-point` | `RepairTurret` | 1×1 | chumbo 30, cobre 30, silício 20 | 52,0 | 1 | 52,0 | 0,87 | 6428 |
| Repair Turret | `repair-turret` | `RepairTurret` | 2×2 | silício 90, tório 80, plastânio 60 | 238,0 | 1 | 238,0 | 3,97 | 6438 |

> **Nota sobre `malign`:** o código declara `size = 5` (linha 6043), enquanto o relatório de auditoria registra 9×9 como medição in-game (`docs/RELATORIO_AUDITORIA_A1_MODULOS.md`, seção 2.1). O tamanho não entra no valor de construção; o custo é o da receita, e a divergência de tamanho fica anotada aqui sem ser resolvida nesta sessão.

---

## 4. Módulos de suporte (13 blocos catalogados)

**[FATO]** As categorias e os blocos aceitos estão em `SupportModuleCategory.of()` (`core/src/orbital/type/SupportModuleCategory.java`, linhas 28–38). A conversão cria habilidades em `SupportMount.ability()` (`SupportMount.java`, linhas 145–180).

| Categoria | Módulo | ID | Classe | Tamanho | Requisitos (itens) | Final (ticks) | Segundos | Bloco base colocável? | Linha (`Blocks.java`) |
|---|---|---|---|---:|---|---:|---:|---|---:|
| `repair` | Mender | `mender` | `MendProjector` | 1×1 | chumbo 30, cobre 25 | 33,5 | 0,56 | Sim | 1922 |
| `repair` | Mend Projector | `mend-projector` | `MendProjector` | 2×2 | chumbo 100, titânio 25, silício 40, cobre 50 | 152,0 | 2,53 | Sim | 1935 |
| `overdrive` | Overdrive Projector | `overdrive-projector` | `OverdriveProjector` | 2×2 | chumbo 100, titânio 75, silício 75, plastânio 30 | 244,0 | 4,07 | Sim | 1947 |
| `overdrive` | Overdrive Dome | `overdrive-dome` | `OverdriveProjector` | 3×3 | chumbo 200, titânio 130, silício 130, plastânio 80, liga de surto 120 | 622,0 | 10,37 | Sim | 1955 |
| `force` | Force Projector | `force-projector` | `ForceProjector` | 3×3 | chumbo 100, titânio 75, silício 125 | 245,0 | 4,08 | Sim | 1967 |
| `tractor` | Parallax | `parallax` | `TractorBeamTurret` | 2×2 | silício 160, titânio 110, grafite 50 | 288,0 | 4,80 | Sim | 3668 |
| `pointDefense` | Segment | `segment` | `PointDefenseTurret` | 2×2 | silício 130, tório 80, tecido de fase 40, titânio 40 | 284,0 | 4,73 | Sim | 3864 |
| `unitRepair` | Repair Point | `repair-point` | `RepairTurret` | 1×1 | chumbo 30, cobre 30, silício 20 | 52,0 | 0,87 | Não (somente módulo) | 6428 |
| `unitRepair` | Repair Turret | `repair-turret` | `RepairTurret` | 2×2 | silício 90, tório 80, plastânio 60 | 238,0 | 3,97 | Não (somente módulo) | 6438 |
| `unitRepair` | Repair Tower | `unit-repair-tower` | `RepairTower` | 2×2 | grafite 90, silício 90, tungstênio 80 | 282,0 | 4,70 | Sim | 6620 |
| `liquid` | Wave | `wave` | `LiquidTurret` | 2×2 | metavido 45, chumbo 75, cobre 25 | 132,5 | 2,21 | Sim | 3553 |
| `liquid` | Tsunami | `tsunami` | `LiquidTurret` | 3×3 | metavido 100, chumbo 400, titânio 250, tório 100 | 790,0 | 13,17 | Sim | 3878 |
| `transport` | Mass Driver | `mass-driver` | `MassDriver` | 3×3 | titânio 125, silício 75, chumbo 125, tório 50 | 327,5 | 5,46 | Sim | 2169 |

**Observações de sobreposição:**

- `parallax`, `segment`, `wave` e `tsunami` **continuam colocáveis como blocos de base** (a lista `moduleOnly` cobre só as 28 torres de combate e `repair-point`/`repair-turret`), apesar de também serem aceitos como módulos de suporte — fonte: `Blocks.hideModuleOnlyTurrets()`, linhas 7061–7077.
- `build-tower` (`BuildTurret`, 3×3: silício 150, óxido 40, tório 60; 234 ticks / 3,90 s; linha 2000) **não tem categoria de suporte** e hoje não é módulo — apenas bloco de construção.

---

## 5. Módulos de arma de unidade (sem valor de construção em código)

**[FATO]** O catálogo `WeaponModules` (`core/src/orbital/content/WeaponModules.java`) não define receita: `Definition` guarda `size`, `cost` (pegada dorsal em px²), `name`, `ship`, `shot` e `tier`, e `copy()` apenas duplica a arma da unidade (linhas 61–89). **Não existe item de construção nem tempo associado.**

- **[FATO]** O campo `cost` exibido na UI é **espaço dorsal** (`size × 32 ao quadrado`, `TurretSlot.pxTile = 32`), não economia — mesma correção registrada em `docs/RELATORIO_AUDITORIA_A1_MODULOS.md`, seção 6.
- **[PROPOSTA — depende da etapa A3]** Se esses módulos passarem a ser construídos, o valor de partida precisa vir de uma tabela nova (por tier T1–T5, com curva ~quadrática conforme `PLANO_ETAPAS_MODULOS.md`, etapa A3). Nada disso está implementado; qualquer número seria invenção, por isso está fora das tabelas acima.

---

## 6. Leitura e lacunas

| Afirmação | Estado |
|---|---|
| Toda torreta oculta como módulo mantém receita declarada em `Blocks.java` | **[FATO]** — seções 3 e 4 |
| Toda receita tem tempo de construção derivado dos custos de item | **[FATO]** — `Block.init()`, linhas 1459–1471 |
| `breach` usa tempo explícito, não derivado; `disperse` e `afflict` têm `buildCostMultiplier = 1,5` | **[FATO]** — linhas 4489 e 5180 |
| `malign` continua sem poder ser módulo por dimensão/orçamento, não por receita | **[FATO]** — `RELATORIO_AUDITORIA_A1_MODULOS.md`, seção 2.1 |
| Módulos de arma de unidade têm valor de construção | **[ACHADO]** — não existe hoje (seção 5) |
| Instalar um módulo gasta itens | **[ACHADO]** — não existe; instalação runtime é gratuita |

**Para a etapa A3:** as tabelas das seções 3 e 4 são a base de valores pronta para ser transformada em custo de módulo (itens e/ou tempo), sem mexer no sistema de blocos. A conversão deve manter os números em um único lugar de conteúdo, com teste cobrindo "todo módulo tem custo e todo custo é alcançável pela cadeia produtiva".

---

## 7. Referências

- `core/src/orbital/content/Blocks.java` — receitas (linhas citadas por linha) e `hideModuleOnlyTurrets()` (linhas 7044–7078)
- `core/src/orbital/content/Items.java` — custos unitários dos itens
- `core/src/orbital/content/WeaponModules.java` — catálogo derivado de armas, sem receita
- `core/src/orbital/type/SupportModuleCategory.java` — categorias e blocos aceitos
- `core/src/orbital/entities/units/SupportMount.java` — conversão bloco → habilidade
- `core/src/orbital/world/Block.java` — fórmula de `buildTime` (linhas 1459–1471), exibição (linha 666), `moduleOnly` (linhas 369–375)
- `core/src/orbital/world/blocks/ConstructBlock.java` — `buildCost = block.buildTime × rules.buildCostMultiplier` (linha 451)
- `core/src/orbital/game/Rules.java` — `buildCostMultiplier` de partida (linha 117)
- `docs/RELATORIO_AUDITORIA_A1_MODULOS.md` — achado de que não existe custo de módulo (seção 6)
- `docs/PLANO_ETAPAS_MODULOS.md` — etapa A3, onde uma tabela de custo deve ser implementada
