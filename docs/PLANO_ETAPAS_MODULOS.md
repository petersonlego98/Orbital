# Plano A — Economia do sistema de módulos, em etapas

**Fonte adaptada:** `estado-fork-baseline-sistemas-existentes.md` (2026-11-09).
**Escopo:** fechar as pendências abertas do sistema de módulos documentado no baseline — sem criar sistema paralelo.
**Estado verificado no código nesta sessão (fatos, com evidência):**

- Catálogo de módulos de arma existe e deriva do conteúdo em runtime: `core/src/orbital/content/WeaponModules.java` (catálogo, correção de armas espelhadas, tier T1–T5 derivado de `unitTiers`).
- Slots dorsais existem com orçamento de área: `core/src/orbital/type/TurretSlot.java` (`maxSize`, offsets, `pxTile = 32`).
- Módulos de suporte existem como runtime-only: `core/src/orbital/entities/units/SupportMount.java` (efeito recreado por frame, nunca serializado; toggle pelo HUD), com categorias (`SupportModuleCategory`), âncoras (`SupportAnchor`) e efeitos (`SupportEffects.java`).
- Loadout inicial das 20 aéreas configurado: `core/src/orbital/content/TurretLoadouts.java` (slots + suportes por casco, com separação por footprint desenhado em `DorsalGeometry`).
- Desbloqueio de conteúdo é **local**: `core/src/orbital/ctype/UnlockableContent.java` grava `name-unlocked` em `Core.settings`. Não há persistência de loadout em servidor.
- UI: `core/src/orbital/ui/dialogs/TurretLoadoutDialog.java` ordena módulos por tier.

> **Conclusão que o baseline pedia confirmar:** tiers T1–T5 **já existem no código** como derivação das linhas de pesquisa (Serpulo/Erekir e níveis N0–N4 das aéreas, deslocados para T1–T5). O que **não existe** é economia sobre esses tiers (custos, limites por tier, compatibilidade) nem persistência de loadout além do save local da partida.

Rótulos: **[FATO]** = já verificado no código; **[PROPOSTA]** = decisão de design a confirmar antes de implementar; **[PENDÊNCIA]** = aberta no baseline.

---

## Etapa A1 — Auditoria documental do sistema atual

**Objetivo:** fechar as lacunas 1, 7, 8 e 9 do baseline (seção 5) com relatório assinado por evidência.

1. **[PENDÊNCIA→FATO]** Listar cada torreta e cada arma convertida com ID interno e nome técnico, marcando quais continuam disponíveis como bloco de base (lacuna 7) — grep no `Blocks.java` + verificação in-game.
2. **[PENDÊNCIA→FATO]** Tabela comparativa módulo-vindo-de-torreta × módulo-vindo-de-arma: alcance, cadência, munição, comportamento (lacuna 8) — medir in-game ou extrair dos parâmetros de `Weapon`/`Turret`.
3. **[PENDÊNCIA→FATO]** Situação dos módulos de suporte no solo: os projetores ainda existem como blocos de base ou só como módulos (lacuna 9).
4. **[PENDÊNCIA→FATO]** Fórmulas de dano/efetividade atuais por tier (lacuna 3 parcial) e custos de fabricação/desbloqueio atuais (lacuna 4).

**Aceite:** novo relatório em `docs/` (padrão dos RELATORIO_*.md) com cada linha citando origem (arquivo/classe ou captura). Nenhuma afirmação sem medição.

## Etapa A2 — Formato de persistência de loadout

**Objetivo:** definir **[FATO]** como o loadout é salvo hoje e **[PROPOSTA]** o formato que sobreviverá à passagem local→servidor.

1. Mapear a serialização atual: onde o loadout da aeronave entra no save (`.msav`/settings) — o `SupportMount` documenta-se como não serializado, então o caminho precisa ser rastreado no `io/`.
2. **[PROPOSTA]** Definir um formato versionado de loadout (lista de módulos por slot + âncoras + tier), independente de `.msav`, para poder ser gravado depois em BD (depende do schema da etapa C2).
3. Migração: loadouts antigos legíveis pelo novo formato ou descartados com aviso.

**Aceite:** doc de formato com exemplos; teste de round-trip (salvar → carregar → igual) rodando na suíte.

## Etapa A3 — Economia de custo e desbloqueio dos módulos

**Objetivo:** resolver a pendência principal do baseline — **[PROPOSTA DE DESIGN]** rotulada até confirmação.

1. Custo de fabricação por módulo (itens existentes em `Items.java`; nada de moeda nova nesta etapa).
2. Custo de pesquisa por tier na árvore existente (`TechTree.java`), reusando o mecanismo de requisitos por nó.
3. Curva de custo por tier: **[PROPOSTA]** progressão ~quadrática T1→T5, revisada contra a medição da etapa A5 antes de travar.

**Aceite:** tabelas de custo implementadas em código de conteúdo (não hardcoded espalhado), com teste que valida que todo módulo tem custo e todo custo é alcançável pela cadeia produtiva existente.

## Etapa A4 — Regras de compatibilidade e limites

**Objetivo:** lacunas 2 e 5 do baseline.

1. **[FATO]** O orçamento dorsal (área px² em `TurretSlot`/`dorsalCost`) e a restrição `maxSize` por slot já limitam fisicamente. **[PROPOSTA]** Adicionar gate por tier de casco (ex.: módulo T4 não monta em casco T1) via tabela, não por exceção pontual.
2. **[PROPOSTA]** Limites por categoria de suporte já existem (uma por categoria em `TurretLoadouts.java`); manter e documentar.
3. UI: o `TurretLoadoutDialog` deve mostrar por que um módulo não é compatível (custo insuficiente / tier bloqueado / sem espaço).

**Aceite:** cada regra de bloqueio tem mensagem visível na UI e teste unitário da tabela de compatibilidade.

## Etapa A5 — Curva de balanceamento com medição

**Objetivo:** lacuna 3 completa — dano/alcance/consumo por tier medidos, não estimados.

1. Suíte de teste de DPS/alcance por módulo em mapa de teste (o projeto já tem cultura de suíte; reusar infraestrutura de `tests/`).
2. Comparar curva medida contra a curva proposta em A3; ajustar.
3. Relatório de balanceamento com gráfico de DPS por tier antes/depois.

**Aceite:** números medidos na suíte, com carimbo de data, seguindo o padrão de `reports/`.

## Etapa A6 — Persistência de módulos no servidor

**Dependência:** etapas C1–C2 concluídas (nada a fazer antes disso).

1. Gravar loadout (formato da A2) na conta do jogador no BD.
2. Sincronizar entre dispositivos (depende de C8/B16).
3. **[PROPOSTA]** Limites de slots por tipo de aeronave resolvidos como dado da conta, não do save local.

**Aceite:** loadout idêntico em dois dispositivos com a mesma conta; migração local→conta documentada.

---

## Ressalvas explícitas

- A3–A4 são **proposta de design** até o Péterson confirmar números; nada disso entra como "implementado" nos relatórios.
- Esta etapa do plano **não** cria economia entre jogadores (troca de módulos) — isso é escopo de C4/B2.
- Os nomes legados dos blocos só entram nos relatórios conferidos contra wiki/docs de mod (regra do baseline).
