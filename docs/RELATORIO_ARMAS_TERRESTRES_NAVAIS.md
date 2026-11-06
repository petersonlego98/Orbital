# Relatório — armas e comportamento das unidades terrestres e navais

**Tipo:** mapeamento técnico de combate  
**Data:** 6 de novembro de 2026  
**Fonte primária:** `core/src/orbital/content/UnitTypes.java`  
**Fontes de progressão:** `SerpuloTechTree.java`, `ErekirTechTree.java`  
**Contexto de produto:** `docs/RELATORIO_UNIDADES_ORBITAL.md`  
**Escopo:** 28 unidades terrestres + 10 unidades navais herdadas do motor.

---

## 1. Leitura rápida

### 1.1 O que este documento responde

- Qual arma cada unidade terrestre e naval usa.
- Qual é o **teto de tier** de cada linha (T1 a T5).
- Qual o dano, a recarga e o alcance aproximado de cada arma.
- Quais comportamentos e efeitos alteram a batalha além do dano bruto: mira, status, escudo, reparo, supressão, EMP, atropelamento, onda de passo, imunidades etc.

### 1.2 Como interpretar os números

- **Dano** é o dano direto do projétil principal. Splash e fragmentos aparecem separados.
- **Recarga** é em ticks; 60 ticks = 1 segundo.
- **Alcance aprox.** usa `velocidade × vida útil ÷ 8` (1 tile = 8 px). Lasers e railguns usam o comprimento declarado. É uma estimativa de projeto, não uma medição em partida.
- **T** é o tier de progressão da árvore de pesquisa, não uma propriedade do corpo da unidade.
- Valores entre parênteses são observações de comportamento, não números de balanceamento.

### 1.3 Aviso de contexto

O relatório de unidades do Orbital registra que as 38 unidades terrestres e navais estão **carregadas no motor, mas dormentes na experiência atual**: Gier usa apenas ondas aéreas e as campanhas de Serpulo/Erekir estão desativadas. Este documento mapeia o conteúdo como ele existe no código, não como ele aparece hoje ao jogador.

---

## 2. Teto de cada linha

| Linha | T1 | T2 | T3 | T4 | T5 / topo | Papel do topo |
|---|---|---|---|---|---|---|
| Mecha de assalto (Serpulo) | Dagger | Mace | Fortress | Scepter | **Reign** | Cerco pesado, perfuração e fragmentos |
| Mecha de apoio (Serpulo) | Nova | Pulsar | Quasar | Vela | **Corvus** | Laser capital que fere e cura |
| Walker biológico (Serpulo) | Crawler | Atrax | Spiroct | Arkyid | **Toxopid** | Saturação de estilhaços e artilharia |
| Naval de ataque (Serpulo) | Risso | Minke | Bryde | Sei | **Omura** | Railgun de 62,5 tiles |
| Naval de apoio (Serpulo) | Retusa | Oxynoe | Cyerce | Aegires | **Navanax** | EMP, laser de plasma e supressão |
| Tanque (Erekir) | Stell | Locus | Precept | Vanquish | **Conquer** | Canhão de 360 + 12 subprojéteis |
| Mecha/walker (Erekir) | Merui | Cleroi | Anthicus | Tecta | **Collaris** | Artilharia de 48 tiles com 15 fragmentos |
| Hover (Erekir) | Elude | — | — | — | **Elude** | Linha terrestre sem T5; os refabricadores seguintes viram unidades aéreas |
| Neoplasma (Erekir) | — | — | — | — | **Latum** | Especial: não usa arma, esmaga e spawna Renales ao morrer |

**Topos absolutos por atributo:**

- **Maior dano único:** Omura — 1.250 por disparo de railgun.
- **Maior burst:** Conquer — 360 diretos + 12 subprojéteis de 60 + splash.
- **Maior saturação:** Collaris — 260 + 120 de splash + 15 fragmentos teleguiados.
- **Maior alcance:** Omura — 500 px (62,5 tiles).
- **Maior controle:** Navanax — EMP de 100 px de raio + supressão de reparo.
- **Maior resistência bruta:** Reign e Conquer — 22.000–24.000 de vida e 26–30 de armadura.
- **Maior cura/impacto de área:** Corvus — 560 de dano de laser e 25% de cura.

---

## 3. Unidades terrestres — Serpulo

### 3.1 Linha de assalto: Dagger → Reign

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Dagger** | 1 | 150 / 0 | Canhão cinético `large-weapon` | 9 / 13 | ~18,8 tiles | Primeira arma do jogo; projétil reto, sem área. |
| **Mace** | 2 | 550 / 4 | Lança-chamas | 74 / 22 | ~6,8 tiles | Perfura até 2 alvos, atravessa construções, aplica **queimando** por 5 s; imune a queimadura. |
| **Fortress** | 3 | 900 / 9 | Artilharia | 20 + 80 de splash (raio 35) / 60 | ~26,6–30 tiles | **Não mira ar**; explosão em área e recuo/trepidação altos. |
| **Scepter** | 4 | 9.000 / 20 | Canhão principal + 2 montagens | 3×70 + raios de 20/5; secundárias 20 cada / 45 e 12–15 | ~27 tiles | Rajada de 3 tiros com **lightning**; duas torres secundárias independentes. Habilidade: escudo regenerativo de 25 por pulso (máx. 250) a cada 1 s em 7,5 tiles. |
| **Reign** | 5 | 24.000 / 30 | Canhão perfurante | 80 + 18 de splash + 3 fragmentos de 20 / 9 | ~24,4 tiles | Perfura até 10 alvos; cada acerto gera 3 fragmentos perfurantes. DPS direto altíssimo e alta cadência para o porte. |

### 3.2 Linha de apoio: Nova → Corvus

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Nova** | 1 | 200 / 1 | Laser curativo | 13 / 30 | ~19,5 tiles | Cura 5% da vida no acerto; perfura 2 alvos. Aura de reparo: 20 a cada 2 s em 12,5 tiles, até 6 alvos; cura aliados do mesmo tipo a 15%. Constrói. |
| **Pulsar** | 2 | 320 / 4 | Escopeta elétrica | 3×15 / 36 | curto (lightning) | Aplica **choque**; cura 2% + 1,6% no raio. Escudo de 20 por pulso (máx. 40) a cada 5 s. Mina tier 2. |
| **Quasar** | 3 | 640 / 9 | Feixe laser | 45 / 55 | 18,75 tiles | Cura 10% no feixe, com raios laterais. Bolha de força: 500 de escudo, 0,4/tick de regeneração, 6 s de cooldown, raio 7,5 tiles. Mina tier 3. |
| **Vela** | 4 | 8.200 / 16 | Laser contínuo + feixe de reparo | 35 contínuo / 155 | 22,5 tiles; reparo 15 tiles | Laser contínuo que cura 1% e pode incendiar. Feixe de reparo de 1,4/s. Constrói rápido (3,0). Aplica **lentidão** em si mesma ao disparar. Imune a queimadura. |
| **Corvus** | 5 | 18.000 / 14 | Laser gigante | 560 + 5 raios de 50 / 350 | 57,5 tiles | Laser de 75 px de largura, cura 25% e gera raios. Fica **imóvel** (unmoving) por 2 s ao disparar. Quatro pernas, caminhada pesada. |

### 3.3 Linha biológica: Crawler → Toxopid

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Crawler** | 1 | 150 / 0 | Ogiva suicida | 80 de splash (raio 44) / 24 | 3,1 tiles (override 25) | **Explode ao morrer**; dano em construção reduzido a 68%; mata o próprio atirador. IA suicida. |
| **Atrax** | 2 | 600 / 3 | Jato de escória | 13 / 9 | ~17,8 tiles | Projétil líquido de escória; **não acerta ar**; imune a queimadura e derretimento. |
| **Spiroct** | 3 | 1.000 / 9 | 2 emissores de seiva | 23 e 18 / 14 e 18 | 9,4 e 5 tiles | Seiva aplica **sapped** (-30% velocidade, -20% vida) por 3 s e puxa o alvo (knockback negativo). |
| **Arkyid** | 4 | 8.000 / 14 | 3 emissores de seiva + artilharia | 40 cada; 12 + 65 de splash (raio 70) / 9, 14, 22 e 45 | 6,9 e 17,5 tiles | Artilharia com 3 raios, aplica **sapped** por 10 s. Onda de passo: 32 de dano em 30 de raio. |
| **Toxopid** | 5 | 22.000 / 22 | Escopeta de estilhaços + artilharia | 2×110 / 30; 50 + 75 de splash (raio 80) + 9 fragmentos de 30 / 210 | 11,25 e 30 tiles | A artilharia tem 5 raios e fragmentos com splash próprio, todos com **sapped**. Onda de passo: 80 de dano em 60 de raio. |

---

## 4. Unidades terrestres — Erekir

### 4.1 Tanques: Stell → Conquer

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Stell** | 1 | 850 / 6 | Canhão leve | 40 / 50 | ~20 tiles | Tanque básico, torre única, sem área. |
| **Locus** | 2 | 2.100 / 8 | Railgun | 48 / 18 | 20 tiles | Disparo alternado, perfuração com fator 0,8; **atropela** construções frágeis. |
| **Precept** | 3 | 5.000 / 11 | Canhão explosivo | 120 + 50 de splash (raio 20) + 4 fragmentos de 35 / 80 | ~24,5 tiles | Perfura 2 alvos; imune a queimadura/derretimento; **atropela** construções frágeis. |
| **Vanquish** | 4 | 11.000 / 20 | Canhão principal + 2 secundárias | 150 + 50 de splash + 5 fragmentos de 35 / 80; secundárias 50 / 22 e 27 | ~23,8 tiles; secundárias ~22,5 | Dano de atropelamento 2,6; imune a queimadura/derretimento; **atropela** construções frágeis. |
| **Conquer** | 5 | 22.000 / 26 | Canhão massivo | 360 + 12 subprojéteis de 60 (splash 65, raio 30) / 100 | ~35 tiles | Perfura 3 alvos; dispara 6 pares de subprojéteis em leque. Exige **warmup** longo (mínimo 0,9). Atropelamento 5. Imune a queimadura/derretimento. |

### 4.2 Mechas e walkers: Merui → Collaris

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Merui** | 1 | 680 / 4 | Morteiro | 40 + 30 de splash (raio 19) / 63 | ~17,3 tiles | **Não mira ar**; artilharia indireta. |
| **Cleroi** | 2 | 1.100 / 5 | Canhão + ponto de defesa | 30 + 43 de splash (raio 30) / 33; PD 38 / 9 | ~17,5 tiles; PD 12,5 | Ponto de defesa abate projéteis e mísseis inimigos. |
| **Anthicus** | 3 | 2.700 / 7 | Baía de mísseis | 2 mísseis / 130 | míssil ~41,7 tiles | Cada míssil é uma **unidade-míssil** com 55 de vida e 140 de explosão (raio 25) ao morrer. Aplica **lentidão** em si ao disparar. |
| **Tecta** | 4 | 6.500 / 5 | Mísseis teleguiados | 3×51 + 60 de splash (raio 30) / 40 | ~28,9 tiles | Escudo frontal: 2.500 de vida, 0,75/tick, arco de 82°, 8 s de cooldown e **100% de deflexão**. Onda de passo: 32 em 30. |
| **Collaris** | 5 | 18.000 / 9 | Artilharia pesada | 260 + 120 de splash (raio 36) + 15 fragmentos teleguiados de 37 / 130 | 48,1 tiles (override 385) | **Não mira ar**; aplica lentidão em si ao disparar; fragmentos têm splash de 35 e homing. Onda de passo: 32 em 32. |

### 4.3 Hover e neoplasma

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Elude** | 1 | 600 / 1 | Disparo duplo teleguiado | 2×16 / 40 | ~18,8 tiles | Hover rápido; projéteis com homing; não afunda; rastro de movimento. É o único T1 da linha "ship" terrestre. |
| **Latum** | especial | 20.000 / 12 | Nenhuma | — | corpo a corpo | IA de abraço (`HugAI`), atropelamento 2; **spawna 5 Renales ao morrer**. Unidade oculta. |
| **Renale** | especial | 500 / 2 | Nenhuma | — | corpo a corpo | IA de abraço, atropelamento 0,5; unidade oculta invocada pelo Latum. |

---

## 5. Unidades navais — Serpulo

Todas as unidades navais usam `faceTarget = false`: o casco não gira para o alvo; as torres giram de forma independente. É o comportamento que o relatório de produto chama de "virar de lado" e considera ideal para naves capitais.

### 5.1 Linha de ataque: Risso → Omura

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Risso** | 1 | 280 / 2 | Canhão + mísseis | 9 / 13; 12 + 10 de splash (raio 25) / 25 | ~18,8 e ~21,9 tiles | Míssil com homing de 60 px e oscilação lateral. Barco leve de assalto. |
| **Minke** | 2 | 600 / 4 | Flak + artilharia | 3 + 40,5 de splash (raio 15) / 10; 20 + 40 de splash (raio 22,5) / 30 | ~27,6 tiles | Flak antiaéreo de alta cadência; artilharia contra superfície. |
| **Bryde** | 3 | 910 / 7 | Artilharia + 2 mísseis + escudo | 15 + 70 de splash (raio 40) / 65; 2×12 + 10 de splash / 20 | ~33,6 e ~23,6 tiles | Artilharia aplica **blasted**; escudo regenerativo de 20 por pulso (máx. 40) a cada 4 s. |
| **Sei** | 4 | 11.000 / 12 | Lançador de mísseis + canhão | 6×42 + 45 de splash (raio 35) / 45; 3×57 / 60 | ~32,6 e ~30,6 tiles | Mísseis com homing de 80 px, disparados em 3 tubos alternados; canhão de 3 tiros. |
| **Omura** | 5 | 22.000 / 16 | Railgun | 1.250 / 110 | 62,5 tiles | Maior dano único do catálogo terrestre/naval; perfura com fator 0,5. |

### 5.2 Linha de apoio: Retusa → Navanax

| Unidade | T | Vida / armadura | Arma | Dano / recarga | Alcance aprox. | Comportamento e efeitos |
|---|---:|---|---|---|---|---|
| **Retusa** | 1 | 270 / 3 | Feixe de reparo + laser + minas | Reparo 0,75; 12 / 22; 3 minas de 40 de splash (raio 32) / 90 | 15; 19,5; 6,25 tiles | Laser cura 5,5%; minas curam 4%, têm homing, funcionam debaixo d'água e explodem em área. Constrói (1,5). |
| **Oxynoe** | 2 | 560 / 4 | Plasma + ponto de defesa | 23 / 5; PD 17 / 9 | ~7,7; 12,5 tiles | Aplica **queimando** por 4 s e cura 1,5%. Aura de **overclock** por 6 s a cada 6 s em 7,5 tiles: +15% velocidade, +15% dano e +25% cadência. PD abate projéteis. |
| **Cyerce** | 3 | 870 / 6 | Feixe de reparo + mísseis de plasma | Reparo 0,7; 25 + 25 de splash + 7 fragmentos de 11 / 60 | 16,25; ~25 tiles | Fragmentos curam 2,8% e aplicam splash de 13. Míssil com homing e explosão em 40 px. Constrói. |
| **Aegires** | 4 | 12.000 / 12 | Campo de energia + 2 pontos de defesa | 40 / 65; PD 30 / 4 | 22,5 tiles | Campo atinge até 25 alvos, aplica **eletrificado** por 6 s e cura aliados 1,5%. Dois PD de 180 px de alcance. Constrói (3,0). |
| **Navanax** | 5 | 20.000 / 20 | Canhão EMP + 4 lasers de plasma + supressão | 110 + 110 de splash (raio 100) / 65; laser 27 contínuo / 170 | ~37,5; lasers ~12 tiles; supressão 25 tiles | **EMP**: em aliados, acelera energia em 3× por 20 s e cura 20%; em inimigos, reduz a produção de energia e causa 3× dano a estruturas energizadas; em unidades, 0,8× dano + **eletrificado**. Lasers curam 0,4%. Campo de supressão bloqueia cura de construções por 1,5 s a cada 1,5 s. |

---

## 6. Características que alteram a batalha além do dano

### 6.1 Mira e alcance

- **`targetAir = false`:** Fortress, Atrax, Merui e Collaris não conseguem mirar unidades aéreas. É uma fraqueza estrutural contra bombardeiros e gunships.
- **`faceTarget = false`:** todas as 10 unidades navais. O casco segue a rota enquanto as torres acompanham o alvo. Isso permite kiting lateral e disparo em movimento.
- **`singleTarget = true`:** Scepter e Vela concentram fogo em um alvo por vez.
- **Alcance override:** Collaris usa 385 px (48,1 tiles) independentemente da velocidade do projétil; Omura usa comprimento fixo de 500 px.
- **Warmup:** Conquer exige 0,9 de warmup; Anthicus e Collaris também têm tempo de preparação. O dano não sai instantaneamente ao adquirir alvo.

### 6.2 Status aplicados

| Status | Quem aplica | Efeito de combate |
|---|---|---|
| **Queimando** | Mace, Oxynoe | Dano ao longo do tempo por 4–5 s. |
| **Sapped** | Spiroct, Arkyid, Toxopid | −30% velocidade e −20% vida por 3–10 s; seiva também puxa o alvo. |
| **Eletrificado** | Navanax (EMP), Aegires (campo) | −30% velocidade e −40% cadência por 6 s. |
| **Blasted** | Bryde | Status reativo de explosão; interage com congelamento. |
| **Lentidão** | Vela, Anthicus, Collaris (em si) | Reduz a própria velocidade durante o disparo. |
| **Unmoving** | Corvus (em si) | Velocidade zero por 2 s ao disparar o laser. |
| **Overclock** | Oxynoe (aliados) | +15% velocidade, +15% dano e +25% cadência por 6 s. |
| **Supressão** | Navanax | Impede a cura de construções inimigas por 1,5 s, reaplicada a cada 1,5 s. |

### 6.3 Defesa e sustentação

- **Escudo regenerativo em área:** Scepter (25/pulso, máx. 250), Pulsar (20/pulso, máx. 40), Bryde (20/pulso, máx. 40).
- **Bolha de força:** Quasar (500 de escudo, raio 7,5 tiles, 6 s de cooldown).
- **Escudo direcional:** Tecta (2.500, arco de 82°, deflexão total).
- **Campo de energia ofensivo:** Aegires (dano em 25 alvos + cura aliados).
- **Reparo:** Nova (aura), Vela, Retusa, Cyerce (feixes), Cyerce (fragmentos curativos), Navanax (healPercent no EMP).
- **Ponto de defesa:** Cleroi, Oxynoe, Aegires. Abatem projéteis e mísseis, não unidades.
- **Minas:** Retusa. As minas têm homing, curam e explodem em área.

### 6.4 Mobilidade, terreno e colisão

- **Atropelamento (`crushDamage`):** Locus, Precept, Vanquish (2,6), Conquer (5), Latum (2), Renale (0,5).
- **`crushFragile`:** Locus, Precept, Vanquish e Conquer esmagam construções frágeis.
- **Onda de passo (`legSplashDamage`):** Arkyid (32/30), Toxopid (80/60), Tecta (32/30), Collaris (32/32).
- **Imunidades:** Mace (queimadura), Atrax (queimadura/derretimento), Precept, Vanquish e Conquer (queimadura/derretimento), Vela (queimadura).
- **Afogamento:** unidades terrestres têm multiplicadores de tempo de afogamento; navais não afundam por definição. O Elude é hover e não afoga.
- **Voo/baixa altitude:** Vela usa `lowAltitude`; as demais terrestres são de solo.

### 6.5 Alvos, projéteis e padrões de disparo

- **Perfuração:** Mace (2), Reign (10), Precept (2), Vanquish (2), Conquer (3), Omura (fator 0,5), Locus (fator 0,8).
- **Fragmentos:** Reign (3), Precept (4), Vanquish (5), Conquer (12), Toxopid (9), Cyerce (7), Collaris (15).
- **Homing:** mísseis de Risso, Bryde, Sei, Cyerce, Tecta, Elude, fragmentos de Collaris, minas de Retusa.
- **Rajadas/alternância:** Scepter (3 tiros), Minke, Bryde (2 mísseis), Sei (6 mísseis em 3 tubos), Omura, Retusa (3 minas), Locus (alternado), Anthicus (2 mísseis).
- **Arcos limitados:** Toxopid (80°), Tecta (escudo de 82°), Collaris (30°), Anthicus (70°).

---

## 7. Leitura de balanceamento

1. **A linha naval é a melhor fonte de suporte do catálogo.** Cinco das dez unidades (Retusa, Oxynoe, Cyerce, Aegires, Navanax) cobrem reparo, overclock, ponto de defesa e EMP. É o argumento mais forte do relatório de produto para convertê-las em módulos.
2. **Os T5 não são apenas "mais dano".** Reign adiciona fragmentos, Toxopid adiciona saturação, Conquer adiciona warmup e subprojéteis, Collaris adiciona 15 fragmentos teleguiados, Omura troca cadência por um disparo único de 1.250, Navanax troca dano puro por EMP e supressão. Cada topo muda a decisão de combate.
3. **As fraquezas são claras e exploráveis.** Fortress, Atrax, Merui e Collaris não acertam ar. Corvus e Collaris se imobilizam ou se lentificam ao disparar. Conquer tem warmup longo. Omura tem recarga de 110 ticks. Navanax depende de posicionamento para o EMP de 100 px.
4. **O atropelamento é uma arma silenciosa.** Locus, Precept, Vanquish e Conquer causam dano por colisão; em mapas abertos, isso vale mais do que a ficha de dano sugere.
5. **A saturação vence ponto de defesa.** Aegires e Oxynoe abatem projéteis, mas Collaris, Toxopid e Conquer geram volume suficiente para sobrecarregar a interceptação. É a contrapartida que o relatório de produto descreve.

---

## 8. Lacunas e observações

- **Elude não tem T5 terrestre.** A linha "ship" do Erekir segue para unidades aéreas (Avert, Obviate); portanto, o hover terrestre termina no T1.
- **Latum e Renale não têm tier de pesquisa.** São unidades ocultas de neoplasma, sem arma e sem linha de refabricação. Funcionam como especiais de campanha.
- **Não há armas navais fora das 10 unidades listadas.** Toda a artilharia, míssil, railgun, reparo e EMP naval está concentrada nelas.
- **Os números de cura e status são os declarados no código.** Eles não consideram sinergias de time, overdrive de bloco, escudos de constructores ou modificadores de dificuldade.
- **A análise é estática.** Não foi executada uma simulação de combate; alcance e DPS efetivos podem variar com aceleração, arrasto, precisão, tamanho do alvo e interceptação.
