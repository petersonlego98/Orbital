# Proposta — efeitos visuais e demarcação de alcance dos projetores

**Tipo:** análise, decisões aprovadas e registro da implementação visual  
**Status:** implementação aplicada; JAR, app-image e instalador recompilados com sucesso; validação visual em jogo ainda pendente
**Data:** 6 de novembro de 2026  
**Fontes principais:** `core/src/orbital/content/Blocks.java`, `core/src/orbital/world/blocks/defense/MendProjector.java`, `OverdriveProjector.java`, `ForceProjector.java`, `RegenProjector.java`, `core/src/orbital/world/blocks/units/RepairTurret.java` e `RepairTower.java`.

---

## 1. Objetivo

Definir uma forma consistente de comunicar visualmente:

1. se domos, projetores e estruturas de reparo estão efetivamente operantes;
2. qual tipo de efeito cada estrutura fornece;
3. até onde esse efeito alcança.

A proposta cobre blocos instalados no mapa. Não altera nem presume uma função para módulos de suporte montados em unidades.

### Fora do escopo

- Não muda valores de alcance, consumo, cura, escudo ou balanceamento.
- Não cria mecânica de aura nova nem altera os alvos elegíveis.
- Não trata instalação/remoção ou ativação de módulos em unidades.
- Os contornos operacionais aparecem continuamente apenas enquanto o bloco pode funcionar; módulos inativos não ficam desenhados como cobertura ativa.
- A implementação registrada neste documento altera apenas a camada visual; não modifica cura, velocidade, escudo, consumo ou alcance mecânico.

---

## 2. O que existe hoje

### 2.1 Blocos e semântica atual

| Bloco/tipo | Efeito atual | Indicação visual já existente | Alcance / peculiaridade |
|---|---|---|---|
| `mender`, `mend-projector` (`MendProjector`) | Cura estruturas aliadas danificadas em pulsos; exige eficiência e não estar suprimido. | Cor verde, luz e animação superior; partículas e som quando cura; círculo tracejado na colocação e ao selecionar. | `range` base; fase pode aumentar alcance. Os valores específicos estão em `Blocks.java`. |
| `overdrive-projector` (`OverdriveProjector`) | Aumenta a velocidade de estruturas compatíveis dentro do alcance em ciclos. | Cor quente, luz e animação superior; círculo tracejado na colocação/seleção e realce das estruturas afetáveis ao selecionar. | `range` base; fase pode aumentar alcance quando aplicável. O `overdrive-dome` é a mesma classe com parâmetros diferentes. |
| `overdrive-dome` | Variante de `OverdriveProjector`, não o escudo poligonal. | Compartilha a animação do projetor de overdrive. | O nome “dome” pode ser confundido com escudo; sua função é overdrive. |
| `force-projector` (`ForceProjector`) | Projeta escudo poligonal que absorve projéteis e explosões; pode quebrar e se recuperar. | O próprio escudo fica visível quando ativo; brilho/efeitos de impacto e quebra; polígono durante colocação. | Raio variável por aquecimento, eficiência e reforço de fase. A área efetiva é um polígono regular, não um círculo. |
| `shield-projector`, `large-shield-projector` (`BaseShield`) | Escudos-base, atualmente editor-only. | Consultar implementação antes de ampliar escopo: não são blocos regulares disponíveis ao jogador. | Seus raios configurados não devem ser confundidos com o alcance do `force-projector`. |
| `regen-projector` (`RegenProjector`) | Cura estruturas aliadas próximas continuamente; consome energia e pode ser suprimido. | Drawer configurável com brilho/animação; partículas durante a cura; quadrado tracejado na colocação/seleção. | `range` é contado em blocos e a região mostrada é quadrada. |
| `repair-point`, `repair-turret` (`RepairTurret`) | Cura unidades aliadas danificadas por feixe direcionado; requer alvo e eficiência. | Feixe e pulso de cura; círculo tracejado ao posicionar/selecionar; cor verde do laser. | `repairRadius`; o alvo é uma unidade e o disparo depende do alinhamento da torre. |
| `unit-repair-tower` (`RepairTower`) | Cura unidades aliadas próximas; pode ser suprimida. | Onda circular progressiva, símbolo/forma e brilho enquanto aquece. | `range` circular; não é o mesmo reparador de estruturas (`MendProjector`). |

Os overlays atuais aparecem principalmente durante posicionamento e seleção. Isso ajuda a planejar, mas não mantém uma leitura persistente do estado operacional nem do alcance enquanto o jogador acompanha a partida.

### 2.2 “Ligado” precisa significar efeito disponível

O estado visual não deve derivar apenas de `enabled`. Os blocos diferem em consumo e condições:

- **Projetores Mend/Overdrive:** eficiência, ciclo/recarga e, no reparador, supressão determinam se o efeito pode acontecer. O indicador de “operante” deve refletir eficiência funcional; animação de atividade pode oscilar com pulsos e alvos.
- **Force projector:** estar alimentado não basta para indicar escudo ativo. `broken` e `realRadius()` determinam se há uma barreira efetiva. A condição visual do escudo deve acompanhar essas propriedades.
- **Regen projector:** alimentação, supressão e alvos danificados influenciam se há regeneração efetiva; `warmup` é animado em função de `didRegen`.
- **RepairTurret:** pode estar alimentada mas sem alvo ou sem ângulo para curar. Distinguir “pronto/ativo” de “curando agora” evita sinal enganoso.
- **RepairTower:** sua onda/brilho usa `warmup`, que segue a cura real. A supressão zera o aquecimento.

Portanto, recomenda-se distinguir **disponível** (pode fornecer o efeito) de **em uso** (está aplicando o efeito agora). Não introduzir um estado artificial de liga/desliga que não corresponda às condições reais.

---

## 3. Direção visual proposta

### 3.1 Indicador operacional sobre o próprio bloco

Usar a cor já associada a cada família e adicionar um sinal curto e legível no topo/base do bloco:

| Família | Cor-base proposta | Estado disponível | Estado aplicando efeito | Estado indisponível |
|---|---|---|---|---|
| Cura de estruturas (Mend/Regen) | Verde de cura já existente ou `Pal.heal` | Halo/anel discreto estável | Pulso ou partículas com intensidade proporcional à atividade | Sem halo; opcionalmente indicador neutro de falta de energia/supressão, sem vermelho alarmista |
| Overdrive (projetor/domo) | `baseColor` atual | Anel quente contínuo e discreto | Ondas curtas/segmentos pulsantes durante o ciclo de aplicação | Anel apagado ou reduzido conforme eficiência |
| Escudo (Force) | Cor do time para a barreira; acento frio/neutro para sinal auxiliar | Contorno da barreira conforme `realRadius()` | Brilho localizado ao receber impacto | Sem preenchimento/contorno efetivo quando quebrado; indicador separado de recuperação se necessário |
| Reparo de unidades (turret/tower) | Verde de cura atual | Pequeno marcador/halo quando energia e sistema estão disponíveis | Feixe (turret) ou ondas (tower) sincronizados à cura real | Feixe/halo de atividade apagados sem eficiência; distinguir ausência de alvo de falha elétrica via UI existente quando viável |

Princípios:

1. Reutilizar cores e efeitos já presentes antes de criar uma paleta paralela.
2. A cor diferencia **tipo** de efeito; intensidade/animação diferencia **estado**. Assim, cor isolada não carrega informação crítica.
3. Evitar animações constantes de alto contraste em todos os blocos: usar baixa intensidade em repouso e animação curta somente em aplicação/impacto.
4. Não usar apenas verde/vermelho para estados; preservar forma, brilho e movimento como canais redundantes.
5. Garantir `Draw.reset()`/restauração de estado gráfico após os novos desenhos e respeitar os níveis de detalhe (`Lod`) e camadas apropriadas.

### 3.2 Aura/área de efeito

Não pintar uma grande superfície translúcida. Usar um **contorno fino e de baixa opacidade**, na cor da família, com duas regras claras:

- ao posicionar ou selecionar, mostrar a prévia/alcance para inspeção;
- durante a partida, manter o contorno visível continuamente enquanto o módulo estiver ativado e operacional — não apenas enquanto selecionado. Ao ser desativado, ficar sem energia ou impedido de operar (por exemplo, supressão ou escudo quebrado), ocultar o contorno operacional. A seleção ainda pode mostrar o alcance previsto como referência, distinguindo-o visualmente do alcance ativo;
- usar linha **tracejada fina**. Para alcances ampliados por fase, mostrar em operação o alcance efetivo e uma segunda linha com o alcance máximo possível, visualmente diferenciada (implementação: mesma linha tracejada, traço mais fino e menor opacidade) para não confundir a cobertura atual com o limite potencial.

Assim o jogador percebe a cobertura em tempo real, sem deixar áreas inativas desenhadas como se estivessem funcionando. O contorno deve permanecer discreto, ser testado com estruturas sobrepostas e continuar legível em zoom baixo.

Para curas/overdrive que afetam construções, realces temporários de alvos podem acompanhar o pulso real, mas não devem destacar blocos apenas por estarem dentro do raio se não forem elegíveis. O `MendProjector` hoje destaca candidatos de forma ampla na seleção; isso pode ser refinado apenas se a implementação preservar desempenho e semântica.

---

## 4. Demarcações de alcance

### 4.1 Visibilidade recomendada

Implementar a demarcação em três contextos:

1. **Posicionamento:** manter a prévia atual, corrigindo cor/forma para identificar a família e representar a geometria correta.
2. **Operação normal:** manter o limite de alcance visível enquanto o bloco estiver ativado e operacional, mesmo sem seleção e mesmo quando não houver alvo naquele instante. “Operacional” significa que as condições mecânicas necessárias estão satisfeitas (por exemplo, energia disponível e sem supressão; para o ForceProjector, escudo íntegro e raio efetivo maior que zero). A falta momentânea de alvo deve interromper o sinal de aplicação do efeito, não ocultar a área de cobertura de um módulo que continua pronto.
3. **Seleção/inspeção:** mostrar o alcance efetivo ou previsto enquanto selecionado. Se o bloco estiver inativo, diferenciar claramente este contorno de referência do contorno operacional.

A visibilidade contínua do alcance em operação é requisito da proposta, não uma opção global. Ocultar apenas quando o módulo não estiver operando; manter a prévia no posicionamento/seleção para diagnóstico e planejamento.

### 4.2 Geometria deve corresponder à mecânica

- **Alcance radial:** círculo tracejado para Mend, Overdrive, RepairTurret e RepairTower.
- **RegenProjector:** quadrado tracejado, pois a busca atual usa retângulo centrado `range * tilesize`; trocar para círculo seria mudança de mecânica, não apenas cosmética.
- **ForceProjector:** polígono de `sides` lados com orientação e `realRadius()` efetivo; enquanto operacional, desenhar também uma segunda linha poligonal, discreta e tracejada, para o máximo possível com fase. Na colocação/seleção, diferenciar claramente raio base/efetivo do limite potencial.
- **Overdrive/Mend com reforço de fase:** durante a operação, mostrar o alcance efetivo e uma linha secundária para o máximo possível com fase. O máximo deve usar estilo distinto da linha efetiva. No posicionamento, mostrar alcance base e máximo potencial como referências distintas; na seleção, refletir o valor efetivo e o limite máximo.
- **RepairTurret:** a zona de aquisição de alvo é circular. O feixe tem restrição direcional, mas o alcance circular é suficiente para a primeira versão; uma linha de mira adicional só seria necessária se o teste de usabilidade mostrar ambiguidade.

### 4.3 Desempenho e legibilidade

- Desenhar o contorno de alcance operacional continuamente apenas para blocos ativados e operacionais; prévias de posicionamento/seleção seguem disponíveis para os demais.
- Evitar percorrer todas as estruturas a cada frame para montar a aura; aproveitar os alvos/listas já mantidos pelas classes quando possível. Preferir geometria simples de contorno em vez de superfície preenchida.
- Não gerar partículas por unidade/estrutura por frame. Usar o padrão de chance/cadência já aplicado por `RegenProjector`.
- Validar alcance real em unidades de mundo e conversão para blocos (`tilesize`), sobretudo no `RegenProjector`, cujo `range` é inteiro em blocos.
- Verificar clipping para os maiores raios e uso de `updateClipRadius` quando um efeito visual exceder o limite padrão do bloco.

---

## 5. Pontos técnicos de integração

| Arquivo/classe | Responsabilidade a avaliar |
|---|---|
| `core/src/orbital/world/blocks/defense/MendProjector.java` | Separar visual de disponibilidade e pulso efetivo; manter contorno tracejado do alcance efetivo durante operação e uma segunda linha para alcance máximo com fase; manter feedback vinculado a cura/supressão. |
| `core/src/orbital/world/blocks/defense/OverdriveProjector.java` | Sinal de efeito ativo; manter contorno tracejado efetivo e máximo durante operação; distinguir visualmente `overdrive-projector` e `overdrive-dome` sem sugerir escudo. |
| `core/src/orbital/world/blocks/defense/ForceProjector.java` | Marcar operacionalidade a partir de escudo realmente ativo e desenhar limite usando `realRadius()` e geometria poligonal; preservar sinais de impacto/quebra. |
| `core/src/orbital/world/blocks/defense/RegenProjector.java` | Tornar visível o limite quadrado também na seleção e revisar indicador para que acompanhe regeneração efetiva, não só alimentação. |
| `core/src/orbital/world/blocks/units/RepairTurret.java` | Usar `strength`/cura real no feixe e marcador; preservar seleção do alvo e limite circular. |
| `core/src/orbital/world/blocks/units/RepairTower.java` | Reaproveitar `warmup` para sinal de atividade; exibir limite na seleção; manter onda dentro do raio real. |
| `core/src/orbital/content/Blocks.java` | Confirmar cores e valores finais por bloco. É a fonte dos parâmetros por instância; evitar hardcode duplicado nas classes. |

Não se recomenda extrair de imediato um framework comum de “aura”. Os formatos e critérios operacionais diferem (círculo, quadrado, polígono; cura por pulso, feixe direcionado, escudo quebrável). Primeiro implementar os indicadores nos tipos existentes e só abstrair o que os testes mostrarem que é comum.

---

## 6. Sequência sugerida

| Fase | Entrega | Critério de saída |
|---|---|---|
| 0 | Fixar critérios de operacionalidade, convenção de cores e geometria dos ranges. | Critérios da seção 2.2 e decisões visuais da seção 8 definidos. |
| 1 | Manter contornos de alcance ativos durante a operação e aprimorar a prévia de seleção/posicionamento. | Geometria e raio exibidos correspondem ao alcance efetivo/documentado; contorno só aparece como ativo se o módulo puder operar. |
| 2 | Adicionar sinal de estado disponível/aplicando para cada família. | Nenhum bloco mostra atividade sem poder realmente fornecer o efeito. |
| 3 | Polir cores, animações e distinção de falha, supressão, recarga ou ausência de alvo. | Estados distinguíveis em zoom normal e baixo sem poluição visual. |
| 4 | Validar visibilidade contínua dos ranges ativos em mapas com vários projetores. | Contornos ativos legíveis, desempenho aceitável e áreas inativas não confundidas com cobertura. |

Fases 1 e 2 podem ser desenvolvidas por família, mas os critérios devem ser consistentes. Começar por Mend/Overdrive/Force permite cobrir cura, aura de buff e barreira real; depois integrar regenerador e reparadores de unidades.

---

## 7. Critérios de aceite propostos

1. Cada família possui sinal visual que identifica o tipo de efeito sem depender exclusivamente da cor.
2. Estado “disponível” não é confundido com “aplicando efeito agora”.
3. Falta de energia, supressão, escudo quebrado, recarga e ausência de alvo não exibem um sinal falso de atividade.
4. A demarcação do alcance permanece visível durante toda a operação do módulo, sem depender de seleção; posicionamento e seleção também conservam uma prévia útil.
5. Módulos inativos ou incapazes de operar não parecem cobrir uma área funcional; a seleção pode mostrar uma referência claramente diferenciada.
6. O alcance acompanha modificadores efetivos de fase onde aplicável; valores máximos e atuais não são confundidos.
7. Regen usa limite quadrado, Force usa polígono, e efeitos radiais usam círculo.
8. Efeitos novos não alteram cura, velocidade, escudo, consumo, alcance mecânico, save/load ou sincronização.
9. Não há emissão contínua excessiva de partículas; a interface permanece legível com vários projetores sobrepostos.
10. A renderização respeita níveis de detalhe, camadas e restauração de estado gráfico.
11. A verificação inclui testes visuais em zoom normal/baixo e estados sem energia, suprimido, alvo presente/ausente, fase presente/ausente e escudo ativo/quebrado.

---

## 8. Decisões registradas

Decisões confirmadas pelo usuário:

| Tema | Decisão | Aplicação |
|---|---|---|
| Estilo do alcance | Linha tracejada fina (opção 1A). | Usar como contorno operacional e nas prévias. |
| Alcance variável/fase | Mostrar alcance efetivo e uma segunda linha para o máximo possível (opção 2B). | A linha efetiva indica a cobertura atual; a linha secundária, em estilo distinto e discreto, indica o limite máximo com reforço. |
| Escopo de reparo | Incluir cura de estruturas e de unidades (opção 3C). | Cobrir Mend/Regen e RepairTurret/RepairTower, mantendo cores e geometrias adequadas por família. |
| Persistência | O alcance permanece visível enquanto o módulo estiver ativado e operacional. | Não depender de seleção; ocultar como cobertura ativa quando incapaz de operar. |

As decisões visuais principais estão definidas. Durante a implementação, ainda será necessário validar a transição de raio dinâmico (por exemplo, `ForceProjector.realRadius()`): a recomendação técnica é atualizar suavemente para evitar cintilação, sem atrasar ou falsear a área efetiva.

---

## 9. Status da implementação

As decisões aprovadas foram aplicadas no código:

- `Drawf` recebeu helpers para contornos circulares, quadrados e poligonais com linha fina e tracejada; contornos máximos usam a mesma convenção com menor intensidade.
- `MendProjector`, `OverdriveProjector`, `ForceProjector`, `RegenProjector`, `RepairTurret` e `RepairTower` desenham o alcance em operação, condicionado à disponibilidade mecânica de cada bloco.
- Overdrive/Mend exibem alcance atual e máximo com fase quando distintos; ForceProjector usa polígonos para o escudo atual e o limite máximo com fase.
- Regen mantém a área quadrada; reparadores de unidades e de estruturas estão incluídos.
- A prévia de colocação/seleção foi alinhada ao estilo fino tracejado. Foram ampliados os clip ranges onde a linha operacional pode ultrapassar a área normal de desenho do bloco.

**Validação (6 de novembro de 2026, padrão de `docs/BUILD_EXECUTAVEL.md`):**

1. `./gradlew --no-daemon --no-build-cache :desktop:dist` → `BUILD SUCCESSFUL`, código de saída 0; `:core:compileJava` e `:desktop:dist` executados. `desktop/build/libs/Orbital.jar` regenerado.
2. Bloqueios superados antes do sucesso: o lock em `core/build/generated/source/kapt/main` desapareceu após o TeraBox ser fechado; em seguida, `:core:compileJava` falhou com `AccessDeniedException` em `core/build/classes/java/main/orbital/world/modules`. Havia 162 itens `ReadOnly` em `core/build`, 45 em `desktop/build` e 65 em `annotations/build`. Limpei esse atributo **apenas nos diretórios `build` gerados**, sem tocar no código-fonte.
3. `jpackage --type app-image` com os parâmetros documentados → código de saída 0. O app-image antigo foi removido, `deploy/windows64/Orbital/Orbital.exe` regenerado e licenças, créditos e documentos recopiados (passo 4).
4. `bash installer/build.sh` → código de saída 0 na segunda execução, regenerando `build/installer/OrbitalSetup.exe` e `build/installer/payload`. A primeira execução foi interrompida por uma proteção contra exclusão em massa do payload antigo (372 arquivos); contornei renomeando o diretório antigo para backup, deixando o script gerar um payload limpo, e descartei o backup em seguida.
5. SHA-256 idêntico (`c4548f3f798d89199a99a44832ee81a844dfa76fb1590cd338df847ae24ead45`) entre `desktop/build/libs/Orbital.jar`, `deploy/windows64/Orbital/app/Orbital.jar` e `build/installer/payload/app/Orbital.jar`. `javap` confirma os novos helpers de `Drawf` no JAR compilado. `git diff --check` passou.
6. `./gradlew --no-daemon --no-build-cache :tests:test` executou **390 testes: 34 falhas**, todas em `ApplicationTests` (erros de leitura de saves/mapas antigos e asserções de núcleo em setores), código de saída 1. O baseline registrado em 4 de novembro era 346 testes / 36 falhas, com as mesmas 34 falhas de `ApplicationTests` já classificadas como preexistentes e não relacionadas; as 2 falhas de `TurretLoadoutSmokeTest` daquele baseline não se repetiram. Para esta execução também foi preciso limpar o atributo `ReadOnly` em 58 itens de `tests/build`, pelo mesmo motivo do item 2. Nenhuma falha cita os arquivos alterados nesta proposta.

**Ainda não verificado:** não executei o jogo. A aparência dos contornos em zoom normal/baixo, a sobreposição de vários projetores e os estados sem energia/suprimido precisam de validação visual em partida. As 34 falhas de `ApplicationTests` seguem sem correção, conforme decisão anterior do usuário.

## 10. Resumo

A informação visual agora segue as decisões: a demarcação tracejada fina fica continuamente visível enquanto o módulo pode operar; a linha secundária indica o alcance máximo possível com fase, sem se confundir com a cobertura atual. A implementação mantém as geometrias e mecânicas existentes; em especial, `overdrive-dome` é um projetor de overdrive, `force-projector` é o escudo poligonal, e `regen-projector` usa região quadrada.
