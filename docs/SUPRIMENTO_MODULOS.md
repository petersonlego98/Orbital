# Suprimento dos módulos: munição, líquido e energia

**Data:** 2026-11-10 (três pools separados em 2026-11-11)
**Estado:** implementado em código, coberto por `tests/src/test/java/ModuleSupplyTest.java` (12 casos) e pelas suítes de loadout existentes (108 casos no grupo torre/suporte/muralha/arma).
**Escopo:** armazenar, por casco, os recursos que os módulos dorsais usam — munição (item), líquido e energia, em pools separados — e mostrar os três em barras coloridas no menu de módulos e no HUD.

---

## 1. Decisões do Peterson, nesta sessão

| Decisão | Escolha |
|---|---|
| Onde ficam os valores | Por casco (`WeaponsComp.moduleSupply`) |
| Consumo em jogo | Só exibir: pools preenchidos e lidos; nada é drenado |
| Recurso escolhido | Escolha do jogador no menu (um seletor por pool de munição) |
| Capacidade/valores | Derivado do bloco (parâmetros que o bloco já declara) |
| Onde as barras | Área própria abaixo dos encaixes (menu) + três barras empilhadas no HUD |
| Quantas barras | **Três**, uma por pool: munição, líquido e energia, cada uma somando os valores dos módulos daquele tipo |

## 2. O que o código faz

### 2.1 Três pools, cada um somando seus módulos

`core/src/orbital/type/ModuleSupply.java` guarda três pools independentes:

- **Munição (item)** — `item` (escolhido pelo jogador), `ammo`/`ammoCapacity`. Cada torre de item soma seu próprio buffer (`Turret.maxAmmo`) a essa barra.
- **Líquido** — `liquid`, `liquidAmount`/`liquidCapacity`. Cada torre de líquido e cada módulo de suporte líquido (`wave`, `tsunami`) soma seu `Block.liquidCapacity`.
- **Energia** — `energy`/`energyCapacity`. Todo consumidor de energia (torre de energia, projetor, massa driver) soma `Block.consPower.usage × ModuleSupply.energyTicks` (5 s) — a única constante nova, porque o consumidor do motor é taxa, não buffer.

Cada pool de munição é alimentado por um recurso escolhido entre os que os módulos instalados aceitam, e as escolhas são independentes: um casco pode ter torre de item **e** módulo de líquido ao mesmo tempo. Como nada é consumido ainda, montar um módulo completa o pool dele até a capacidade; remover módulo reduz a capacidade junto. O pool vive no `WeaponsComp` como `transient`, no mesmo padrão session-only do loadout, e é recriado em `setupWeapons()` (casco que já nasce com módulo, como o Duo do mono) e recalculado em toda montagem/remoção (`refreshModuleSupply()`).

### 2.2 Derivação dos valores (`core/src/orbital/type/ModuleSupplies.java`)

| Pool | Origem do valor |
|---|---|
| Munição (item) | `Turret.maxAmmo` do bloco da torre |
| Líquido | `Block.liquidCapacity` do bloco (alcance de torres líquidas: `wave`, `tsunami`) |
| Energia | `Block.consPower.usage × 5 s` (`ModuleSupply.energyTicks`) |

### 2.3 Escolha do recurso

`WeaponsComp.ammoItemOptions()` e `ammoLiquidOptions()` listam, em ordem de conteúdo e sem repetição, **apenas** os itens e líquidos aceitos por módulos montados (torres dorsais e suportes): nenhum recurso que nada no casco possa gastar entra na lista — é a mesma regra de compatibilidade do momento de montar. No menu, cada pool tem seu próprio grupo de botões; clicar troca só aquele pool e recalcula todas as barras. Um recurso que deixa de ser oferecido (a torre que o definia foi removida) cai no primeiro restante do seu pool.

Os dois grupos ficam separados por um traço e por espaço próprios, e o rótulo de cada um diz o que ele é ("Munição compatível" / "Líquido compatível").

### 2.4 Onde as barras aparecem

- **Menu (`TurretLoadoutDialog`)**: área própria na última posição do trilho, abaixo dos encaixes dorsais e dos de suporte — painel com fundo, título "Suprimento dos módulos", um seletor por pool de munição (só aparece quando algum módulo aceita aquele tipo) e **três barras** empilhadas, altas o suficiente para ler os números. Cada barra tem a cor do recurso escolhido (cinza neutro quando nada alimenta aquele pool); energia usa a cor de energia do jogo.
- **HUD (`HudFragment`)**: três barras **empilhadas**, numa linha própria **acima** dos ícones dos módulos, na barra dorsal do casco. Cada barra tem o **nome do pool escrito dentro dela** ("Munição", "Líquido", "Energia"), a cor do recurso escolhido (cinza neutro quando nada alimenta aquele pool) e os números num rótulo pequeno ao lado, na mesma cor. As barras têm 18 unidades de altura (14 no celular) porque o `Bar` desenha o rótulo com a fonte de contorno do jogo, que não caberia em uma barra mais baixa — foi isso que fez os números se sobreporem numa versão anterior.
- **Largura**: as barras ocupam a largura de **cinco células de módulo** (`5 × (tamanho da célula + padding)`), calculada a cada reconstrução — são barras de leitura, não de toque, então recebem a sala de cinco ícones.
- **Alinhamento**: a linha das barras e a linha dos módulos ficam em **tabelas separadas** dentro do mesmo painel. Isso é obrigatório: num `Table` do arc, a largura da primeira coluna vale para todas as linhas, então a barra larga na mesma tabela criava um vão entre o primeiro e o segundo módulo. Os valores são lidos ao vivo e `refreshTurretBar` reconstrói a área em toda montagem/remoção/troca de recurso.
- **Cards e linhas de módulos**: sem barras.

### 2.5 Textos (`bundle.properties` / `bundle_pt_BR.properties`)

`module.supply.title`, `module.supply.ammo`, `module.supply.ammo.label`, `module.supply.liquid`, `module.supply.liquid.label`, `module.supply.energy`, `module.supply.none`, `module.supply.noammo` — nas duas linguagens.

## 3. O que **não** foi feito (de propósito)

1. **Nada é consumido.** Atirar, pulsar reparo ou ativar projetores não drenam os pools; as barras mostram a reserva.
2. **Sem reabastecimento.** Não há drenagem por pousar em bloco/núcleo nem regeneração.
3. **Sem custo de instalação.** Montar/remover módulo continua gratuito (a receita do bloco segue documentada em `CATALOGO_VALORES_CONSTRUCAO_MODULOS.md`).
4. **Sem persistência.** Os pools são `transient`, no mesmo padrão do loadout.
5. **Multiplicadores de regra** (`Rules.buildCostMultiplier`, `buildSpeed`) não afetam os pools.

## 4. Verificação

| Suíte | Testes | Resultado |
|---|---:|---|
| `ModuleSupplyTest` (novo) | 12 | 0 falhas |
| `SupportLoadoutTest` | 43 | 0 falhas |
| `TurretAmmoCompatibilityTest` | 6 | 0 falhas |
| `TurretLoadoutDialogTest` | 7 | 0 falhas |
| `TurretLoadoutSmokeTest` | 7 | 0 falhas |
| `TurretModuleShootingTest` | 3 | 0 falhas |
| `WallLoadoutTest` | 22 | 0 falhas |
| `WeaponModuleTest` | 8 | 0 falhas |

`ModuleSupplyTest` cobre: pools vazios sem módulos; torre de item somando seu buffer; **duas torres somando na mesma barra**; torre de energia alimentando só a energia; suporte somando dreno de energia; `wave` (suporte líquido) alimentando **apenas** a barra de líquido; torre de item e módulo líquido rodando juntos; escolher um recurso sem mexer no outro pool; recurso escolhido precisa estar entre os oferecidos; remover módulo encolhendo só a sua barra; `clamp()` por pool; e a coerência com a regra de compatibilidade de munição.
