# Relatório comparativo — Módulos e Construção: Dark Nebula vs. Orbital

**Data:** 23/11/2026
**Escopo:** sistemas de módulos de navios e construção/economia do **Hades' Star: DARK NEBULA** (Parallel Space Inc.) comparados ao estado atual do **Orbital** (inclui as mudanças mais recentes: brocas como módulos, fábricas aéreas com evolução por nível e construção gratuita no modo Novidades).

> **Fontes e reliability:** dados do Dark Nebula vêm da [Google Play](https://play.google.com/store/apps/details?id=com.ParallelSpace.Cerberus2) (descrição oficial, 60+ módulos), do [blog de desenvolvimento da Parallel Space](https://blog.hadesstar.com/2023/10/dark-nebula-upgrade-guide.html) (guia de transição 2023) e do post [Module changes in DARK NEBULA](https://blog.hadesstar.com/2022/10/module-changes-in-dark-nebula.html). Contagens de módulos, estações, níveis e regras vêm da wiki comunitária [hadesstar.wiki](https://hadesstar.wiki/modules), que declara usar dados da versão **6.600.0** do app — valores podem mudar com ajustes de servidor. O lado Orbital foi lido diretamente do código-fonte deste repositório.

---

## 1. Visão geral

| | Dark Nebula | Orbital |
|---|---|---|
| Gênero | Estratégia espacial online persistente, servidor compartilhado, corporações | Automação/sandbox tático (derivado de Mindustry), jogo local com online opcional |
| Plataformas | Android, iOS, console (PS/Xbox), Windows via loja | Windows 10/11 64 bits e Android 7+ (mesmo código base) |
| Escala | Galáxia persistente com estados Amarelo/Vermelho/Azul/Branco/Quântico; até 20 jogadores | Mapa 2D por partida; modos sobrevivência, sandbox, ataque, PvP, Novidades e Battle Royale 8x8 |
| Unidade de progressão | Império: navios (Battleship/Miner/Transport), estações, planetas, módulos e Corporação | Cascos de unidade, edifícios, tech tree, módulos dorsais e nível de edifício |
| Tempo | Sessões curtas + guerras de dias (White Star 5 d, Quantum Star meses) | Partida por sessão; sessão de teste descartável no modo Novidades |

---

## 2. Módulos no Dark Nebula

### 2.1 Catálogo (60 módulos, 6 categorias)

| Categoria | Qtd. | Exemplos | Onde pode ser equipado |
|---|---:|---|---|
| WEAPON | 9 | Laser, Mass Battery, Battery, Dual Laser, Barrage, Dart Launcher, Chain Ray, Rocket Launcher, Pulse | Battleship |
| SHIELD | 8 | Alpha, Impulse, Regenerating, Omega, Blast, Mirror, Ally, Motion Shield | Battleship |
| COMBAT (antigo SUPPORT) | 14 | EMP, Solitude, Fortify, Teleport, Weapon Amplifier, Barrier, Destiny, Delta Rocket, Vengeance, Bond, Leap, Suspend, Omega Rocket, Remote Bomb | **Somente Battleship** (a partir da 2ª edição) |
| DRONE | 8 | Decoy/Repair/Rocket Drone, Laser Turret, Cerberus Drone, Chain Ray Turret, Delta Drones, Drone Squad | Battleship |
| MINING | 10 | Mining Boost, Artifact Boost, Hydrogen Replicator, Genesis, Remote Mining, Enrich, Crunch, Hydrogen Upload, Hydrogen Rocket, Blast Drone | Miner |
| TRANSPORT | 11 | Cargo Bay Extension, Shipment Computer, Cargo Repair, Rush, Stealth, Trade Burst, Shipment Drone, Red Star Life Extender, Relic Drone, Dispatch, Cargo Rocket | Transport |

**Filosofia de design declarada pelos desenvolvedores:** módulos que "escondiam" sistemas do jogo foram removidos e o efeito foi incorporado ao base game. Exemplos: *Time Warp* → substituído por *Weapon Amplifier* (aumenta todas as armas em alcance); *Sanctuary* → removido; *Teleport* → exclusivo de Battleship; *Barrier* deixou de ser "pausa obrigatória". O objetivo é que **todos** os módulos sejam desejáveis e que existam loadouts diferentes por tipo de estrela.

### 2.2 Progressão

- Artefatos de **Red Stars** (6 tipos: WEAPON, SHIELD, COMBAT, DRONE, MINING, TRANSPORT) são depositados na **Research Station** e viram **Blueprints**.
- Blueprints **não são consumidos**: cada nível exige um total acumulado que você precisa possuir, mais **Créditos** e **tempo de pesquisa**. Nível máximo: **15**.
- **Desequipar/reinstalar módulos é grátis**, o que incentiva experimentação.
- Número de slots por navio cresce com o nível da Red Star; cada tipo de navio tem slots especializados (Miner só MINING, Transport só TRANSPORT, Battleship COMBAT/WEAPON/SHIELD/DRONE).
- Economia: **Credits** (envios/shipments, planetas, estações de comércio), **Hydrogen** (restrição para saltos, mineração e entregas — capped e renovável), **Artifacts/Blueprints**, **Relics** (White Star), **Empire Power**, **Crystals** (aceleração, compra).

### 2.3 Construção no Dark Nebula

- Sistema estelar **Amarelo** é a base: colonize e suba de nível planetas, construa estações, descubra setores com scanners.
- Estações principais: **Shipyard** (10 níveis — define capacidade da frota), **Research Station**, **Short/Red/Blue/White Star Scanner**, **Trade Station** (10), **Shipment Relay** (11), **Star Harvester** (11/16), **Warp Lane Hub** (22), **Diplomacy Station**, **Jump Gate**, **Quantum Star Gate**, **Red Star Base** (20), entre outras.
- Construção custa **Credits + tempo**; é possível acelerar com **Crystals**. Estações podem ser realocadas por Credits.
- Regras de posicionamento: distância mínima de **150 AU** entre estações, **160 AU** de planetas, **60 AU** de campos de asteroides e **120 AU** da estrela; no máximo 14 campos de asteroides por setor.
- Sua estrela caseira é **segura offline**: ninguém ataca sua base enquanto você não está.
- Construir navios e subir o nível deles também usa Credits + tempo no Shipyard; **módulos são pesquisados separadamente** das melhorias do casco.

---

## 3. Módulos no Orbital

### 3.1 Quatro famílias de módulos montáveis

1. **Módulos de torreta** — blocos `Turret` adaptados via `TurretWeapon`: 28 torretas de combate + 2 de reparo (`repair-point`, `repair-turret`). São `moduleOnly` (escondidas da construção; ground/naval ainda usam reconstrutores).
2. **Módulos de arma nativa** — cópias das armas do próprio casco (`WeaponModules`), com sufixo **T1–T5** no nome, usadas apenas para ordenar/exibir no catálogo.
3. **Módulos de suporte** — 10 categorias em `SupportModuleCategory`: `repair`, `overdrive`, `force`, `tractor`, `pointDefense`, `unitRepair`, `liquid`, `transport`, **`mining`** (brocas) e `armor` (placas). Regras: **um por categoria**, exceto armaduras, que empilham.
4. **Placas de armadura** — paredes grandes (2x2) ocupam **slots de torreta** e orçamento dorsal; aparecem como tintura do casco.

### 3.2 Geometria e regras de montagem

- `UnitType.turretSlots` e `UnitType.supportAnchors` são posições calibradas por casco (ex.: `mono` 1 slot; `quad` 2 slots + 9 âncoras; `oct` 5 slots + âncoras).
- Orçamento dorsal: `dorsalBudget() = spriteArea() * 0.5 * loadoutBudgetMultiplier` (ex.: `oct` tem multiplicador 0,85 e área 160.000 px²).
- Validação usa `DorsalGeometry.fitsHull`, `fitsSilhouette`, sobreposição por footprint desenhado e compatibilidade de munição (`ammoCompatible`). Âncoras de categorias diferentes podem compartilhar a mesma coordenada, mas só um módulo é instalado por posição.
- No HUD: barra inferior com **uma célula por grupo de módulos idênticos (selo ×N)**, barra de recarga/calor, teclas numéricas, alternância automático/manual, botão de soltar (X vermelho no 8x8).
- Pools de suprimento derivados dos módulos: **munição, líquido e energia** (`ModuleSupply` / `ModuleSupplies`); energia é leitura, não gasto.
- No Battle Royale 8x8, módulos caídos no setor são instalados por clique/toque e podem ser soltos de volta.

### 3.3 Progressão e construção no Orbital

- Edifícios são colocados livremente no mapa em grade, com **custos em itens**, energia, líquidos e transporte por esteiras/tubulações.
- **Brocas são módulos** desde a última mudança: não existem mais brocas-prédio; todas dão bônus de velocidade/alcance de mineração ao casco.
- **Fábricas de unidades aéreas (e o ship-fabricator de Erekir) evoluem por nível**: T1→T5 trocando o sprite pelo do reconstrutor correspondente, pagando o custo da receita; podem montar qualquer casco de tier ≤ nível e evoluir uma unidade alimentada (consome a unidade + recursos, com progresso). Reconstrutores continuam construíveis para ground/naval.
- **Nível de edifício** (`BuildingLevels`, até **100**): custo escala a partir dos requisitos do bloco, pago do núcleo, com evolução cronometrada; torretas, silos e núcleos aplicam multiplicadores.
- **Construção manual**: o jogador coloca cada edifício pelo menu; não há planejador que escolha fontes, unloaders ou transporte automaticamente. No modo Novidades a construção é **gratuita** (recursos infinitos por time, sem alcance ilimitado nem mudança de gamemode).
- Modos: sobrevivência, sandbox, ataque, PvP, editor, **Novidades** (showcase com tutorial e Ferramentas de teste) e **Battle Royale 8x8**.
- Extras técnicos: tech tree, esquemáticos, editor de mapas, lógica por script, mods via JAR, replay de eventos, RNG determinístico por tick, suporte OpenGL/Vulkan, multiplayer via ArcNet/relay Cloudflare opcional, política de privacidade sem telemetria.

---

## 4. Equivalências diretas

| Dark Nebula | Orbital | Grau de equivalência |
|---|---|---|
| Slot WEAPON (Laser, Barrage, Rocket…) | `TurretWeapon` + módulos de arma nativa (`WeaponModules`) | **Alto** — armas no casco, com categorias e munição |
| Slot SHIELD (Alpha, Omega, Blast…) | `SupportModuleCategory.force` (ForceProjector → ForceFieldAbility) + placas `armor` | **Médio** — escudo de área/tintura em vez de camada por navio |
| Slot COMBAT (EMP, Teleport, Fortify, Bond…) | `repair`, `unitRepair`, `tractor`, `pointDefense`, `overdrive` | **Parcial** — Orbital não tem teleporte/EMP/Bond como módulo; tem campos e reparo |
| Slot DRONE (Decoy/Repair/Rocket Drone…) | **Sem equivalente direto** — o mais próximo é `unitRepair` (RepairBeam/RepairTower) e unidades aliadas controladas por IA | **Baixo** — lacuna clara |
| Slot MINING (Mining Boost, Genesis, Enrich…) | Categoria `mining`: 8 brocas como módulo, `MiningModuleAbility`, mineração universal das unidades | **Alto** (já que as brocas viraram módulos) |
| Slot TRANSPORT (Cargo Bay, Rush, Trade Burst…) | Categoria `transport` (`mass-driver`) + inventário da unidade | **Médio** — a logística de esteiras/unloaders ficou oculta do menu; o transporte se dá por módulos e inventário |
| Blueprint + Research Station (nível 15) | Tech tree + blocos como fonte de módulo (sem nível de módulo) | **Baixo** — Orbital não tem níveis de módulo; tem nível de edifício (100) e nível de fábrica (5) |
| Shipyard (capacidade da frota) | `UnitFactory` + limites de unidade (`unitCap`) e evolução por nível | **Médio** |
| Planetas/estações com Credits + tempo | Blocos com itens + tempo + energia; `BuildingLevels` para evolução | **Médio** |
| Relógio de supernova / tempestade | `waves`, `attackMode`, Battle Royale sem núcleos | **Diferente** — Orbital não usa "estrelas que colapsam" |

---

## 5. Recursos extras que o Orbital tem (e o Dark Nebula não tem ou faz diferente)

1. **Brocas como módulo dorsal** — o Dark Nebula usa módulos *de mineração* para melhorar a taxa, mas mantém a mineração como atividade de nave; no Orbital a broca virou literalmente um módulo montável com tier, velocidade e alcance.
2. **Fábricas que evoluem de nível com sprite próprio** — T1→T5 trocando para os sprites dos reconstrutores, e podendo evoluir uma unidade dentro da própria fábrica.
3. **Nível de edifício até 100** com evolução cronometrada, multiplicadores de saúde/capacidade/velocidade e custo escalado a partir dos requisitos do bloco.
4. **Sistema de slots/âncoras com geometria real** — footprint, silhueta, sobreposição, orçamento dorsal e compatibilidade de munição; o Dark Nebula usa slots fixos por tipo de nave sem geometria.
5. **Módulos idênticos agrupados (×N)** e controle por número/auto-alvo, com barra de recarga individual — o Dark Nebula trata módulos como slots independentes.
6. **Construção gratuita no modo Novidades** — resources infinitos por time para testar sem economia, mantendo regras normais de alcance e velocidade.
7. **Battle Royale 8x8 com loot de módulos pelo chão**, sem núcleos, última esquadra viva vence.
8. **Cadeia produtiva montada à mão** — o jogador escolhe minério, crafter, energia e transporte; o Dark Nebula foca em operações manuais assíncronas.
9. **Lógica programável, esquemáticos, editor de mapas e mods via JAR**, além de replay de eventos e RNG determinístico por tick — ferramentas de criação ausentes no DN.
10. **Arquitetura multiplataforma real (Windows + Android) com um único código base**, OpenGL e Vulkan, e **privacidade sem telemetria** (política pública no próprio jogo).
11. **Suporte offline e LAN/personalizado**, enquanto o DN exige conexão e servidor persistente.
12. **Economia de itens modular e transporte físico** (esteiras, tubos, unloaders, drones de construção) em vez de Credits/Hydrogen centralizados.

---

## 6. Lacunas do Orbital frente ao Dark Nebula

- **Progressão de módulos por nível** (1→15 com blueprints acumulados, créditos e tempo de pesquisa) — hoje o módulo é apenas "obtido ao pesquisar o bloco", sem níveis.
- **Restrição por classe de nave** (Miner/Transport/Battleship com slots especializados e COMBAT exclusivo) — no Orbital qualquer casco aceita quase qualquer módulo que caiba.
- **Drone modules** (decoy, reparo, foguete, torreta) como categoria própria.
- **Galáxia persistente e corporações** com guerras assíncronas de vários dias, Time Machine e relíquias — o Orbital é orientado a partidas.
- **Economia de artefatos/blueprints** com comércio horizontal entre jogadores.
- **Aceleração paga (Crystals)** e sistema de monetização — o Orbital é GPLv3 e sem telemetria.

---

## 7. Conclusão

Os dois jogos resolvem problemas parecidos com abordagens opostas:

- **Dark Nebula** aposta em **metaprogressão de longo prazo**: módulos são o centro do jogo, com níveis, blueprints, slots por classe de nave e um metajogo persistente de corporações/estrelas.
- **Orbital** aposta em **agência dentro da partida**: o jogador desenha a nave (geometria, slots, orçamento), automatiza a fábrica, controla unidades e pode até evoluir a própria fábrica — com foco em sandbox, criação e partidas curtas.

As ideias do DN que melhor "encaixariam" no Orbital, sem quebrar a identidade do projeto, são: (1) níveis de módulo com custo acumulado (o sistema de `getTotalRequirements` já existe e poderia ser reutilizado), (2) uma categoria de **drone modules**, e (3) restrições de loadout por classe de casco para criar decisões mais interessantes — todas compatíveis com o sistema de âncoras atual.

---

### Referências

- Google Play — Hades' Star: DARK NEBULA (descrição oficial): https://play.google.com/store/apps/details?id=com.ParallelSpace.Cerberus2
- Parallel Space — *Dark Nebula upgrade guide* (04/10/2023): https://blog.hadesstar.com/2023/10/dark-nebula-upgrade-guide.html
- Parallel Space — *Module changes in DARK NEBULA* (12/10/2022): https://blog.hadesstar.com/2022/10/module-changes-in-dark-nebula.html
- Hades' Star Wiki (comunitária, dados v6.600.0) — Modules, Ships, Space stations, How to play, Mechanics, Artifacts: https://hadesstar.wiki/
- GamingOnPhone — lançamento Android/iOS (10/11/2023): https://gamingonphone.com/news/hades-star-dark-nebula-a-space-strategy-game-is-now-available-on-android-and-ios/
- Código-fonte do Orbital: `core/src/orbital/{type,content,game,ui,world}/...`, `tests/src/test/java/...`

---

## 8. Anexo — comparativo lado a lado de cada equivalência

Cada tabela compara o mecanismo correspondente nos dois jogos. "Equivalência" mede o quanto a **função** é a mesma, não o quanto o código se parece.

### E1 — Armas (WEAPON ↔ torretas/armas nativas)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Catálogo | 9 módulos: Laser, Mass Battery, Battery, Dual Laser, Barrage, Dart Launcher, Chain Ray, Rocket Launcher, Pulse | 28 torretas adaptadas (`TurretWeapon`) + cópias das armas dos cascos (`WeaponModules`, T1–T5) | Alta |
| Onde vai | Slot WEAPON do Battleship | Slot de torreta ou mount nativo do casco | Igual em espírito |
| Espaço | Slot fixo, sem geometria | Footprint, silhueta, orçamento dorsal e sobreposição (`DorsalGeometry`) | Orbital mais complexo |
| Munição | Sem munição por módulo; dano/cooldown | Item/líquido com compatibilidade e pool compartilhado (`ModuleSupply`) | Orbital mais profundo |
| Seleção de alvo | Automática pelo jogo | Automática ou manual **por módulo**, com alternância no HUD | Orbital dá mais controle |
| Progressão | Nível 1–15 por blueprints | Sem nível de módulo; desbloqueio vem da tech tree do bloco | DN mais profundo |

### E2 — Escudos (SHIELD ↔ `force` + `armor`)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Catálogo | 8 escudos: Alpha, Impulse, Regenerating, Omega, Blast, Mirror, Ally, Motion | ForceProjector → `ForceFieldAbility`; placas grandes → categoria `armor` | Média |
| Como funciona | Parte do dano passa (ex.: Alpha deixa 95% no Quantum Star; Motion deixa 60%) | Escudo poligonal com lados/raio/regeneração; placas somam pontos de vida e aplicam tintura | Diferente |
| Abrangência | Protege o próprio navio (Ally Shield protege aliados em alcance) | Protege unidades aéreas e construções próximas | Parecido |
| Combinabilidade | Vários escudos empilháveis | **Um** módulo `force` por casco; placas `armor` empilham | Orbital mais restritivo |
| Veredito | Camada de defesa reativa | Defesa de área + armadura acumulável | Média |

### E3 — Combate/suporte (COMBAT ↔ `repair`/`unitRepair`/`tractor`/`pointDefense`/`overdrive`)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Catálogo | 14 módulos: EMP, Solitude, Fortify, Teleport, Weapon Amplifier, Barrier, Destiny, Delta Rocket, Vengeance, Bond, Leap, Suspend, Omega Rocket, Remote Bomb | Cinco categorias de suporte, cada uma com 1–3 blocos (mend/overdrive/force/parallax/segment/repair-point/repair-turret/unit-repair-tower) | Parcial |
| Restrição de navio | **Somente Battleship** | Qualquer casco que tenha a âncora da categoria | Orbital mais livre |
| Tipo de efeito | Controle: desativar, teleportar, prender, pausar, amplificar | Suporte: curar, boostar, defender, rebocar aliado, interceptar projéteis | Função diferente |
| O que falta no Orbital | — | Teleport, EMP, Bond, Leap, Suspend, Destiny, Vengeance e Amplifier não existem como módulo | Lacuna clara |
| O que o Orbital tem e o DN não | — | Cura auto-alvo para aliados, defesa de pontos e rebocador de unidades com comportamento próprio | Contrapartida |

### E4 — Drones (DRONE ↔ sem categoria equivalente)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Catálogo | 8 módulos: Decoy, Repair, Rocket Drone, Laser Turret, Cerberus Drone, Chain Ray Turret, Delta Drones, Drone Squad | Não existe categoria de drone; o mais próximo são `unitRepair` (RepairBeam/RepairTower) e unidades aliadas com IA | **Baixa** |
| Entidade | Cria drones persistentes no mapa, com vida e comportamento próprio | Habilidade anexada ao casco; não cria entidades autônomas | Diferente |
| Defesa / distração | Decoy atrai fogo inimigo | `tractor` e `pointDefense` fazem papel parcial (atração/interceptação) | Parcial |
| Ataque | Rocket Drone, Laser Turret, Chain Ray Turret | Módulos de torreta montados no casco | Diferente |
| Veredito | Categoria inteira dedicada a entidades destacáveis | Orbital concentra tudo no próprio casco | Maior lacuna do comparativo |

### E5 — Mineração (MINING ↔ categoria `mining`)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Catálogo | 10 módulos: Mining Boost, Artifact Boost, Hydrogen Replicator, Genesis, Remote Mining, Enrich, Crunch, Hydrogen Upload, Hydrogen Rocket, Blast Drone | 8 brocas como módulo + `MiningModuleAbility` + mineração universal das unidades | Alta |
| Recurso | **Hydrogen** (com teto e renovação diária) | Itens: cobre, chumbo, titânio, tório, etc. | Diferente |
| Ação | Miner nave viaja até asteroides e extrai | Unidade aponta laser para o tile e recebe o item | Diferente |
| Progressão | Nível 1–15 do módulo | Tier 2–7 da broca vira multiplicador de velocidade e bônus de alcance | Função equivalente |
| Combinação | Vários módulos de mineração em slots de Miner | **Uma broca por casco** (categoria exclusiva) | Orbital mais restritivo |

### E6 — Transporte (TRANSPORT ↔ `transport` + logística)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Catálogo | 11 módulos: Cargo Bay Extension, Shipment Computer, Cargo Repair, Rush, Stealth, Trade Burst, Shipment Drone, Red Star Life Extender, Relic Drone, Dispatch, Cargo Rocket | Categoria `transport` (`MassDriver` → `MassDriverAbility`) + inventário da unidade e offload no núcleo | Média |
| Objetivo | Entregar shipments por **Credits**, com bônus e custo de Hydrogen | Mover itens fisicamente para núcleo/unidades/other buildings | Igual em intenção, diferente em escala |
| Automação | Shipment Relay entrega tudo instantaneamente por Hydrogen | Cadeia produtiva montada manualmente pelo jogador | DN automático; Orbital manual |
| Velocidade | Rush reduz consumo de Hydrogen do Transport | Unidades têm velocidade própria; módulos não alteram combustível | Diferente |
| Veredito | Logística centrada em entrega e economia | Logística centrada em fluxo físico de itens | Média |

### E7 — Progressão de módulos (Blueprints ↔ Tech tree / custo acumulado)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Fonte | Artefatos de Red Star → Research Station → Blueprints de 6 tipos | Tech tree (`SerpuloTechTree`, `ErekirTechTree`) + pesquisa de blocos | Diferente |
| Consumo | Blueprints **não são gastos**: cada nível exige um total acumulado | Não há nível de módulo; o módulo existe após desbloqueio do bloco | Diferente |
| Nível máximo | 15 | Nenhum para módulos (edifícios chegam a 100; fábricas a 5) | DN mais profundo |
| Custo extra | Credits + tempo de pesquisa | Itens de construção + tempo + energia | Análogo em espírito |
| Reutilização | `UnitType.getTotalRequirements()` no Orbital já faz soma acumulada — poderia sustentar níveis de módulo | Base técnica existe | Oportunidade de design |

### E8 — Construção/estações (sistema estelar ↔ blocos no mapa)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Onde se constrói | Sistema estelar Amarelo, com planetas e setores | Mapa 2D em grade (512x512 no BR, 256x256 showcase) | Diferente |
| Unidade | Estações com nível e custo em Credits + tempo | Blocos com custo em itens + energia + líquidos | Média |
| Exemplos | Shipyard, Research Station, scanners, Trade Station, Shipment Relay, Star Harvester, Warp Lane Hub | Fábricas, torretas, núcleos, esteiras, tubos, brocas-módulo, unidades | Diferente |
| Regras espaciais | Distância mínima: 150 AU entre estações, 160 de planetas, 60 de asteroides, 120 da estrela | Grade, footprint, colisão, `Build.validPlace` | Orbital mais granular |
| Evolução | Estações sobem de nível (Shipyard 10, Star Harvester 11/16, Red Star Base 20) | `BuildingLevels` até 100, custo escalado `1 + (nível-1)*0.35` | Orbital vai mais longe |
| Automação | Shipment Relay acelera entregas | Cadeias completas montadas à mão | DN mais automático |
| Segurança offline | Casa é inviolável offline | Depende do modo; BR e PvP têm combate | Diferente |

### E9 — Fábricas de navios (Shipyard ↔ UnitFactory evoluído)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Função | Shipyard define capacidade da frota; níveis 1–10 | `UnitFactory` produz unidades com planos e tempo; capacidade via `unitCap` | Média |
| Progressão | Nível do Shipyard com Credits + tempo | Nível T1→T5 com custo do reconstrutor e troca de sprite | Análogo |
| Unidades | Transport, Miner, Battleship (7 níveis cada) | Cascos aéreos/terrestres/navais com tiers T1–T5 | Parcial |
| Evolução de unidade | Navios sobem de nível no shipyard | Fábrica aérea evolui uma unidade alimentada: consome a original + recursos, gera a de tier maior | Orbital único nesse ponto |
| Reconstrutores | Não existem (módulos cuidam da progressão) | Continuam para ground/naval; aéreos viraram evolução da fábrica | Diferente |

### E10 — Slots e encaixe (slots fixos ↔ geometria dinâmica)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Modelo | Slots por classe de nave; número cresce com Red Star level | Slots de torreta + âncoras de suporte por casco, com posições calibradas | Função igual, implementação oposta |
| Restrição | Classe define o que cabe | `SupportAnchor.accepts`, `TurretSlot.accepts`, tamanho, footprint, silhueta | Orbital muito mais rígido geometricamente |
| Quantidade exemplar | Battleship tem slots de WEAPON/SHIELD/COMBAT/DRONE | `mono`: 1 slot; `quad`: 2 slots + 9 âncoras; `oct`: 5 slots + âncoras | — |
| Categoria exclusiva | COMBAT só em Battleship | Uma por categoria de suporte; `armor` empilha | Parcial |
| Veredito | Simples e legível | Poderoso e complexo | Diferente, não inferior |

### E11 — Loot e aleatoriedade (artefatos ↔ drops do 8x8)

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Origem | Red Stars (10 min, supernova) | Battle Royale 8x8 (32 drops iniciais + 1 a cada 30 s) | Análogo |
| Tipos | 6 tipos de artefato (WEAPON, SHIELD, COMBAT, DRONE, MINING, TRANSPORT) | 11 tipos de `ModuleDropKind` (inclui `mining` e `weapon`) | Orbital mais granular |
| Resultado | Research → blueprints | Instala o módulo direto no casco | Diferente |
| Risco | Naves pegas na supernova são perdidas | Sem núcleos; última esquadra viva | Diferente |

### E12 — Economia e pressão de tempo

| Aspecto | Dark Nebula | Orbital | Equivalência |
|---|---|---|---|
| Recursos | Credits, Hydrogen, Blueprints, Relics, Empire Power, Crystals | Itens (minério), energia, líquidos, suprimentos de módulos | Diferente |
| Tempo | App diário: entregas, Blue Stars, Research; guerras de dias | Partida por sessão; construção manual pelo jogador em tempo real | Diferente |
| Pressão | Supernova em minutos; White Star 5 dias | Waves/ataque/BR; modo Novidades sem pressão e sem custo | Orbital mais casual |
| Aceleração | Crystals | `BuildingLevels` com evolução cronometrada; nada pago | — |
| Veredito | MMO com economia persistente | Sandbox/tático autossuficiente | Diferente por design |
