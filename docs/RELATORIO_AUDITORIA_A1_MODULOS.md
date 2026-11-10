# Auditoria A1 — Sistema de módulos: estado real verificado no código

**Etapa:** A1 do [PLANO_ETAPAS_MODULOS.md](PLANO_ETAPAS_MODULOS.md)
**Data:** 2026-11-10 | **Método acordado:** apenas código-fonte (análise estática). Nada foi verificado in-game; toda afirmação deste relatório cita arquivo e linha. Confirmações que exigem jogo estão marcadas **[NÃO VERIFICADO IN-GAME]**.
**Objetivo:** fechar as 9 lacunas da seção 5 de `estado-fork-baseline-sistemas-existentes.md` com evidência de código, consolidando os relatórios existentes (`RELATORIO_TORRES_COMO_MODULOS.md`, `RELATORIO_CORRESPONDENCIAS_ARMAS_MODULOS.md`) e verificando-os contra o código atual.

---

## 1. Resumo das 9 lacunas do baseline

| # | Lacuna do baseline | Resultado |
|---|---|---|
| 1 | Nomes/IDs dos blocos convertidos | **Fechada** — catálogo completo com linha de código em §2 |
| 2 | Slots por tipo de aeronave | **Fechada** — orçamento dorsal medido por casco, em §4 |
| 3 | Tiers/fórmulas de dano atuais | **Fechada com achado** — tiers T1–T5 existem; **não há fórmula de dano por tier**, §5 |
| 4 | Custos atuais de fabricação/desbloqueio | **Fechada com achado** — **não existe custo de módulo**; `cost` é espaço, não economia, §6 |
| 5 | Regras de compatibilidade | **Fechada** — silhueta/orçamento implementadas; compatibilidade por tier **não existe**, §7 |
| 6 | Como o loadout é salvo hoje | **Fechada com achado** — **o loadout não é salvo em nenhum lugar**, §8 |
| 7 | Torres convertidas ainda são blocos de base? | **Fechada com decisão** — ocultas de toda construção; continuam existindo como módulos (flag `moduleOnly`), §9 |
| 8 | Módulo-de-torre × módulo-de-arma | **Fechada** — diferença documentada em nível de mecanismo, §10 |
| 9 | Suporte ainda existe em solo? | **Fechada com matiz** — mesma situação da 7, §11 |

---

## 2. Lacuna 1 — Catálogo verificado com ID e linha de código

### 2.1 Blocos de torre (31, todos ainda definidos em `Blocks.java`)

| Torre | ID | Classe | Linha |
|---|---|---|---:|
| Duo | `duo` | `ItemTurret` 1x1 | 3276 |
| Scatter | `scatter` | `ItemTurret` 2x2 | 3356 |
| Scorch | `scorch` | `ItemTurret` | 3442 |
| Hail | `hail` | `ItemTurret` 1x1 | 3486 |
| Wave | `wave` | `LiquidTurret` 2x2 | 3552 |
| Lancer | `lancer` | `PowerTurret` 2x2 | 3584 |
| Arc | `arc` | `PowerTurret` 1x1 | 3625 |
| Parallax | `parallax` | `TractorBeamTurret` 2x2 | 3671 |
| Swarmer | `swarmer` | `ItemTurret` 2x2 | 3682 |
| Salvo | `salvo` | `ItemTurret` 2x2 | 3758 |
| Segment | `segment` | `PointDefenseTurret` 2x2 | 3863 |
| Tsunami | `tsunami` | `LiquidTurret` 3x3 | 3877 |
| Fuse | `fuse` | `ItemTurret` 3x3 | 3942 |
| Ripple | `ripple` | `ItemTurret` 3x3 | 3982 |
| Cyclone | `cyclone` | `ItemTurret` 3x3 | 4115 |
| Foreshadow | `foreshadow` | `ItemTurret` 4x4 | 4233 |
| Spectre | `spectre` | `ItemTurret` 4x4 | 4278 |
| Meltdown | `meltdown` | `LaserTurret` 4x4 | 4346 |
| Breach | `breach` | `ItemTurret` 3x3 | 4383 |
| Diffuse | `diffuse` | `ItemTurret` | 4495 |
| Sublimate | `sublimate` | `ContinuousLiquidTurret` | 4585 |
| Titan | `titan` | `ItemTurret` 4x4 | 4671 |
| Disperse | `disperse` | `ItemTurret` 4x4 | 4878 |
| Afflict | `afflict` | `PowerTurret` | 5062 |
| Lustre | `lustre` | `ContinuousTurret` | 5185 |
| Scathe | `scathe` | `ItemTurret` | 5250 |
| Smite | `smite` | `ItemTurret` 5x5 | 5694 |
| Malign | `malign` | `PowerTurret` 9x9 | 5964 |
| Repair Point | `repair-point` | `RepairTurret` 1x1 | 6427 |
| Repair Turret | `repair-turret` | `RepairTurret` 2x2 | 6437 |
| Build Tower | `build-tower` | `BuildTurret` 3x3 | 1999 |

**Correção de fato em relação ao relatório de correspondências:** `malign` está definido como `PowerTurret` (linha 5964), não como classe de energia genérica. Pela regra de `TurretWeapon.canAdapt` (linha 107–111 de `TurretWeapon.java`), um `PowerTurret` com `shootType` **é tecnicamente adaptável** — o que impede o Malign como módulo hoje não é a classe, é o tamanho (9x9 não cabe em slot algum) e o orçamento. O relatório anterior já concluía "não é módulo"; a justificativa técnica agora está corrigida para "incompatível por dimensão, não por classe".

### 2.2 Blocos de suporte convertidos (5 aceitos pelo sistema)

| Bloco | ID | Linha em `Blocks.java` |
|---|---|---:|
| Mender | `mender` (`MendProjector`) | 1921 |
| Mend Projector | `mend-projector` (`MendProjector`) | 1934 |
| Overdrive Projector | `overdrive-projector` | 1946 |
| Overdrive Dome | `overdrive-dome` (`OverdriveProjector`) | 1954 |
| Force Projector | `force-projector` | 1966 |

Confirmação do baseline: os nomes eram *Overdrive Projector*, variante de domo (*overdrive-dome*), *Mend Projector/Mender* e *Force Projector* — todos confirmados com os IDs acima.

### 2.3 Módulos de arma (catálogo derivado, não estático)

`WeaponModules.all()` (`WeaponModules.java`, linhas 136–164) deriva o catálogo em runtime de **todas as unidades do conteúdo**, incluindo mods. Exclusões confirmadas em código (linhas 175–183): `TurretWeapon` (evita duplicar torres), `RepairBeamWeapon`, `BuildWeapon`, `MineWeapon`, `PointDefenseWeapon`, `PointDefenseBulletWeapon`, e armas sem `bullet`. Armas espelhadas aparecem uma única vez (linha 153). Isso fecha a pendência do baseline sobre "armas de todas as unidades convertidas": a conversão é o próprio catálogo derivado, com correção de cadência para armas espelhadas (`reload/2`, linhas 75–78).

---

## 3. Disponibilidade real dos módulos hoje (o que o jogador consegue montar)

| Nível | Conjunto | Evidência |
|---|---|---|
| **Aceito hoje pelo sistema** | Torreta: **`duo` apenas** (injetada em Mono, Flare e variantes `mono-duo`/`flare-duo`). Suporte: `mender`, `mend-projector`, `overdrive-projector`, `overdrive-dome`, `force-projector` | `TurretLoadouts.applyRemovableTurrets` (linhas 139–157); `SupportModuleCategory.of` |
| **Adaptável tecnicamente** | `ItemTurret` com munição e `PowerTurret` com `shootType` (inclui `LaserTurret` por herança; inclui `malign` por classe, barrado por dimensão) | `TurretWeapon.canAdapt`, linhas 107–111 |
| **Somente catálogo** | `LiquidTurret`, `ContinuousLiquidTurret`, `PointDefenseTurret`, `TractorBeamTurret`, `ContinuousTurret`, `RepairTurret`, `BuildTurret` | Falham em `canAdapt` |

Módulos de arma do catálogo derivado (`WeaponModules`) são apresentados no diálogo de loadout e instaláveis por montagem runtime — a UI existe (`TurretLoadoutDialog.java`).

---

## 4. Lacuna 2 — Slots e orçamento por casco

- **[FATO]** Cada casco aéreo tem slots explícitos e multiplicador de orçamento: `TurretLoadouts.setup(...)` para as 20 aéreas (arquivo inteiro; exemplo: `mono` 1,00× e área 2304 px², linha 34; `gamma` com dois slots por fuselagem dupla, linhas 45–47).
- **[FATO]** Orçamento efetivo = 50% × área × multiplicador (`UnitType.dorsalBudget()`); validação de silhueta C ≤ 50% e L ≤ 70% (`UnitType.canMount()`). Ambos confirmados pelos testes em `tests/src/test/java/TurretLoadoutSmokeTest.java` (listados em `RELATORIO_TORRES_COMO_MODULOS.md` §21.4 — **[NÃO VERIFICADO IN-GAME]** nesta sessão: o teste não foi executado agora).
- **[FATO]** Suporte: âncoras separadas do orçamento de torre, um módulo por categoria (`SupportModuleCategory`, `SupportAnchor`; `TurretLoadouts.java`, cabeçalho).

---

## 5. Lacuna 3 — Tiers e fórmulas de dano (achado principal)

1. **[FATO]** Tiers T1–T5 existem: mapa `unitTiers` em `WeaponModules.java` (linhas 103–119), derivado das linhas de pesquisa Serpulo/Erekir e dos níveis N0–N4 das aéreas (N0→T1 … N4→T5). Usado apenas para **sufixo de exibição** (`"Risso T1"`) e **ordenação dos grids** no diálogo (linha 131).
2. **[ACHADO] Não existe fórmula de dano por tier.** O módulo herda o dano do projétil original sem qualquer escala: `TurretWeapon` copia o `bullet` do tipo de munição da torre (linhas 26–30, 140–144) e `WeaponModules.Definition.copy()` copia a arma inteira (linhas 61–89). O tier não altera reload, range, dano nem consumo — é só rótulo.
3. **[FATO]** A única "normalização" de cadência implementada é a correção de armas espelhadas no catálogo de armas (`reload/2`, `WeaponModules.java` linhas 75–78) e o tratamento de arma única no adaptador de torre (`mirror = false`, `TurretWeapon` linha 38).

**Consequência para A3/A5:** a "economia de tiers" do plano precisa ser construída do zero; não há nada para ajustar. A curva de balanceamento proposta (A5) partirá dos valores legados das torres/armas, sem escala por tier até decisão de design.

---

## 6. Lacuna 4 — Custos (achado)

1. **[ACHADO] Não existe custo de fabricação nem de instalação de módulo.** O campo `cost` que aparece na UI (`TurretLoadoutDialog` linha 580, `WeaponModules.Definition.cost`, `TurretWeapon.cost()`) é o **orçamento de espaço dorsal em px²** (`size × 32` ao quadrado) — não é economia. `TurretLoadoutDialog` não consulta inventário, itens ou desbloqueio: busca textual por `cost|requirement` só encontra o custo de pegada e munição.
2. **[FATO]** Os blocos legados mantêm receitas de construção e nós de pesquisa da árvore herdada (`SerpuloTechTree.java`: nós `duo` linha 263, `mender` linha 220, `scatter` linha 299, `spectre` linha 304) — mas isso é o sistema vanilla de blocos, não do sistema de módulos.
3. **[FATO]** Instalar/remover módulo é gratuito e instantâneo em runtime (montagem via `WeaponsComp.mountTurret`); não há despêndio de itens.

---

## 7. Lacuna 5 — Compatibilidade

1. **[FATO]** Regras implementadas: pegada/silhueta (C ≤ 50%, L ≤ 70% — `UnitType.canMount`), orçamento dorsal somado só de módulos removíveis (`loadoutCost()`), tamanho máximo por slot (`TurretSlot.accepts`), um suporte por categoria e bloqueio de módulo equivalente a habilidade nativa (`SupportModuleCategory.ofAbility` → `repair`/`overdrive`/`force`).
2. **[ACHADO] Não existe compatibilidade por tier.** Nada impede instalar um módulo T5 (ex.: arma de `eclipse`) num casco T1: o catálogo derivado não filtra por tier e o diálogo não tem gate. O gate por tier é **[PROPOSTA]** da etapa A4, não implementado.
3. **[FATO]** A categoria `field` proposta no relatório de correspondências (seção 7) **continua não implementada** — `SupportModuleCategory` não tem `field`; `ShieldRegenFieldAbility`, `EnergyFieldAbility` e `SuppressionFieldAbility` seguem sem categoria.

---

## 8. Lacuna 6 — Como o loadout é salvo hoje (achado)

1. **[ACHADO] O loadout não é persistido em lugar nenhum.** Busca em `io/versions/` (Save* e Legacy*) não encontra serialização de `mount`, `weapon` ou loadout de unidade — os saves só serializam tiles. A única referência a "loadout" em `io/` é `JsonIO.java` linha 85, que é o `Rules.loadout` vanilla (loadout inicial de partida), **não** o loadout de módulos.
2. **[FATO]** O `TurretLoadoutDialog` edita o loadout **de uma unidade selecionada em runtime** (cabeçalho do arquivo, linha 29) — nada disso sobrevive a save/load ou à morte da unidade.
3. **[FATO]** O que persiste de forma implícita: as variantes de demonstração `mono-duo` e `flare-duo` têm o Duo **cozido na definição do `UnitType`** (`TurretLoadouts.applyRemovableTurrets`, linhas 142–157) — e, portanto, sobrevivem a save porque fazem parte do conteúdo.
4. **[FATO]** Desbloqueio de conteúdo continua local (`UnlockableContent.java`, chave `name-unlocked` em `Core.settings`).

**Consequência para A2:** o formato versionado do Plano A é construção nova, sem legado a migrar além das duas variantes cozidas.

---

## 9. Lacuna 7 — Torres convertidas ainda são blocos de base?

**Resposta atualizada em 2026-11-10 (decisão do Péterson, implementada nesta sessão): não — agora estão ocultas de qualquer construção.**

1. **[FATO]** As 28 torres de combate + `repair-point` + `repair-turret` (30 blocos) são escondidas de todo menu de construção por `Blocks.hideModuleOnlyTurrets()` (`Blocks.java`, fim de `load()`): `buildVisibility = BuildVisibility.hidden`, `inEditor = false`. Não aparecem no menu de construção, no editor de mapas nem em sandbox.
2. **[FATO]** Cada um desses blocos recebe `moduleOnly = true` — flag nova em `Block.java`. Ela existe porque o filtro do catálogo de módulos (`TurretLoadoutDialog.rebuildTurretGrid`) usa `isHidden()`, que deriva de `buildVisibility`: sem a flag, esconder do menu de construção tiraria as torres **também do catálogo de módulos**. O filtro passou a aceitar blocos ocultos que sejam `moduleOnly` (idem no espelho do filtro em `TurretLoadoutDialogTest`).
3. **[FATO]** O teste `TurretLoadoutDialogTest.afterRemovingTheNativeDuoTheCatalogueStillOffersItsFootprint` pegou exatamente essa regressão na primeira implementação (catálogo fechado, `offer = 0`). Com a flag, as 6 classes de teste de módulos passam: 63 testes, 0 falhas.
4. **[FATO]** Definições, sprites, receitas e nós de pesquisa permanecem intocados — o sistema de módulos lê os blocos normalmente (`TurretWeapon.of`, injeção do Duo, catálogo derivado).
5. **[FATO]** Foram mantidos como blocos de base, por decisão: `build-tower` (instalação de mapa) e os 5 projetores de suporte (`mender`, `mend-projector`, `overdrive-projector`, `overdrive-dome`, `force-projector`) — que também continuam válidos como módulos dorsais.
6. **[MATIZ]** O método usa lista explícita de nomes (não classe), então torres de mods mantêm a própria decisão de visibilidade. Mapas/saves existentes com essas torres já colocadas continuam funcionando — `buildVisibility` só restringe construção nova.

---

## 10. Lacuna 8 — Módulo-vindo-de-torre × módulo-vindo-de-arma

| Aspecto | Módulo de torre (`TurretWeapon`) | Módulo de arma (`WeaponModules.Definition`) |
|---|---|---|
| Origem | Bloco `Turret` do mapa | `Weapon` de uma unidade qualquer |
| Munição | Primeiro tipo de `ammoTypes` do bloco (`of()`, linha 141); poder ter bateria implícita (torre de energia = sem item) | `bullet` nativo da arma |
| Cadência | `reload` da torre | `reload` da arma; espelhadas corrigidas para `/2` |
| Alcance | **Não copiado** — `TurretWeapon` não define `range`; a arma usa o alcance padrão de unidade (`bullet.speed × lifetime` via mira) | Idem, comportamento de arma nativa |
| Padrão de tiro | Copiado (`shoot = turret.shoot`, canos visuais recriados como `RegionPart`, linhas 70–84) | Padrão original da arma, sem espelho |
| Recuo/rotação/copiar | Copiados do bloco (linhas 44–61) | Originais |
| Tamanho/pegada | `turretSize × 32` px² | Lado do sprite em px, limitado 1–5 (`spritePx`, linhas 185–193) |
| Tier | Não rotulado (usa o nome do bloco) | Sufixo T1–T5 do casco de origem |
| Removível | Sempre (`removable = true`) | Sempre (`copy.removable = true`) |

**[NÃO VERIFICADO IN-GAME]** A diferença prática de alcance entre as duas famílias (o baseline pedia isso como comparativo de comportamento em combate) não pode ser fechada sem rodar o jogo; o comparativo numérico de parâmetros por arma já está nas tabelas de `RELATORIO_CORRESPONDENCIAS_ARMAS_MODULOS.md` §3, verificadas contra `UnitTypes.java`/`Blocks.java` naquela sessão.

---

## 11. Lacuna 9 — Suporte em solo

Mesma resposta da lacuna 7, com os 5 blocos de suporte: definições vivas em `Blocks.java` (§2.2), nós de pesquisa presentes (ex.: `mender`, `SerpuloTechTree.java` linha 220), campanha Serpulo/Erekir fora do jogo → **dormentes**. Os blocos de suporte continuam sendo blocos de base e **também** são módulos dorsais — as duas formas coexistem no catálogo, com a de módulo aceita apenas para os 5 IDs listados.

---

## 12. Conclusões que alimentam as próximas etapas

1. **Para A2:** loadout é edição runtime sem persistência — o formato versionado nasce do zero; única migração são as variantes `mono-duo`/`flare-duo`.
2. **Para A3:** zero economia implementada; o campo `cost` existente é espaço, deve ser renomeado na UI em algum momento para não confundir jogador com "custo de compra".
3. **Para A4:** falta gate por tier; a tabela proposta não colide com nada existente.
4. **Para A5:** curva de balanceamento parte dos valores legados sem escala; a suíte precisa medir DPS real por módulo.
5. **Fato corrigido:** `malign` é `PowerTurret` e tecnicamente adaptável; a barreira é dimensão/orçamento, não classe.
6. **Verificação pendente de jogo (fora do escopo acordado):** acessibilidade das torres/suportes no editor e sandbox; comportamento de alcance dos dois tipos de módulo em combate.
