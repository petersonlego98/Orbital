# Relatório — correspondências entre armas de unidades e módulos de torre/suporte

**Tipo:** comparação de equivalência funcional  
**Data:** 6 de novembro de 2026  
**Fontes primárias:** `core/src/orbital/content/UnitTypes.java`, `core/src/orbital/content/Blocks.java`  
**Fontes do sistema de módulos:** `core/src/orbital/type/TurretWeapon.java`, `core/src/orbital/type/SupportModuleCategory.java`, `core/src/orbital/type/SupportAnchor.java`, `core/src/orbital/content/TurretLoadouts.java`, `core/src/orbital/content/SupportEffects.java`  
**Escopo:** 28 unidades terrestres + 10 navais, confrontadas apenas com os módulos de torre e suporte que têm **igualdade, semelhança forte ou aproximação parcial**. Itens sem correspondência foram omitidos.  
**Decisão registrada:** as correspondências são referência de cobertura, não backlog de adaptação; as unidades destacadas ficam fora da conversão em módulo, com **Vela** como única exceção (seção 5). A seção 7 registra, como **proposta não implementada**, uma categoria `field` para os campos de unidade hoje sem categoria.

---

## 1. Como este relatório classifica as correspondências

### 1.1 Graus

| Grau | Critério |
|---|---|
| **Igualdade** | Mesma classe de projétil e mesmos valores centrais de dano, dimensão e tempo de vida; ou mesma habilidade com parâmetros equivalentes. |
| **Forte** | Mesmo mecanismo de combate e mesma função tática, com números, alcance ou cadência diferentes. |
| **Parcial** | Compartilha a família de comportamento, mas muda o papel, o alvo ou a forma de entrega. |

### 1.2 O que está disponível hoje

Há três níveis diferentes, e eles não devem ser confundidos:

| Nível | O que inclui | Consequência |
|---|---|---|
| **Aceito hoje pelo sistema** | A torre `duo` é a única injetada automaticamente por `TurretLoadouts.applyRemovableTurrets`. Os suportes aceitos por `SupportModuleCategory.of` são `mender`, `mend-projector`, `overdrive-projector`, `overdrive-dome` e `force-projector`. | É o conjunto que o código atual reconhece como módulo dorsal. |
| **Adaptável tecnicamente** | `TurretWeapon.canAdapt` aceita `ItemTurret` com munição e `PowerTurret` com `shootType`. | A torre pode virar arma de unidade, mas não é injetada por padrão e depende de slot compatível. |
| **Somente catálogo de projeto** | `LiquidTurret`, `PointDefenseTurret`, `TractorBeamTurret`, `ContinuousTurret`, `RepairTurret` e `BuildTurret`. | Não passam por `TurretWeapon.canAdapt`; existem como bloco, mas não como módulo de torre no sistema atual. `LaserTurret` é exceção: herda de `PowerTurret`, então uma instância como Meltdown é tecnicamente adaptável. |

> **Leitura correta:** as tabelas abaixo comparam **conteúdo e comportamento**, não afirmam que toda torre listada já possa ser equipada pelo jogador. A coluna “Disponibilidade” separa esses casos.

### 1.3 Mapeamento de suporte no código atual

`SupportModuleCategory.ofAbility` reconhece:

- `RepairFieldAbility` → **repair**
- `StatusFieldAbility` com `overdrive` ou `overclock` → **overdrive**
- `ForceFieldAbility` ou `ShieldArcAbility` → **force**

Não são reconhecidos como categoria de suporte:

- `ShieldRegenFieldAbility` — escudo regenerativo de unidade, não barreira de projéteis.
- `EnergyFieldAbility` — dano e cura em área, sem categoria única.
- `SuppressionFieldAbility` — supressão de reparo, sem módulo equivalente.

A seção 7 propõe uma categoria **`field`** para esses três casos. Ela é uma proposta de projeto, **ainda não implementada** no código atual.

---

## 2. Resumo das correspondências mais fortes

| Correspondência | Grau | Motivo curto |
|---|---|---|
| **Dagger / Risso ↔ Duo** | **Igualdade** | Mesmo projétil `BasicBulletType(2.5f, 9)`, `7x9`, `lifetime=60`. |
| **Minke ↔ Scatter** | **Forte** | Mesma munição de chumbo `FlakBulletType(4.2f, 3)`, splash `27 * 1.5`, raio 15. |
| **Omura ↔ Foreshadow** | **Forte** | Ambos usam `RailBulletType` com `length = 500`; mudam dano, recarga e fator de perfuração. |
| **Toxopid ↔ Fuse** | **Forte** | Escopeta de estilhaços curta, com spread e alto dano por disparo. |
| **Collaris / Toxopid / Arkyid ↔ Titan / Ripple** | **Forte a parcial** | Artilharia de área com splash, fragmentos e, em alguns casos, status. |
| **Oxynoe ↔ Overdrive Projector / Dome** | **Forte** | Buff em área temporário para aliados; o bloco acelera construções, a unidade aplica `overclock`. |
| **Quasar / Tecta ↔ Force Projector** | **Forte a parcial** | Escudo de energia; bolha versus arco direcional. |
| **Vela / Cyerce / Retusa ↔ Repair Point / Repair Turret** | **Forte** | Feixe contínuo de reparo em aliados. |
| **Nova ↔ Mender / Mend Projector** | **Forte** | Cura periódica em área, com múltiplos alvos. |

**Nota de escopo:** esta tabela é uma lista de **cobertura funcional**, não uma lista de unidades aprovadas para virar módulo. A decisão de adaptação está na seção 5.

---

## 3. Armamentos — correspondências

### 3.1 Torres de projétil e kinetic

| Módulo atual | Disponibilidade | Unidade(s) e arma | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Duo** (`duo`) — `ItemTurret` 1x1 | **Aceito hoje** | **Dagger** `large-weapon`; **Risso** `mount-weapon` | **Igualdade** | `BasicBulletType(2.5f, 9)` com `width=7`, `height=9`, `lifetime=60`. É o mesmo projétil-base nos três. | Duo recarrega em 20 ticks; Dagger e Risso em 13. O Duo usa munição de cobre com `ammoMultiplier=2`; Dagger não declara multiplicador. |
| **Scatter** (`scatter`) — `ItemTurret` 2x2 | Adaptável | **Minke** `mount-weapon` | **Forte** | `FlakBulletType(4.2f, 3)`, `splashDamage=27 * 1.5`, `splashDamageRadius=15`, projétil `6x8`. | Scatter dispara 2 projéteis, recarga 18, alcance 220 e é dedicado a ar; `lifetime=60`. Minke dispara em cadência 10, usa `lifetime=52,5` e alcance semelhante. |
| **Cyclone** (`cyclone`) — `ItemTurret` 3x3 | Adaptável | **Minke** `mount-weapon` | **Parcial** | Flak antiaéreo de alta cadência, múltiplos canos e dano por explosão. | Cyclone é 3x3, três canos, recarga 10, alcance 200 e splash maior; Minke é uma montagem menor. |
| **Fuse** (`fuse`) — `ItemTurret` 3x3 | Adaptável | **Toxopid** `large-purple-mount` | **Forte** | Escopeta de estilhaços (`ShrapnelBulletType`) de curto alcance, com spread e dano concentrado. | Fuse: 3 tiros em 20°, comprimento 100, 66/105 de dano, recarga 35, alcance 90. Toxopid: 2 tiros em 17°, comprimento 90, 110 de dano, recarga 30. |
| **Spectre** (`spectre`) — `ItemTurret` 4x4 | Adaptável | **Reign** `reign-weapon` | **Forte** | Canhão kinetic de alta cadência com perfuração. | Spectre: 50–80 de dano, recarga 7, alcance 260, `pierceCap=2`. Reign: 80 de dano, recarga 9, `pierceCap=10`, splash 18 e 3 fragmentos. |
| **Salvo** (`salvo`) — `ItemTurret` 2x2 | Adaptável | **Scepter** `scepter-weapon` | **Parcial** | Rajada de tiros kinetic em sequência. | Salvo: 4 tiros, recarga 29, alcance 190, 15–31 de dano. Scepter: 3 tiros de 70, recarga 45, lightning e alcance maior. |
| **Swarmer** (`swarmer`) — `ItemTurret` 2x2 | Adaptável | **Anthicus** `anthicus-weapon`; **Sei** `sei-launcher`; **Tecta** `tecta-weapon`; **Risso** `missiles-mount`; **Bryde** `missiles-mount` | **Forte a parcial** | Salva de mísseis com splash; várias unidades usam mísseis teleguiados. | Swarmer: 4 mísseis, recarga ≈34,3, alcance 240, mísseis de 10–18 + splash; apenas parte das munições tem homing. Anthicus lança 2 unidades-míssil com explosão de 140; Sei lança 6 mísseis de 42 + 45; Tecta lança 3 de 51 + 60; Risso/Bryde usam mísseis menores de 12 + 10. |

### 3.2 Artilharia, área e cerco

| Módulo atual | Disponibilidade | Unidade(s) e arma | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Hail** (`hail`) — `ItemTurret` 1x1 | Adaptável | **Fortress** `artillery`; **Merui** `merui-weapon`; **Minke** `artillery-mount` | **Forte** | Artilharia indireta de área; Hail, Fortress e Merui não miram ar. | Hail: 20 + 33 de splash, raio 18,75, recarga 60, alcance 235. Fortress: 20 + 80, raio 35, recarga 60. Merui: 40 + 30, raio 19, recarga 63. Minke: 20 + 40, raio 22,5, recarga 30. |
| **Ripple** (`ripple`) — `ItemTurret` 3x3 | Adaptável | **Arkyid** `large-purple-mount`; **Toxopid** `toxopid-cannon` | **Forte a parcial** | Artilharia de área com splash e fragmentos secundários; Ripple é terrestre. | Ripple: 40 + 70/90 de splash, raio 22,5/37,5, recarga 120, alcance 290, até 15 fragmentos. Arkyid: 12 + 65, raio 70, recarga 45, lightning e `sapped`. Toxopid: 50 + 75, raio 80, 9 fragmentos, recarga 210 e `sapped`. |
| **Titan** (`titan`) — `ItemTurret` 4x4 | Adaptável | **Collaris** `collaris-weapon`; **Conquer** `conquer-weapon`; **Precept** `precept-weapon`; **Vanquish** `vanquish-weapon` | **Forte a parcial** | Artilharia pesada / projétil explosivo com splash; Titan é terrestre. | Titan: 350/700 de dano, splash 350/750, raio 65/36, recarga 138, alcance 390. Collaris: 260 + 120, raio 36, 15 fragmentos teleguiados, recarga 130, alcance 385. Conquer: 360 + 12 subprojéteis, recarga 100. Precept: 120 + 50 + 4 fragmentos. Vanquish: 150 + 50 + 5 fragmentos. |

### 3.3 Energia, laser e railgun

| Módulo atual | Disponibilidade | Unidade(s) e arma | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Arc** (`arc`) — `PowerTurret` 1x1 | Adaptável | **Pulsar** `heal-shotgun-weapon`; **Scepter** `scepter-weapon` | **Forte a parcial** | Lightning em cadeia; Pulsar aplica `shocked` e o Arc também usa `LightningBulletType` com status. | Arc: 12 de dano, comprimento 25, recarga 35, alcance 90, terrestre. Pulsar: 3 projéteis de 15, comprimento 8 + rand 7, recarga 36 e cura. Scepter usa lightning como efeito secundário de projéteis kinetic. |
| **Lancer** (`lancer`) — `PowerTurret` 2x2 | Adaptável | **Quasar** `beam-weapon` | **Forte** | Feixe laser carregado, com dano concentrado e alcance médio. | Lancer: 140 de dano, comprimento 173, `armorMultiplier=4`, terrestre, recarga 80. Quasar: 45 de dano, comprimento 150, feixes laterais e cura 10%, recarga 55. |
| **Meltdown** (`meltdown`) — `LaserTurret` 4x4 | Adaptável (herda `PowerTurret`) | **Vela** `vela-weapon` | **Forte** | Laser contínuo de longo alcance. | Meltdown: 78 de dano, comprimento 200, aplica `melting`, recarga 90. Vela: 35 de dano, comprimento 180, cura 1%, aplica lentidão em si ao disparar. |
| **Foreshadow** (`foreshadow`) — `ItemTurret` 4x4 | Adaptável | **Omura** `omura-cannon` | **Forte** | Ambos usam `RailBulletType` com `length = 500`; mesmo papel de tiro único de longo alcance. | Foreshadow: 1.350 de dano, recarga 200, `pierceDamageFactor=1`. Omura: 1.250 de dano, recarga 110, `pierceDamageFactor=0.5`, em casco naval móvel. |

### 3.4 Ponto de defesa, líquidos e controle

| Módulo atual | Disponibilidade | Unidade(s) e arma | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Segment** (`segment`) — `PointDefenseTurret` 2x2 | **Catálogo** | **Cleroi** `cleroi-point-defense`; **Oxynoe** `point-defense-mount`; **Aegires** `point-defense-mount` | **Forte** | Intercepta projéteis e mísseis inimigos; não atira em unidades. | Segment: 30 de dano, recarga 8, alcance 180. Cleroi: 38/9, alcance 100. Oxynoe: 17/9, alcance 100. Aegires: 30/4, alcance 180, duas montagens. |
| **Wave** (`wave`) / **Tsunami** (`tsunami`) — `LiquidTurret` | **Catálogo** | **Atrax** `atrax-weapon` | **Forte a parcial** | Projétil líquido de escória (`Liquids.slag`), terrestre. | Wave: 4 de dano, recarga 3, alcance 110. Tsunami: 4,75 x 2, recarga 3, alcance 190. Atrax: 13 de dano, recarga 9, alcance ≈178. |
| **Parallax** (`parallax`) — `TractorBeamTurret` 2x2 | **Catálogo** | **Spiroct** `spiroct-weapon` / `mount-purple-weapon` | **Parcial** | Controle por puxão/knockback negativo. | Parallax: força 16, alcance 300, dano 0,5. Spiroct: knockback −1,24/−0,65, alcance 75/40, além de `sapped` e dano direto. |

---

## 4. Suporte — correspondências

### 4.1 Reparo

| Módulo atual | Disponibilidade | Unidade(s) / habilidade | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Mender** (`mender`) — `MendProjector` 1x1 | **Aceito hoje** | **Nova** `RepairFieldAbility` | **Forte** | Cura periódica em área, atingindo vários aliados. | Mender: `healPercent=4`, recarga 200, alcance 40, boost com silício. Nova: 20 de cura a cada 2 s, alcance 100, até 6 alvos, `smartHeal`, mas só 15% do valor em aliados do mesmo tipo (`sameTypeHealMult=0.15`). |
| **Mend Projector** (`mend-projector`) — `MendProjector` 2x2 | **Aceito hoje** | **Nova** `RepairFieldAbility` | **Forte** | Mesma função de aura de reparo, em escala maior. | Mend Projector: 11%, recarga 250, alcance 85, boost com `phaseFabric`. Nova continua com cura fixa e alcance maior. |
| **Repair Point** (`repair-point`) — `RepairTurret` 1x1 | Bloco de reparo (fora da categoria) | **Vela** `repair-beam-weapon-center-large`; **Cyerce** `repair-beam-weapon-center`; **Retusa** `repair-beam-weapon-center` | **Forte** | Feixe contínuo de reparo em aliados. | Repair Point: `repairSpeed=0.45`, raio 60. Vela: 1,4, alcance 120. Cyerce: 0,7, alcance 130. Retusa: 0,75, alcance 120. |
| **Repair Turret** (`repair-turret`) — `RepairTurret` 2x2 | Bloco de reparo (fora da categoria) | **Vela**, **Cyerce**, **Retusa**; cura no acerto de **Nova**, **Quasar**, **Corvus**, **Oxynoe** e **Navanax** | **Forte a parcial** | Reparo sustentado por feixe ou cura percentual no disparo. | Repair Turret: `repairSpeed=3`, raio 145, consome energia. As unidades usam feixes menores ou cura embutida no dano; não são `RepairFieldAbility` e não entram na categoria `repair` por `SupportModuleCategory.of`. |

### 4.2 Overdrive

| Módulo atual | Disponibilidade | Unidade(s) / habilidade | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Overdrive Projector** (`overdrive-projector`) — 2x2 | **Aceito hoje** | **Oxynoe** `StatusFieldAbility(StatusEffects.overclock)` | **Forte** | Buff temporário em área para aliados. | Bloco: `speedBoost=1.5`, alcance 80, foco em construções. Oxynoe: +15% velocidade, +15% dano e +25% cadência por 6 s, recarga 6 s, alcance 60 px (7,5 tiles). |
| **Overdrive Dome** (`overdrive-dome`) — 3x3 | **Aceito hoje** | **Oxynoe** `StatusFieldAbility(StatusEffects.overclock)` | **Forte** | Mesmo mecanismo, com multiplicador maior. | Dome: `speedBoost=2.5`, alcance 200, `useTime=300`. Oxynoe mantém o bônus de dano, que o bloco não tem. |

### 4.3 Escudo e força

| Módulo atual | Disponibilidade | Unidade(s) / habilidade | Grau | O que coincide | Diferença principal |
|---|---|---|---|---|---|
| **Force Projector** (`force-projector`) — 3x3 | **Aceito hoje** | **Quasar** `ForceFieldAbility` | **Forte** | Bolha regenerativa que absorve dano para aliados. | Force Projector: 750 de escudo, raio 101,7. Quasar: 500 de escudo, raio 60, regeneração 0,4/tick, cooldown 6 s. |
| **Force Projector** (`force-projector`) — 3x3 | **Aceito hoje** | **Tecta** `ShieldArcAbility` | **Parcial** | Escudo de energia com regeneração e deflexão. | Tecta: 2.500 de escudo, arco de 82°, regeneração 0,75/tick, cooldown 8 s, 100% de deflexão. O Force Projector é uma bolha de 360°, não um arco frontal. |
| **Force Projector** (`force-projector`) — 3x3 | **Aceito hoje** | **Scepter**, **Pulsar**, **Bryde** `ShieldRegenFieldAbility` | **Parcial** | Pulsos de escudo em área para aliados. | Scepter: 25/pulso, máximo 250, 1 s, raio 60. Pulsar: 20/40, 5 s, raio 60. Bryde: 20/40, 4 s, raio 60. É escudo de unidade, não barreira de projéteis; `SupportModuleCategory.ofAbility` **não** o reconhece como `force`. Candidato futuro à categoria `field` (seção 7). |

### 4.4 Correspondências compostas ou fora das categorias atuais

| Referência | Unidade(s) / habilidade | Grau | O que coincide | Limite atual |
|---|---|---|---|---|
| **Repair Turret + torre de dano em área** | **Aegires** `EnergyFieldAbility` | **Parcial** | Dano em até 25 alvos, `electrified` por 6 s e cura de 1,5% em aliados, em raio de 180. | Não existe um único módulo que faça as duas coisas; `EnergyFieldAbility` não é mapeada por `SupportModuleCategory`. |
| **Build Tower** (`build-tower`) | **Retusa**, **Oxynoe**, **Cyerce**, **Aegires**, **Navanax**, **Nova**, **Vela**, **Quasar**, **Pulsar** | **Parcial** | Unidades com `buildSpeed` ajudam a construir e reparar estruturas. | `build-tower` tem `buildSpeed=1.5` e alcance 200, mas não é uma categoria de suporte dorsal. Implementação futura registrada em [`PROPOSTA_BUILD_SPEED_E_BUILD_TOWER.md`](PROPOSTA_BUILD_SPEED_E_BUILD_TOWER.md). |

---

## 5. Decisão de escopo — não adaptar as unidades destacadas como novos módulos

**Decisão:** os destaques deste relatório são **referências de cobertura**, não uma lista de unidades a converter em módulos. Quando um módulo atual já entrega a mesma função, a unidade correspondente deve ser **excluída do backlog de adaptação**. A única exceção é **Vela**, cujo feixe de reparo deve ser tratado como candidato a módulo. A tabela abaixo registra os casos que motivaram a decisão; os demais achados de armamento continuam apenas como comparação.

**Racional:** adaptar uma unidade significa extrair sua arma ou habilidade para um módulo dorsal. Quando o módulo já existe, isso duplica a função e cria duas fontes de balanceamento. Em habilidades, o risco é maior: `RepairFieldAbility`, `StatusFieldAbility`, `ForceFieldAbility` e `ShieldArcAbility` já bloqueiam módulos equivalentes por `SupportModuleCategory.ofAbility`; manter a unidade como nativa evita conflito de estado e preserva a identidade do casco.

| Destaque | Unidade(s) | Módulo existente que cobre a função | Decisão |
|---|---|---|---|
| Dagger / Risso ↔ Duo | Dagger, Risso | `duo` | **Não adaptar Dagger/Risso.** O Duo já reproduz o projétil; criar outro módulo seria duplicação. |
| Minke ↔ Scatter / Cyclone | Minke | `scatter`, `cyclone` | **Não adaptar Minke.** O flak já está coberto em duas escalas. |
| Omura ↔ Foreshadow | Omura | `foreshadow` | **Não adaptar Omura.** O rail de 500 já existe como módulo. |
| Toxopid ↔ Fuse | Toxopid | `fuse` | **Não adaptar Toxopid.** A escopeta de estilhaços já tem módulo. |
| Collaris / Arkyid / Toxopid ↔ Titan / Ripple | Collaris, Arkyid, Toxopid | `titan`, `ripple` | **Não adaptar essas unidades.** A artilharia de área já está coberta. |
| Nova ↔ Mender / Mend Projector | Nova | `mender`, `mend-projector` | **Não adaptar Nova.** A aura de reparo já é a categoria `repair`. |
| Oxynoe ↔ Overdrive Projector / Dome | Oxynoe | `overdrive-projector`, `overdrive-dome` | **Não adaptar Oxynoe.** O `overclock` já é a categoria `overdrive`. |
| Quasar / Tecta ↔ Force Projector | Quasar, Tecta | `force-projector` | **Não adaptar Quasar/Tecta.** `ForceFieldAbility` e `ShieldArcAbility` já são a categoria `force`. |
| Scepter / Pulsar / Bryde ↔ Force Projector | Scepter, Pulsar, Bryde | `force-projector` | **Não adaptar hoje.** O escudo é `ShieldRegenFieldAbility`, que não é reconhecido como `force`; manter nativo. Candidato futuro à categoria `field` (seção 7). |
| Aegires ↔ reparo + dano em área | Aegires | Nenhum módulo único | **Não adaptar hoje.** É um efeito composto, sem categoria atual. Candidato futuro à categoria `field` (seção 7). |
| Navanax ↔ supressão | Navanax | Nenhum módulo | **Não adaptar hoje.** Supressão é mecânica exclusiva de capital. Candidato futuro à categoria `field` (seção 7). |
| Cyerce / Retusa ↔ Repair Point / Repair Turret | Cyerce, Retusa | `repair-point`, `repair-turret` | **Não adaptar Cyerce/Retusa.** Os feixes são parte da identidade de suporte desses cascos. |
| **Vela ↔ Repair Point / Repair Turret** | **Vela** | `repair-point`, `repair-turret` | **Exceção: manter como candidato a módulo.** Apenas o `repair-beam-weapon-center-large`, não o laser principal. |

**Por que Vela é a exceção:** o feixe de reparo da Vela é uma arma separada (`RepairBeamWeapon`) com `repairSpeed=1.4`, alcance 120 e funcionamento contínuo. Ele pode ser isolado do laser da hull sem depender de uma `Ability`, o que dá um limite de módulo claro. Cyerce e Retusa continuam nativas. A exceção é **de produto, não de código**: hoje o feixe da Vela também não é reconhecido como módulo `repair`; ele precisaria ser implementado como módulo novo.

> **Armamentos:** os destaques de armamento acima não viram módulos novos. Eles permanecem no relatório como referência de comparação. A adaptação de torres continua limitada por `TurretWeapon.canAdapt`, pelo tamanho do slot e pela decisão de produto.  
> **Importante:** esta decisão não remove nem altera torres/blocos que já existem; ela apenas retira as unidades destacadas do backlog de conversão em módulo.

---

## 6. Achados que não são suporte reconhecido hoje

### 6.1 `ShieldRegenFieldAbility` — Scepter, Pulsar e Bryde

**Arquivo:** `core/src/orbital/entities/abilities/ShieldRegenFieldAbility.java`

**O que faz:** a cada `reload`, percorre unidades aliadas no `range` e adiciona `amount` ao `shield` de cada uma, até `max`. Não cria barreira física, não intercepta projéteis, não protege construções e não tem arco direcional. É um pulso de pontos de escudo em unidades.

| Unidade | `amount` | `max` | `reload` | `range` | Observação |
|---|---:|---:|---:|---:|---|
| Scepter | 25 | 250 | 60 ticks (1 s) | 60 px (7,5 tiles) | Pulso mais forte e frequente. |
| Pulsar | 20 | 40 | 300 ticks (5 s) | 60 px (7,5 tiles) | Escudo pequeno, recarga longa. |
| Bryde | 20 | 40 | 240 ticks (4 s) | 60 px (7,5 tiles) | Versão naval do pulso. |

**Por que não é suporte reconhecido:** `SupportModuleCategory.ofAbility` só mapeia `ForceFieldAbility` e `ShieldArcAbility` para `force`. `ShieldRegenFieldAbility` retorna `null`.

**Consequência prática:** pelo critério de categoria, uma unidade com esse ability **não bloqueia** a instalação de um `force-projector`, porque `ofAbility` não vê conflito. O risco é empilhar dois sistemas de escudo (`unit.shield` nativo + módulo de força) e dificultar o balanceamento; qualquer implementação futura precisa validar esse estado no `SupportMount`.

**Decisão:** excluir da adaptação hoje. Se um dia for modular, o caminho proposto é a categoria `field`, família **shield** (seção 7), com exclusividade explícita em relação a `force`.

### 6.2 `EnergyFieldAbility` — Aegires

**Arquivo:** `core/src/orbital/entities/abilities/EnergyFieldAbility.java`

**O que faz:** a cada 65 ticks, atinge até 25 alvos em um raio de 180 px. Contra inimigos, causa 40 de dano e aplica `electrified` por 6 s; contra aliados, cura 1,5% da vida máxima, com multiplicador de 0,5 para unidades do mesmo tipo. Pode atingir unidades e construções. Usa `Damage.findAbsorber`, então o campo é absorvido por plastânio como um efeito elétrico.

**Por que não é suporte reconhecido:** não é `repair` (o foco não é cura), não é `force` (não cria escudo) e não é `overdrive` (não dá buff). É um efeito composto de dano em área + status + cura. `SupportModuleCategory` não tem categoria para isso.

**Consequência prática:** mapear esse ability para `repair` perderia o dano e o `electrified`; mapear para `force` seria incorreto. A adaptação exigiria a categoria `field`, família **energy** (seção 7), mantendo as duas faces: dano em inimigos e cura em aliados.

**Decisão:** excluir da adaptação hoje. Aegires mantém o campo como identidade nativa; a modularização futura fica registrada como candidata a `field`.

### 6.3 `SuppressionFieldAbility` — Navanax

**Arquivo:** `core/src/orbital/entities/abilities/SuppressionFieldAbility.java`

**O que faz:** a cada `maxDelay` de 90 ticks (1,5 s), chama `Damage.applySuppression` em um raio de 200 px, com `reload=90`, `maxDelay=90` e `applyParticleChance=13`. O efeito impede o reparo de construções inimigas por 1,5 s e é reaplicado a cada 1,5 s; `Damage.applySuppression` percorre apenas blocos de time diferente. Não cura, não dá escudo e não buffa aliados.

**Por que não é suporte reconhecido:** não existe categoria `suppression` em `SupportModuleCategory`. É uma mecânica de negação de reparo, não de sustentação.

**Consequência prática:** se fosse transformado em módulo, precisaria da categoria `field`, família **suppression** (seção 7), e de uma regra clara de alvo (provavelmente apenas construções inimigas), além de decidir se o efeito acumula com outras supressões. Hoje isso é exclusivo do Navanax.

**Decisão:** excluir da adaptação hoje. Manter como mecânica nativa de capital, com modularização futura apenas como candidata a `field`.

### 6.4 `RepairBeamWeapon` — Vela, Cyerce e Retusa

**O que é:** `RepairBeamWeapon` é uma **arma**, não uma `Ability`. `SupportModuleCategory.ofAbility` recebe abilities e, por isso, nunca vê esses feixes. `SupportModuleCategory.of(Block)` também não aceita os blocos de reparo por nome, exceto `mender` e `mend-projector`.

| Unidade | Arma | `repairSpeed` | Alcance |
|---|---|---:|---:|
| Vela | `repair-beam-weapon-center-large` | 1,4 | 120 |
| Cyerce | `repair-beam-weapon-center` | 0,7 | 130 |
| Retusa | `repair-beam-weapon-center` | 0,75 | 120 |
| Repair Point | `repair-point` | 0,45 | 60 |
| Repair Turret | `repair-turret` | 3,0 | 145 |

**Consequência prática:** Vela, Cyerce e Retusa têm reparo funcional, mas **não são reconhecidas como suporte `repair`** pelo sistema de categorias. Se o objetivo for um módulo de reparo, o feixe precisa ser extraído como arma/bloco próprio e passar por uma regra de compatibilidade; não basta marcar a unidade como `repair`.

**Decisão:** Vela é a única exceção e permanece como candidata, usando apenas `repair-beam-weapon-center-large`. Cyerce e Retusa continuam nativas.

### 6.5 `buildSpeed` e Build Tower

`buildSpeed` não é uma habilidade de combate. É a **velocidade com que uma unidade constrói, desmonta e repara estruturas**. Quanto maior o número, mais rápido o trabalho rende. Ele aparece na ficha da unidade como um multiplicador, ao lado de velocidade, vida e capacidade de carga, e é usado pelo sistema de construção do jogo — não pelo sistema de módulos dorsais.

Em termos práticos:

- um valor baixo, como **0,3** da Nova, significa que ela é lenta para levantar uma parede ou terminar uma torre;
- um valor médio, como **1,5** da Retusa, já permite acompanhar uma linha de construção;
- um valor alto, como **3,0** da Vela, **3,0** do Aegires ou **3,5** do Navanax, faz a unidade funcionar quase como uma construtora dedicada.

Valores registrados no catálogo atual:

| Unidade | `buildSpeed` | Leitura simples |
|---|---:|---|
| Nova | 0,3 | Constrói devagar; o foco dela é reparo, não obra. |
| Pulsar | 0,5 | Ajuda pouco em construção. |
| Quasar | 1,1 | Constrói em ritmo próximo do normal. |
| Retusa | 1,5 | Bom apoio de obra, além do reparo. |
| Oxynoe | 2,0 | Constrói rápido. |
| Cyerce | 2,0 | Constrói rápido. |
| Vela | 3,0 | Muito rápida para construir e reparar. |
| Aegires | 3,0 | Muito rápido para construir e reparar. |
| Navanax | 3,5 | O mais rápido do grupo; perfil de construtora de capital. |

O **Build Tower** (`build-tower`) é outra coisa: é um **bloco de construção**, não um módulo que a unidade veste. Ele ocupa uma área 3x3, tem alcance de 200 px e `buildSpeed=1.5`, e funciona como uma torre que constrói por conta própria. Para operar, consome energia e nitrogênio. Ou seja, ele é uma instalação do mapa — como uma torre de reparo ou um gerador — e não uma peça dorsal que se encaixa no casco de uma unidade.

**Por que não entra em `repair`, `overdrive` ou `force`:**

- não é `repair`, porque não devolve vida a unidades em combate; ele ergue e conserta **estruturas**;
- não é `overdrive`, porque não dá bônus temporário a aliados;
- não é `force`, porque não gera escudo.

Também não é um bom candidato à categoria `field` proposta na seção 7: `field` trata de efeitos em área sobre unidades, e construção é logística de base.

**Decisão para este relatório:** não adaptar agora. A construção continua sendo uma função de casco, e o Build Tower continua sendo um bloco de mapa. A trilha de implementação futura — incluindo a categoria `build`, o `build-assist-projector` e as opções de transporte do Build Tower — está documentada em [`PROPOSTA_BUILD_SPEED_E_BUILD_TOWER.md`](PROPOSTA_BUILD_SPEED_E_BUILD_TOWER.md).

---

## 7. Proposta — categoria `field` (campo)

> **Status:** proposta de projeto. **Não implementada.** Nenhuma linha de código foi alterada por esta seção; ela serve para registrar a decisão de desenho e o desenho técnico mínimo caso a categoria seja aprovada.

### 7.1 Por que criar uma categoria nova

As três habilidades da seção 6 têm um ponto em comum: são **efeitos de área aplicados por uma unidade**, mas não se encaixam em nenhuma categoria existente.

- Forçá-las em `repair` perderia dano, status ou negação de reparo.
- Forçá-las em `overdrive` prometeria um bônus que elas não dão.
- Forçá-las em `force` misturaria duas coisas diferentes: `force` é uma **barreira que absorve projéteis**; as habilidades da seção 6 operam sobre **estado de unidades ou construções**.

Uma categoria própria resolve isso sem distorcer o significado das três atuais. O nome proposto é **`field`** (“campo”), porque todas funcionam como uma aura periódica em uma área ao redor da unidade.

### 7.2 Abrangência

| Família | Habilidade de origem | Unidade(s) | O que o campo faz | Por que não cabe em `repair`/`force`/`overdrive` |
|---|---|---|---|---|
| **shield** | `ShieldRegenFieldAbility` | Scepter, Pulsar, Bryde | Adiciona pontos a `unit.shield` de aliados próximos, até um máximo. | Não é barreira de projéteis como `ForceFieldAbility`/`ShieldArcAbility`; é recarga de escudo de unidade. |
| **energy** | `EnergyFieldAbility` | Aegires | Dano em área, `electrified` por 6 s e cura percentual em aliados. | É dano + status + cura ao mesmo tempo; não é reparo puro nem buff. |
| **suppression** | `SuppressionFieldAbility` | Navanax | Impede o reparo de construções inimigas por 1,5 s, reaplicado a cada 1,5 s. | É negação de reparo, não sustentação; não existe categoria equivalente. |

A categoria `field` cobre as três famílias. As famílias são **rótulos internos de comportamento**, não novas categorias de topo. Isso é intencional: mantém a regra “um módulo por categoria” e evita que uma unidade empilhe escudo, dano e supressão no mesmo casco.

### 7.3 Regras propostas

1. **Um módulo `field` por unidade.** A regra já existente do enum é mantida: a unidade pode carregar no máximo um bloco de cada categoria. Como as três famílias vivem em `field`, elas são mutuamente exclusivas entre si.
2. **Habilidade nativa bloqueia módulo equivalente.** Se o casco já tem `ShieldRegenFieldAbility`, `EnergyFieldAbility` ou `SuppressionFieldAbility`, ele não pode instalar um módulo `field`. Isso preserva a identidade da unidade e evita duas fontes do mesmo efeito.
3. **`field` e `force` são mutuamente exclusivos.** Essa é a regra mais importante e a que exige código além do enum. `ShieldRegenFieldAbility` também escreve em `unit.shield`, o mesmo estado compartilhado usado por `ForceFieldAbility`. Sem exclusividade, o jogador poderia empilhar escudo nativo regenerativo com uma barreira de força, e o balanceamento perderia referência. A exclusividade vale nos dois sentidos: quem tem `field` não instala `force`, e quem tem `force` não instala `field`.
4. **A supressão continua restrita a alvos válidos.** `SuppressionFieldAbility` já percorre apenas construções de time diferente via `Damage.applySuppression`. O módulo não deve passar a afetar aliados nem unidades.
5. **O campo de energia mantém as duas faces.** O módulo de Aegires precisa continuar causando dano em inimigos e curando aliados; não deve ser simplificado para apenas um dos lados.
6. **A exceção da Vela não muda.** O feixe `repair-beam-weapon-center-large` continua sendo candidato a `repair`, não a `field`.
7. **Construção fica fora.** `buildSpeed` e `Build Tower` não entram em `field`; eles são logística de base, não aura de combate.

### 7.4 Blocos provisórios

Nenhum destes blocos existe hoje. Os nomes são provisórios e servem apenas para fixar a intenção de cada família.

| Nome provisório | Família | Habilidade equivalente | Observação |
|---|---|---|---|
| `shield-regen-projector` | shield | `ShieldRegenFieldAbility` | Pulso de escudo em aliados; não intercepta projéteis. |
| `energy-field-projector` | energy | `EnergyFieldAbility` | Dano + `electrified` em inimigos e cura em aliados. |
| `suppression-projector` | suppression | `SuppressionFieldAbility` | Impede reparo de construções inimigas na área. |

### 7.5 Esboço técnico

O esboço abaixo é **ilustrativo**. Ele mostra o mínimo que precisaria mudar em `core/src/orbital/type/SupportModuleCategory.java`, mas não foi aplicado.

```java
public enum SupportModuleCategory{
    repair,
    overdrive,
    force,
    field; // proposta: ainda não existe

    public static @Nullable SupportModuleCategory of(Block block){
        if(block == null || block.name == null) return null;

        return switch(block.name){
            case "mender", "mend-projector" -> repair;
            case "overdrive-projector", "overdrive-dome" -> overdrive;
            case "force-projector" -> force;
            //proposta: famílias de campo, todas na mesma categoria
            case "shield-regen-projector", "energy-field-projector", "suppression-projector" -> field;
            default -> null;
        };
    }

    public static @Nullable SupportModuleCategory ofAbility(Ability ability){
        if(ability == null) return null;

        if(ability instanceof RepairFieldAbility) return repair;

        if(ability instanceof StatusFieldAbility status &&
            (status.effect == StatusEffects.overdrive || status.effect == StatusEffects.overclock)) return overdrive;

        if(ability instanceof ForceFieldAbility || ability instanceof ShieldArcAbility) return force;

        //proposta: campos de unidade não são barreira de projéteis
        if(ability instanceof ShieldRegenFieldAbility
            || ability instanceof EnergyFieldAbility
            || ability instanceof SuppressionFieldAbility) return field;

        return null;
    }

    /** Proposta: field e force disputam unit.shield e não podem coexistir. */
    public static boolean conflicts(SupportModuleCategory a, SupportModuleCategory b){
        if(a == null || b == null) return false;
        return a == b || (a == field && b == force) || (a == force && b == field);
    }
}
```

Além do enum, a proposta exigiria três pontos de integração:

- **`SupportMount.createAbility`** precisaria reconhecer os novos blocos e criar a `Ability` correspondente, do mesmo jeito que hoje faz com `MendProjector`, `OverdriveProjector` e `ForceProjector`.
- **`SupportMount.removed`** precisaria liberar `unit.shield` também para `ShieldRegenFieldAbility`, não só para `ForceFieldAbility`. Hoje a limpeza de escudo é específica da barreira de força.
- **A instalação do módulo** precisaria consultar `conflicts`, e não apenas comparar categorias iguais. Sem isso, `field` e `force` continuariam podendo coexistir.

### 7.6 O que esta proposta não faz

- Não transforma Scepter, Pulsar, Bryde, Aegires ou Navanax em módulos hoje.
- Não altera `SupportModuleCategory`, `SupportMount` ou qualquer bloco existente.
- Não cria os três blocos provisórios.
- Não muda a decisão da seção 5: as unidades continuam nativas até uma implementação aprovada.

---

## 8. Leitura final

1. **A igualdade real é curta e precisa:** só o projétil de **Dagger/Risso** coincide exatamente com a munição de cobre do **Duo**. A munição de chumbo do **Scatter** é praticamente a mesma do flak do **Minke**, mas com `lifetime` diferente.
2. **As melhores correspondências de torre são de função, não de ficha:** Omura/Foreshadow compartilham a mesma classe e o mesmo comprimento de rail; Toxopid/Fuse compartilham a escopeta de estilhaços; Collaris/Titan e Toxopid/Ripple compartilham artilharia de área com fragmentos.
3. **O suporte atual cobre bem reparo e força, e cobre overdrive de forma adaptada:** `mender`/`mend-projector` têm análogos claros em Nova; `force-projector` tem análogos em Quasar e Tecta; Oxynoe é o único overclock de unidade e conversa com os dois overdrives.
4. **As correspondências não são um backlog de adaptação:** Dagger/Risso, Minke, Omura, Toxopid, Collaris, Arkyid, Nova, Oxynoe, Quasar, Tecta, Scepter, Pulsar, Bryde, Aegires, Navanax, Cyerce e Retusa ficam fora da conversão em módulos **hoje**. **Vela é a única exceção**, e somente pelo feixe `repair-beam-weapon-center-large`. Scepter, Pulsar, Bryde, Aegires e Navanax só poderiam voltar como candidatos à categoria `field` proposta na seção 7, que ainda não existe no código.
5. **Há três lacunas explícitas:** `ShieldRegenFieldAbility` não é mapeado como `force`; `EnergyFieldAbility` não tem categoria; `SuppressionFieldAbility` não tem módulo. Por isso, Scepter/Pulsar/Bryde, Aegires e Navanax não devem ser apresentados como “já suportados” pelo sistema atual. A seção 7 registra a proposta `field` para cobrir esses três casos, mas deixa claro que ela **ainda não existe no código**.
6. **A maior parte do catálogo de torres não é instalável hoje:** Duo é a única injeção automática. Scatter, Hail, Fuse, Ripple, Spectre, Titan, Foreshadow, Swarmer, Salvo, Arc, Lancer e outras `ItemTurret`/`PowerTurret` são **tecnicamente adaptáveis** por `TurretWeapon`, mas ainda dependem de slot, tamanho e exposição no jogo. Segment, Wave, Tsunami, Parallax, Lustre, Repair Turret e Build Tower ficam no **catálogo de projeto**; Meltdown entra na exceção de `LaserTurret`, que herda `PowerTurret`. O estudo de construção futura está em [`PROPOSTA_BUILD_SPEED_E_BUILD_TOWER.md`](PROPOSTA_BUILD_SPEED_E_BUILD_TOWER.md).
