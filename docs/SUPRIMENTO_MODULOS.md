# Suprimento dos módulos: munição, líquido e energia

**Data:** 2026-11-10 · revisões em 2026-11-11 (três pools separados, reparo aéreo, jato das torres líquidas, energia como leitura)
**Estado:** implementado em código, coberto por `tests/src/test/java/ModuleSupplyTest.java` (17 casos), `tests/src/test/java/SupportModuleEffectsTest.java` (15 casos) e pelas suítes de loadout existentes.
**Escopo:** armazenar, por casco, os recursos que os módulos dorsais usam — munição (item), líquido e energia, em pools separados — mostrar os três em barras coloridas no menu e no HUD, e fazer os módulos gastarem o que carregam.

---

## 1. Decisões do Peterson, nesta sessão

| Decisão | Escolha |
|---|---|
| Onde ficam os valores | Por casco (`WeaponsComp.moduleSupply`) |
| Energia | **Nunca é gasta** — a taxa do bloco só dimensiona a barra |
| Recurso escolhido | Escolha do jogador no menu (um seletor por pool de munição) |
| Capacidade/valores | Derivado do bloco (parâmetros que o bloco já declara) |
| Onde as barras | Área própria abaixo dos encaixes (menu) + três barras empilhadas no HUD |
| Quantas barras | **Três**, uma por pool: munição, líquido e energia, cada uma somando os valores dos módulos daquele tipo |
| Seleção de líquido | Disponível desde o primeiro módulo adicionado (2.3) |
| Reparo | Os módulos de reparo curam **unidades aéreas** (2.6) |
| Torres de líquido | Atiram de verdade: o jato é a munição líquida do bloco (2.7) |
| Carga da unidade | A logística gasta o pool do casco, nunca o item que a unidade carrega (2.4) |

## 2. O que o código faz

### 2.1 Três pools, cada um somando seus módulos

`core/src/orbital/type/ModuleSupply.java` guarda três pools independentes:

- **Munição (item)** — `item` (escolhido pelo jogador), `ammo`/`ammoCapacity`. Cada torre de item soma seu próprio buffer (`Turret.maxAmmo`) a essa barra.
- **Líquido** — `liquid`, `liquidAmount`/`liquidCapacity`. Cada torre de líquido e cada módulo de suporte líquido (`wave`, `tsunami`) soma seu `Block.liquidCapacity`.
- **Energia** — `energy`/`energyCapacity`. Todo consumidor de energia (torre de energia, projetor, massa driver) soma `Block.consPower.usage × ModuleSupply.energyTicks` (5 s) — a única constante nova, porque o consumidor do motor é taxa, não buffer.

Cada pool de munição é alimentado por um recurso escolhido entre os que os módulos instalados aceitam, e as escolhas são independentes: um casco pode ter torre de item **e** módulo de líquido ao mesmo tempo. Montar um módulo completa o pool dele até a capacidade; remover módulo reduz a capacidade junto. O pool vive no `WeaponsComp` como `transient`, no mesmo padrão session-only do loadout, e é recriado em `setupWeapons()` (casco que já nasce com módulo, como o Duo do mono) e recalculado em toda montagem/remoção (`refreshModuleSupply()`).

### 2.2 Derivação dos valores (`core/src/orbital/type/ModuleSupplies.java`)

| Pool | Origem do valor |
|---|---|
| Munição (item) | `Turret.maxAmmo` do bloco da torre |
| Líquido | `Block.liquidCapacity` do bloco (alcance de torres líquidas: `wave`, `tsunami`) |
| Energia | `Block.consPower.usage × 5 s` (`ModuleSupply.energyTicks`) — só o tamanho da barra |

### 2.3 Escolha do recurso

`WeaponsComp.ammoItemOptions()` lista **apenas** os itens aceitos por módulos montados (torres dorsais e suportes): nenhum recurso que nada no casco possa gastar entra na lista — é a mesma regra de compatibilidade do momento de montar.

`WeaponsComp.ammoLiquidOptions()` lista os líquidos dos módulos de líquido **instalados** e também dos que o casco ainda pode hospedar (`wave`/`tsunami` com âncora livre na categoria), então o seletor de líquido aparece **desde o primeiro módulo adicionado**, e não só depois de montar um líquido. Um casco sem âncora para a categoria líquida (um `mono`, por exemplo) não oferece líquido nenhum.

As duas listas são devolvidas **na ordem do conteúdo** (cobre antes de grafite, água antes de escória), porque a ordem do mapa de munição é ordem de hash e entregaria um padrão arbitrário ao pool. Cada pool tem seu próprio seletor no menu; clicar troca só aquele pool e recalcula todas as barras. Um recurso que deixa de ser oferecido cai no primeiro restante do seu pool.

Os dois grupos ficam separados por um traço e espaço próprios, com os rótulos "Munição compatível" e "Líquido compatível".

### 2.4 Como o valor é gasto

Cada pool é pago numa unidade diferente, porque cada custo tem uma natureza diferente (`WeaponsComp.update()` — o laço real do jogo):

- **Energia não é gasta.** A taxa do bloco só dimensiona a barra; nada sai do pool. Foi exatamente isso que quebrou as torres líquidas: `wave`/`tsunami` têm consumidor de energia, o pool esvaziou em segundos e o módulo ficou escuro para sempre — com a energia virando leitura, o jato não para mais. Um módulo dorsal não tem linha de energia para rodar, então cobrar energia só tiraria o módulo do jogador.
- **Munição** é um **custo por tiro**: cada tiro de uma torre dorsal custa o `ammoPerShot` do bloco, medido por `mount.totalShots` (uma rajada paga a rajada). Sem o mínimo para o próximo tiro, a torre fica carregada e calada.
- **Líquido** é um **custo por jato**: cada jato do módulo líquido cobra `1 / ammoMultiplier` do pool, a mesma relação do bloco. Acabou o líquido, o jato para.
- **Recarga**: sem cadeia logística ainda (nada leva item ou líquido a um módulo dorsal), os pools de munição e líquido recarregam sozinhos até a capacidade em `ModuleSupply.refillTicks` (600 ticks = 10 s). Um casco que para de atirar recupera; um que atira sem parar fica no nível em que custo por tiro e recarga se equilibram. Quando existir reabastecimento real (núcleo, lançamento de munição), é esse número que sai.
- **A carga da unidade é intocável**: o módulo de logística lança o que está no pool do casco e nunca mexe no `stack` da unidade — o que a unidade carrega é carga dela, não suprimento de módulo. Testado em `massDriverModuleLaunchesTheHullStoresAndKeepsTheUnitCargo`.

### 2.5 Onde as barras aparecem

- **Menu (`TurretLoadoutDialog`)**: área própria na última posição do trilho, abaixo dos encaixes dorsais e dos de suporte — painel com fundo, título "Suprimento dos módulos", um seletor por pool de munição (só aparece quando algum módulo aceita aquele tipo) e **três barras** empilhadas, altas o suficiente para ler os números. Cada barra tem a cor do recurso escolhido (cinza neutro quando nada alimenta aquele pool); energia usa a cor de energia do jogo.
- **HUD (`HudFragment`)**: três barras **empilhadas**, numa linha própria **acima** dos ícones dos módulos, na barra dorsal do casco. Cada barra tem o **nome do pool escrito dentro dela** ("Munição", "Líquido", "Energia") — 18 unidades de altura (14 no celular), porque o `Bar` desenha o rótulo com a fonte de contorno do jogo — e os números num rótulo pequeno ao lado, na mesma cor. A largura das barras é a de **cinco células de módulo** (`5 × (tamanho da célula + padding)`), calculada a cada reconstrução.
- **Alinhamento**: a linha das barras e a linha dos módulos ficam em **tabelas separadas** dentro do mesmo painel. Isso é obrigatório: num `Table` do arc, a largura da primeira coluna vale para todas as linhas, então a barra larga na mesma tabela criava um vão entre o primeiro e o segundo módulo.
- **Cards e linhas de módulos**: sem barras.

### 2.6 Reparo passa a curar unidades aéreas

Os módulos de reparo montados no casco curam **unidades voadoras**: `RepairFieldAbility` (mender, mend-projector, torre de reparo de unidades) e `RepairBeamAbility` (ponto de reparo, torre de reparo) têm a marca `airOnly`, ligada em `SupportMount.createAbility()`. Um campo de reparo dorsal pertence a um casco aéreo, então serve a asa com que ele voa — o análogo do bloco, que repara as construções em volta, não existe numa unidade. As outras categorias (sobrecarga, campo de força, trator, ponto de defesa, líquido, logística) seguem iguais.

### 2.7 Torres de líquido atiram

`LiquidSupportAbility` (wave, tsunami) deixou de ser só um campo de status: a cada `reload` ticks ela mira o inimigo mais próximo que o bloco miraria (`targetAir`/`targetGround` do bloco) e **dispara a munição líquida do bloco** — mesmo projétil, mesmo dano, mesmo status, mesmos efeitos. O líquido vem do pool do casco (o líquido escolhido na barra) e cada jato o cobra, como o bloco cobra o dele. Enquanto jorra, ela mantém o papel de suporte do bloco: apaga fogo em aliados e aplica o status do líquido nos inimigos dentro do campo.

## 3. O que **não** foi feito

1. **Sem custo de instalação.** Montar/remover módulo continua gratuito (a receita do bloco segue documentada em `CATALOGO_VALORES_CONSTRUCAO_MODULOS.md`).
2. **Sem persistência.** Os pools são `transient`, no mesmo padrão do loadout.
3. **Multiplicadores de regra** (`Rules.buildCostMultiplier`, `buildSpeed`) não afetam os pools.
4. **A recarga é provisória** — ver 2.4.

## 4. Verificação

| Suíte | Testes | Resultado |
|---|---:|---|
| `ModuleSupplyTest` | 17 | 0 falhas |
| `SupportModuleEffectsTest` (novo) | 15 | 0 falhas |
| `SupportLoadoutTest` | 43 | 0 falhas |
| `TurretAmmoCompatibilityTest` | 6 | 0 falhas |
| `TurretLoadoutDialogTest` | 7 | 0 falhas |
| `TurretLoadoutSmokeTest` | 7 | 0 falhas |
| `TurretModuleShootingTest` | 3 | 0 falhas |
| `WallLoadoutTest` | 22 | 0 falhas |
| `WeaponModuleTest` | 8 | 0 falhas |

`SupportModuleEffectsTest` exercita **cada** categoria de suporte pelo laço real (`Groups.unit.update()`): campo de reparo, projetor de reparo, trator, ponto de defesa, feixe de reparo, torre de reparo, módulo líquido (jato + gasto de líquido + status + jato contínuo após 800 pulsos), logística, sobrecarga e campo de força — e prova que os reparos curam voadores e ignoram terrestres.

`ModuleSupplyTest` cobre: pools vazios sem módulos; torre de item somando seu buffer; **duas torres somando na mesma barra**; torre de energia alimentando só a energia; suporte somando sua própria energia; `wave` (suporte líquido) alimentando **apenas** a barra de líquido; torre de item e módulo líquido rodando juntos; escolher um recurso sem mexer no outro pool; **seleção de líquido disponível com apenas um módulo montado**; recurso escolhido precisa estar entre os oferecidos; **energia que nunca sai do pool**; **pool de energia vazio não silenciando módulo**; **recarga até a capacidade**; **cada tiro pagando o `ammoPerShot`**; `clamp()` por pool; e a coerência com a regra de compatibilidade de munição.
