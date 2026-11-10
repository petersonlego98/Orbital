# RELATORIO_REFATORACAO_CODIGO_MORTO.md

**Data:** 2026-11-10 · **Escopo:** código próprio do Orbital (pós-fork `7e42c5c182bd`)
**Método:** varredura por referência cruzada (grep em `core/src`, `desktop/src`, `server/src`,
`tools/src`, `tests/src`, `annotations/src`) sobre os 38 arquivos novos do core, 16 arquivos
do backend Vulkan e 43 arquivos modificados. Achados de maior impacto verificados
manualmente antes da publicação. **Nenhum arquivo foi editado** — este relatório é
proposta, não changelog.

O código herdado do Mindustry (pacote renomeado `mindustry → orbital`) **não** foi
candidato a poda: é upstream em uso, e mexer nele confunde diff próprio com diff herdado.

---

## 1. Possíveis bugs (correções)

| # | Onde | Problema | Correção |
|---|------|----------|----------|
| **B1** | `TurretLoadoutDialog.java:582` + `:1117` | Tooltip de módulos de arma exibe **px⁴**: `module.cost` já é área em px² (`WeaponModules.Definition.cost`, `WeaponModules.java:188-193`) e `tooltip()` aplica `cost * cost` de novo. Torretas/suportes passam o lado linear, por isso o erro só aparece na aba de módulos de arma. | Passar `Mathf.sqrt(module.cost)` na linha 582, ou padronizar tudo em área e remover o quadrado do tooltip. |
| **B2** | `TurretLoadoutDialog.java:834-837` | Javadoc de `budget(int)` diz "in px²" mas retorna o **lado** (`size * TurretSlot.pxTile`). Só "funciona" porque o tooltip eleva ao quadrado. | Corrigir junto com B1 (renomear para `budgetSide()` ou unificar em área). |
| **B3** | `RepairAI.java:73-87` | `this.target` só é atribuído quando `damagedTarget != null` e **nunca volta a `null`**. Quando o prédio é reparado/destruído, a unidade continua `aim(target)` e atirando com alvo órfão; unidades sem weapons param de mirar por completo. | `else { this.target = null; }` ao fim de `updateTargeting()`. |
| **B4** | `MendProjector.java:91,145` · `OverdriveProjector.java:143,159` | Destaque de blocos usa `range + phaseRangeBoost` (alcance máximo) mesmo com `phaseHeat == 0`, enquanto o círculo sólido desenha o alcance real — o jogador vê blocos selecionados que não serão afetados. | Ou voltar `realRange` no `eachBlock`, ou assumir como intencional (alcance potencial) — decidir. |
| **B5** | `ForceProjector.java:309-310` | Círculo "potencial máximo" desenha `radius + phaseRadiusBoost` **sem** `radscl`; com `radscl > 1` o círculo de máximo fica menor que o ativo. Cosmético. | Multiplicar por `radscl`. |

Diffs examinados e limpos (sem problemas): `AIController`, `MenuFragment`, `LoadingFragment`,
`AboutDialog`, `SettingsMenuDialog`, `Links`, `FileTree`, `DataAssetType`, `MobileButton`,
`PlanetDialog`, `ModsDialog`, `ExpeditionClock`, `TestNewFeaturesMode`, `SupportMount`,
`HudFragment`, `Weapon`, `UnitType`, `Control`, `Gamemode`/`Rules`, `ContentLoader`, `UI`,
`ClientLauncher`, `RendererPreference`, `Drawf`, `LoadRenderer`, `Planets`, `UnitTypes`,
`QuatiaPlanetGenerator`.

---

## 2. Código morto — core (arquivos próprios)

### 2.1 Campos sem nenhuma referência (remover)
1. `DorsalGeometry.supportMinWidthFraction` — `DorsalGeometry.java:17` (verificado: única ocorrência é a declaração).
2. `OrbitalTheme.gold` — `OrbitalTheme.java:40` (escrito, nunca lido).
3. `SupportMount.anchorIndex` — `SupportMount.java:28` (escrito no construtor, nunca lido).
4. `SupportAnchor.name` — `SupportAnchor.java:17` (consumidores usam `anchor.category.name()`).
5. `TurretSlot.name` — `TurretSlot.java:17` (só o construtor morto de 4 args escreve).

### 2.2 Construtores mortos (remover)
1. `SupportMount(Block, SupportModuleCategory, int, float, float)` (5 args) — `SupportMount.java:44-46`; a única construção externa usa 6 args (`WeaponsComp.java:158`).
2. `TurretSlot(int)` — `TurretSlot.java:19-21`.
3. `TurretSlot(String, int, float, float)` — `TurretSlot.java:29-32` (remover junto com `TurretSlot.name`).

### 2.3 Métodos públicos só internos (→ `private`)
`PlanetNames.generate(String)` (`:34`) · `WeaponModules.tierOf(UnitType)` (`:131`) ·
`ExpeditionClock.remainingMillis()` (`:67`) · `ExpeditionClock.collectCargo(boolean)` (`:128`) ·
`GlobalInventory.add(ItemSeq)` (`:33`) · `GlobalInventory.save()` (`:41`) ·
`DorsalGeometry.sizeFromRegionPx(float)` (`:96`) · `DorsalVisual.positions(UnitType)` (`:53`) ·
`SupportMount.regionPx()` (`:64`) · `TurretLoadoutDialog.tinted(Drawable, Color)` (`:386`).

### 2.4 API só de teste (produção nunca chama — decidir intenção)
- `RendererPreference.get()/set()/vulkanRequested()` — só testes; produção lê/escreve a setting
  direto pela chave (`VulkanPreferences.java:79`, `OptionsSetting` em `SettingsMenuDialog.java:579`).
- `TurretWeapon.cost()` — só testes; produção usa o campo `dorsalCost`.
- `SupportMount.visualWidthFraction(UnitType)`, `SupportAnchor.scaled(float)`,
  `DorsalGeometry.supportMaxWidthFraction` — só `SupportLoadoutTest.java`.

### 2.5 Imports não usados (7)
`TurretLoadouts.java:5` · `QuatiaPlanetGenerator.java:3,7` · `EnemyBuildingDialog.java:4,14` ·
`TurretLoadoutDialog.java:17` · `OrbitalTheme.java:9`.

### 2.6 Resíduo
Javadoc órfão em `TurretLoadoutDialog.java:696-699` (descreve `heading()`, que está em `:712`;
ficou colado antes do javadoc de `lockedLine()`).

---

## 3. Código morto — backend Vulkan

**Achado estrutural:** `VulkanBackend.rendererWired()` retorna constante `false`
(`VulkanBackend.java:45-47`), logo `select()` nunca devolve `Type.VULKAN` e a preferência
"vulkan" em Settings **nunca ativa o backend** — só gera os warnings. `VulkanApplication`
só roda via flag `-vulkan-app`, ignorando a preferência persistida. Os dois caminhos de
seleção são inconsistentes entre si.

### 3.1 Métodos sem chamador (remover)
- `VulkanDevice.presentAndReadBack` (`:444`) + binding `nativePresentAndReadBack` (`:699`)
- `VulkanDevice.drawIndexed` (`:357`) — verificado: única ocorrência é a declaração
- `VulkanDevice.nativeCreatePipeline` (`:681`) — nunca chamada; `pipeline()` usa `pipelineWithLayout` (remover junto com o lado C++)
- `VulkanGL20.getPipelineCache` (`:303`)
- `VulkanApplication.presentFrame()` (`:252`) — vazio
- `VulkanDevice.pipelineWithLayout` (`:216`) — só interno → `private` (e extrair a validação duplicada com `pipelineState`, `:592-615`)

### 3.2 Campos mortos
- `VulkanGraphics.clearR/clearG/clearB` (`VulkanGraphics.java:31`) — o comentário "kept in sync" é falso.
- `VulkanDevice.surfaceWidth/surfaceHeight/imageIndex` (`:51-52`) — escritos, nunca lidos.
- `VulkanDevice.vSync` (`:49`) — lido por `hasVSync()` mas **nunca atribuído** → `hasVSync()` retorna sempre `false` (bug de log, não só campo morto).
- `VulkanGL20.pixelRowLength/clearStencil` (`:69,59`), `BufferData.usage` (`:224`), `TextureData.mipmapped` (`:240`), `FramebufferData.depthRenderbuffer` (`:250`).
- **Caminho quebrado associado:** `FramebufferData.depth/stencil` jamais viram `true` — `ensureNativeFramebuffer` (`:2347-2378`) sempre cria com `depth=false, stencil=false`.

### 3.3 Sistema morto em produção
`VulkanPipelineCache` + `VulkanPipelineState`: em `VulkanGL20.draw` (`:1593,1602`) o
`getOrCreate(snapshot, () -> 1L)` é chamado com fábrica constante e o retorno descartado —
o cache não influencia a renderização (a criação real ocorre em `VulkanDevice.pipelineState`,
`:1549`). Só é usado de verdade por `VulkanGlPortTest.java:160-179`. Candidato a remoção
(~80 linhas) ou a efetivação como cache real.

> **Executado em 2026-11-10 (F4, decisão por análise):** o cache foi **removido**. A decisão
> se baseia no lado nativo: `nativeCreatePipelineWithState` (`VulkanDevice.cpp:2274`) chama
> `createPipeline` → `vkCreateGraphicsPipelines` a cada mudança de estado e destrói o pipeline
> anterior (`invalidatePipeline()`, `VulkanDevice.cpp:2170-2174`) — não existe API de "bind por
> handle", logo o cache Java, que guarda `Long` handles, não pode ser efetivado sem redesenhar
> o JNI/C++. Removidos: `VulkanPipelineCache.java` (classe inteira), o campo e `getPipelineCache()`
> em `VulkanGL20`, as duas chamadas no-op em `draw()` e o teste `pipelineCacheReusesIdenticalState`
> em `VulkanGlPortTest`. `VulkanPipelineState` foi **mantido** — é a chave de comparação que
> evita recriação a cada draw (`activePipeline.equals(snapshot)`), e não está morto.
> Verificação: `:desktop:compileJava` + `:tests:compileTestJava` OK; suíte `orbital.desktop.vulkan.*`
> 63 testes, 0 falhas, 7 skipped (testes de janela/dispositivo, exigem hardware).
> A preferência "vulkan" em Settings foi mantida como está — é um feature flag deliberado e o
> texto do bundle já é honesto ("Experimental ... OpenGL continua ativo").

### 3.5 Wiring do Vulkan — ligado e **revertido** em 2026-11-10

**Resultado do teste em máquina real:** o Péterson executou o build com o wiring ativo em uma
máquina Vulkan-capable e o port renderizou **saída quebrada (retângulos coloridos)** em jogo
real. O port nunca foi validado em renderização real — os self-tests offscreen passavam, mas
não cobrem o caminho completo de gameplay.

**Ação: reversão no mesmo dia.**
1. `VulkanBackend.rendererWired()` de volta a `false`, com javadoc registrando o motivo e a data.
2. `DesktopLauncher`: ramo `backend == VULKAN` voltou ao aviso de fallback, com comentário
   explicando a reversão e a proibição de religar antes de corrigir o defeito.
3. `VulkanBackendSelectionTest`: testes de volta às expectativas "sempre OpenGL" (11 testes, 0 falhas).
4. App-image e instalador regenerados com o código revertido; smoke test do empacotado OK
   (menu em 17,6 s via OpenGL). Jars idênticos nas três cópias (sha256 `3b668ee1…`).

**A permanência do wiring ligado é vetada até:** o defeito de renderização ser diagnosticado e
corrigido **em uma máquina com GPU Vulkan-capable** — este ambiente não tem driver Vulkan
(Intel HD 4600/Haswell), então nenhum ajuste de renderização pode ser verificado aqui.
O port continua alcançável apenas via flag experimental `-vulkan-app`.

### 3.4 Resíduos de debug
- `SpirvCompiler.java:94-95` — imprime o relatório de falha (com o **fonte completo do shader**) tanto em `System.out` quanto em `System.err`. Remover o `System.out`, usar `Log.err` e truncar o dump.
- `VulkanGL20.java:120,1569-1574` — `Log.info` de draw a cada 500 frames no caminho normal de render.
- `VulkanApplication.java:177-189` — heartbeat de 5 s roda sempre no modo `-vulkan-app`.
- `DesktopLauncher.java:76-79` — `VulkanChecks.windowCheck = ...` duplicado duas vezes.
- Imports não usados: `VulkanApplication.java` (5), `VulkanGraphics.java` (4), `VulkanWindow.java` (3), `VulkanPreferences.java` (2).

---

## 4. Duplicações / redução de código

| # | Onde | Oportunidade | Economia |
|---|------|--------------|----------|
| **D1** | `UnitType.java:676-692` vs `WeaponsComp.java:48-64` | Mesma soma/contagem de custo dorsal (`loadoutCost`/`mountedTurrets`) sobre `Seq<Weapon>` e `WeaponMount[]`. Extrair métodos estáticos. | ~20 linhas |
| **D2** | `Drawf.java:406-432` | `rangeCircle` e `rangeCircleMax` idênticos, só o stroke difere (0.9 vs 0.7). Fundir com parâmetro. | ~12 linhas |
| **D3** | 6 arquivos (incl. `EnemyBuildingDialog.java:66`) | Normalização de busca `trim().replaceAll(" +"," ").toLowerCase()` repetida. Extrair `normalizeSearch(String)`. | ~6 linhas + consistência |
| **D4** | `WeaponsComp.java:96-112` | `ammoCompatible` copia array para `Seq` só para `contains`; `ammoDefined` reitera mounts. | ~10 linhas |
| **D5** | `TurretLoadoutDialog.java:497-636` | Catálogo triplicado (`rebuildTurretGrid`/`rebuildWeaponGrid`/`rebuildSupportGrid` — mesmo esqueleto). Generalizar. | ~70 linhas |
| **D6** | `TurretLoadoutDialog.java:427-478,639-674,151-167` | Linha "instalado" repetida 3×. Extrair `installedRow(...)`. | ~45 linhas |

**Extração do `TurretLoadoutDialog` (1160 linhas):** trilho de slots (~300 linhas) →
`DorsalSlotRail`; drags `CardDrag`/`SlotDrag` (~125) → arquivos próprios; card do catálogo
(~140) → `ModuleCardRenderer`. Com D5+D6 o arquivo cai para ~500-600 linhas sem mudança
de comportamento.

**i18n (observação):** strings hardcoded em pt-BR fora do bundle (`PlanetDialog.java:1371`,
`ExpeditionDialog`, toasts de `ExpeditionClock`, `GlobalInventory.java:74`) — inconsistente
com o padrão `@chave` do resto do código.

---

## 5. Estado das mudanças não commitadas

`git status` mostrava, antes deste relatório: `Blocks.java`, `Block.java`,
`TurretLoadoutDialog.java`, `TurretLoadoutDialogTest.java` e `Orbital.jar`. O conjunto é
coeso (campo `moduleOnly` + marcação de 30 blocos + filtro de catálogo + teste) e completo.
**Dois pontos a confirmar:**
1. `TurretLoadoutDialogTest.java` **duplica** a expressão do filtro em vez de chamar o predicado do diálogo — se a regra mudar de novo, os dois divergem em silêncio.
2. `TestNewFeaturesMode.enemyBlocks()` exclui blocos `BuildVisibility.hidden`, então o seletor de estruturas inimigas deixa de oferecer as 28 torretas marcadas — se o modo de teste deveria posicioná-las, precisará da mesma exceção `moduleOnly`.

---

## 6. Proposta de execução (aguarda confirmação)

| Fase | Conteúdo | Risco |
|------|----------|-------|
| **F1 — correções** | B1+B2 (tooltip/budget), B3 (RepairAI), B5 (ForceProjector). B4 só após decisão sua. | Baixo |
| **F2 — poda morta** | Campos/construtores/métodos das seções 2 e 3, imports, resíduos de debug. | Baixo |
| **F3 — duplicações** | D1-D6 + extrações do `TurretLoadoutDialog`. | Médio (tocar em UI) |
| **F4 — Vulkan (decisão)** | Remover `VulkanPipelineCache` ou efetivá-lo; definir o papel da preferência "vulkan" enquanto `rendererWired()` é `false`. | Médio |

Toda fase termina com `bash build_msvc.sh`-equivalente **não se aplica aqui** — build
Gradle (`gradlew`) + testes, com apagar o binário antes de recompilar e compilação/execução
tratadas como códigos de saída separados.
