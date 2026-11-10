# Plano C — Infraestrutura de servidor persistente, em etapas

**Fonte adaptada:** `pesquisa-motor-mindustry.md` (2026-11-09) — lacunas B1–B17 da seção 3, reordenadas por dependência técnica.
**Estado verificado no código nesta sessão (fatos):**

- Fork renomeado para `orbital.*` em todo o `core/src/`; multiplayer é o cliente-servidor herdado (`core/src/orbital/net/` com `Net.java`, `ArcNetProvider`, `Administration`, `ValidateException`).
- Servidor dedicado headless existe: `server/src/orbital/server/ServerControl.java` + `ServerLauncher.java`.
- Identidade é **UUID local**: `core/src/orbital/core/Platform.java` grava `uuid` em `Core.settings`. Nenhuma conta/sessão no pacote `net`.
- Persistência é **arquivo** (`.msav`/settings), não BD: desbloqueios em `Core.settings` (`UnlockableContent.java`), servidor só tem autosave.
- `core/src/orbital/service/` contém apenas conquistas locais (`Achievement`, `GameService`, `SStat`) — não é camada de serviço online.
- Não há classes de trade/economia/carteira no `core/src/orbital/` — a lacuna B4 confirmada pela pesquisa vale para o fork.

> Conclusão: as lacunas B1–B17 da pesquisa se aplicam integralmente a este fork. A ordem abaixo segue dependência, não a numeração original.

Rótulos: **[FATO]** = verificado; **[PROPOSTA]** = decisão de arquitetura a confirmar; risco por etapa citado da seção 5 da pesquisa.

---

## Etapa C0 — Decisões de base (antes de qualquer código)

1. **[PROPOSTA]** Arquitetura de duas camadas (seção 4.3 da pesquisa): **simulação** por setor/shard (reusa `Rules`, modos, ondas) + **estado compartilhado** global (contas, carteiras, alianças, território) em serviço central fora da simulação. Shards postam eventos e pedem validação ao serviço central; I/O novo roda fora da thread principal via `Core.app.post` (motor não é thread-safe fora dela — fato da pesquisa F21).
2. **[PROPOSTA]** Pilha do BD (SQL embarcado primeiro, ex.: SQLite/H2, com camada de repositório para trocar depois) e formato de snapshot de setor.
3. Política de fork/contribuição e conformidade GPLv3 para distribuição futura (pesquisa 4.1) — registrar antes da primeira distribuição pública.
4. **[FATO]** Não usar `.msav` como persistência multi-usuário; `.msch` vira formato de blueprint transportável (decisão 4.4 da pesquisa).

**Aceite:** documento de arquitetura com diagrama das duas camadas e decisão de BD registrada.

## Etapa C1 — Contas e autenticação (B1) — risco alto

1. Registro/login com credencial (email ou login próprio — **[PROPOSTA]**, a decidir), sessões com token, vínculo de dispositivos.
2. **[FATO]** O UUID local não é identidade final; vira chave de migração inicial (primeiro login importa o progresso local uma vez, com aviso).
3. Interface de login no cliente desktop e Android (UI touch já existe como base, A16 da pesquisa).

**Aceite:** login em dois dispositivos com a mesma conta; token expira e renova; credenciais nunca em texto puro.

## Etapa C2 — Banco de dados servidor-autoritativo (B2, B14) — risco alto

1. **[PROPOSTA]** Schema inicial: `conta`, `carteira`, `loadout` (formato do Plano A2), `aliança`, `posse de setor`, `estatística`. Versionamento de schema desde o primeiro dia (B14).
2. Separar **estado de jogador (BD)** de **estado de simulação (snapshot de setor)**.
3. Backups automáticos e procedimento de rollback antes do primeiro lançamento.
4. Plano A2 (formato de loadout) entra aqui como tabela.

**Aceite:** escrita transacional testada; restauro de backup executado uma vez com evidência; migração v1→v2 simulada.

## Etapa C3 — Estado de mundo compartilhado (B3, B17 parcial) — risco alto

1. **[PROPOSTA]** Modelo global: galáxia → sistemas → setores com posse e estado; começa com **um** mundo lógico antes de múltiplos shards.
2. Orquestração de shard por setor: processo do `ServerControl` por instância, com snapshot persistido no BD (C2).
3. Transição de jogador entre setores carrega identidade + inventário (global) + blueprint `.msch` — nunca copia o `.msav` inteiro.
4. O relógio real da estrela verreira (Plano B1) vive nesta camada.

**Aceite:** jogador entra no setor A, sai, entra no setor B com inventário intacto; snapshot do setor A restaurável após restart do processo.

## Etapa C4 — Economia servidor-autoritativa (B4, B11) — risco médio-alto

1. Carteira por conta, inventário persistente, registro de transação com auditoria (log imutável).
2. Invariantes de saldo: toda transferência é debitada de um lado e creditada no outro na mesma transação.
3. Faucets/sinks conectados à cadeia produtiva existente (A1/A2 da pesquisa: crafting e transporte do motor viram fontes de produção).
4. Observabilidade mínima: produção/consumo por setor para detectar inflação (B11) antes de ligar trocas entre jogadores.
5. **Depois disso** (e não antes): troca dirigida entre jogadores; mercado aberto com listagem de preços é **[PROPOSTA]** adiada — o concorrente não tem mercado aberto e funciona.

**Aceite:** teste de estresse de transações concorrentes sem saldo negativo; auditoria reconstrói o histórico de qualquer carteira.

## Etapa C5 — Alianças e chat persistente (B5, B6) — risco médio

1. Entidade de aliança: membros, cargos/permissões, banco compartilhado, marcação de inatividade (sem punição automática — lição da pesquisa).
2. Chat com canais por escopo (global, aliança), histórico persistente, moderação reusando os rate limits existentes do `Administration` (A18 da pesquisa).
3. UI de aliança no cliente seguindo o padrão touch (B13).

**Aceite:** chat sobrevive a restart; permissões de cargo testadas; webhook/_logs de ações administrativas.

## Etapa C6 — Progresso offline e idle (B7) — risco médio

1. Relógio de servidor como fonte única; produção acumulada por intervalo com **cap** e regra de decay definidas **antes** do balanceamento (ordem exigida pela pesquisa).
2. Proteção contra exploração de intervalo (teto por ciclo, auditoria de ganhos anômalos).
3. Tela de resumo de ausência (consumida pelo Plano B3).

**Aceite:** simulação de 48h de ausência com resultado idêntico em dois cálculos (unitário + integração); cap respeitado.

## Etapa C7 — Eventos globais e anti-cheat (B8, B9, B10) — risco médio-alto

1. Agendador de eventos no serviço central, spawn por shard, recompensas persistentes; reusa ondas/clima/objetivos existentes (A5/A20 da pesquisa).
2. Validação autoritativa de ações econômicas e de progressão no servidor (o `strict` atual é só ponto de partida — fato da pesquisa 4.6).
3. **[FATO]** Mods `.jar` carregam sem sandbox; política de mods no MMO: lista de permitidos no servidor + separação conteúdo(cliente)×plugin(servidor) + aviso no cliente.
4. Logs imutáveis de ações sensíveis.

**Aceite:** cliente adulterado (valor de saldo modificado) rejeitado pelo servidor em teste; mod não listado é recusado no servidor.

## Etapa C8 — Escalabilidade, ranks e sincronização (B12, B13, B15, B16) — risco alto/última fase

1. Medir o teto real de entidades/jogadores por instância **antes** de prometer simultâneos (não há benchmark oficial do motor — lacuna documentada na pesquisa 6.3). Benchmark em Android incluído.
2. Sharding em múltiplos processos com serviço central de contas/chat/economia fora da simulação — só depois do mundo lógico único (C3) estiver estável.
3. Serviço de perfil: XP/nível, ranks, leaderboards por conta (independente da simulação; pode ser entregue antes do resto para retenção — B15).
4. Sincronização entre dispositivos com resolução de conflitos (B16); `.msav` legado importa como conveniência, nunca como fonte de verdade.

**Aceite:** relatório de benchmark com números medidos e carimbo; perfil visível em dois dispositivos com dados idênticos.

---

## Ressalvas explícitas

- **Risco alto** em C0–C3 e C8 é da tabela da seção 5 da pesquisa; nenhuma etapa destas deve ser prometida com prazo antes de protótipo.
- Nenhum número de jogadores simultâneos pode ser declarado sem o benchmark da C8.1.
- A camada Steam (`steamworks4j`) permanece isolada no módulo desktop; `core` e `server` não assumem acoplamento a biblioteca proprietária (restrição 4.1.3 da pesquisa).
- Cada etapa entrega em ordem; saltar dependência (ex.: economia antes do BD) invalida a auditoria posterior.
