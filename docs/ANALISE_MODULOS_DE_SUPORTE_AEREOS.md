# Análise — módulos de suporte em unidades aéreas

**Data:** 6 de novembro de 2026  
**Escopo:** Reparador (`mender`), Projetor de Reparo (`mend-projector`), Projetor de Sobrecarga (`overdrive-projector`), Domo de Sobrecarga (`overdrive-dome`) e Projetor de Campo de Força (`force-projector`).  
**Pergunta de projeto:** em quais cascos cada módulo pode coexistir com uma torreta, ocupando posições distintas e sem sobreposição?  
**Base consultada:** definições de blocos e unidades, sistema de slots/orçamento dorsal, adaptador de torreta, habilidades de unidade, atlas de sprites e gerador de IO de entidades.  
**Estado atual:** fase 1 (encaixe físico), fase 2 (efeitos funcionais), fase 3 (escala visual por casco), fase 4 (proporção unificada, derivada das medidas de **todos** os cascos, para **torretas + módulos de suporte**) e fase 5 (ícones de suporte no HUD inferior com selo **ativo/inativo** e alternância por clique) **commitadas** (`14900c021c`); fase 6 (recalibração das posições dorsais sobre a silhueta real + colisão por tamanho desenhado) implementada e verificada por build/testes, aguardando revisão visual em jogo. Persistência em saves/sync segue fora de escopo.

---

## 0. Decisões de escopo (confirmadas por Péterson)

### 0.1 Fase 1 — encaixe físico (concluída)

| Decisão | Valor escolhido |
|---|---|
| Alcance da entrega | **Somente encaixe físico/visual** — posicionamento, sobreposição e UI. Nenhum efeito de gameplay. |
| Orçamento dorsal | Módulos de suporte **não consomem nem ampliam** o orçamento, a contagem de slots ou `mountedTurrets()`. |
| Posições | **Manuais por casco**, gravadas como tabela fixa; nenhuma geração automática em runtime. |
| Limite de quantidade | **Um por categoria**, não um por bloco exato. |

### 0.2 Fase 2 — efeitos funcionais (concluída)

Pedido posterior de Péterson: *"pode deixar funcional os modulos"*. As decisões foram confirmadas por escolha explícita:

| Decisão | Valor escolhido |
|---|---|
| Alvo dos efeitos | **Unidades aliadas em área**; o campo de força protege **a própria unidade** portadora. |
| Parâmetros | Usar os **parâmetros reais do bloco** (alcance, recarga, percentual de cura, multiplicador de velocidade, vida do escudo, raio e lados do polígono). Unidades não têm energia nem inventário de itens, portanto **não há consumo** nem boosts de tecido de fase. |
| Conflito com habilidade nativa | Cascos que já possuem efeito equivalente **não aceitam** o módulo daquela categoria; a UI mostra o efeito nativo como **módulo fixo, não removível**. Exemplo dado: `oct` já tem escudo. |
| Persistência | **Somente na sessão** — sem save e sem sincronização de rede nesta fase. |

### 0.3 Fase 3 — escala visual por casco (concluída)

Pedido posterior de Péterson, após revisão em jogo: *"poderia diminuir o tamanho dos modulos de suporte para caber nas unidades, pode manter a compatibilidade de cada unidade ajuste somente o tamanho nao precisa ser um tamanho fixo pode ser tamanho definido para cada unidade"*.

| Decisão | Valor escolhido |
|---|---|
| O que muda | **Somente o tamanho desenhado do sprite**. Nenhuma regra de aceitação, âncora, footprint, alcance, recarga, cura, escudo ou orçamento é alterada. |
| Granularidade | **Escala por casco** — na fase 3 via `UnitType.supportVisualScale`, com override opcional **por âncora** (`SupportAnchor.visualScale`). Não é um tamanho fixo global. **Substituída na fase 4** por uma regra derivada das medidas reais. |
| Limites | A escala efetiva fica presa a uma faixa relativa ao casco: o sprite desenhado nunca passa de **28%** da largura do casco e nunca fica abaixo de **10%** (quando o sprite nativo já é menor, a escala fica em 1). Módulos só **encolhem**, `0 < escala <= 1`. |
| Compatibilidade | **Idêntica à fase 2**, congelada em teste de snapshot para os 22 cascos. |

### 0.4 Fase 4 — proporção unificada para torretas e suporte (concluída)

Pedido posterior de Péterson, após nova revisão em jogo: *"falta alguns ajustes, vc consegue analisar os tamanhos de todas as unidades e se basear nisso para todas as torretas e modulos de suporte ficarem ajsutados"*.

| Decisão | Valor escolhido |
|---|---|
| Abrangência | **Todos os 22 cascos/variantes** e **todos os módulos dorsais**: torretas removíveis (`TurretWeapon`) e os cinco blocos de suporte. Armas nativas do casco **não** são escaladas. |
| Origem do tamanho | **Medido**, não calibrado à mão: `sqrt(dorsalAreaOverride)` (48 px em `mono`/`flare`/`alpha` até 400 px em `oct`) mais a contagem de posições dorsais do casco e o tamanho real do sprite do módulo no atlas. |
| Regra | **Uma única** (`DorsalVisual`) para torretas e suporte; a tabela manual de escalas da fase 3 foi removida. |
| Granularidade | A escala é **definida para cada casco** pela própria medida; permanecem dois ajustes opcionais: `UnitType.dorsalVisualTuning` (por casco) e `SupportAnchor.scaled(float)` (por âncora). |
| O que muda | **Somente o desenho** (`Weapon.draw`, `Weapon.drawOutline`, `UnitType.drawSupportModules`). Nenhuma regra de aceitação, âncora, footprint, custo de orçamento, alcance, cura, escudo, boost, UI ou serialização. |
| Compatibilidade | **Idêntica às fases 2 e 3**, reconfirmada pelo mesmo snapshot de 22 cascos. |

### 0.5 Fase 5 — ícones de suporte no HUD inferior (concluída)

Pedido posterior de Péterson, após aprovar a fase 4: *"ficou muito bom, poderia adicionar para os modulos de suporte o icone aparecer ao lado das torretas na parte inferior adicione para aparecer ativo encima desses suporte, quando clica encima deles eles desativa"*.

| Decisão | Valor escolhido |
|---|---|
| Onde | **Barra dorsal inferior** (`HudFragment`, `turret-loadout`), nas células **imediatamente após as torretas** e antes dos botões `+`/martelo. |
| Selo | Texto **ATIVO** / **INATIVO** sobreposto **em cima do ícone**, vivo a cada frame; borda âmbar + ícone normal quando ativo, borda cinza + ícone escurecido + barra vermelha quando inativo. |
| Semântica do clique | Confirmada por escolha explícita: **alternar ativo/inativo**. O módulo **continua instalado** — mantém âncora, footprint, categoria ocupada e bloqueio de um segundo módulo da categoria. Um segundo clique reativa. |
| Remoção | Permanece **apenas no diálogo** de loadout (botão `Icon.cancel`); o HUD nunca desinstala. |
| Efeito | Desligar bloqueia `created`/`update`/`draw` da habilidade e **libera o pool de escudo compartilhado**; religar reinicializa o pool pelos parâmetros reais do bloco. |
| Persistência | `active` é estado de sessão, no `SupportMount` transitório — **nenhuma mudança** em IO de entidades, saves ou sync. |

### 0.6 Fase 6 — posições sobre a silhueta real e colisão por tamanho desenhado (concluída)

Pedido posterior de Péterson, após revisar o jogo em capturas: as torretas apareciam **soltas na borda** da silhueta — propostas geometricamente válidas, mas pousadas em espaço transparente fora do sprite.

| Decisão | Valor escolhido |
|---|---|
| Diagnóstico | A silhueta usada em `fitsHull` é o **quadrado de área equivalente** do sprite, não a arte. Os cantos desse quadrado são transparentes na maioria dos capitais (`oct`, `quad`, `eclipse`, `antumbra`, `disrupt`), então uma âncora "dentro da silhueta" podia estar **fora do desenho**. |
| Separação entre módulos | Passa a usar o **tamanho desenhado** (`DorsalGeometry.visualFootprint`), e não a pegada do bloco. A fase 4 encolheu os sprites: um 5×5 no `oct` é desenhado com ~16 unidades de mundo, não 40. Cobrar 40 de folga empurrava os módulos para os cantos vazios. |
| Silhueta e orçamento | **Permanecem** com a pegada cheia do bloco (`fitsHull`, `dorsalBudget`) — a régua de aceitação não muda. |
| o que muda na prática | `WeaponsComp.overlapsTurretMounts` / `overlapsSupportMounts` / `findTurretSlot` / `findSupportAnchor`. Nenhuma regra de aceitação, `maxSize`, alcance, cura, escudo, orçamento, UI ou serialização. |
| Posições | Recalibradas casca a casca em `TurretLoadouts`: cada posição ** mantém a direção** relativa ao centro do casco e entra até o sprite tocar a arte. |
| Verificação | Teste novo `noTwoDorsalModulesOverlapOnceDrawn` (22 cascos) mais snapshot de compatibilidade intacto. |

Categorias:

- **reparo:** `mender` **ou** `mend-projector`;
- **sobrecarga:** `overdrive-projector` **ou** `overdrive-dome`;
- **campo de força:** `force-projector`.

Categorias diferentes podem coexistir — 1 reparo + 1 sobrecarga + 1 campo de força — **se o conjunto couber com a torreta e sem sobreposição**. A identificação é feita por `block.name` (não por classe Java), porque `mender`/`mend-projector` compartilham `MendProjector` e os dois blocos de sobrecarga compartilham `OverdriveProjector`.

> **Correção de registo:** a primeira versão deste documento dizia "no máximo uma unidade de cada *tipo*" (cinco tipos). A regra confirmada pelo usuário é **uma por categoria** (três categorias). O texto antigo nas seções 6 e 8 foi substituído abaixo.

---

## 1. Conclusão executiva

A análise cobre os **20 cascos aéreos** cadastrados em `TurretLoadouts`, mais as duas variantes de demonstração (`monoDuo`, `flareDuo`). Pela regra de triagem de silhueta já usada para torretas (`cobertura ≤ 50%` e `largura relativa ≤ 70%`), os módulos de **1×1** passam individualmente em todos os cascos; os de **2×2** passam em 10; os de **3×3** passam em 6. Essa triagem é apenas um filtro de tamanho **individual** e não prova encaixe conjunto.

Após a implementação do encaixe físico (fase 1), o resultado real de packing é mais restritivo do que a triagem individual sugeria:

- **5 cascos** aceitam as três categorias simultaneamente: `quad`, `antumbra`, `disrupt`, `eclipse`, `oct`;
- **1 casco** aceita duas: `quell` (reparo + sobrecarga);
- **4 cascos** aceitam apenas reparo 1×1: `emanate`, `horizon`, `zenith`, `obviate`;
- **12 cascos** não aceitam nenhum módulo de suporte junto de uma torreta: `mono`, `flare`, `poly`, `alpha`, `beta`, `gamma`, `evoke`, `incite`, `avert`, `mega`, `monoDuo`, `flareDuo`.

Ou seja: **"1×1 passa em 20/20" era uma aprovação de silhueta isolada, não de convivência com torreta.** Em cascos pequenos, a torreta 1×1 e um reparador 1×1 simplesmente não cabem lado a lado com folga.

**Atualização da fase 2 (conflito nativo):** `oct` possui `ForceFieldAbility` e `RepairFieldAbility` nativas, então reparo e campo de força passam a ser **módulos fixos** e deixam de ser instaláveis; resta apenas **sobrecarga** como categoria livre. `poly` também tem reparo nativo, mas já não tinha âncoras. Assim, na versão funcional, **4 cascos** efetivamente recebem três módulos (`quad`, `antumbra`, `disrupt`, `eclipse`), `quell` recebe dois, 4 recebem um e `oct` recebe um — todos os demais, nenhum. As âncoras físicas de `oct` continuam existindo; a rejeição vem da regra de conflito, não de falta de espaço, e está coberta por teste.

---

## 2. Medidas dos cinco módulos

A escala usada no relatório de torretas é 32 px por tile. Os blocos ocupam 1, 2 ou 3 tiles por lado; as dimensões informadas são a pegada quadrada nominal, não o raio do efeito.

| Módulo | Bloco/classe atual | Pegada no mapa | Sprite principal | Efeito relevante | Recurso atual |
|---|---|---:|---:|---|---|
| **Reparador** | `mender` / `MendProjector` | 1×1 tile = 32×32 px | 32×32 px | Alcance 40; até 60 com silício | Energia e silício para boost |
| **Projetor de Reparo** | `mend-projector` / `MendProjector` | 2×2 = 64×64 px | 64×64 px | Alcance 85; até 100 com fase | Energia e tecido de fase para boost |
| **Projetor de Sobrecarga** | `overdrive-projector` / `OverdriveProjector` | 2×2 = 64×64 px | 64×64 px | Alcance padrão da classe: 80; boost de fase +20 | Energia e tecido de fase |
| **Domo de Sobrecarga** | `overdrive-dome` / `OverdriveProjector` | 3×3 = 96×96 px | 96×96 px | Alcance 200; sem boost de alcance por fase (`hasBoost = false`) | Energia, fase e silício |
| **Projetor de Campo de Força** | `force-projector` / `ForceProjector` | 3×3 = 96×96 px | 96×96 px | Raio 101,7; até 181,7 com bônus de fase | Energia e tecido de fase |

**Nota de escala:** as entradas do atlas confirmam 32×32, 64×64 e 96×96 px, respectivamente. O alcance de reparo, sobrecarga ou escudo é uma área de efeito em gameplay; não altera a pegada física do módulo. Em especial, o escudo do Projetor de Campo de Força não transforma o bloco 3×3 numa peça física do tamanho do raio do campo.

**Nota de fase 2:** a coluna "Recurso atual" descreve o bloco de construção. Em unidades esses consumos **não existem** — os parâmetros numéricos de alcance, recarga, cura, boost e escudo são reaproveitados, mas sem energia, itens ou bônus de fase (seção 11.2).

## 3. Método de triagem

```text
P = lado da pegada em pixels
A = largura do sprite do casco × altura do sprite do casco
Cobertura = P² / A
Largura relativa = P / maior(largura do casco, altura do casco)
Aprovado na triagem individual = cobertura ≤ 50% e largura relativa ≤ 70%
```

Esse cálculo é apenas um **filtro de tamanho individual**. Ele não testa a forma real do sprite, a posição dorsal, a torreta coexistente, canos/recuo, sombras nem os efeitos visuais.

## 4. Triagem dos 20 cascos (registro histórico)

`Slot atual` resume os tamanhos máximos declarados para torretas em `TurretLoadouts`; não significa que o slot esteja livre nem que aceite os projetores.

| Casco | Sprite (px) | Slots dorsais atuais (maxSize) | Reparador 1×1 | Projetores 2×2 | Projetores 3×3 |
|---|---:|---|---|---|---|
| Mono | 48×48 | 1 | Sim | Não | Não |
| Flare | 48×48 | 1 | Sim | Não | Não |
| Poly | 56×57 | 1 | Sim | Não | Não |
| Alpha | 48×48 | 1 | Sim | Não | Não |
| Beta | 56×54 | 1 | Sim | Não | Não |
| Gamma | 68×68 | 1 | Sim | Não | Não |
| Evoke | 56×54 | 1 | Sim | Não | Não |
| Incite | 66×72 | 1 | Sim | Não | Não |
| Emanate | 82×84 | 1 | Sim | Não | Não |
| Horizon | 72×72 | 1 | Sim | Não | Não |
| Avert | 96×96 | 2 | Sim | Sim | Não |
| Mega | 100×100 | 2 | Sim | Sim | Não |
| Zenith | 112×112 | 2, 1 | Sim | Sim | Não |
| Obviate | 140×130 | 2, 2 | Sim | Sim | Não |
| Quad | 220×220 | 4, 2 | Sim | Sim | Sim |
| Quell | 162×162 | 3, 1 | Sim | Sim | Sim |
| Antumbra | 216×240 | 4, 2, 2 | Sim | Sim | Sim |
| Disrupt | 243×243 | 4, 2 | Sim | Sim | Sim |
| Eclipse | 320×321 | 5, 5, 2 | Sim | Sim | Sim |
| Oct | 400×400 | 5, 4, 4, 2, 2 | Sim | Sim | Sim |

### 4.1 Contagem por pegada

- **Reparador (1×1):** triagem individual aprovada em **20/20** cascos.
- **Projetor de Reparo e Projetor de Sobrecarga (2×2):** aprovados em **10/20**: Avert, Mega, Zenith, Obviate, Quad, Quell, Antumbra, Disrupt, Eclipse e Oct.
- **Domo de Sobrecarga e Projetor de Campo de Força (3×3):** aprovados em **6/20**: Quad, Quell, Antumbra, Disrupt, Eclipse e Oct.

## 5. Pode ficar ao lado de uma torreta?

### 5.1 Estado original (antes da implementação)

- Os cinco projetores não são `Turret`; `TurretWeapon.of()`, `UnitType.canMount(Turret)` e `WeaponsComp.mountTurret()` só aceitam torretas.
- `TurretSlot(int)` define offset `(0, 0)` e `TurretWeapon` nasce com `x = 0`, `y = 0`; dois elementos nesse ponto se sobrepõem.
- Não havia teste de interseção entre mounts nem estado de runtime para suportes.

### 5.2 O que a área somada não prova

O cálculo de cobertura individual não demonstra que duas formas cabem na silhueta, e o orçamento dorsal só contabiliza torretas removíveis. Mesmo footprints sem interseção podem cobrir nariz/cabine, motores, asas ou o curso de rotação/recuo do cano — isso exige inspeção visual, não apenas geometria.

### 5.3 Estado atual

O encaixe físico **e** os efeitos de gameplay foram implementados (seções 9 e 11). A resposta técnica agora é: **existe um conjunto válido "torreta + suportes funcionais" nos cascos listados na seção 1, validado por geometria conservadora e por testes de comportamento**, mas ainda **não revisado visualmente em jogo**.

## 6. Recomendações — estado de atendimento

| # | Recomendação original | Estado |
|---|---|---|
| 1 | Limitar quantidade por tipo/categoria | **Atendido** — uma por categoria, aplicado em `hasSupportCategory` / `findSupportAnchor`. |
| 2 | Não reutilizar slots de torreta como aprovação automática | **Atendido** — `SupportAnchor` é separado de `TurretSlot` e não consome orçamento. |
| 3 | Definir posições `(x, y)` reais por casco, acompanhando a rotação | **Atendido** — tabela manual em `TurretLoadouts`, desenho com `Angles.trnsx/trnsy(unit.rotation - 90, …)`. |
| 4 | Testar o conjunto completo, não só pares | **Atendido em geometria e em comportamento** — validação de todos os pares e 17 testes JUnit; **pendente** a inspeção visual. |
| 5 | Contabilizar footprint no orçamento compartilhado | **Deliberadamente não aplicado** — decisão do usuário: suportes não consomem orçamento nem slots. |
| 6 | Especificar função em unidades (reparo/sobrecarga/escudo) | **Atendido na fase 2** — cada `SupportMount` executa uma habilidade de unidade com os parâmetros reais do bloco (seção 11.2). |
| 7 | Renderizar e validar conjuntos representativos em escala de gameplay | **Pendente** — não foi aberto o jogo; âncoras são propostas geométricas. |
| 8 | Evitar duplicar efeitos nativos do casco | **Atendido na fase 2** — `hasNativeSupport` bloqueia a categoria e a UI exibe o efeito como módulo fixo (seção 11.4). |

---

## 7. Fontes no código

- `core/src/orbital/content/Blocks.java` — tamanho, alcance, consumo e parâmetros dos cinco blocos (aprox. linhas 1921–1978).
- `core/src/orbital/content/SupportEffects.java` — dois `StatusEffect` ocultos de sobrecarga, com `speedBoost` lido dos blocos.
- `core/src/orbital/core/ContentLoader.java` — ordem de criação de conteúdo; `SupportEffects.load()` após `Blocks.load()`.
- `core/src/orbital/content/TurretLoadouts.java` — multiplicadores, áreas, slots e âncoras manuais por casco.
- `core/src/orbital/type/UnitType.java` — `spriteArea`, `dorsalBudget`, `fitsSilhouette`, `hasNativeSupport`, `drawWeapons`, `drawSupportModules`, `drawSupportModuleEffects`.
- `core/src/orbital/type/TurretSlot.java`, `SupportAnchor.java`, `SupportModuleCategory.java` (inclui `ofAbility`), `DorsalGeometry.java`.
- `core/src/orbital/type/TurretWeapon.java` — adaptação de `Turret`, campo `slotIndex`.
- `core/src/orbital/entities/units/SupportMount.java` — mount de runtime: âncora física + `Ability` funcional + ciclo `created/update/draw/removed`.
- `core/src/orbital/entities/comp/WeaponsComp.java` — regras de montagem/remoção/colisião, `transient SupportMount[]` e tick dos efeitos em `update()`.
- `core/src/orbital/entities/abilities/{RepairFieldAbility,StatusFieldAbility,ForceFieldAbility,ShieldArcAbility}.java` — habilidades de unidade reutilizadas e usadas na detecção de nativos.
- `core/src/orbital/ui/dialogs/TurretLoadoutDialog.java` — seção de UI de suporte, efeitos ativos e módulos nativos fixos.
- `core/assets/bundles/bundle.properties` e `bundle_pt_BR.properties` — chaves `support.loadout.*` e `support.category.*`.
- `tests/src/test/java/SupportLoadoutTest.java` — 17 testes de regressão físicos + funcionais.
- `core/build/generated/source/kapt/main/orbital/gen/` — 12 classes geradas com o tick `mount.update(this)`.
- `annotations/.../EntityIO.java` — motivo de `supportMounts` ser `transient`.
- `core/assets/sprites/sprites.aatls` — atlas consultado para medir regiões recortadas vs. área nominal.
- `docs/RELATORIO_TORRES_COMO_MODULOS.md` — regras anteriores de cobertura e dimensões dos cascos.

---

## 8. Decisão registrada

A regra de quantidade é **no máximo um módulo por categoria por aeronave** (reparo, sobrecarga, campo de força); categorias diferentes coexistem se o conjunto couber com a torreta e sem sobreposição.

A **fase 1** entregou apenas o encaixe físico/visual: posições manuais por casco, validação de colisão, um por categoria e UI de instalação/remoção, sem consumir orçamento ou slots de torreta. A **fase 2** tornou os módulos funcionais: reparo e sobrecarga afetam unidades aliadas em área, o campo de força protege a portadora, todos com os parâmetros reais dos blocos e sem consumo de energia/itens. Cascos com efeito equivalente nativo não recebem o módulo da categoria e mostram o efeito como fixo, não removível.

A matriz de combinações foi validada por geometria (**0 erros rígidos em 22 cascos**) e por testes de comportamento, mas **ainda não foi aprovada por inspeção visual em jogo**. Persistência em saves e sincronização em rede **não** fazem parte do escopo: a carga de suportes é `transient` e vale apenas para a sessão.

---

## 9. Implementação — encaixe físico (fase 1)

### 9.1 Modelo geométrico

`DorsalGeometry` usa unidades de mundo, com `tilesize = 8` e `TurretSlot.pxTile = 32` px, logo `4 px por unidade de mundo`.

```text
footprint(size)  = size * 8                 (unidades de mundo)
halfHull         = sqrt(spriteArea()) / 8   (silhueta equivalente, quadrada)
fitsHull         = |x| + footprint/2 <= halfHull - 0.5  (e o mesmo para y)
overlaps(a, b)   = |dx| < (fa + fb)/2 + 1  E  |dy| < (fa + fb)/2 + 1
```

`clearance = 1` unidade de mundo (4 px) e `edgeMargin = 0.5`. A silhueta é derivada de `spriteArea()`, que usa `dorsalAreaOverride` (área nominal **não recortada**). As regiões do atlas são recortadas — por exemplo, `mono` é 40×40 px no atlas, mas sua área de projeto é 48×48 px — e usá-las tornaria o encaixe mais restritivo em jogo do que nos testes headless. Por isso **não** são usadas. A silhueta é uma aproximação quadrada: cascos retangulares (ex.: `antumbra` 216×240) são tratados pelo lado equivalente de mesma área.

### 9.2 Regras em runtime

- `findSupportAnchor(block)`: rejeita blocos sem categoria, categorias já ocupadas, cascos com efeito nativo equivalente (fase 2), âncoras incompatíveis, posições fora da silhueta e colisões com torretas ou suportes já montados.
- `findTurretSlot(turret)`: espelha a mesma checagem no sentido inverso, e foi integrado a `canMount(Turret)`.
- `mountTurret` passa a gravar `x`, `y` e `slotIndex` reais do slot escolhido; `assignStaticTurretAnchors` faz o mesmo para os Duo estáticos de `mono`/`flare` e variantes demo.
- `mountSupport`, `removeSupportMount`, `removeSupport`, `hasSupportCategory` operam sobre `SupportMount[]`, sem tocar em `mounts`, `loadoutCost()`, `mountedTurrets()` ou `turretSlots`.
- `UnitType.drawSupportModules` desenha os sprites dentro de `drawWeapons`, compartilhando cor, escala e camada, com a mesma convenção de rotação das armas.

### 9.3 Distribuição de âncoras por casco

| Casco | Slots (maxSize) | Âncoras (categoria: maxSize) | Categorias |
|---|---|---|---:|
| Mono, Flare, Poly, Alpha, Beta, Evoke | 1 | — | 0 |
| Gamma, Incite | 1, 1 | — | 0 |
| Avert, Mega | 2 | — | 0 |
| MonoDuo, FlareDuo | 1 | — | 0 |
| Emanate, Horizon, Zenith, Obviate | 1–2 | repair: 1 | 1 |
| Quell | 3, 1 | repair: 2, overdrive: 2 | 2 |
| Quad | 4, 2 | repair: 2, overdrive: 3, force: 3 | 3 |
| Antumbra, Disrupt, Eclipse, Oct | 2–5 | repair: 2, overdrive: 3, force: 3 | 3 |

Onde `maxSize` é 1, apenas `mender` aparece; onde é 2, `mend-projector` e `overdrive-projector` também aparecem; `force: 3` permite `force-projector` e `overdrive: 3` permite `overdrive-dome`. A coluna "Categorias" é a capacidade **física**; a capacidade **instalável** depois da regra de nativos está na seção 1.

### 9.4 Critérios rígidos de validação

Para **todos** os 22 cascos:

1. todo slot cabe na silhueta com seu `maxSize`;
2. toda âncora cabe na silhueta com seu `maxSize`;
3. nenhuma âncora colide com outra âncora;
4. toda âncora tem **pelo menos um slot de torreta** que não colide com ela — garante que existe uma combinação "torreta + suporte".

Resultado: **0 erros em 22 cascos**. Os mesmos critérios foram transformados em testes JUnit em `SupportLoadoutTest`.

**Colisões entre slots de torreta** (7 cascos: `gamma`, `incite`, `emanate`, `obviate`, `quell`, `antumbra`, `eclipse`) **não** são tratadas como erro de configuração: nesses cascos a silhueta é pequena demais para duas torretas grandes separadas. Em runtime `findTurretSlot` bloqueia a segunda montagem, portanto os slots são **mutuamente exclusivos** e nunca produzem dois sprites sobrepostos.

### 9.5 Correção de rota (erro próprio registrado)

A primeira versão das âncoras foi validada apenas contra o **primeiro** slot de cada casco. Uma revalidação completa de todos os pares encontrou **13 de 22 cascos com problemas**: âncoras/slots fora da silhueta (o caso mais grave impedia até a torreta principal em `avert`, `mega`, `zenith`, `quell` e `antumbra`), âncoras sobrepostas entre si e âncoras que colidiam com todos os slots. A tabela foi recalibrada caso a caso, preservando as posições válidas e ajustando apenas os cascos afetados; a validação final está na seção 9.4. Também foi necessário substituir `spriteWidth()` por `sqrt(spriteArea())` em `fitsHull` para eliminar a divergência entre headless e jogo.

### 9.6 Limitações conhecidas (estado após a fase 2)

1. **Sem persistência e sem sincronização de rede.** `supportMounts` é `transient` em `WeaponsComp` e `SupportMount.ability` também é `transient`. `EntityIO` exclui campos `transient` das revisões geradas; torná-los serializáveis exigiria novo `TypeIOHandler`, nova revisão de entidade e compatibilidade com saves antigos. A carga de suportes vale apenas para a sessão/processo atual.
2. **Efeitos de gameplay — atendidos na fase 2** (seção 11). Permanecem fora de escopo, por decisão explícita: consumo de energia/itens e bônus de tecido de fase, porque unidades não têm inventário nem rede elétrica.
3. **Sem revisão visual em jogo.** As âncoras são propostas geométricas; nariz, cabine, asas, motores, recuo de cano e o desenho dos campos de efeito não foram inspecionados.
4. **Silhueta aproximada.** Quadrado de área equivalente; não usa máscara de alpha nem contorno real do sprite.
5. **Categorias por nome.** `SupportModuleCategory.of` compara `block.name`; um mod que renomeie ou prefixe esses blocos não será reconhecido. Para sobrecarga existe fallback para `StatusEffects.overclock` quando o bloco não tem `StatusEffect` dedicado.
6. **UI separada, não orçamentada.** `TurretLoadoutDialog` mantém o cabeçalho de orçamento exclusivo de torretas e lista os suportes numa seção própria, agora com aviso de **efeitos ativos** e com a lista de módulos nativos fixos.
7. **`assignStaticTurretAnchors` cobre apenas** `mono`, `flare`, `monoDuo` e `flareDuo`, que são as únicas unidades com torreta removível definida em conteúdo.
8. **Habilidades não sobrevivem a reload de conteúdo/sessão.** Como são instâncias criadas no ato da montagem, qualquer recriação de unidade exige reinstalar os módulos.

---

## 10. Verificação de build e testes — fase 1 (registro histórico)

> Os números desta seção correspondem à **fase 1**, quando `SupportLoadoutTest` tinha 10 casos e a suíte inteira 356 testes. A verificação da fase 2 está na seção 12.

### 10.1 Testes direcionados

`:tests:test --tests SupportLoadoutTest --tests TurretLoadoutSmokeTest` → **BUILD SUCCESSFUL**, 17 testes, 0 falhas. `SupportLoadoutTest` cobre 10 casos: detecção de categoria por `block.name`, validade geométrica das âncoras, limite de um por categoria, remoção, coexistência sem sobreposição, cascos capitais com três categorias, não impacto no orçamento de torretas e rejeição de blocos não suportados.

Para cobrir a última edição em `UnitType.drawWeapons`, repeti a verificação com `--rerun-tasks`, forçando `:core:kaptKotlin`, `:core:compileJava`, `:tests:compileTestJava` e `:tests:test` a executarem de fato (nenhum `UP-TO-DATE`): **BUILD SUCCESSFUL**, 17 testes, 0 falhas. A primeira tentativa dessa recompilação forçada falhou com `Failed to clean up output files for task ':core:compileJava'`, problema de atributos `ReadOnly` do Windows já observado antes nesta árvore; após limpar 63 entradas `ReadOnly` em `core/build`, `tests/build` e `annotations/build`, a repetição passou. Não foi um erro de código. `git diff --check` também está limpo.

### 10.2 Suíte completa

`:tests:test` → **356 testes, 34 falhas**, todas concentradas em `ApplicationTests` (leitura de saves 108/114/152, mapas e setores legados). Todas as demais suítes passaram:

| Suíte | Testes | Falhas |
|---|---:|---:|
| `ApplicationTests` | 135 | 34 |
| `LogicTests` | 122 | 0 |
| `PatcherTests` | 34 | 0 |
| `power.*` (3 suítes) | 39 | 0 |
| `SupportLoadoutTest` | 10 | 0 |
| `TurretLoadoutSmokeTest` | 7 | 0 |
| Demais (`DataAssetTests`, `ModTestAllure`, `TestNewFeaturesModeTest`) | 9 | 0 |

### 10.3 As 34 falhas são preexistentes — medição contra baseline

Para não atribuir ao acaso, criei um **worktree Git limpo e destacado no HEAD `cc23175dea`**, sem nenhuma das alterações desta feature, e rodei `:tests:test --tests ApplicationTests` nele:

```text
baseline (HEAD limpo):  135 testes, 34 falhas
com a feature:          135 testes, 34 falhas
nomes das falhas:       idênticos (diferença de conjuntos vazia nos dois sentidos)
```

Ou seja, **nenhuma regressão** foi introduzida: as 34 falhas já existiam antes e decorrem de saves/mapas legados incompatíveis com o leitor atual, não do encaixe de suportes.

### 10.4 Prova de que a serialização de entidades não mudou

Como `WeaponsComp` foi reescrito, comparei o `Unit.java` gerado pelo kapt no baseline e na árvore atual. O diff contém **apenas**:

- 2 imports (`orbital.world.Block`, `orbital.entities.units.SupportMount`);
- o campo `public transient SupportMount[] supportMounts = {};`;
- o getter `supportMounts()` e o setter `supportMounts(...)`.

Não há nenhuma alteração em `read()`, `write()`, `readSync()`, `writeSync()` nem em revisões de entidade — coerente com `EntityIO`, que exclui campos `transient` do conjunto serializado. Isso confirma por código, e não só por contagem de testes, que a carga de suportes não pode corromper saves antigos.

### 10.5 O que **não** foi verificado

- Nenhuma execução do jogo em GUI e nenhuma inspeção visual das âncoras (seção 9.6, item 3).
- Nenhum teste de persistência em save ou sincronização em rede — funcionalidade deliberadamente fora de escopo.
- Nenhum commit foi criado; as alterações seguem apenas na working tree.

---

## 11. Implementação — efeitos funcionais (fase 2)

### 11.1 Arquitetura

Os efeitos **não** foram injetados em `UnitType.abilities`, que é um array compartilhado por tipo e copiado em `setType()`. Cada `SupportMount` possui sua própria `transient Ability ability`, criada no construtor a partir do bloco montado. Isso mantém a carga por unidade independente e evita alterar o conteúdo estático.

O ciclo de vida é costurado em quatro pontos:

| Ponto | Arquivo | O que faz |
|---|---|---|
| Montagem | `WeaponsComp.mountSupport` | cria o `SupportMount`, adiciona ao array e chama `mount.created(unit)` — inicializa estado compartilhado como `unit.shield`. |
| Tick | `WeaponsComp.update()` | itera `supports()` e chama `mount.update(self())`, junto dos `WeaponMount`. O processador de entidades faz inline disso em **12 classes geradas** (`core/build/generated/source/kapt/main/orbital/gen/`). |
| Desenho | `UnitType.drawSupportModuleEffects(unit)` | chamado logo após o loop de habilidades nativas em `draw(Unit)`; faz `Draw.reset()` e `mount.draw(unit)` por módulo. Os sprites físicos continuam em `drawSupportModules`, dentro de `drawWeapons`. |
| Remoção | `WeaponsComp.removeSupportMount` | atualiza o array **antes** de chamar `mount.removed(self())`, para que a liberação de estado veja se ainda existe outro dono. |

`ContentLoader.createBaseContent()` chama `SupportEffects.load()` **depois** de `Blocks.load()` e antes de `Loadouts.load()` — ordem necessária porque os multiplicadores de sobrecarga são lidos dos próprios blocos.

### 11.2 Mapeamento bloco → habilidade de unidade

`SupportMount.createAbility(block)` usa a classe do bloco e os parâmetros reais:

| Bloco | Habilidade criada | Parâmetros efetivos |
|---|---|---|
| `mender` | `RepairFieldAbility(0f, 200f, 40f, 4f)` | recarga 200, alcance 40, cura 4% da vida máxima por pulso |
| `mend-projector` | `RepairFieldAbility(0f, 250f, 85f, 11f)` | recarga 250, alcance 85, cura 11% |
| `overdrive-projector` | `StatusFieldAbility(projectorBoost, 61f, 60f, 80f)` | `speedMultiplier` 1,5; duração `reload + 1`; recarga 60; alcance 80 |
| `overdrive-dome` | `StatusFieldAbility(domeBoost, 61f, 60f, 200f)` | `speedMultiplier` 2,5; recarga 60; alcance 200; `hasBoost = false` |
| `force-projector` | `ForceFieldAbility(101.7f, 1.5f, 750f, 500f, 6, 0f)` | raio 101,7; regeneração 1,5/tick; escudo 750; cooldown 750/1,5 = 500; 6 lados; rotação 0 |

Observações:

- `RepairFieldAbility` recebe `amount = 0` e usa `healPercent` como fração da vida máxima do alvo, espelhando o bloco; o alvo passa a ser `Units.nearby(unit.team, …)` em vez de `indexer.eachBlock(...)`.
- `OverdriveProjector` aplica boost em construções; em unidades o equivalente é um `StatusEffect` temporário aplicado a aliados na área, com o **mesmo** `speedBoost` do bloco.
- `ForceProjector` protege construções; em unidades o `ForceFieldAbility` mantém `unit.shield` da própria portadora e absorve projéteis. `regen` usa `cooldownNormal` do bloco (1,5) e o cooldown é derivado como o tempo de recarga completa do pool.
- **Não há** `consumePower`, `consumeItem`, `phaseBoost`, `phaseRangeBoost`, `speedBoostPhase` nem `phaseShieldBoost`: unidades não possuem energia, inventário ou rede de fluidos. Isso foi decisão explícita de Péterson.

### 11.3 Status effects ocultos de sobrecarga

`SupportEffects` cria `projectorBoost` e `domeBoost` com `speedMultiplier` lido de `Blocks.overdriveProjector.speedBoost` (1,5) e `Blocks.overdriveDome.speedBoost` (2,5). São efeitos internos:

```text
show = false; hideDatabase = true; generateIcons = false;
effect = Fx.none; effectChance = 0f; permanent = false;
```

São acrescentados **ao fim** da lista de conteúdo, portanto os IDs dos status existentes e a compatibilidade de saves/sync não mudam. Como mods podem alterar `speedBoost` via JSON depois da criação do conteúdo base, `SupportMount` **reatribui** `status.speedMultiplier = drive.speedBoost` no momento da instalação. Se o bloco não tiver efeito dedicado (bloco de mod renomeado), há fallback para `StatusEffects.overclock`.

### 11.4 Conflito com habilidades nativas

`SupportModuleCategory.ofAbility(Ability)` reconhece:

| Habilidade nativa | Categoria |
|---|---|
| `RepairFieldAbility` | `repair` |
| `StatusFieldAbility` com `StatusEffects.overdrive` ou `overclock` | `overdrive` |
| `ForceFieldAbility` ou `ShieldArcAbility` | `force` |

`ShieldRegenFieldAbility`, `SuppressionFieldAbility`, `EnergyFieldAbility` e afins **não** são equivalentes e não bloqueiam nada. Entre os 22 cascos desta análise, apenas dois têm categoria nativa:

| Casco | Habilidade nativa | Categoria fixa | Instalável |
|---|---|---|---|
| `poly` | `RepairFieldAbility` | reparo | nenhuma extra (não tem âncoras) |
| `oct` | `ForceFieldAbility(140f, 4f, 7000f, …)` + `RepairFieldAbility(130f, …)` | campo de força + reparo | apenas sobrecarga |

`quell`/`disrupt` têm `SuppressionFieldAbility` e por isso **continuam aceitando** sobrecarga. `UnitType.hasNativeSupport(category)` consulta `abilities`, e `WeaponsComp.findSupportAnchor` retorna `-1` para a categoria em conflito. Isso também impede que um escudo montado de 750 substitua o escudo nativo de 7000 do `oct`, já que `unit.shield` é estado compartilhado.

Na UI (`TurretLoadoutDialog`), cada categoria nativa aparece numa seção "Módulos nativos" com ícone representativo (`mender`, `overdriveProjector`, `forceProjector`), o rótulo da categoria, o texto "Fixo, não removível" e um cadeado. A seção de suportes instaláveis continua filtrando por `canMountSupport`, então essas categorias simplesmente não aparecem como opção.

### 11.5 Liberação segura de estado compartilhado

`SupportMount.removed(unit)` só zera `unit.shield` quando o módulo removido é de campo de força **e**:

1. o casco não tem `hasNativeSupport(force)`; e
2. nenhum outro `SupportMount` restante possui `ForceFieldAbility`.

A chamada ocorre depois da atualização do array, justamente para que a segunda condição enxergue o estado final. Sem isso, remover um módulo poderia apagar o escudo de uma habilidade nativa.

### 11.6 UI e bundles

Chaves atualizadas em `bundle.properties` e `bundle_pt_BR.properties`: `support.loadout.active` substituiu o antigo aviso "apenas visual"; `support.loadout.size` passou de `visual` para `active`/`ativo`; foram adicionadas `support.loadout.native`, `support.loadout.fixed` e `support.loadout.native.locked`. A seção de suportes também passou a listar os módulos nativos fixos. UTF-8 e CRLF dos dois bundles foram preservados.

### 11.7 Persistência

Continua **somente na sessão**, conforme escolha de Péterson. `supportMounts` e `ability` são `transient`; o `Unit.java` gerado não ganhou nenhum `read()`/`write()` novo nem nova revisão de entidade (prova na seção 10.4, válida também para a fase 2 porque nenhum campo serializável foi adicionado). Não há sincronização em rede: em multiplayer, a carga instalada por um jogador não é replicada.

### 11.8 Limitações da fase 2

1. **Sem balanceamento em jogo.** Os valores são os dos blocos de construção, aplicados a unidades sem nenhum ajuste de custo, duração ou escala de dano. Pode ser forte ou fraco demais em partida real.
2. **Sem consumo/recurso.** Não existe custo de instalação, energia, munição ou aquecimento; apenas o limite físico de âncoras.
3. **Sem feedback visual validado.** `drawSupportModuleEffects` delega às habilidades, mas nenhuma tela foi aberta para conferir partículas, raios ou escudos sobre o casco.
4. **Sem teste de confronto.** Não foi verificado em teste se o escudo montado absorve projéteis nem se o reparo/sobrecarga funcionam sob dano contínuo; os testes cobrem cura de aliado, aplicação de status e criação/remoção do pool de escudo.
5. **Sem compatibilidade com mods que renomeiam blocos.** A detecção é por `block.name`, com fallback apenas para o status de sobrecarga.
6. **Sem persistência/sync** (seção 11.7) e **sem commit**.

---

## 12. Verificação da fase funcional

### 12.1 Compilação e testes direcionados

`:core:compileJava` → **BUILD SUCCESSFUL**.

`:tests:test --tests SupportLoadoutTest --tests TurretLoadoutSmokeTest --rerun` → **BUILD SUCCESSFUL**, com `:core:compileJava` e `:tests:test` **executados de fato** (nem `UP-TO-DATE`, nem `FROM-CACHE`):

| Suíte | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| `SupportLoadoutTest` | 17 | 0 | 0 | 0 |
| `TurretLoadoutSmokeTest` | 7 | 0 | 0 | 0 |
| **Total** | **24** | **0** | **0** | **0** |

`SupportLoadoutTest` passou de 10 para 17 casos. Além dos 10 testes físicos da fase 1 (com `capitalHullsCarryAllThreeCategories` ajustado para os 4 cascos sem conflito nativo), foram adicionados 7 funcionais:

1. `nativeAbilitiesAreFixedAndBlockEquivalentModules` — `poly` bloqueia reparo; `oct` bloqueia reparo e força, mas ainda aceita sobrecarga;
2. `supportAbilitiesUseTheRealBlockParameters` — conferência dos parâmetros de `RepairFieldAbility`, `StatusFieldAbility` e `ForceFieldAbility` contra os blocos;
3. `projectorAndDomeUseTheirOwnBoostStrength` — 1,5x vs 2,5x nos status ocultos;
4. `forceModuleCreatesAndReleasesItsShield` — `created()` preenche `unit.shield` e `removed()` libera;
5. `supportUpdateAndDrawDelegateToTheAbility` — delegação de tick e desenho;
6. `repairModuleHealsADamagedAlly` — cura real de aliado danificado via `Units.nearby`;
7. `overdriveModuleAppliesItsBoostToNearbyAllies` — status aplicado a aliado próximo.

**Erro de ambiente registrado (não é erro de código):** a primeira execução direcionada voltou `BUILD SUCCESSFUL` com `:tests:test FROM-CACHE`, portanto não era evidência nova. Ao forçar execução real, três tentativas falharam por atributos `ReadOnly` do Windows:

```text
1) Unable to delete directory '...\annotations\build\classes\java\main'
2) Failed to clean up output files for task ':core:compileJava'
3) AccessDeniedException: ...\core\build\classes\java\main\orbital\maps\planet
```

Limpei 97 entradas `ReadOnly`, depois mais 9 e por fim os **3 diretórios** remanescentes (`entities\comp`, `io\versions`, `maps\planet`). Só após essa última limpeza a compilação e os testes rodaram de fato. O stacktrace confirma que a falha era de permissão de filesystem, não de compilação ou lógica.

### 12.2 Suíte completa

`:tests:test` → **363 testes, 34 falhas**, `BUILD FAILED`. Em relação à fase 1 (356 testes), o acréscimo de **+7** são exatamente os novos testes funcionais.

| Suíte | Testes | Falhas |
|---|---:|---:|
| `ApplicationTests` | 135 | 34 |
| `DataAssetTests` | 4 | 0 |
| `LogicTests` | 122 | 0 |
| `ModTestAllure` | 1 | 0 |
| `PatcherTests` | 34 | 0 |
| `SupportLoadoutTest` | 17 | 0 |
| `TestNewFeaturesModeTest` | 4 | 0 |
| `TurretLoadoutSmokeTest` | 7 | 0 |
| `power.ConsumeGeneratorTests` | 19 | 0 |
| `power.DirectConsumerTests` | 3 | 0 |
| `power.PowerTests$PowerGraphTests` | 17 | 0 |
| **Total** | **363** | **34** |

As 34 falhas são todas de `ApplicationTests`: saves `load108Save` / `load114Save` / `load152Save` / `load152BESave`, mapas (`atolls`, `crateredBattleground`, `cruxscape`, `littoralShipyard`, `nuclearComplex`, `taintedWoods`, `testingGrounds`) e setores `serpulo-*`.

### 12.3 Comparação com baseline limpo (refeita nesta fase)

Como a fase 2 mexeu em `ContentLoader`, `WeaponsComp`, `UnitType` e no conteúdo de status, **não** reaproveitei a medição da fase 1: criei novamente um worktree destacado e limpo no HEAD `cc23175dea` e rodei `:tests:test --tests ApplicationTests`:

```text
baseline limpo:  135 testes, 34 falhas
com a fase 2:    363 testes no total, 34 falhas (todas em ApplicationTests)
diferença baseline -> feature: 0 nomes
diferença feature -> baseline: 0 nomes
conjuntos idênticos: sim
```

**Nenhuma regressão** foi introduzida pela fase funcional. As 34 falhas são preexistentes e vêm de saves/mapas legados incompatíveis com o leitor atual.

### 12.4 Código gerado e serialização (revalidados)

- Diff de `core/build/generated/source/kapt/main/orbital/gen/Unit.java` entre baseline e árvore atual: **apenas** 2 imports (`Block`, `SupportMount`), o campo `transient supportMounts`, o getter e o setter. Nenhuma linha de `read()`, `write()`, `readSync()`, `writeSync()` ou revisão foi alterada — coerente com `EntityIO`, que exclui campos `transient`.
- **12 classes geradas** contêm `if (mount != null) mount.update(this);`, confirmando que o tick dos suportes foi incorporado às unidades geradas pelo processador de entidades.

### 12.5 Integridade dos arquivos

- `bundle.properties`: UTF-8 OK, 3712 CRLF, 0 LF isolado.
- `bundle_pt_BR.properties`: UTF-8 OK, 3707 CRLF, 0 LF isolado.
- `git diff --check`: limpo (apenas avisos de `core.autocrlf` sobre LF → CRLF, esperados nesta árvore).

### 12.6 O que **não** foi verificado

- Nenhuma execução do jogo em GUI; âncoras, sprites e efeitos visuais **não** foram inspecionados.
- Nenhum teste de balanceamento, absorção real de projéteis pelo escudo montado, dano contínuo ou interação com outras habilidades nativas.
- Nenhum teste de save, persistência ou sincronização em rede — fora de escopo por decisão explícita.
- Nenhum commit criado; todas as alterações seguem apenas na working tree.

---

## 13. Implementação — escala visual por casco (fase 3)

> **Registro histórico.** Esta seção e a seção 14 descrevem a fase 3, que usava uma tabela manual
> de escalas por casco (`UnitType.supportVisualScale`) e 6º argumento em `TurretLoadouts.setup()`.
> A **fase 4 (seções 15 e 16)** substituiu essa tabela pela regra derivada das medidas. O
> diagnóstico e as medições abaixo continuam válidos e são a base da regra nova.

### 13.1 Diagnóstico

As capturas enviadas por Péterson mostravam os módulos desenhados em **tamanho nativo de bloco**: um `force-projector` 3×3 tem sprite de **96 px** e, sobre um `quad` de **220 px**, ocupava **44% da largura do casco**. Com três âncoras (±15 unidades de mundo), três sprites desse porte cobriam quase toda a silhueta e se sobrepunham visualmente à torreta e entre si — ainda que a geometria de colocação estivesse correta.

Medições reais do atlas (`sprites.aatls`, dimensões não-cortadas) confirmam que `dorsalAreaOverride` é exatamente a área do sprite do casco:

| Bloco | sprite | Casco | sprite | Área usada |
|---|---:|---|---:|---:|
| `mender` | 32×32 | `horizon` | 72×72 | 5184 |
| `mend-projector` | 64×64 | `quell` | 162×162 | 26244 |
| `overdrive-projector` | 64×64 | `quad` | 220×220 | 48400 |
| `overdrive-dome` | 96×96 | `disrupt` | 243×243 | 59049 |
| `force-projector` | 96×96 | `eclipse` | 320×321 | 102720 |
|  |  | `oct` | 400×400 | 160000 |

Ou seja, o problema **não** era de compatibilidade geométrica: era puramente de escala de desenho. `Draw.rect(region, x, y, rotation)` usa o tamanho nativo da região (`region.width * region.scl() * Draw.xscl`), e não havia fator de escala.

### 13.2 O que foi alterado

| Arquivo | Mudança |
|---|---|
| `core/src/orbital/type/UnitType.java` | Novo campo `supportVisualScale` (padrão `1f`) e `drawSupportModules()` passa a desenhar com largura/altura explícitas (`Draw.rect(region, x, y, w, h, rotation)`), multiplicando o tamanho nativo por `mount.visualScale`. O cálculo mantém `region.scl()`, `Draw.xscl` e `Draw.yscl`, preservando o espelhamento atual. |
| `core/src/orbital/type/SupportAnchor.java` | Novo campo `visualScale` (`-1` = herdar do casco) e método fluente `scaled(float)`. `accepts()` continua idêntico. |
| `core/src/orbital/type/DorsalGeometry.java` | Novo `supportVisualScale(UnitType, SupportAnchor, regionPx)` e as constantes `supportMinWidthFraction = 0.10`, `supportMaxWidthFraction = 0.28`. Toda a geometria existente (`footprint`, `fitsHull`, `overlaps`) permanece intacta. |
| `core/src/orbital/content/TurretLoadouts.java` | Overload de `setup()` com 6º argumento = escala visual do casco. Configurados apenas os **10 cascos com âncoras**; os 12 restantes ficam no padrão `1f` (irrelevante, pois não têm módulos). `maxSize` e offsets **não** foram tocados. |
| `core/src/orbital/entities/units/SupportMount.java` | Novo campo `final float visualScale`, construtor sobrecarregado e helpers `regionPx()`, `visualWidth()`, `visualWidthFraction()`. `footprint()` continua retornando `block.size * tilesize`. |
| `core/src/orbital/entities/comp/WeaponsComp.java` | `mountSupport()` resolve a escala uma vez, no momento da instalação, e a entrega ao `SupportMount`. `findSupportAnchor()` e `findTurretSlot()` **não** consultam a escala. |

`regionPx()` tem fallback determinístico (`block.size * TurretSlot.pxTile`) para execuções headless, onde o atlas é `blankAtlas()`. Para os cinco blocos esse fallback coincide exatamente com o sprite real.

### 13.3 Fórmula da escala efetiva

```text
hullPx  = sqrt(type.spriteArea())              // largura desenhada do casco, em px
base    = anchor.visualScale > 0 ? anchor.visualScale : type.supportVisualScale
min     = min(1, hullPx * 0.10 / regionPx)    // piso: não deixa o módulo desaparecer
max     = min(1, hullPx * 0.28 / regionPx)    // teto: não deixa o módulo engolir o casco
escala  = max(min, min(max, base))            // sempre em (0, 1]
```

Como `0.10 < 0.28` e ambas as expressões são monotônicas, `min <= max` é garantido. A escala **nunca aumenta** o sprite. O piso é útil em cascos grandes com blocos 1×1: em `quad`, um `mender` sobe de `0.46` para `0.6875` e fica com 22 px (10% do casco) em vez de 14,7 px, mantendo-se reconhecível.

### 13.4 Tabela de escalas configuradas e resultado efetivo

Valores para o **maior bloco aceito por cada âncora** (o pior caso visual):

| Casco | sprite (px) | escala base | maior bloco | nativo px | escala efetiva | desenhado px | % do casco |
|---|---:|---:|---|---:|---:|---:|---:|
| `emanate` | 83 | 0.70 | `mender` | 32 | 0.700 | 22.4 | 27.0% |
| `horizon` | 72 | 0.65 | `mender` | 32 | 0.630 | 20.2 | 28.0% |
| `zenith` | 112 | 0.80 | `mender` | 32 | 0.800 | 25.6 | 22.9% |
| `obviate` | 135 | 0.85 | `mender` | 32 | 0.850 | 27.2 | 20.2% |
| `quell` | 162 | 0.60 | `mend-projector` / `overdrive-projector` | 64 | 0.600 | 38.4 | 23.7% |
| `quad` | 220 | 0.46 | `mend-projector` | 64 | 0.460 | 29.4 | 13.4% |
|  |  |  | `overdrive-dome` / `force-projector` | 96 | 0.460 | 44.2 | 20.1% |
| `antumbra` | 228 | 0.48 | `mend-projector` | 64 | 0.480 | 30.7 | 13.5% |
|  |  |  | `overdrive-dome` / `force-projector` | 96 | 0.480 | 46.1 | 20.2% |
| `disrupt` | 243 | 0.50 | `mend-projector` | 64 | 0.500 | 32.0 | 13.2% |
|  |  |  | `overdrive-dome` / `force-projector` | 96 | 0.500 | 48.0 | 19.8% |
| `eclipse` | 320 | 0.60 | `mend-projector` | 64 | 0.600 | 38.4 | 12.0% |
|  |  |  | `overdrive-dome` / `force-projector` | 96 | 0.600 | 57.6 | 18.0% |
| `oct` | 400 | 0.68 | `mend-projector` | 64 | 0.680 | 43.5 | 10.9% |
|  |  |  | `overdrive-dome` / `force-projector` | 96 | 0.680 | 65.3 | 16.3% |

Cascos menores mantêm módulos relativamente maiores para continuarem legíveis; os cascos com três âncoras recebem a redução mais forte. Em `quad`, os dois módulos 3×3 caem de **96 px (44% do casco)** para **44,2 px (20%)**.

### 13.5 O que **não** mudou

- `SupportAnchor.maxSize`, `accepts()`, offsets e quantidade de âncoras por casco.
- `DorsalGeometry.footprint()`, `fitsHull()`, `overlaps()`, `clearance`, `edgeMargin`.
- `SupportMount.footprint()`, `x`, `y`, `category`, `anchorIndex`.
- `findSupportAnchor()`, `findTurretSlot()`, `canMount()`, `canMount(Turret)`, orçamento dorsal e `mountedTurrets()`.
- `createAbility()` e todos os parâmetros de gameplay: `range`, `reload`, `healPercent`, `speedBoost`, `radius`, `shieldHealth`, `sides`.
- Desenho dos **efeitos** (anéis de escudo/campo): esses continuam em tamanho real, pois o pedido foi sobre os módulos.
- Interface do diálogo de loadout, bundles e serialização (`supportMounts` continua `transient`).

A escala é resolvida **uma vez**, na instalação, e armazenada no mount transitório. Nenhum campo serializado foi adicionado.

---

## 14. Verificação da fase 3

### 14.1 Compilação

- `:core:compileJava` → `BUILD SUCCESSFUL`.
- Ocorreu novamente o erro de filesystem do Windows (`Failed to clean up output files for task ':core:compileJava'`); limpei **71 atributos `ReadOnly`** nos diretórios de build e a recompilação passou. Não era erro de código.
- `:desktop:dist` → `BUILD SUCCESSFUL`.

### 14.2 Testes direcionados

`SupportLoadoutTest` + `TurretLoadoutSmokeTest` + `OrbitalThemeTest`:

| Suíte | Testes | Falhas |
|---|---:|---:|
| `SupportLoadoutTest` | **22** | 0 |
| `TurretLoadoutSmokeTest` | 7 | 0 |
| `OrbitalThemeTest` | 4 | 0 |
| **Total** | **33** | **0** |

Os **5 testes novos** desta fase:

1. `visualScalePreservesEveryHullCompatibility` — snapshot congelado dos 22 cascos, comparado com a lista real de categorias instaláveis (inclui o conflito nativo de `oct`). **Nenhum casco ganhou ou perdeu categoria.**
2. `visualScaleIsConfiguredPerHull` — confirma a tabela de escalas por casco (`quad` 0.46, `oct` 0.68, `emanate` 0.70), ≥ 6 valores distintos e o padrão `1f` em cascos sem âncora.
3. `supportSpritesShrinkButKeepFullFootprintAndAnchors` — para toda combinação casco/âncora/bloco aceita: `x`/`y` iguais ao offset, `footprint()` igual a `block.size * tilesize`, escala em `(0, 1]` e sprite dentro do teto de 28% do casco.
4. `perAnchorOverrideChangesOnlyTheDrawingScale` — um override por âncora reduz ainda mais o sprite sem tocar em `maxSize`, `accepts()` ou offsets.
5. `visualScaleDoesNotChangeGameplayEffects` — `RepairFieldAbility.range`/`healPercent` continuam iguais aos do bloco mesmo com sprite reduzido.

> **Erro próprio registrado:** na primeira execução, 1 teste falhou porque escrevi os rótulos do snapshot como `monoDuo`/`flareDuo`, enquanto os nomes reais de conteúdo são `mono-duo`/`flare-duo`. Corrigi os rótulos e a suíte passou; não houve falha de lógica de produção.

### 14.3 Suíte completa e baseline

`:tests:test` → **372 testes, 34 falhas**, `BUILD FAILED`. Em relação à fase anterior (367 testes), o acréscimo de **+5** são exatamente os novos testes desta fase.

Comparação normalizada com o baseline limpo no HEAD `cc23175dea` (`build/failures-baseline.txt`):

```text
baseline limpo:  34 falhas (todas em ApplicationTests)
com a fase 3:    34 falhas (todas em ApplicationTests)
novos nomes:     0
nomes ausentes:  0
conjuntos idênticos após normalizar "testSectorValidity() > ": sim
```

Saves legados (`load108Save`, `load114Save`, `load152Save`, `load152BESave`), mapas e setores `serpulo-*` continuam falhando exatamente como no baseline. **Nenhuma regressão.**

### 14.4 Executável

Atualizei **somente** `deploy/windows64/Orbital/app/Orbital.jar`, sem apagar nada do app-image (o guard de exclusão em massa continua ativo e não foi contornado):

| Item | SHA-256 |
|---|---|
| `desktop/build/libs/Orbital.jar` | `bdafdaa6781a9ebb06ee8183da7fca4e312f5d6f00f8414a4a1d9a0b1cc485f6` |
| `deploy/windows64/Orbital/app/Orbital.jar` | `bdafdaa6781a9ebb06ee8183da7fca4e312f5d6f00f8414a4a1d9a0b1cc485f6` |
| `deploy/windows64/Orbital/Orbital.exe` | `f2f080735e0c0d9a895c3ec93f7bcc651f50677beae09f1e4b8b21b92d45ce28` (inalterado) |

Validações: `unzip -t` sem erros; `javap` confirma no JAR os campos `SupportMount.visualScale`, `UnitType.supportVisualScale` e as constantes/método novos de `DorsalGeometry`; as texturas do tema (`ui/orbital-*.png`) e `OrbitalTheme.class` seguem presentes; `runtime/` continua com **350 arquivos** e o `.exe`, `Orbital.cfg` e `.jpackage.xml` não foram mexidos. Como o ícone não mudou, não foi necessário regerar o `.exe`.

### 14.5 O que **não** foi verificado nesta fase

- Não abri a GUI nem capturei tela do resultado final; a conferência visual depende de Péterson reabrir o `.exe`.
- Não mexi em efeitos, balanceamento, persistência, sincronização de rede nem criei commit.
- Os valores de escala são uma escolha de legibilidade, não de balanceamento de gameplay; podem ser ajustados individualmente em `TurretLoadouts.setup()` sem tocar em compatibilidade. **Na fase 4 essa tabela manual deixou de existir.**

---

## 15. Implementação — proporção unificada para torretas e suporte (fase 4)

### 15.1 Diagnóstico da nova captura

A captura revisada por Péterson (`Captura de tela 2026-11-05 193907.png`) mostrou que:

- os módulos de **suporte** já estavam pequenos (losangos verdes), resultado correto da fase 3;
- as **torretas dorsais** continuavam em tamanho nativo de bloco, porque `Weapon.draw()` chamava `Draw.rect(region, wx, wy, rotation)` sem fator de escala — o mesmo problema que a fase 3 resolveu só para o suporte;
- a proporção variava de forma incoerente entre cascos, pois a tabela da fase 3 só cobria os **10 cascos com âncoras** e era calibrada à mão.

O quadrado cinza com anéis laranja isolado no gramado é um **bloco colocado no mundo** (não um módulo montado): ele aparece em posições diferentes nas três capturas conforme a câmera se move. Não foi alterado.

### 15.2 Medição de todos os cascos e módulos

Medições de `core/assets/sprites/sprites.aatls` via `TextureAtlasData` (regiões não-cortadas / `dorsalAreaOverride`):

- **Cascos:** de **48 px** (`mono`, `flare`, `alpha`, `mono-duo`, `flare-duo`) a **400 px** (`oct`), com 22 cascos/variantes medidos.
- **Suporte:** `mender` 32×32, `mend-projector` 64×64, `overdrive-projector` 64×64, `overdrive-dome` 96×96, `force-projector` 96×96.
- **Torretas:** `duo` 26×28 (cortado de 32×32), `scatter` 54×58 (de 64×64), `fuse`/`tsunami` 98×98, `foreshadow` 128×130, `spectre`/`meltdown` 130×130; uma torre 5×5 tem ~160 px.
- Em `mono`, uma `duo` nativa cobria **58%** da largura do casco; em `quad`, um `force-projector` nativo cobria **44%**.

Tabela completa. `posições` = `turretSlots.size + supportAnchors.size`. As colunas de escala são **avaliações da regra**, não uma lista de compatibilidade (um `mono`, por exemplo, não aceita módulo 3×3 nem torre 5×5):

| Casco | área px² | hullPx | posições | alvo 1×1 | duo 28px | sup. 3×3 96px | torre 5×5 160px |
|---|---:|---:|---:|---:|---:|---:|---:|
| `mono` | 2304 | 48 | 1 | 28.0% | 0.480 | 0.140 | 0.084 |
| `flare` | 2304 | 48 | 1 | 28.0% | 0.480 | 0.140 | 0.084 |
| `poly` | 3192 | 56 | 1 | 27.7% | 0.559 | 0.165 | 0.099 |
| `alpha` | 2304 | 48 | 1 | 28.0% | 0.480 | 0.140 | 0.084 |
| `beta` | 3024 | 55 | 1 | 27.8% | 0.545 | 0.160 | 0.096 |
| `gamma` | 4624 | 68 | 2 | 26.1% | 0.634 | 0.198 | 0.119 |
| `evoke` | 3024 | 55 | 1 | 27.8% | 0.545 | 0.160 | 0.096 |
| `incite` | 4752 | 69 | 2 | 26.1% | 0.642 | 0.201 | 0.121 |
| `emanate` | 6888 | 83 | 3 | 24.4% | 0.723 | 0.242 | 0.145 |
| `horizon` | 5184 | 72 | 2 | 26.0% | 0.668 | 0.210 | 0.126 |
| `avert` | 9216 | 96 | 1 | 26.4% | 0.904 | 0.280 | 0.168 |
| `mega` | 10000 | 100 | 1 | 26.2% | 0.937 | 0.292 | 0.175 |
| `zenith` | 12544 | 112 | 3 | 23.5% | 0.940 | 0.317 | 0.196 |
| `obviate` | 18200 | 135 | 3 | 22.8% | 1.000 | 0.371 | 0.236 |
| `quad` | 48400 | 220 | 5 | 18.2% | 1.000 | 0.491 | 0.340 |
| `quell` | 26244 | 162 | 4 | 20.9% | 1.000 | 0.410 | 0.281 |
| `antumbra` | 51840 | 228 | 6 | 17.0% | 1.000 | 0.476 | 0.329 |
| `disrupt` | 59049 | 243 | 5 | 17.5% | 1.000 | 0.526 | 0.366 |
| `eclipse` | 102720 | 320 | 6 | 14.5% | 1.000 | 0.588 | 0.415 |
| `oct` | 160000 | 400 | 8 | 11.0% | 1.000 | 0.571 | 0.411 |
| `mono-duo` | 2304 | 48 | 1 | 28.0% | 0.480 | 0.140 | 0.084 |
| `flare-duo` | 2304 | 48 | 1 | 28.0% | 0.480 | 0.140 | 0.084 |

`1.000` significa que o sprite nativo **já é menor** que o alvo e não precisa encolher (ex.: `duo` de 28 px em `oct` de 400 px = 7%).

### 15.3 Fórmula unificada

```text
hullPx     = sqrt(type.spriteArea())                       // medido, 48..400 px
posicoes   = type.turretSlots.size + type.supportAnchors.size
t          = clamp((hullPx - 48) / (400 - 48), 0, 1)
base       = 0.28 + (0.16 - 0.28) * t                     // casco pequeno → alvo maior
tier       = 0.02 * (blockSize - 1)                       // blocos maiores podem ler maiores
crowd      = max(0.65, 1 - 0.045 * (posicoes - 1))        // casco cheio → módulos menores
alvo       = clamp((base + tier) * crowd, 0.10, 0.28)     // faixa da fase 3 preservada
escala     = clamp(alvo * hullPx / modulePx * tuning,
                   min(1, 0.10 * hullPx / modulePx), 1)
```

`modulePx` é o maior lado da região **realmente desenhada** (regiões cortadas do atlas incluídas), com fallback determinístico `blockSize * 32` em headless. `tuning` é `UnitType.dorsalVisualTuning` (padrão `1`). Um override por âncora (`SupportAnchor.scaled`) substitui o valor derivado, ainda dentro da faixa.

A mesma função (`DorsalVisual.scale`) é usada por `DorsalGeometry.turretVisualScale()` e `DorsalGeometry.supportVisualScale()`. Diferença operacional apenas: a torreta resolve a escala **no desenho**, a cada frame, e o suporte resolve **na instalação**, guardando-a no `SupportMount` transitório.

### 15.4 O que foi alterado

| Arquivo | Mudança |
|---|---|
| `core/src/orbital/type/DorsalVisual.java` | **Novo.** Constantes medidas (48/400 px), `hullPx`, `positions`, `targetFraction`, `scale`, `supportScale`, `turretScale`. Regra única e pura, sem dependência de `Core.atlas`. |
| `core/src/orbital/type/DorsalGeometry.java` | `supportVisualScale()` passa a delegar a `DorsalVisual`; novo overload com `blockSize`, `turretVisualScale()` e `sizeFromRegionPx()`. `footprint`, `fitsHull`, `overlaps`, `clearance` e `edgeMargin` **intactos**. |
| `core/src/orbital/type/UnitType.java` | Campo `supportVisualScale` (tabela manual) substituído por `dorsalVisualTuning = 1f`, multiplicador opcional por casco. `drawSupportModules()` continua desenhando com largura/altura explícitas. |
| `core/src/orbital/type/Weapon.java` | Novo hook `visualScale(Unit)` (padrão `1f`) e helper `drawRegion(...)`. `draw()` e `drawOutline()` aplicam a escala a `region`, `cellRegion`, `heatRegion`, `outlineRegion`, sombra e às `parts` (via `Draw.xscl`/`Draw.yscl` ao redor dos loops). Confirmado por `javap` que `Draw.rect(region, x, y, w, h, rot)` **não** reaplica `xscl`, por isso `region.scl() * Draw.xscl * escala` é reproduzido explicitamente. |
| `core/src/orbital/type/TurretWeapon.java` | Override de `visualScale(Unit)` → `DorsalGeometry.turretVisualScale(unit.type, turretSize, regionPx())`, mais `regionPx()`. Nenhum campo de gameplay alterado. |
| `core/src/orbital/content/TurretLoadouts.java` | Removidos o 6º argumento de `setup()` e os **10 valores manuais**. `maxSize`, offsets, orçamento e âncoras permanecem idênticos. |
| `core/src/orbital/entities/comp/WeaponsComp.java` | `mountSupport()` passa `block.size` ao resolver a escala. `mountTurret()` agora ignora `weapon.load()` quando `Core.atlas == null` (headless), evitando NPE; em jogo o comportamento é o mesmo. |
| `core/src/orbital/type/SupportAnchor.java` | Javadoc atualizado: `visualScale` é override absoluto opcional, e não mais herança de uma escala por casco. |
| `tests/src/test/java/SupportLoadoutTest.java` | Fase 3: 22 → **27 testes**. Cinco testes novos/substituídos cobrindo medidas, faixa de proporção, regra compartilhada, escala de torretas vs. armas nativas, mount em runtime e ajuste por casco. |

### 15.5 O que **não** mudou

- Compatibilidade dos 22 cascos (snapshot congelado da fase 2 continua passando).
- `TurretSlot.maxSize`, `accepts()`, offsets, `dorsalBudget()`, `loadoutCost()`, `cost()`, `hasSlotFor()`, `fitsSilhouette()`.
- `DorsalGeometry.footprint()`, `fitsHull()`, `overlaps()` e todas as verificações de colocação.
- `SupportMount.footprint()`, `x`, `y`, `category`, `anchorIndex` e todos os parâmetros de habilidade.
- Efeitos desenhados (anéis de escudo/campo), UI, bundles, `supportMounts` transitório e IO de entidades.
- **Armas nativas dos cascos** continuam em tamanho original: só `TurretWeapon` sobrescreve `visualScale()`.

## 16. Verificação da fase 4

### 16.1 Medição (evidência, não estimativa)

`build/tools/AtlasDump.java` leu `core/assets/sprites/sprites.aatls` via `TextureAtlasData` do Arc e mediu as regiões **reais**:

- 22 cascos: de 48 px (`mono`, `flare`, `alpha`, `mono-duo`, `flare-duo`) a 400 px (`oct`); `sqrt(spriteArea())` confere com `dorsalAreaOverride`.
- Suporte: `mender` 32², `mend-projector`/`overdrive-projector` 64², `overdrive-dome`/`force-projector` 96².
- Torretas: `duo` 26×28 (recortado de 32²), `scatter` 54×58 (de 64²), `fuse`/`tsunami` 98², `foreshadow` 128×130, `spectre`/`meltdown` ~130².
- `javap` confirmou que `Draw.rect(region, x, y, w, h, rot)` **não** reaplica `Draw.xscl`/`Draw.yscl`; por isso `Weapon.drawRegion()` reproduz `region.width * region.scl() * Draw.xscl * escala` explicitamente.

A captura enviada também mostrou um quadrado cinza/laranja com portas circulares **destacado do casco**, em posições de tela diferentes entre capturas conforme a câmera se move: é um **bloco colocado no mundo**, não um módulo montado. Blocos do mundo estão fora do escopo desta fase e não foram alterados.

### 16.2 Testes

| Conjunto | Resultado |
|---|---|
| `OrbitalThemeTest` | 4 testes, 0 falhas |
| `SupportLoadoutTest` | 27 testes, 0 falhas (22 da fase 3 + 5 de proporção) |
| `TurretLoadoutSmokeTest` | 7 testes, 0 falhas |
| **Direcionados (total)** | **38 testes, 0 falhas** |
| **Suíte completa `:tests:test`** | **377 testes, 34 falhas** |

Os 5 testes novos cobrem: extremos medidos dos cascos, curva derivada de escala, faixa de proporção em **todos** os cascos e módulos, regra compartilhada torreta/suporte, armas nativas sem escala, mount de torreta em runtime preservando slot/custo/footprint e `dorsalVisualTuning` alterando **só** o desenho.

### 16.3 Comparação com o baseline (com erro próprio registrado)

O baseline limpo em worktree destacado do HEAD `cc23175dea` tem **135 testes / 34 falhas**. A comparação da fase 4 errou duas vezes antes de ficar correta:

1. Primeiro script usou `cur = {}` (dicionário) e chamou `.add()` → `AttributeError`. Corrigido para `set()`.
2. Segundo script normalizava apenas o prefixo `testSectorValidity() > ` **no início da linha inteira**, mas no baseline ele vem depois de `ApplicationTests :: `. Isso produziu listas falsas de "novos" e "ausentes" com nomes equivalentes em formatos diferentes.

Correção adotada: **canonizar ambos os lados** para o último token após `::` e após ` > ` (ex.: `ApplicationTests :: testSectorValidity() > atolls` → `atolls`, e o XML do JUnit que traz só `atolls` → `atolls`). Resultado real:

```text
baseline: 34   atuais: 34
NOVOS (regressao): []
AUSENTES (baseline nao reproduzido): []
IGUAIS: True
```

Lista normalizada salva em `build/failures-phase4.txt`. Conclusão: as 34 falhas são exatamente as preexistentes de `ApplicationTests` (saves legados e validade de setores); **nenhuma regressão introduzida pela fase 4**.

### 16.4 Build e executável

```text
./gradlew :desktop:dist --console=plain   → BUILD SUCCESSFUL in 48s (EXIT=0)
```

Antes do build removi preventivamente **67 atributos `ReadOnly`** do Windows nos diretórios `*/build` (o erro recorrente `Failed to clean up stale outputs` é de ambiente, não de código).

| Item | Valor verificado |
|---|---|
| `desktop/build/libs/Orbital.jar` | SHA-256 `03996bc7f5db6868bf0d825532712fa7a8d7c1d68da01059e6006c7809dca957`, 96.976.997 bytes |
| `deploy/windows64/Orbital/app/Orbital.jar` | **mesmo SHA-256** após cópia |
| `deploy/windows64/Orbital/Orbital.exe` | inalterado `f2f080735e0c0d9a895c3ec93f7bcc651f50677beae09f1e4b8b21b92d45ce28` (ícone temático preservado) |
| `app/Orbital.cfg` | inalterado `ffe64b65…e09` |
| `runtime/` | 350 arquivos, intacto |
| `unzip -t` (build e deploy) | `No errors detected` |
| `git diff --check` | limpo |

Conferi também que `DorsalVisual.class`, `Weapon.class`, `TurretWeapon.class` e `UnitType.class` **dentro do JAR** têm o mesmo SHA-256 das classes compiladas, e que as 10+ classes geradas pelo kapt (`orbital/gen/*Unit.class`) contêm a chamada `DorsalGeometry.supportVisualScale(...)` — ou seja, o JAR distribuído é realmente o da fase 4, não um artefato antigo da fase 3 (`bdafdaa6…5f6`).

### 16.5 Pendências e limites

- **Revisão visual em jogo não foi feita por mim**: não abri GUI. Péterson precisa reabrir `D:\Projetos\Orbital\deploy\windows64\Orbital\Orbital.exe` para conferir a proporção.
- Loadouts de suporte continuam **somente na sessão** (sem save/sync), por decisão de escopo da fase 2.
- Balanceamento de gameplay, absorção de projéteis pelo escudo e autoria/licença da key-art fornecida seguem em aberto.
- Nenhum commit foi criado. `build.gradle`, `settings.gradle`, `android/` e `settings.ini` são alterações alheias a este trabalho e foram deixadas intactas.

## 17. Implementação — ícones e alternância no HUD (fase 5)

### 17.1 Diagnóstico

A barra dorsal inferior já existia: `HudFragment.build()` cria `turret-loadout` em `bottom().left()`, com `turretCell()` para cada `WeaponMount` de torreta, um `+` para abrir o diálogo e um martelo. O `TurretLoadoutDialog` listava os módulos de suporte instalados, mas **o HUD não os mostrava** e não havia como ligar/desligar um efeito sem desinstalar o módulo. O texto `support.loadout.active` ("efeitos ativos") também era estático e ficaria mentindo depois de um desligamento.

### 17.2 O que foi alterado

| Arquivo | Mudança |
|---|---|
| `core/src/orbital/entities/units/SupportMount.java` | Novo `public boolean active = true` e `setActive(Unit, boolean)`. `created()`, `update()` e `draw()` passam a exigir `active`. `removed()` agora só considera **outros módulos de força ativos** ao decidir se pode zerar `unit.shield` (estado compartilhado). `setActive` é idempotente: desligar chama `removed()`, religar chama `created()` — que em `ForceFieldAbility` reinstala `unit.shield = scaledMax(unit)`. |
| `core/src/orbital/ui/fragments/HudFragment.java` | `selectedHasTurretBar()` também aceita cascos com suportes instalados; `turretSignature()` inclui a identidade de cada `SupportMount` para reconstruir a barra ao instalar/remover (alternar **não** precisa reconstruir). `rebuildTurretBar()` insere `supportCell()` logo após as torretas. Novos `supportStateKey(boolean)` (público, testável sem GL), `supportTooltip()` e `supportCell()`. |
| `core/src/orbital/ui/fragments/HudFragment.java` — `supportCell()` | Mesma métrica de `turretCell()`: `BorderImage` do `block.uiIcon`, `Stack` com um **selo `Label` no topo** (supplier de texto, `support.hud.active`/`support.hud.inactive`), barra de estado `Bar` e `cell.clicked(() -> mount.setActive(unit, !mount.active))`. `cell.update()` mantém cor da borda (`Pal.accent`/`Pal.gray`), espessura, cor do ícone (`Color.white`/`Color.gray`), fundo e cor do selo (`Pal.heal`/`Pal.remove`). |
| `core/src/orbital/ui/dialogs/TurretLoadoutDialog.java` | Linha de efeitos passa a ser um `label` supplier: mostra `support.loadout.inactive` quando há módulos instalados e **todos** estão desligados. Cada linha instalada ganha um selo de estado vivo (`Ativo`/`Inativo`), atualizado mesmo com o diálogo aberto. |
| `core/assets/bundles/bundle.properties` / `bundle_pt_BR.properties` | 4 chaves novas em cada idioma: `support.hud.active`, `support.hud.inactive`, `support.hud.toggle`, `support.loadout.inactive`. UTF-8 e CRLF preservados (3717/3712 linhas CRLF, 0 LF solto). |
| `tests/src/test/java/SupportLoadoutTest.java` | 27 → **33 testes**. Seis novos: chave do selo, toggle mantendo instalação/categoria, `update`/`draw` bloqueados, cura bloqueada, status de sobrecarga bloqueado e liberação/restore do escudo (incluindo remoção de um módulo já desligado). |

### 17.3 O que **não** mudou

- Compatibilidade dos 22 cascos, âncoras, `maxSize`, offsets, `dorsalBudget()`, `loadoutCost()`, `mountedTurrets()` e `DorsalVisual`.
- Footprints, alcances, recarga, percentual de cura, multiplicador de velocidade, vida/raio/lados do escudo e parâmetros de habilidade.
- Proporção visual das fases 3/4 — o sprite do módulo **no mundo continua desenhado normalmente** quando desligado; o estado é indicado pelo HUD.
- `supportMounts` continua `transient`: `active` vive no mesmo objeto, portanto **nenhuma revisão de IO de entidades**, nenhum save e nenhum sync foram adicionados.
- Remoção de módulos continua exclusiva do diálogo; o HUD só alterna.
- Efeitos nativos fixos (`poly`, `oct`) continuam não instaláveis e não aparecem como alternáveis.

### 17.4 Verificação

*Preenchido ao final da fase, após a suíte completa e o build.*

---

## 18. Implementação — posições sobre a silhueta real (fase 6)

### 18.1 Diagnóstico

As capturas de Péterson mostraram torretas e módulos **soltas na borda** do casco. A causa
não era escala (fase 4) nem aceitação: era a **régua de posição**.

`fitsHull` aprova uma posição quando ela cabe no **quadrado de área equivalente** do sprite
(`sqrt(spriteArea())`). Para o `oct` esse quadrado tem 50 unidades de mundo de lado — mas a
arte do `oct` é um losango alongado: nos cantos do quadrado **não há pixel pintado**. Uma
âncora como `(34, -34)` passa em `fitsHull` e, em jogo, flutua sobre o fundo.

Medindo o alpha de cada sprite em `core/assets-raw/sprites/units/`, a folga real é bem menor
que a régua: no `oct`, um módulo 3×3 desenhado (~13,7 unidades de mundo de lado) só pousa
inteiro na arte dentro de um raio de cerca de `±20` unidades, não `±50`.

### 18.2 O que foi alterado

| Arquivo | Mudança |
|---|---|
| `core/src/orbital/type/DorsalGeometry.java` | Novo `visualFootprint(type, blockSize, regionPx)`: o lado, em unidades de mundo, que o módulo **ocupa quando desenhado** (`turretVisualScale * regionPx / pixelsPerWorldUnit`). |
| `core/src/orbital/entities/comp/WeaponsComp.java` | `overlapsTurretMounts` e `overlapsSupportMounts` passam a receber o **tamanho desenhado**; `findTurretSlot` e `findSupportAnchor` calculam esse tamanho para o candidato. `fitsHull` e o orçamento dorsal **continuam** com a pegada do bloco. |
| `core/src/orbital/content/TurretLoadouts.java` | Offsets recalibrados casca a casca. Cada posição **mantém a direção** relativa ao centro do casco e entra até o sprite tocar a arte — o leiaute angular original (frente/trás/lados) foi preservado. |
| `tests/src/test/java/SupportLoadoutTest.java` | `supportAnchorsNeverOverlapEachOther` e `everySupportAnchorHasACompatibleTurretSlot` passam a usar o tamanho desenhado; novo caso `noTwoDorsalModulesOverlapOnceDrawn` cobre os 22 cascos. |

### 18.3 Regra efetiva

```text
# silhueta e orçamento (inalterados, desde a fase 1)
fitsHull(x, y, size)   = |x| + footprint(size)/2 <= halfHull - 0.5   (idem para y)

# separação entre módulos (fase 6)
overlaps(a, b)         = |dx| < (drawn(a) + drawn(b))/2 + 1  E  |dy| < (drawn(a) + drawn(b))/2 + 1
drawn(size)            = size * 32 * DorsalVisual.scale(casco, size) / pixelsPerWorldUnit
```

Duas consequências diretas: (1) dois módulos pequenos podem agora dividir uma região que a
pegada do bloco considerava ocupada — é o que permite ao `oct` ter 5 slots dentro da arte; e
(2) a régua de silhueta não foi afrouxada, então **nenhum casco ganhou categoria**.

### 18.4 Posições resultantes

| Casco | Slots (maxSize) | Âncoras (categoria: maxSize) |
|---|---|---|
| Emanate | 1, 1 | repair: 1 |
| Horizon | 1 | repair: 1 |
| Avert, Mega | 2 | — |
| Zenith | 2, 1 | repair: 1 |
| Obviate | 2, 2 | repair: 1 |
| Quell | 3, 1 | repair: 2, overdrive: 2 |
| Quad | 4, 2 | repair: 2, overdrive: 3, force: 3 |
| Antumbra | 4, 2, 2 | repair: 2, overdrive: 3, force: 3 |
| Disrupt | 4, 2 | repair: 2, overdrive: 3, force: 3 |
| Eclipse | 5, 5, 2 | repair: 2, overdrive: 3, force: 3 |
| Oct | 5, 4, 4, 2, 2 | repair: 2, overdrive: 3, force: 3 |

Os valores numéricos estão em `TurretLoadouts.load()`; a contagem de slots/âncoras e os
`maxSize` são **idênticos** às fases 1–5 — só os offsets mudaram.

### 18.5 O que **não** mudou

- `DorsalGeometry.footprint()`, `fitsHull()`, `clearance`, `edgeMargin` e o orçamento dorsal.
- `TurretSlot.maxSize` / `SupportAnchor.maxSize`, `accepts()` e a compatibilidade dos 22 cascos.
- Parâmetros de gameplay: alcance, recarga, cura, boost, raio/lados/vida do escudo.
- Escala visual (`DorsalVisual`), HUD da fase 5, bundles, diálogo e serialização.

### 18.6 Limitações

1. **Sem inspeção visual em jogo nesta fase.** As posições foram medidas contra o alpha do
   sprite e validadas por testes; o julgamento final (nariz, cabine, motores, recuo de cano)
   continua dependendo de você abrir o jogo.
2. **Silhueta ainda aproximada.** `fitsHull` segue usando o quadrado de área equivalente; a
   arte é consultada fora do jogo (medição de alpha), não em runtime. A régulá de aceitação
   permanece conservadora de propósito.
3. **`obviate` e `avert`/`mega` ficaram com módulos pequenos e próximos ao centro**, porque a
   arte desses cascos é estreita: não há espaço na borda para um módulo do tamanho aceito.
4. **Somente na sessão:** sem save e sem sincronização, como nas fases anteriores.
