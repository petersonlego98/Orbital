# Plano B — Experiência de jogo e retenção, em etapas

**Fonte adaptada:** `pesquisa-dark-nebula-hades-star.md` (2026-11-09). Apenas **princípios e mecanismos** das seções 6–8 da pesquisa; nenhum nome, lore ou arte do Hades' Star/DN entra no projeto (regra de identidade do baseline).
**Ancoragem no projeto:** visão de jogo consolidada em `docs/VISAO_DE_JOGO_ORBITAL.md` (estrela vermelha com relógio real que **continua mesmo com o jogo fechado** — decisão já confirmada; Quatia como primeira colônia; Serpulo/Erekir fora da campanha).

Rótulos: **[LIÇÃO VERIFICADA]** = princípio observado na pesquisa do concorrente; **[PROPOSTA]** = tradução para o Orbital, a confirmar; **[DEPENDÊNCIA]** = requisito do Plano C.

---

## Etapa B1 — Expedição de estrela vermelha com janela real

**[LIÇÃO VERIFICADA]** Modo PvE curto cooperativo com janela de tempo fechada (10 min no DN) cria sessões de "bolso de tempo" que se acumulam em progressão de longo prazo.

1. **[FATO DO PROJETO]** A visão já decide: contagem real, não para com o jogo fechado. **[PROPOSTA]** Implementar o relógio de expedição como tempo de servidor (não de cliente), com estado da expedição persistido entre sessões — depende de C3 (mundo compartilhado).
2. **[PROPOSTA]** Recompensa por participação, não só por conclusão completa (evita punir quem volta tarde).
3. **[PROPOSTA]** Nave perdida na expedição não retorna (risco real), reusando a frota equipada pelo Plano A.

**Aceite:** expedição interrompida e retomada em outro dia com relógio e cargas consistentes; teste com relógio adiantado manualmente.

## Etapa B2 — Economia de três moedas com papéis claros

**[LIÇÃO VERIFICADA]** Separar moeda de progressão, moeda de ação e moeda premium dá legibilidade econômica (créditos / hidrogênio / cristais no concorrente).

1. **[PROPOSTA]** Definir os três papéis com nomes próprios de Orbital: moeda de progressão (construção/pesquisa), moeda de ação (movimento, saltos, ativação de módulos — a que tem teto renovável), moeda de conveniência/cosmético.
2. **[PROPOSTA]** A moeda de ação com teto ≈ 1,5–2 dias de produção é o gancho de retorno diário mais barato de implementar e o mais validado pela pesquisa.
3. **[DEPENDÊNCIA]** Carteiras por conta no servidor: etapa C4. Até lá, implementar só a simulação local com interface que depois conecta ao BD.

**Aceite:** documento de economia com tabela fonte→sink por moeda; implementação local testável antes da integração.

## Etapa B3 — Progresso offline sem punição

**[LIÇÃO VERIFICADA]** Produção que continua offline + relatório de retorno no login transforma ausência em recompensa, não punição; sem saque de recursos de quem está fora.

1. **[PROPOSTA]** Produção acumulada por intervalo no servidor com cap de armazenamento — depende de C6.
2. **[PROPOSTA]** Tela de "resumo de ausência" no login: o que produziu, o que expirou, o que aguarda.
3. **[PROPOSTA]** Nenhum modo permite atacar a colônia de jogador offline (princípio verificado, alinhado à visão atual de expedições contra ambiente, não contra bases).

**Aceite:** logout → tempo adiantado → login → relatório correto e dentro do cap; teste automatizado do cálculo de intervalo.

## Etapa B4 — Ganchos diários leves

**[LIÇÃO VERIFICADA]** Um objetivo diário único + recompensa por participação sustenta o loop sem virar lista massiva de dailies.

1. **[PROPOSTA]** Objetivo diário único rotativo (entregar carga, completar expedição, usar módulo X), 24h de validade, recompensa em moeda de progressão.
2. **[PROPOSTA]** Barra de recompensa por participação no modo PvP curto (quando existir) — paga mesmo sem vitória.
3. **[PENDÊNCIA]** Textos de módulos e UI corretos desde o primeiro nível — a queixa nº 1 de onboarding do concorrente foi descrição enganosa de módulo inicial; a auditoria A1 cobre isso de graça.

**Aceite:** objetivo rotativo com teste de renovação às 24h; nenhum texto de módulo divergente após auditoria A1.

## Etapa B5 — Camada social: alianças e cooperação

**[LIÇÃO VERIFICADA]** Retenção social é o eixo mais forte: grupo com benefício econômico mensurável, modos que exigem grupo, comunidade externa estruturada.

1. **[DEPENDÊNCIA]** Entidade de aliança + chat persistente: etapas C5.
2. **[PROPOSTA]** Expedições cooperativas privadas de aliança (análogo funcional às estrelas vermelhas privadas; forma própria).
3. **[PROPOSTA]** Bônus de renda por participação ativa do grupo — nunca bônus que expirem sob pressão de presença contínua (desgaste nº 1 documentado: pressão 24/7 em modo de 5 dias **é causa oficial de desistência** no concorrente; evitado por princípio).

**Aceite:** bônus de aliança visível na UI e auditável no servidor; modo cooperativo exige ≥2 contas conectadas.

## Etapa B6 — Ciclos fechados de competição e maestria

**[LIÇÃO VERIFICADA]** Temporadas com ciclo encerrado, prêmio cosmético (não de poder) e status visível dentro do grupo convertem habilidade em identificação sem P2W.

1. **[DEPENDÊNCIA]** Ranks/leaderboards por conta: etapa C8 parcial.
2. **[PROPOSTA]** Temporadas curtas com recompensa cosmética exclusiva (FOMO ético: vende identificação, não poder).
3. **[PROPOSTA]** Coleção de módulos como maestria: catálogo determinístico (sem gacha), variantes de módulo como segundo ciclo de coleção — extensão natural da economia do Plano A.
4. **[PROPOSTA]** Loadouts nomeados e salvos por conta (extensão direta da A6).

**Aceite:** leaderboard persistente entre partidas; recompensa de fim de temporada entregue automaticamente.

## Etapa B7 — Eventos globais e sazonalidade

**[LIÇÃO VERIFICADA]** Eventos agendados com notificação sem exigir presença contínua equilibram FOMO e respeito ao tempo; endgame precisa de segunda estrada de progressão.

1. **[DEPENDÊNCIA]** Agendador de eventos no servidor: etapa C7.
2. **[PROPOSTA]** Evento global periódico que afeta múltiplos setores (reusa ondas/clima existentes no motor, lacuna B8 da pesquisa do motor).
3. **[PROPOSTA]** Endgame: expansão de alto nível que continua dando progressão após o teto do modo principal (evita a queixa de "endgame vazio" que durou anos no concorrente).

**Aceite:** evento spawna em janela agendada e afeta setores distintos; recompensa registrada na conta.

---

## Anti-padrões que este plano proíbe (da seção 8 da pesquisa)

1. Obrigar presença contínua em modo longo sem alternativa.
2. Lentidão que só destrava com aceleração paga.
3. Matchmaking de novatos contra contas maximizadas.
4. Overhaul que invalida progresso de veteranos (migrações sempre preservam identidade).
5. Gacha em conteúdo poderoso.
6. Notificações/e-mails agressivos.
7. Descrições de sistema imprecisas no onboarding.

Cada etapa acima é revisada contra esta lista antes de fechar.
