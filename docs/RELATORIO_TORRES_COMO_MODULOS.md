# Relatório — Torres como módulos de casco

**Tipo:** análise de produto e documento de conceito  
**Data:** 4 de novembro de 2026  
**Base:** código do protótipo Orbital e documento `docs/VISAO_DE_JOGO_ORBITAL.md`  
**Relacionado:** `docs/RELATORIO_UNIDADES_ORBITAL.md`, `docs/RELATORIO_SISTEMA_DE_SETORES.md`  
**Público:** design, produção e liderança técnica  
**Leitura:** não exige conhecimento de programação; o anexo final é destinado à equipe técnica.

---

## 1. Resumo executivo

O relatório anterior propôs transformar as unidades terrestres e navais em **módulos** acopláveis às unidades aéreas. Este documento trata de um tipo específico de módulo, muito mais concreto e já existente no jogo: **as torres**.

O Orbital herdou do Mindustry **31 definições de torre**. Hoje elas são conteúdo de base: peças colocadas no chão, ligadas a energia, líquido e munição, pensadas para defender um setor parado. No entanto, a fantasia do jogo é uma **nave com equipamento acoplado ao casco**. A pergunta natural é:

> **Por que não colocar uma torre nas costas de uma unidade aérea?**

A resposta curta é: **porque o motor não tem esse encaixe hoje, mas quase tudo o que ele precisa já existe**. Torres já sabem mirar, recarregar, gastar munição, aquecer e escolher alvos. Unidades já sabem carregar armas que giram de forma independente do corpo (`Weapon` + `WeaponMount`). O que falta é a ponte entre os dois mundos: um **encaixe dorsal** com regra de tamanho, custo e recurso.

A proposta deste documento é:

> **Torres passam a ser módulos dorsais. Uma torre só pode ser montada se a pegada dela couber nas costas da unidade sem cobrir a unidade por inteiro.**

Para tornar a regra objetiva, usamos três medidas tiradas dos próprios sprites:

1. **Pegada (`P`)** — o lado da torre em pixels (1x1 = 32 px, 2x2 = 64 px, e assim por diante).
2. **Cobertura (`C`)** — quanto da área visível da unidade a torre ocupa: `C = P² ÷ A`, onde `A` é a área do sprite da unidade.
3. **Largura (`L`)** — quanto da maior dimensão da unidade a torre ocupa: `L = P ÷ maior lado da unidade`.

A torre é aprovada quando:

- **`C ≤ 50%`** — a torre não cobre a unidade por completo;
- **`L ≤ 70%`** — a torre não atravessa a silhueta de ponta a ponta;
- **nariz/cabine, motores e asas continuam visíveis**.

O primeiro caso validado é exatamente o exemplo pedido: **Duo (1x1, sprite 32x32) no Mono (sprite 48x48)**. A cobertura é de **44%** e a largura é de **67%**. Passa nas duas regras, com margem curta — o que faz dele um ótimo teste inicial, e não um caso qualquer.

**O que isso resolve:**

- dá função imediata a 31 torres que hoje são conteúdo dormente;
- cria progressão de equipamento sem inventar uma nova categoria de arte;
- transforma o Minhocão de munição, energia e calor em decisões de build;
- reforça a fantasia de nave modular;
- aproveita um sistema de combate já testado.

**O que ainda falta:**

- um encaixe dorsal de verdade no modelo de unidade;
- regra de slots por classe de casco;
- munição/energia dedicada à torre;
- tela de montagem e persistência da configuração;
- rebalanceamento das torres, hoje calibradas para defesa estática;
- decisão sobre torres que só atacam alvos terrestres.

**Recomendação em cinco fases:**

1. **Provar** com Duo + Mono, em uma variante de unidade, sem tocar no sistema inteiro.
2. **Definir** slots, pegada, custo e a regra de cobertura como dados.
3. **Montar** um kit inicial de 6 torres, não as 31.
4. **Criar** o recurso de munição/energia da torre e a tela de preparação.
5. **Expandir** por classe de casco, com curadoria — não com o catálogo inteiro.

**A frase que resume a proposta:**

> **A torre deixa de ser uma peça no chão e passa a ser o que a nave carrega nas costas.**

---

## 2. A ideia em uma página

### 2.1 O princípio

O relatório de unidades dividiu a nave em três camadas: **casco**, **arma** e **módulo**. A torre dorsal ocupa um espaço entre a arma e o módulo:

```text
CASCO (unidade aérea)
  ├─ ARMA DE NARIZ      → arma nativa da unidade, dispara para frente
  ├─ TORRE DORSAL       → peça giratória, mira sozinha, consome munição/energia
  ├─ MÓDULO ATIVO       → habilidade com recarga
  ├─ MÓDULO PASSIVO     → efeito permanente
  └─ PORÃO / SENSOR     → carga, alcance, utilidade
```

- **Arma de nariz:** já existe. É o `Weapon` preso ao corpo, geralmente com pouca rotação.
- **Torre dorsal:** o que este documento propõe. Gira 360°, tem base, cano, munição e, às vezes, calor.
- **Encaixe dorsal:** o espaço físico nas costas do casco. Não é um slot abstrato: tem **tamanho** e **posição**.
- **Pegada:** o quadrado que a torre ocupa nesse encaixe.

### 2.2 O que muda e o que permanece

| Elemento | Hoje | Proposta |
|---|---|---|
| Torres | Blocos colocados no chão | Módulos dorsais de unidade |
| Encaixe | Tile do mapa | Área dorsal do sprite |
| Mira | Automática na base | Automática ou manual na unidade |
| Munição | Itens no prédio | Magazine da torre ou carga da unidade |
| Energia | Rede elétrica | Bateria da unidade |
| Líquido | Tubulação | Tanque da unidade |
| Calor | Rede de calor/coolant | Calor da torre, simplificado |
| Reparo/construção | Torres de apoio | Módulos utilitários de unidade |
| Balanceamento | Defesa estática | Combate móvel, com alcance reduzido |

### 2.3 Regras de design

1. **A torre precisa caber.** Se cobrir a unidade inteira, não entra. Silhueta é leitura de combate.
2. **A torre precisa ser lida.** Base, cano e recuo devem ser visíveis mesmo em combate agitado.
3. **Tamanho é classe.** Uma torre 1x1 é equipamento leve; 4x4 é arma de capital; 9x9 não é módulo, é fortaleza.
4. **Recurso é decisão.** Munição, energia e calor precisam competir com carga, velocidade ou defesa.
5. **Nada de torre que só atira no chão.** Se o jogo é aéreo, `targetAir = false` vira "anti-capital" ou é desativado.
6. **Poucas torres por casco.** Uma ou duas, no máximo três em cascos capitais. Muitas torres apagam a identidade da unidade.
7. **A torre não substitui o casco.** Ela melhora um papel; não transforma um minerador em nave de guerra sozinha.

### 2.4 Um exemplo concreto

**Mono com Duo:**

- Mono é uma unidade aérea de mineração, **sem arma alguma**. Vida 100, rápida, barata.
- Duo é uma torre 1x1 de dois canos, alcance 160, recarga 20, munição de cobre, grafite ou silício.
- A pegada do Duo é 32x32; o sprite do Mono é 48x48. Cobertura 44%, largura 67%.
- Resultado: um minerador que ganha defesa próxima e pode se proteger de interceptadores baratos.

Esse é o **primeiro caso de teste** porque é o menor encaixe possível no menor casco possível. Se o Duo não parecer bom no Mono, nenhuma torre vai parecer.

**Eclipse com Spectre:**

- Eclipse é uma nave capital de 320x321, com 22.000 de vida.
- Spectre é uma torre 4x4, alcance 260, recarga 7, cadência alta, munição de cobre, grafite, tório e piratita.
- A cobertura é de apenas 16%; a largura, 40%.
- Resultado: uma capital que troca parte da carga por uma metralhadora pesada de cobertura.

Os dois exemplos mostram a mesma regra funcionando em escalas opostas.

### 2.5 Glossário rápido

| Termo | Significado |
|---|---|
| **Torre** | Bloco de defesa com base, cano e mira independente |
| **Torre dorsal** | Torre montada nas costas de uma unidade |
| **Pegada** | Quadrado ocupado pela torre, em pixels |
| **Cobertura** | Fração da área do sprite da unidade ocupada pela torre |
| **Largura relativa** | Fração da maior dimensão da unidade ocupada pela torre |
| **Slot dorsal** | Encaixe disponível no casco, com tamanho máximo |
| **Magazine** | Reserva de munição da torre |
| **Anti-capital** | Releitura das torres que só atingem alvos terrestres |

---

## 3. Como as torres funcionam hoje

### 3.1 Uma torre é um prédio, não um equipamento

No código, uma torre é um **bloco** (`Block`) colocado em um tile. Ela tem uma construção (`TurretBuild`) que guarda munição, calor, recarga e alvo. Essa construção existe no mundo, ocupa espaço e recebe energia, líquido ou itens de uma rede.

Isso é o oposto do que uma unidade precisa. Uma unidade carrega o equipamento **dentro de si** e se move. A torre não tem tile, não tem rede e não tem uma equipe de construção.

Existe uma pista interessante: toda `TurretBuild` cria internamente uma representação de unidade (`BlockUnitc unit`) para permitir controle por lógica e por jogador. Ou seja, **a torre já sabe se comportar como unidade** — mas não o contrário.

### 3.2 O que uma torre já faz bem

| Comportamento | Onde está | Reaproveitável? |
|---|---|---|
| Mirar e girar | `TurretBuild` | Sim |
| Escolher alvo aéreo/terrestre | `targetAir` / `targetGround` | Sim, com revisão |
| Recarregar | `reload` / `ReloadTurret` | Sim |
| Consumir munição por item | `ItemTurret` | Sim, com novo magazine |
| Consumir energia | `PowerTurret` | Precisa de bateria |
| Consumir líquido | `LiquidTurret` | Precisa de tanque |
| Aquecer e esfriar | `heat`, `coolant` | Precisa de calor de unidade |
| Recuo visual | `recoil` | Sim |
| Defesa de ponto | `PointDefenseTurret` | Sim |
| Feixe trator | `TractorBeamTurret` | Sim |
| Reparar | `RepairTurret` | Sim, com outra base |
| Construir | `BuildTurret` | Sim, com outra base |

### 3.3 As cinco famílias de torre

1. **Torres de item** — a maioria. Disparam projéteis e trocam de comportamento conforme a munição. Ex.: Duo, Scatter, Hail, Ripple, Spectre.
2. **Torres de energia** — não usam munição; usam eletricidade. Ex.: Lancer, Arc, Afflict, Malign.
3. **Torres de líquido** — usam líquido como munição ou como refrigeração. Ex.: Wave, Tsunami, Sublimate.
4. **Torres contínuas** — feixes laser, sem projétil. Ex.: Meltdown, Lustre.
5. **Torres de utilidade** — não causam dano direto: defesa de ponto, trator, reparo e construção.

### 3.4 O que o Orbital usa hoje

As torres do Orbital são conteúdo **dormente**. Elas existem em `Blocks.java`, têm sprites, receitas e estatísticas, mas não aparecem nos geradores de Quatia ou Gier. São resquício do catálogo herdado, como as unidades terrestres e navais.

Isso é uma vantagem: **não há economia de base para proteger**. Podemos reposicionar as torres como equipamento sem quebrar mapas, campanhas ou balanceamento de defesa existente.

### 3.5 Resumo numérico

| Família | Quantidade | Tamanhos |
|---|---|---|
| Item | 17 | 1x1 a 5x5 |
| Energia | 4 | 1x1 a 9x9 |
| Líquido | 3 | 2x2 a 3x3 |
| Contínua | 2 | 4x4 |
| Defesa de ponto | 1 | 2x2 |
| Trator | 1 | 2x2 |
| Reparo | 2 | 1x1 e 2x2 |
| Construção | 1 | 3x3 |
| **Total** | **31** | — |

*(A soma inclui reparo e construção, que não são `Turret` puras, mas compartilham a fantasia de "torre".)*

---

## 4. Catálogo por tamanho

A tabela abaixo é a fonte para a regra de encaixe. O tamanho em tiles define a pegada em pixels: **1 tile = 32 px**.

### 4.1 Torres 1x1 — pegada 32 px

| Torre | Classe | Alcance | Recarga | Alvos | Observação |
|---|---|---:|---:|---|---|
| **Duo** | Item | 160 | 20 | Ar e terra | Dois canos, munição básica. Caso de teste. |
| **Scorch** | Item | 60 | 6 | Terra | Curto alcance, chama. Precisa de releitura anti-capital. |
| **Hail** | Item | 235 | 60 | Terra | Artilharia leve. Boa candidata a anti-capital. |
| **Arc** | Energia | 90 | 35 | Terra | Raio em cadeia. Precisa de bateria. |
| **Repair Point** | Reparo | raio 60 | — | Aliados | Reparo lento de proximidade, consome energia. |

### 4.2 Torres 2x2 — pegada 64 px

| Torre | Classe | Alcance | Recarga | Alvos | Observação |
|---|---|---:|---:|---|---|
| **Scatter** | Item | 220 | 18 | Ar | Flak. Natural para um jogo aéreo. |
| **Wave** | Líquido | 110 | 3 | Ar e terra | Congela e empurra. Precisa de tanque. |
| **Lancer** | Energia | 165 | 80 | Terra | Laser preciso. Precisa de bateria. |
| **Parallax** | Trator | 300 | — | Ar | Puxa alvos. Ótimo módulo utilitário. |
| **Swarmer** | Item | 240 | ~34 | Ar | Mísseis teleguiados. |
| **Salvo** | Item | 190 | 29 | Ar e terra | Rajada de projéteis. |
| **Segment** | Defesa de ponto | 180 | 8 | Projéteis | Abate mísseis inimigos. |
| **Repair Turret** | Reparo | raio 145 | — | Aliados | Reparo de longo alcance, consome energia. |

### 4.3 Torres 3x3 — pegada 96 px

| Torre | Classe | Alcance | Recarga | Alvos | Observação |
|---|---|---:|---:|---|---|
| **Tsunami** | Líquido | 190 | 3 | Ar e terra | Onda de líquido. Precisa de tanque. |
| **Fuse** | Item | 90 | 35 | Ar e terra | Espingarda de curto alcance. |
| **Ripple** | Item | 290 | 120 | Terra | Artilharia de área. |
| **Cyclone** | Item | 200 | 10 | Ar | Flak pesado. |
| **Breach** | Item | 190 | 40 | Ar e terra | Perfurante de linha. |
| **Diffuse** | Item | 125 | 30 | Ar e terra | Rajada em cone. |
| **Sublimate** | Líquido/contínuo | 130 | — | Ar e terra | Feixe químico. |
| **Build Tower** | Construção | 6 | 200 | — | Constrói e repara. |

### 4.4 Torres 4x4 — pegada 128 px

| Torre | Classe | Alcance | Recarga | Alvos | Observação |
|---|---|---:|---:|---|---|
| **Foreshadow** | Item | 500 | 200 | Ar e terra | Railgun. Consome energia e coolant. |
| **Spectre** | Item | 260 | 7 | Ar e terra | Metralhadora pesada. |
| **Meltdown** | Laser | 195 | 90 | Ar e terra | Feixe contínuo perfurante. |
| **Titan** | Item | 390 | ~138 | Terra | Artilharia pesada. |
| **Disperse** | Item | 310 | 9 | Ar | Fragmentos antiaéreos. |
| **Afflict** | Energia | 368 | 50 | Ar e terra | Rajada de energia. |
| **Lustre** | Contínuo | 250 | — | Ar e terra | Feixe em área. |
| **Scathe** | Item | 1350 | 600 | Terra | Canhão de cerco. Número de base, inviável em unidade. |

### 4.5 Torres 5x5 e 9x9

| Torre | Classe | Pegada | Alcance | Recarga | Observação |
|---|---|---:|---:|---:|---|
| **Smite** | Item | 160 px | 300 | 100 | Arma de capital, 5x5. |
| **Malign** | Energia | 288 px | 410 | 3,5 | Fortaleza 9x9. **Não é módulo.** |

### 4.6 Leitura do catálogo

- **1x1 e 2x2 são o kit de módulos.** São pequenas, legíveis e cabem em quase todo casco.
- **3x3 é equipamento médio.** Já exige casco grande; é onde entram as torres de área.
- **4x4 é arma de capital.** Só faz sentido em naves de 36 px de hitbox para cima.
- **5x5 é o limite.** Smite só cabe em Eclipse e Oct, por orçamento.
- **9x9 não é módulo.** Malign deve continuar sendo fortaleza de mapa, chefe ou peça de cenário.

---

## 5. A regra de encaixe

### 5.1 O problema

"Dá para montar?" não pode ser respondido no olho. Precisa de uma regra que o designer consiga aplicar em segundos, que o programador consiga transformar em dados e que o jogador consiga entender na tela.

A regra precisa responder a três perguntas:

1. **A torre cabe fisicamente?** — pegada menor que a área dorsal.
2. **A torre apaga a unidade?** — cobertura menor que metade da silhueta.
3. **A unidade continua reconhecível?** — nariz, motores e asas visíveis.

### 5.2 As medidas

| Símbolo | Nome | Como calcular |
|---|---|---|
| `P` | Pegada | `size × 32 px` |
| `A` | Área da unidade | `largura × altura` do sprite |
| `C` | Cobertura | `P² ÷ A` |
| `L` | Largura relativa | `P ÷ maior(largura, altura)` |

### 5.3 Os limites

| Critério | Limite | Por quê |
|---|---:|---|
| Cobertura `C` | **≤ 50%** | Acima disso a torre domina a silhueta; a unidade vira "suporte de torre". |
| Largura `L` | **≤ 70%** | Acima disso a torre atravessa o casco de ponta a ponta e esconde nariz ou motores. |
| Visibilidade | **Obrigatória** | Nariz/cabine, motores e asas precisam continuar visíveis. |
| Classe | **Compatível** | Casco leve aceita 1x1; capital aceita até 4x4 ou 5x5. |

### 5.4 Como aplicar

1. Meça o sprite da unidade (largura × altura).
2. Calcule `C` e `L` para o tamanho da torre.
3. Se `C > 50%` ou `L > 70%`, **reprove**.
4. Se passar, faça a checagem visual: nariz, motores e asas.
5. Se a checagem visual falhar, **reprove mesmo com números bons**.

### 5.5 Por que 50% e 70%

- **50% de área** é o ponto em que a torre ainda é um acessório. Acima disso, a leitura dominante passa a ser a torre.
- **70% de largura** impede o caso clássico de "torre maior que o casco", em que a unidade some embaixo.
- Os dois limites juntos são conservadores de propósito. É mais fácil afrouxar depois do que explicar por que a nave sumiu.

### 5.6 O que a regra não resolve

- **Formato do casco.** Um sprite 100x100 pode ser uma asa fina ou um bloco cheio. A área não distingue.
- **Posição do encaixe.** A regra não diz *onde* a torre fica; só diz se cabe.
- **Sobreposição de asas.** Precisa de checagem visual.
- **Escala de arte.** Um sprite grande pode ter detalhe pequeno; a torre pode parecer enorme mesmo dentro do limite.

Por isso a regra é um **filtro**, não uma aprovação automática. Ela reduz o espaço de teste; a arte decide o resto.

---

## 6. Tabela de compatibilidade medida

Os valores abaixo foram calculados a partir dos sprites reais do projeto. Cada célula mostra **cobertura/largura** e `OK` quando a combinação passa nos dois limites.

### 6.1 Unidades aéreas

*(Inclui os cascos de combate, apoio e o núcleo legado, porque todos são candidatos a receber torres.)*

| Unidade | Sprite | 1x1 (32) | 2x2 (64) | 3x3 (96) | 4x4 (128) | 5x5 (160) |
|---|---|---:|---:|---:|---:|---:|
| **Mono** | 48x48 | 44%/67% **OK** | 178%/133% | 400%/200% | 711%/267% | 1111%/333% |
| **Flare** | 48x48 | 44%/67% **OK** | 178%/133% | 400%/200% | 711%/267% | 1111%/333% |
| **Poly** | 56x57 | 32%/56% **OK** | 128%/112% | 289%/168% | 513%/225% | 802%/281% |
| **Alpha** | 48x48 | 44%/67% **OK** | 178%/133% | 400%/200% | 711%/267% | 1111%/333% |
| **Beta** | 56x54 | 34%/57% **OK** | 135%/114% | 305%/171% | 542%/229% | 847%/286% |
| **Gamma** | 68x68 | 22%/47% **OK** | 89%/94% | 199%/141% | 354%/188% | 554%/235% |
| **Evoke** | 56x54 | 34%/57% **OK** | 135%/114% | 305%/171% | 542%/229% | 847%/286% |
| **Incite** | 66x72 | 22%/44% **OK** | 86%/89% | 194%/133% | 345%/178% | 539%/222% |
| **Emanate** | 82x84 | 15%/38% **OK** | 59%/76% | 134%/114% | 238%/152% | 372%/190% |
| **Avert** | 96x96 | 11%/33% **OK** | 44%/67% **OK** | 100%/100% | 178%/133% | 278%/167% |
| **Horizon** | 72x72 | 20%/44% **OK** | 79%/89% | 178%/133% | 316%/178% | 494%/222% |
| **Mega** | 100x100 | 10%/32% **OK** | 41%/64% **OK** | 92%/96% | 164%/128% | 256%/160% |
| **Zenith** | 112x112 | 8%/29% **OK** | 33%/57% **OK** | 73%/86% | 131%/114% | 204%/143% |
| **Obviate** | 140x130 | 6%/23% **OK** | 23%/46% **OK** | 51%/69% | 90%/91% | 141%/114% |
| **Quad** | 220x220 | 2%/15% **OK** | 8%/29% **OK** | 19%/44% **OK** | 34%/58% **OK** | 53%/73% |
| **Quell** | 162x162 | 4%/20% **OK** | 16%/40% **OK** | 35%/59% **OK** | 62%/79% | 98%/99% |
| **Antumbra** | 216x240 | 2%/13% **OK** | 8%/27% **OK** | 18%/40% **OK** | 32%/53% **OK** | 49%/67% **OK** |
| **Disrupt** | 243x243 | 2%/13% **OK** | 7%/26% **OK** | 16%/40% **OK** | 28%/53% **OK** | 43%/66% **OK** |
| **Eclipse** | 320x321 | 1%/10% **OK** | 4%/20% **OK** | 9%/30% **OK** | 16%/40% **OK** | 25%/50% **OK** |
| **Oct** | 400x400 | 1%/8% **OK** | 3%/16% **OK** | 6%/24% **OK** | 10%/32% **OK** | 16%/40% **OK** |

### 6.2 Matriz resumida por tamanho

| Tamanho | Cabe em | Não cabe |
|---|---|---|
| **1x1 (32)** | Todas as 20 unidades aéreas | — |
| **2x2 (64)** | Avert, Mega, Zenith, Obviate, Quad, Quell, Antumbra, Disrupt, Eclipse, Oct | Mono, Flare, Poly, Alpha, Beta, Gamma, Evoke, Incite, Emanate, Horizon |
| **3x3 (96)** | Quad, Quell, Antumbra, Disrupt, Eclipse, Oct | As demais |
| **4x4 (128)** | Quad, Antumbra, Disrupt, Eclipse, Oct | As demais |
| **5x5 (160)** | Antumbra, Disrupt, Eclipse, Oct (Quad no limite) | As demais |
| **9x9 (288)** | Nenhuma | Todas |

### 6.3 O que a tabela revela

1. **1x1 é universal.** Duo, Scorch, Hail, Arc e as torres de reparo podem ser testadas em qualquer casco.
2. **2x2 separa as naves de apoio das de combate.** As unidades de mineração e escolta ficam de fora — o que é saudável.
3. **3x3 e 4x4 são de capital.** Só Quad para cima.
4. **5x5 é raridade.** Só as quatro maiores.
5. **O salto é grande.** Não há torre entre 160 px e 288 px; Malign fica sozinha e deve permanecer fora do sistema.
6. **Quad é o primeiro casco "versátil".** É a menor unidade que aceita 4x4 e quase aceita 5x5.
7. **Antumbra é o primeiro casco confortável.** Aceita 5x5 fisicamente (49%/67%), mas o orçamento de 0,70 impede Smite; na prática, o teto é 4x4.

### 6.4 Casos de fronteira

| Caso | Situação | Decisão sugerida |
|---|---|---|
| **Mono + Duo** | 44%/67% | Aprovado. Exemplo oficial. |
| **Alpha + Duo** | 44%/67% | Aprovado tecnicamente; Alpha já tem arma. |
| **Emanate + 2x2** | 59%/76% | Reprovado pela largura. Não forçar. |
| **Horizon + 2x2** | 79%/89% | Reprovado. Horizon é estreito. |
| **Obviate + 3x3** | 51%/69% | Reprovado por 1 ponto de cobertura. Reavaliar com arte. |
| **Quad + 5x5** | 53%/73% | Reprovado. No limite, mas passa dos dois. |
| **Quell + 4x4** | 62%/79% | Reprovado. Quell é largo, mas não o bastante. |
| **Malign** | 9x9 | Fora do sistema. |

---

## 7. O caso Duo + Mono

### 7.1 Por que esse caso

O Mono é o melhor primeiro teste possível:

- é a unidade mais básica do roster aéreo;
- **não tem arma** — então qualquer torre é ganho visível;
- tem sprite pequeno (48x48), o que torna a regra de cobertura dura;
- é um minerador, então a torre muda o papel sem virar nave de guerra;
- é barato, o que permite testar em quantidade.

O Duo é a menor torre de combate:

- 1x1, 32x32;
- dois canos, leitura clara;
- munição básica e barata;
- recarga rápida, alcance curto;
- já é a "primeira arma" na progressão herdada.

### 7.2 Os números

| Medida | Valor |
|---|---:|
| Sprite do Mono | 48 × 48 = 2.304 px² |
| Pegada do Duo | 32 × 32 = 1.024 px² |
| Cobertura | 1.024 ÷ 2.304 = **44%** |
| Largura relativa | 32 ÷ 48 = **67%** |
| Limite de cobertura | 50% ✔ |
| Limite de largura | 70% ✔ |

### 7.3 O que isso significa na prática

- A torre ocupa menos da metade da área visível.
- Ela cobre dois terços da largura — ou seja, sobra um terço do casco para ler nariz e motores.
- O Mono continua reconhecível: a cabine e a cauda não somem.
- A margem é curta: **6 pontos de cobertura e 3 de largura**. Pequenas mudanças de arte podem reprovar o caso. Isso é bom: obriga a validar cedo.

### 7.4 O que o Duo vira no Mono

O Duo de base é uma torre **anti-terra**. No espaço, isso não faz sentido. Três opções:

1. **Anti-interceptador:** vira defesa próxima contra unidades leves. É a opção mais simples.
2. **Anti-capital:** mantém o alcance curto, mas com dano concentrado em alvos grandes e lentos.
3. **Defesa de ponto barata:** não causa dano alto, mas abate mísseis.

Recomendação: **anti-interceptador** no MVP. É o que o minerador precisa para sobreviver a uma escolta barata.

### 7.5 O que o jogador sente

- "Meu minerador agora tem defesa."
- "Perdi velocidade porque carrego uma torre."
- "Preciso de munição para a torre, e ela compete com a carga que eu minero."
- "O Duo é pequeno, mas resolve."

Essas quatro frases são o sistema inteiro em miniatura.

---

## 8. Tipos de torre e o recurso que exigem

### 8.1 O problema dos recursos

Uma unidade não tem rede elétrica, tubulação, rede de calor nem inventário multi-item. Ela tem:

- vida;
- velocidade;
- uma carga de **um único item** (`ItemStack`);
- capacidade de carga (`itemCapacity`);
- nenhum recurso de energia, líquido ou calor.

Isso significa que **cada família de torre precisa de uma decisão de design** antes de virar módulo.

### 8.2 Matriz de recursos

| Família | Recurso hoje | Recurso na unidade | Dificuldade | Recomendação |
|---|---|---|---|---|
| **Item** | Itens no prédio | Magazine próprio ou carga | Média | MVP, com magazine dedicado |
| **Energia** | Rede elétrica | Bateria da unidade | Média | Fase 2 |
| **Líquido** | Tubulação | Tanque da unidade | Média | Fase 2 |
| **Calor** | Coolant/rede | Calor da torre | Alta | Simplificar para recarga |
| **Defesa de ponto** | Itens | Magazine | Baixa | MVP |
| **Trator** | Itens | Magazine | Baixa | Fase 2 |
| **Reparo** | Energia | Bateria da unidade | Baixa | MVP |
| **Construção** | Itens | Carga da unidade | Baixa | Fase 3 |

### 8.3 Torres de item

São a maioria e o melhor ponto de partida. O jogador escolhe a munição; a munição muda o comportamento (dano, alcance, área, cadência).

**Decisão necessária:** a munição da torre é a mesma carga que a unidade minera?

- **Se sim:** o minerador escolhe entre lucro e defesa. É uma decisão forte, mas pode frustrar.
- **Se não:** a torre tem um magazine próprio, separado da carga. É mais legível, mas exige nova UI e novo estado.

Recomendação: **magazine próprio no MVP**, com capacidade pequena. A carga continua sendo a carga.

### 8.4 Torres de energia

Lancer, Arc, Afflict e Malign não usam munição; usam eletricidade. Em uma unidade, isso vira **bateria**.

- A bateria recarrega com o tempo ou com itens condutores.
- A torre consome por disparo.
- Sem bateria, a torre não atira.

Isso cria um módulo de "energia" que também alimenta outras coisas. É uma boa base para o sistema de módulos do relatório anterior.

### 8.5 Torres de líquido

Wave, Tsunami e Sublimate precisam de líquido. Em uma unidade, isso vira **tanque**.

- O tanque tem capacidade pequena.
- O líquido pode ser recarregado por item ou por coleta.
- O efeito é forte: congelar, empurrar, corroer.

Recomendação: deixar para a fase 2. É a família com menos retorno para o esforço inicial.

### 8.6 Torres de calor

Foreshadow e outras torres usam calor e coolant. Unidades **não têm** sistema de calor; o `WeaponMount` tem apenas um valor visual de `heat`.

Opções:

1. **Ignorar calor** e usar recarga normal.
2. **Transformar calor em recarga:** quanto mais dispara, mais lento fica.
3. **Criar calor de unidade:** sistema novo, caro.

Recomendação: **opção 2**. É simples, legível e preserva a fantasia de arma pesada.

### 8.7 Torres de defesa de ponto e trator

Segment e Parallax são módulos utilitários, não armas de dano. Eles são ótimos para escolta e para o papel de "nave de apoio".

- **Segment:** abate mísseis. Essencial em um jogo com mísseis teleguiados.
- **Parallax:** puxa alvos. Perfeito para controle de espaço.

Ambos são baratos de implementar e mudam o jogo mais do que "+10% de dano".

### 8.8 Torres de reparo e construção

Repair Point e Repair Turret não são `Turret`; são `Block` e `RepairTurret`. Build Tower é `BuildTurret`. Elas não compartilham a mesma base.

Isso significa que **não entram no mesmo encaixe de arma** sem trabalho extra. Recomendação:

- tratar reparo como **módulo de suporte** (slot separado);
- tratar construção como **módulo de utilidade** (slot separado);
- não misturar com torres de combate no MVP.

---

## 9. Implicações de gameplay

### 9.1 Slots por classe de casco

| Classe | Exemplos | Slots típicos | Tamanho máximo |
|---|---|---|---|
| **Leve** | Mono, Flare, Poly, Alpha | 1 | 1x1 |
| **Média** | Horizon, Mega, Zenith, Avert | 1 | 2x2 |
| **Pesada** | Obviate, Quell, Quad | 1 a 2 | 4x4 |
| **Capital** | Antumbra, Disrupt, Eclipse, Oct | 2 a 5 | 5x5 |

Observação: a contagem fixa é só leitura. A regra real é o **orçamento dorsal** da seção 16.1, com multiplicador por unidade na seção 19.7. A tabela de compatibilidade diz o que *cabe*; o orçamento diz o que *deve* caber.

### 9.2 Custo e contrapartidas

Toda torre precisa custar algo além do slot:

| Contrapartida | Efeito |
|---|---|
| **Peso** | Reduz velocidade e aceleração |
| **Munição** | Consome carga ou magazine |
| **Energia** | Consome bateria |
| **Calor** | Reduz cadência com uso prolongado |
| **Assinatura** | Aumenta alcance de detecção inimiga |
| **Custo de produção** | Aumenta o preço da unidade |

Recomendação: **peso + munição** no MVP. São as duas contrapartidas mais legíveis.

### 9.3 Munição e economia

Se a torre usa magazine próprio:

- o jogador precisa reabastecer entre combates;
- o magazine tem capacidade pequena;
- a munição pode ser trocada na preparação.

Se a torre usa a carga da unidade:

- o minerador escolhe entre minerar e atirar;
- a decisão é mais tensa, mas menos legível;
- pode gerar frustração em combate.

Recomendação: **magazine próprio**, com opção de reabastecer pela carga em caso de emergência.

### 9.4 Alvos

O jogo é aéreo. Torres com `targetAir = false` precisam de uma destas saídas:

1. **Reinterpretar como anti-capital:** lentas, dano alto, alcance longo.
2. **Reinterpretar como anti-estrutura:** atacam bases e naves grandes.
3. **Desativar no encaixe dorsal:** continuam existindo como defesa de mapa.

Recomendação: **opção 1** para Hail, Ripple, Titan e Scathe; **opção 2** para Scorch; **opção 3** para as demais.

### 9.5 Escala de alcance

As torres de base têm alcances pensados para mapas:

- Scathe: 1350;
- Foreshadow: 500;
- Titan: 390;
- Malign: 410.

Em uma unidade móvel, esses números quebram o combate. Recomendação:

- **clamp de alcance** ao montar: no máximo 300 para 2x2, 350 para 3x3, 400 para 4x4/5x5;
- **multiplicador de recarga** para armas muito fortes;
- **redução de dano** em alvos móveis.

### 9.6 Legibilidade

Uma torre dorsal precisa ser lida em três estados:

1. **Parada:** base, cano e silhueta da unidade visíveis.
2. **Mirando:** rotação independente, sem esconder o nariz.
3. **Atirando:** recuo, fogo e calor visíveis, sem cobrir os efeitos da unidade.

Regras de arte:

- a torre deve ter contraste próprio;
- o cano não deve apontar para dentro do casco;
- o recuo não deve invadir a cauda;
- a torre não deve usar a mesma cor dominante do casco.

---

## 10. Como o jogador vê isso

### 10.1 Tela de preparação

Fluxo sugerido:

1. O jogador escolhe o casco.
2. A tela mostra a **vista dorsal** da unidade com os slots.
3. As **armas nativas aparecem como slots bloqueados**, com cadeado e a marca "nativa". Elas não podem ser removidas nem trocadas.
4. O jogador arrasta uma torre para um slot livre.
5. O jogo mostra a **cobertura em tempo real**:
   - verde até 40%;
   - amarelo de 40% a 50%;
   - vermelho acima de 50%.
6. Se passar, o botão "Montar" libera.
7. A configuração é salva por unidade.

A cobertura em tempo real é o que transforma a regra em ferramenta de design visível. O jogador aprende a regra sem ler o manual.

### 10.2 Durante o combate

- **Base da torre:** desenhada sob o casco.
- **Cano:** desenhado sobre o casco, com rotação independente.
- **Munição:** barra fina ao lado da torre, não sobre o casco.
- **Calor:** brilho no cano.
- **Recarga:** pequeno anel ou pulso.

### 10.3 HUD da unidade selecionada

Quando o jogador seleciona uma unidade, a **parte inferior da tela** deve mostrar os **ícones das torretas instaladas naquela unidade**.

Regras:

1. A faixa de ícones aparece **somente com a unidade selecionada**.
2. Cada torre instalada ocupa um ícone, na ordem dos slots dorsais.
3. A **arma nativa** aparece com cadeado e a marca "nativa", igual à tela de preparação.
4. A **torre removível** aparece com borda destacada e pode ser selecionada para ver detalhes.
5. O ícone mostra o estado da torre:
   - recarga;
   - munição ou magazine;
   - calor;
   - nível Mk.
6. Se a unidade não tiver torre, a faixa é omitida ou mostra "sem torre".
7. Em telas pequenas, os ícones ficam acima da barra de informações da unidade, sem cobrir o controle de movimento.
8. Tocar/clicar no ícone destaca a torre correspondente no casco.
9. A faixa é somente leitura durante o combate; troca e remoção continuam na preparação.

**Entrega atual:** a faixa já mostra recarga e calor por torre. Munição por torre e nível Mk aparecem quando esses sistemas entrarem no MVP.

**Objetivo:** o jogador entende o armamento da unidade sem abrir uma janela e sem decorar a silhueta.

### 10.4 Feedback de erro

Quando a torre não cabe:

- o slot fica vermelho;
- a torre mostra o contorno do tamanho;
- uma dica curta explica: "Cobertura acima de 50%" ou "Largura acima de 70%".

### 10.5 Progressão

- **Blueprints de torre** desbloqueiam a peça.
- **Blueprints de casco** desbloqueiam slots.
- **Munições** desbloqueiam comportamentos.
- **Melhorias de torre** aumentam cadência, alcance ou capacidade.

---

## 11. Viabilidade técnica

> Esta seção é um resumo para produto. O detalhamento está no anexo.

### 11.1 O que existe hoje

| Peça | O que faz | Serve para torre dorsal? |
|---|---|---|
| `Turret` / `TurretBuild` | Torre de bloco, com mira, munição, calor | Como referência de lógica, não como base direta |
| `Weapon` | Arma de unidade | Base do adaptador |
| `WeaponMount` | Estado de arma de unidade | Base do estado da torre |
| `WeaponsComp` | Cria mounts fixos a partir de `UnitType.weapons` | Precisa de mudança para mounts dinâmicos |
| `UnitType.weapons` | Lista fixa de armas | Precisa de slots |
| `ItemStack` | Carga única da unidade | Não serve como magazine multi-munição |
| `BlockUnitc` | Unidade interna da torre | Pista de reuso, não de montagem |

### 11.2 As três opções

| Opção | Como funciona | Vantagens | Desvantagens |
|---|---|---|---|
| **A. Adaptador de arma** | Cada torre vira um `Weapon` especial | Rápido, usa render e mira existentes | Perde mapa de munição e calor de torre |
| **B. Encaixe de torre** | Unidade ganha estado de torre reutilizando `TurretBuild` | Fidelidade total | Exige refatorar `TurretBuild`, que depende de prédio e rede |
| **C. Variantes de unidade** | Gerar `UnitType` com armas fixas para cada loadout | Zero mudança estrutural | Explosão combinatória |

### 11.3 Recomendação

**Fase 1 — Opção C + A.** Criar variantes de unidade com um adaptador de arma por torre. É a prova mais barata de Duo + Mono e não exige mudança no motor.

**Fase 2 — Opção B leve.** Introduzir `TurretMount` na unidade, com estado próprio, reutilizando a lógica de mira e recarga, mas sem depender de rede elétrica ou tile.

**Fase 3 — consolidação.** Migrar o adaptador para o encaixe real e remover as variantes.

### 11.4 O que não usar

- **`PayloadAmmoTurret`:** está marcada como "Do not use this class!" e não tem visuais. Não serve como base.
- **`RepairTurret` e `BuildTurret`:** não estendem `Turret`; exigem tratamento separado.
- **`Malign`:** 9x9 não cabe em nenhuma unidade. Não é módulo.

### 11.5 O que precisa nascer

1. `UnitType.turretSlots` — quantidade e tamanho máximo.
2. `TurretMountSpec` — torre + posição + munição.
3. Estado de torre por unidade — recarga, calor, alvo, munição.
4. Magazine ou bateria — conforme a família.
5. Persistência da configuração.
6. Sincronização de rede.
7. UI de preparação com slots nativos bloqueados.
8. `loadoutBudgetMultiplier` por unidade.

---

## 12. Riscos

### 12.1 Riscos de design

| Risco | Impacto | Mitigação |
|---|---|---|
| Torres apagarem a identidade da unidade | Alto | Regra de cobertura + limite de slots |
| Combinações invencíveis | Alto | Orçamento de 50% + multiplicador por unidade + contrapartidas |
| Torres de base desbalancearem o combate móvel | Alto | Clamp de alcance, recarga e dano |
| Falta de decisão real | Médio | Munição, peso e calor precisam doer |
| Excesso de torres no catálogo | Médio | Kit inicial de 6, não 31 |

### 12.2 Riscos técnicos

| Risco | Impacto | Mitigação |
|---|---|---|
| `WeaponsComp` cria mounts fixos | Alto | Variantes no MVP; `TurretMount` na fase 2 |
| `ItemStack` único | Alto | Magazine próprio |
| `TurretBuild` depende de prédio/rede | Alto | Refatorar para controlador headless |
| Sprite de torre em escala de bloco | Médio | Validar 1:1 com a arte da unidade |
| Sincronização de mounts | Médio | `SyncLocal` já existe para mounts |
| Persistência de loadout | Médio | Formato próprio por unidade |

### 12.3 Riscos de produção

| Risco | Impacto | Mitigação |
|---|---|---|
| 31 torres × 20 unidades = 620 combinações | Alto | Curadoria por classe |
| Arte de montagem para cada torre | Médio | Reuso do sprite top-down |
| UI de preparação | Médio | Protótipo simples primeiro |
| Balanceamento contínuo | Alto | Kit pequeno e telemetria |

### 12.4 O risco maior

O risco maior não é técnico. É **transformar toda unidade em plataforma de torre**. Se qualquer casco puder carregar qualquer torre, o jogo perde silhueta, papel e decisão. A regra de cobertura existe justamente para impedir isso.

---

## 13. Roadmap recomendado

### Fase 0 — Prova visual (1 a 2 semanas)

- Arte mockada de Duo no Mono.
- Checagem de cobertura e leitura.
- Uma variante de unidade com o Duo como arma adaptada.
- Teste de combate: minerador contra interceptadores.

**Critério de sucesso:** o Duo parece parte do Mono, não um adesivo.

### Fase 1 — Regra e dados (2 a 3 semanas)

- Tabela de compatibilidade como dados.
- Slots por classe de casco.
- Ferramenta interna de checagem de cobertura.
- Documentação para arte.

**Critério de sucesso:** qualquer pessoa consegue dizer se uma torre cabe em menos de um minuto.

### Fase 2 — Kit inicial (3 a 4 semanas)

Seis torres:

1. **Duo** — dano básico.
2. **Scatter** — antiaéreo.
3. **Segment** — defesa de ponto.
4. **Hail** — anti-capital.
5. **Repair Point** — suporte.
6. **Parallax** — controle.

O MVP cobre **todas as 20 unidades aéreas**, não um recorte de cascos. O recorte é de torres: seis no início.

**Critério de sucesso:** seis papéis distintos, sem sobreposição, disponíveis em todos os cascos.

### Fase 3 — Preparação e persistência (3 a 4 semanas)

- Tela de montagem.
- Cobertura em tempo real.
- Salvamento por unidade.
- Reabastecimento de magazine.

**Critério de sucesso:** o jogador monta, salva, entra em combate e a configuração persiste.

### Fase 4 — Recursos (4 a 6 semanas)

- Bateria para torres de energia.
- Tanque para torres de líquido.
- Calor simplificado.
- Custo de peso.

**Critério de sucesso:** cada família tem uma decisão própria.

### Fase 5 — Expansão (contínua)

- Torres 3x3 e 4x4 para capitais.
- Smite como arma de capital.
- Malign permanece como fortaleza.
- Curadoria contínua.

### O que não fazer primeiro

- Não implementar as 31 torres.
- Não criar calor de unidade completo.
- Não fazer UI definitiva antes da prova visual.
- Não mexer em `TurretBuild` antes de saber se o adaptador resolve.
- Não colocar torre em unidade sem arma antes de validar o Duo no Mono.

---

## 14. Perguntas abertas

1. **Torre substitui ou soma?** A unidade mantém a arma de nariz e ganha a torre, ou a torre ocupa o slot da arma?
2. **Quantas torres por casco?** Uma por classe, ou slots múltiplos?
3. **A torre pode ser destruída separadamente?** Se sim, vira subsistema de dano.
4. **A munição é magazine ou carga?** A decisão muda a economia inteira.
5. **Torres antiaéreas viram o padrão?** Ou mantemos armas anti-capital?
6. **Qual o destino de Malign?** Chefe, fortaleza ou corte?
7. **A torre gira 360°?** Ou tem arco limitado, como uma arma de casco?
8. **A torre conta para o custo da unidade?** Como o jogador paga por ela?
9. **Como o jogador troca de torre em campo?** Só na preparação ou em estação?
10. **Torres de reparo e construção entram no sistema?** Ou ficam em slots separados?
11. **Qual o limite de alcance em unidade?** 300? 400? Depende do tamanho?
12. **Como comunicar cobertura sem números?** Barra, cor, contorno ou todas?

---

## 15. Anexo técnico — onde a ideia toca o código

### 15.1 Mapa de arquivos

| Arquivo | Papel |
|---|---|
| `core/src/orbital/content/Blocks.java` | Definições das 31 torres, a partir da linha ~3276 |
| `core/src/orbital/world/blocks/defense/turrets/Turret.java` | Mira, munição, recuo, calor, `BlockUnitc` |
| `core/src/orbital/world/blocks/defense/turrets/BaseTurret.java` | Base de torre, alcance, overlap, coolant |
| `core/src/orbital/world/blocks/defense/turrets/ReloadTurret.java` | Recarga e resfriamento |
| `core/src/orbital/world/blocks/defense/turrets/ItemTurret.java` | Mapa de munição por item |
| `core/src/orbital/world/blocks/defense/turrets/PowerTurret.java` | Consumo de energia |
| `core/src/orbital/world/blocks/defense/turrets/LiquidTurret.java` | Consumo de líquido |
| `core/src/orbital/world/blocks/defense/turrets/ContinuousTurret.java` | Feixes contínuos |
| `core/src/orbital/world/blocks/defense/turrets/PointDefenseTurret.java` | Defesa de ponto |
| `core/src/orbital/world/blocks/defense/turrets/TractorBeamTurret.java` | Feixe trator |
| `core/src/orbital/world/blocks/defense/turrets/PayloadAmmoTurret.java` | Não usar |
| `core/src/orbital/world/blocks/units/RepairTurret.java` | Reparo, estende `Block` |
| `core/src/orbital/world/blocks/defense/BuildTurret.java` | Construção, estende `BaseTurret` |
| `core/src/orbital/type/Weapon.java` | Arma de unidade, `mountType`, `x/y`, mira |
| `core/src/orbital/type/TurretSlot.java` | Encaixe dorsal e constante de escala em pixels |
| `core/src/orbital/type/TurretWeapon.java` | Adaptador de torre de bloco para arma de unidade |
| `core/src/orbital/content/TurretLoadouts.java` | Multiplicadores, áreas e slots das 20 unidades aéreas |
| `core/src/orbital/entities/units/WeaponMount.java` | Estado de recarga, calor, alvo |
| `core/src/orbital/entities/comp/WeaponsComp.java` | Criação de mounts e montagem/remoção em runtime |
| `core/src/orbital/type/UnitType.java` | `hitSize`, `itemCapacity`, `turretSlots`, orçamento dorsal |
| `core/src/orbital/content/UnitTypes.java` | Unidades aéreas, stats e variante `mono-duo` |
| `core/src/orbital/ui/fragments/HudFragment.java` | HUD; destino da faixa inferior com ícones das torres |

### 15.2 Constatações de código

1. **Não há montagem dorsal nativa.** Nenhum campo de `UnitType` referencia torre, bloco ou slot.
2. **`WeaponsComp.setupWeapons` cria mounts uma única vez**, a partir de `def.weapons`. Adicionar torre em runtime exige recriar mounts ou variantes.
3. **`TurretBuild` cria `BlockUnitc unit`**, mas isso serve para controle por lógica, não para montagem.
4. **`Weapon` já suporta `x`, `y`, `rotate`, `shootX`, `shootY`, `parts`**, o suficiente para posicionar uma torre no casco.
5. **`WeaponMount` já tem `reload`, `heat`, `rotation`, `targetRotation`, `aimX/aimY`**, o suficiente para o estado da torre.
6. **`ItemTurret` usa `Seq<AmmoEntry>` e `totalAmmo`**, não `ItemStack`. A unidade não tem magazine equivalente.
7. **`UnitComp` guarda um único `ItemStack`**, com `itemCapacity`.
8. **`UnitType` não tem energia, líquido ou calor de unidade.**
9. **`RepairTurret` estende `Block`** e não compartilha `TurretBuild`.
10. **`BuildTurret` estende `BaseTurret`** e não `Turret`.
11. **`Malign` é 9x9 (288 px)** e não cabe em nenhuma unidade.
12. **As torres não são usadas pelos geradores atuais**, então são conteúdo livre para reposicionamento.

### 15.3 Fórmulas

```text
P = size * 32
A = spriteWidth * spriteHeight
C = (P * P) / A
L = P / max(spriteWidth, spriteHeight)

aprovado = (C <= 0.50) && (L <= 0.70) && visibilidadeOk
```

### 15.4 Dados medidos

```text
Duo        32x32     Scatter    64x64     Lancer     64x64
Ripple     96x96     Spectre   128x128    Titan     128x128
Smite     160x160    Malign    288x288 (main 160x160)

Mono       48x48     Flare      48x48     Poly       56x57
Alpha      48x48     Beta       56x54     Gamma      68x68
Evoke      56x54     Incite     66x72     Emanate    82x84
Avert      96x96     Horizon    72x72     Mega      100x100
Zenith    112x112    Obviate   140x130    Quad      220x220
Quell     162x162    Antumbra  216x240    Disrupt   243x243
Eclipse   320x321    Oct       400x400
```

### 15.5 Esboço de modelo de dados

```java
// proposta, não implementação
class TurretSlot {
    int maxSize;          // 1..5
    float offsetX, offsetY; // posição dorsal
    boolean mirrored;     // espelhar em cascos simétricos
}

class MountedTurret {
    Turret block;         // torre montada
    int ammo;             // magazine
    float reload, heat;   // estado
    float aimX, aimY;     // alvo
}

// UnitType
Seq<TurretSlot> turretSlots;
float turretWeight;       // penalidade de velocidade
```

### 15.6 Checklist de implementação

- [x] Definir `UnitType.turretSlots`.
- [x] Definir dados de pegada por torre.
- [x] Implementar cálculo de cobertura.
- [x] Implementar validação de montagem.
- [x] Criar adaptador `Weapon` para torre (MVP).
- [x] Criar variante de unidade com Duo.
- [ ] Validar Duo + Mono em combate.
- [ ] Criar magazine de torre.
- [ ] Criar UI de preparação.
- [x] Criar faixa inferior com ícones das torres da unidade selecionada.
- [ ] Persistir loadout.
- [ ] Sincronizar mounts.
- [ ] Rebalancear alcance e recarga.
- [ ] Reinterpretar `targetAir = false`.
- [ ] Separar reparo/construção em slots próprios.
- [ ] Definir destino de Malign.
- [ ] Documentar regra para a equipe de arte.

### 15.7 Pontos de atenção

- **Não usar `PayloadAmmoTurret`.** A própria classe avisa.
- **Não reaproveitar `TurretBuild` diretamente** sem separar a dependência de `Building`, tile e rede.
- **Não assumir que `BlockUnitc` resolve montagem.** Ele resolve controle.
- **Não esquecer o desenho da base.** Uma torre sem base parece um adesivo.
- **Não ignorar a sombra.** Torres de bloco desenham sombra de tile; em unidade, a sombra precisa seguir o casco.
- **Não usar escala de bloco sem teste.** Validar 1:1 com a arte da unidade.

---

## 16. Plano de implantação por unidade e nível

Esta seção responde à pergunta prática: **como as torres entram nas outras unidades aéreas e nos níveis mais altos?**

A resposta tem duas partes:

1. **Uma regra de orçamento dorsal**, que substitui a contagem fixa de slots por uma medida de área.
2. **Um plano por unidade**, com as torres recomendadas e o papel que cada combinação cria.

### 16.1 Orçamento dorsal em vez de slots fixos

A contagem de slots da seção 9.1 é uma aproximação. Em níveis mais altos, ela não funciona: um casco capital pode carregar uma torre 5x5 ou três torres menores, e as duas opções são válidas.

A regra proposta é um **orçamento de área**:

```text
Orçamento dorsal = 50% da área do sprite × multiplicador da unidade
Custo da torre   = P²
Soma dos custos  ≤ orçamento dorsal
```

O multiplicador (`loadoutBudgetMultiplier`) é a válvula de balanceamento por casco. O padrão é 1,0; uma capital muito forte pode cair para 0,8. A arma nativa fixa não entra nessa conta.

Ou seja: a **soma das coberturas de todas as torres não pode passar de 50%** da área da unidade. Cada torre individual continua limitada a 50% e 70% de largura.

| Unidade | Área | Mult. | Orçamento efetivo | Combinações possíveis |
|---|---:|---:|---:|---|
| Mono | 2.304 | 1,00 | 1.152 | 1×1x1 |
| Flare | 2.304 | 1,00 | 1.152 | 1×1x1 |
| Poly | 3.192 | 1,00 | 1.596 | 1×1x1 |
| Alpha | 2.304 | 1,00 | 1.152 | 1×1x1 |
| Beta | 3.024 | 1,00 | 1.512 | 1×1x1 |
| Gamma | 4.624 | 1,00 | 2.312 | 2×1x1 |
| Evoke | 3.024 | 1,00 | 1.512 | 1×1x1 |
| Incite | 4.752 | 1,00 | 2.376 | 2×1x1 |
| Emanate | 6.888 | 1,00 | 3.444 | 2×1x1 (ou 3×1x1) |
| Horizon | 5.184 | 1,00 | 2.592 | 1×1x1 |
| Avert | 9.216 | 0,90 | 4.147 | 1×2x2 |
| Mega | 10.000 | 1,00 | 5.000 | 1×2x2 |
| Zenith | 12.544 | 0,90 | 5.644 | 1×2x2 + 1×1x1 |
| Obviate | 18.200 | 0,95 | 8.645 | 2×2x2 |
| Quad | 48.400 | 0,85 | 20.570 | 1×4x4 + 1×2x2 |
| Quell | 26.244 | 0,80 | 10.497 | 1×3x3 + 1×1x1 |
| Antumbra | 51.840 | 0,70 | 18.144 | 1×4x4 + 1×1x1 ou 1×3x3 + 2×2x2 |
| Disrupt | 59.049 | 0,75 | 22.143 | 1×4x4 + 1×2x2 |
| Eclipse | 102.720 | 0,50 | 25.680 | 1×5x5 ou 1×4x4 + 1×3x3 ou 2×3x3 + 1×2x2 |
| Oct | 160.000 | 0,85 | 68.000 | 1×5x5 + 2×4x4 + 2×2x2 |

**Vantagens:**

- funciona em qualquer casco, sem tabela de slots por classe;
- permite builds diferentes no mesmo casco;
- é fácil de mostrar na UI: uma barra de orçamento que enche;
- impede a combinação que cobre a unidade inteira.

**Limite:** o orçamento não impede sobreposição entre torres. A posição dos encaixes precisa ser definida com arte.

### 16.2 Níveis de casco

| Nível | Nome | Unidades | Orçamento | Perfil |
|---|---|---|---|---|
| **N0** | Bootstrap | Mono, Flare, Poly | 1x1 | Primeiro contato, tutorial |
| **N1** | Enxame | Alpha, Beta, Gamma, Evoke, Incite, Emanate | 1x1 a 2×1x1 | Barato, muitas unidades |
| **N2** | Escolta | Horizon, Avert, Mega, Zenith | 1x1 a 2x2 | Primeiras torres médias |
| **N3** | Linha | Obviate, Quell, Quad | 2x2 a 4x4 | Combate sustentado |
| **N4** | Capital | Antumbra, Disrupt, Eclipse, Oct | 4x4 a 5x5 + múltiplas | Endgame |

### 16.3 Plano por unidade

#### N0 — Bootstrap

| Unidade | Slots | Torres recomendadas | Papel com torre |
|---|---|---|---|
| **Mono** | 1×1x1 | Duo, Hail, Repair Point | Minerador que se defende |
| **Flare** | 1×1x1 | Duo, Arc, Hail | Interceptador com segundo cano |
| **Poly** | 1×1x1 | Duo, Repair Point | Construtor armado |

#### N1 — Enxame

| Unidade | Slots | Torres recomendadas | Papel com torre |
|---|---|---|---|
| **Alpha** | 1×1x1 | Duo, Arc | Enxame de pressão |
| **Beta** | 1×1x1 | Duo, Hail | Enxame com dano concentrado |
| **Gamma** | 2×1x1 | Duo + Duo, Duo + Arc | Enxame pesado |
| **Evoke** | 1×1x1 | Duo, Repair Point | Logística de linha de frente |
| **Incite** | 2×1x1 | Duo + Duo, Duo + Arc | Escolta barata |
| **Emanate** | 2×1x1 | Duo + Duo, Repair Point + Duo | Suporte de esquadrão |

#### N2 — Escolta

| Unidade | Slots | Torres recomendadas | Papel com torre |
|---|---|---|---|
| **Horizon** | 1×1x1 | Duo, Hail, Repair Point | Bombardeiro leve |
| **Avert** | 1×2x2 | Scatter, Segment, Salvo, Lancer | Caça antiaéreo |
| **Mega** | 1×2x2 | Segment, Scatter | Suporte de frota |
| **Zenith** | 1×2x2 + 2×1x1 | Swarmer + Duo, Scatter + Duo, Salvo + Duo | Bombardeiro de linha |

#### N3 — Linha

| Unidade | Slots | Torres recomendadas | Papel com torre |
|---|---|---|---|
| **Obviate** | 2×2x2 | Swarmer + Swarmer, Scatter + Segment | Caça pesado |
| **Quell** | 1×3x3 + 1×1x1 | Cyclone + Duo, Fuse + Duo, Breach + Duo, Diffuse + Duo | Nave de linha |
| **Quad** | 1×4x4 + 1×2x2 | Spectre + Segment, Titan + Scatter, Foreshadow + Segment | Plataforma de armas |

#### N4 — Capital

| Unidade | Slots | Torres recomendadas | Papel com torre |
|---|---|---|---|
| **Antumbra** | 1×4x4 + 1×1x1 ou 1×3x3 + 2×2x2 | Spectre + Duo; Titan + Duo; Meltdown + Duo; Cyclone + Segment + Segment | Capital de assalto |
| **Disrupt** | 1×4x4 + 1×2x2 | Foreshadow + Segment; Spectre + Scatter; Titan + Segment | Cerco de longo alcance |
| **Eclipse** | 1×5x5 ou 1×4x4 + 1×3x3 ou 2×3x3 + 1×2x2 | Smite; Spectre + Cyclone; Meltdown + Cyclone; Cyclone + Cyclone + Segment | Capitânia |
| **Oct** | 1×5x5 + 2×4x4 + 2×2x2 | Smite + Spectre + Meltdown + Segment + Segment | Supercapital |

### 16.4 O que cada nível ensina

| Nível | Lição de design | Torre-âncora |
|---|---|---|
| **N0** | "Uma torre muda o papel de uma unidade simples" | Duo |
| **N1** | "Duas torres pequenas criam um papel novo" | Duo + Arc |
| **N2** | "Uma torre média define a identidade da nave" | Scatter |
| **N3** | "Builds diferentes no mesmo casco" | Spectre / Titan |
| **N4** | "A capital é uma plataforma de decisões" | Smite |

### 16.5 Ordem de desbloqueio

A progressão recomendada não segue o tamanho da torre; segue o **que o jogador aprende**.

| Etapa | Desbloqueia | Casco de estreia | O que ensina |
|---|---|---|---|
| 1 | **Duo** | Mono | Torre básica, munição, peso |
| 2 | **Scatter** | Avert | Antiaéreo, recarga, alvo |
| 3 | **Segment** | Mega / Avert | Defesa de ponto |
| 4 | **Repair Point** | Poly / Mono | Suporte, energia |
| 5 | **Hail** | Horizon / Beta | Anti-capital |
| 6 | **Cyclone** | Quell | Área, 3x3 |
| 7 | **Spectre** | Quad | Cadência, 4x4 |
| 8 | **Smite** | Eclipse | Peso de capital, 5x5 |
| 9 | **Foreshadow / Afflict / Meltdown** | Antumbra / Disrupt | Escolha de dano no endgame |
| 10 | **Build completa** | Oct | Múltiplos slots e orçamento |

### 16.6 Munição por nível

| Nível | Munições | Uso |
|---|---|---|
| **Básica** | Cobre, grafite, silício | Torres 1x1 e 2x2 |
| **Avançada** | Tório, tungstênio, carbeto | Torres 3x3 e 4x4 |
| **Exótica** | Liga de surto, fase, óxido | Torres 4x4 e 5x5 |

Regra sugerida: a munição disponível acompanha o nível do casco. Um Mono não carrega liga de surto; uma Eclipse não perde tempo com cobre.

### 16.7 Combinações proibidas

| Combinação | Motivo |
|---|---|
| Duas torres 5x5 na mesma unidade | Passa do orçamento e cobre o casco |
| Smite + Smite | Duplicata de arma de capital; quebra a leitura |
| Malign em qualquer unidade | 9x9, não cabe |
| Torre 2x2 no Horizon | 79%/89%, reprovado |
| Torre 3x3 no Obviate | 51%/69%, reprovado por 1 ponto |
| Torre 4x4 na Quell | 62%/79%, reprovado |
| Torre 5x5 na Quad | 53%/73%, reprovado |
| Smite na Antumbra ou Disrupt | Orçamento efetivo abaixo de 25.600 px² |
| Smite + 4x4 na Eclipse | Orçamento efetivo de 25.680 px² |

### 16.8 Níveis mais altos: o que muda de verdade

Nos cascos N3 e N4, a torre deixa de ser um acessório e passa a ser **subsistema**. Isso exige quatro decisões:

1. **Alvo independente.** Cada torre escolhe seu próprio alvo; não compartilha a mira da arma de nariz.
2. **Munição independente.** Cada torre tem magazine próprio. Uma Oct com Smite e Spectre tem duas reservas.
3. **Dano independente.** A torre pode ser destruída antes do casco. Isso cria a leitura de "desarmar" a nave.
4. **Recursos independentes.** Torres de energia usam bateria; torres de líquido usam tanque. A capital gerencia os dois.

Sem esses quatro pontos, uma capital com três torres é só uma unidade com três armas — e o sistema perde o propósito.

---

## 17. Níveis de melhoria das torres (Mk I–III)

Criar 31 torres × 3 níveis seria inviável. A proposta é **uma torre, três estados**, sem novos blocos.

A mesma lógica vale para o **armamento nativo fixo**: ele pode subir de Mk sem virar módulo removível. O jogador melhora a arma que a unidade já tem, mas não a troca.

| Nível | Ganho | Custo | Efeito de design |
|---|---|---|---|
| **Mk I** | Base | — | Torre como existe hoje |
| **Mk II** | +25% cadência, +10% alcance, +50% magazine | Recursos + munição avançada + nível do casco | Especialização |
| **Mk III** | +50% cadência, +20% alcance, efeito único | Recursos + drop de chefe + material exótico | Identidade de build |

As melhorias **custam recursos** e são aplicadas na preparação. Não são gratuitas nem automáticas.

Exemplos de efeito único:

- **Duo Mk III:** terceiro cano, leque mais aberto.
- **Scatter Mk III:** fragmentos que ricocheteiam.
- **Segment Mk III:** abate dois mísseis por disparo.
- **Spectre Mk III:** rajada perfurante.
- **Smite Mk III:** descarga elétrica em cadeia.

Implementação: os níveis são **multiplicadores no mount**, não novas definições de bloco.

```java
class MountedTurret {
    float reloadMultiplier = 1f;   // 1.0 / 1.25 / 1.5
    float rangeMultiplier = 1f;    // 1.0 / 1.1 / 1.2
    int magazineMultiplier = 1;    // 1 / 1.5 / 2
    int mark = 1;
}
```

Isso mantém o catálogo em 31 torres e o balanceamento em uma tabela de três linhas.

---

## 18. Implantação técnica por nível

O plano de conteúdo precisa caber no plano de código. A tabela abaixo liga cada nível de casco a uma fase técnica.

| Fase | Escopo de conteúdo | Mudança técnica | Critério de saída |
|---|---|---|---|
| **A** | Mono + Duo | Variante de unidade + adaptador `Weapon` | Duo parece parte do Mono |
| **B** | N0 e N1 (1x1) | Dados de slot, orçamento, loadout, magazine | Build de 1x1 persiste e funciona |
| **C** | N2 e N3 (2x2 a 4x4) | `MountedTurret` com alvo e munição próprios; mounts dinâmicos | Duas torres no mesmo casco |
| **D** | N4 (5x5+) | Subsistema de torre, bateria/tanque, dano separado | Capital com três torres |

### 18.1 Fase A — adaptador

- `TurretWeapon extends Weapon` com `bullet`, `reload`, `range`, `targetAir`.
- Uma variante de `UnitType` com a torre na lista de armas.
- Sem magazine; munição infinita para provar a leitura.
- Um slot fixo no centro dorsal do Mono.

### 18.2 Fase B — dados e orçamento

- `TurretSlot` com `maxSize`, `offsetX`, `offsetY`.
- `UnitType.dorsalBudget` calculado da área do sprite × `loadoutBudgetMultiplier`.
- Validação de `C`, `L` e soma.
- Magazine por torre e persistência do loadout.
- UI de preparação com barra de orçamento e armas fixas como slots bloqueados.

### 18.3 Fase C — torres independentes

- `MountedTurret` com `target`, `reload`, `heat`, `ammo`.
- `WeaponsComp` passa a aceitar mounts adicionados em runtime.
- Mira independente por torre (não compartilhar `aim`).
- Slots espelhados para cascos simétricos.
- Torres 2x2, 3x3 e 4x4.

### 18.4 Fase D — capital

- Múltiplos slots com posições validadas por arte.
- Bateria e tanque como recursos de unidade.
- Dano separado por torre.
- Prioridade de alvo por torre.
- Smite e as torres 5x5.
- Oct com quatro slots.

### 18.5 O que fica fora

- **Malign** — 9x9, fortaleza ou chefe.
- **Build Tower** — construção não é combate; slot próprio, se entrar.
- **Repair Turret 2x2** — suporte, slot próprio.
- **Sublimate/Wave/Tsunami** — só depois de tanque de unidade.

---

## 19. Mapa de armamento nativo e torres removíveis

Esta seção aplica as decisões de design: **o que já existe na unidade é fixo; o que é adicionado é removível**.

### 19.1 As duas camadas

| Camada | O que é | Removível? | Consome orçamento? |
|---|---|---|---|
| **Armamento nativo fixo** | A arma que a unidade já tem no código e no sprite | **Não** | **Não** |
| **Torre removível** | Módulo de torre adicionado pelo jogador | **Sim** | **Sim** |

Regras firmadas:

1. **Toda arma existente é fixa.** Não há tela para removê-la ou trocá-la.
2. **Toda torre adicionada é removível.** Pode ser trocada na preparação.
3. **A arma fixa não consome o orçamento dorsal.** Ela já faz parte da silhueta.
4. **Unidade com arma fixa ainda pode receber torre removível**, se o orçamento permitir.
5. **Quell e Disrupt mantêm o arco limitado** (60° e 22°).
6. **Arma de nariz não vira torre.** Ela continua sendo arma de nariz.
7. **Munição da arma fixa é infinita no MVP**, balanceada por recarga.
8. **Arma fixa pode ter Mk I–III**, mas o dano é compartilhado com o casco.

### 19.2 Mapa das 20 unidades aéreas

| Unidade | Armamento nativo (fixo) | Gira? | Referência de torre | Pode receber removível? |
|---|---|---|---|---|
| **Mono** | Nenhum | — | — | Sim — 1x1 |
| **Flare** | Canhão triplo, 9 de dano | Não | Duo | Sim — 1x1 |
| **Poly** | Míssil de reparo, 5,5% | Não | Repair | Sim — 1x1 |
| **Mega** | 2× laser de reparo | **Sim** | Repair beam | Sim — 2x2 |
| **Horizon** | Bomba, 27 + 25 de área | Não | Hail | Sim — 1x1 |
| **Zenith** | Míssil teleguiado, 2 tiros | **Sim** | Swarmer | Sim — 2x2 + 1x1 |
| **Antumbra** | 2× míssil + 1× canhão de 55 | **Sim** | Swarmer ×2 + Spectre | Sim — 4x4 + 1x1 ou 3x3 + 2×2x2 |
| **Eclipse** | Laser de 115 + 2× flak | **Sim** | Meltdown + Cyclone ×2 | Sim — 5x5 ou 3 slots menores |
| **Quad** | Bomba de 220 + reparo | Não | Hail | Sim — 4x4 + 2x2 |
| **Oct** | Nenhum (escudo e reparo) | — | — | Sim — 5x5 + 2×4x4 + 2×2x2 |
| **Avert** | Hélice de 29 + fragmentos | Não | Scatter | Sim — 2x2 |
| **Obviate** | Orbe de 75 + raios | Não | Titan | Sim — 2×2x2 |
| **Quell** | Míssil de 70, arco de 60° | **Sim** | Swarmer/Hail | Sim — 3x3 + 1x1 |
| **Disrupt** | 3 mísseis, arco de 22° | **Sim** | Swarmer/Foreshadow | Sim — 4x4 + 2x2 |
| **Alpha** | Laser bolt de 11 | Não | Arc | Sim — 1x1 |
| **Beta** | Laser bolt de 11, 2 tiros | Não | Arc | Sim — 1x1 |
| **Gamma** | Laser bolt de 11, leque | Não | Arc | Sim — 2×1x1 |
| **Evoke** | Feixe de reparo, 3,1 | Não | Repair Turret | Sim — 1x1 |
| **Incite** | Feixe de reparo + braço de construção | **Parcial** | Build Tower | Sim — 2×1x1 |
| **Emanate** | 2× feixe de reparo | Não | Repair Turret | Sim — 2×1x1 |

### 19.3 Contagens

| Item | Quantidade |
|---|---:|
| Unidades com armamento nativo fixo | **18** |
| Unidades sem arma | **2** (Mono e Oct) |
| Armas nativas com rotação independente | **12**, em 7 unidades |
| Unidades que podem receber torre removível | **20** |
| Torres adicionadas que viram fixas | **0** |

As 7 unidades com armas de rotação independente:

1. **Mega** — 2 lasers de reparo.
2. **Zenith** — 1 pod de mísseis.
3. **Antumbra** — 2 pods de mísseis + 1 canhão.
4. **Eclipse** — 1 laser + 2 flaks.
5. **Quell** — 1 míssil de arco limitado.
6. **Disrupt** — 1 lança-mísseis de arco limitado.
7. **Incite** — 1 braço de construção (utilitário).

### 19.4 Observações

- **A arma fixa é identidade da unidade.** O jogador não a remove; ele escolhe o que adicionar ao redor dela.
- **Mono e Oct** não têm arma de combate. São os únicos cascos em que a torre removível é a única fonte de dano.
- **Antumbra e Eclipse** já têm três armas giratórias; a torre removível é complemento, não substituto.
- **Quell e Disrupt** continuam com arco limitado; a torre removível não corrige essa limitação.
- **Mega, Evoke, Emanate e Incite** são suporte. A torre removível pode dar a eles uma função de combate ou reforçar o suporte.
- **A semelhança de dano não promove arma a torre.** Flare continua com canhão de nariz, mesmo tendo dano de Duo.

### 19.5 Consequências e risco

| Consequência | Efeito |
|---|---|
| Arma fixa não custa orçamento | Todas as 20 unidades mantêm o orçamento base livre |
| Arma fixa é infinita no MVP | Menos economia, mais foco na escolha da torre |
| Torre removível é sempre opcional | Nenhuma unidade perde a identidade ao instalar |
| Unidades já armadas ganham mais poder | Risco de power creep |

**Mitigação:** o orçamento tem um multiplicador por unidade (`loadoutBudgetMultiplier`). Ex.: Eclipse com 0,50× e Mono com 1,00×. A arma fixa continua gratuita; só o espaço para módulos é ajustado.

### 19.6 Decisões complementares

| # | Decisão | Resposta |
|---|---|---|
| 1 | Multiplicador de orçamento por unidade | **Sim** |
| 2 | Torre removível pode repetir a arma fixa | **Sim** |
| 3 | Arma fixa troca de munição | **Não** — mantém o projétil original |
| 4 | Custo das melhorias Mk | **Custa recursos** |
| 5 | UI mostra armas fixas como slots bloqueados | **Sim** |
| 6 | Escopo do MVP | **Todas as unidades aéreas** |

Desdobramentos:

- **Orçamento efetivo** = `50% × área × loadoutBudgetMultiplier`. O multiplicador é a válvula de balanceamento; não altera a arma fixa.
- **Duplicatas são permitidas.** Uma Antumbra pode ter Spectre fixa e outra Spectre removível. O limite é o orçamento e a leitura visual.
- **Arma fixa não muda de munição.** O Duo fixo do Mono, por exemplo, continua usando o projétil definido na unidade; a escolha de munição existe só nas torres removíveis.
- **Mk custa recursos.** Mk II e Mk III entram na economia de materiais, não são gratuitos.
- **A tela de preparação mostra as armas fixas como slots bloqueados**, com cadeado e a indicação "nativa". O jogador vê o que já tem e o que pode adicionar.
- **O MVP cobre as 20 unidades aéreas.** Não há recorte por casco; o recorte é por torre disponível.

### 19.7 Multiplicadores por unidade

Valores iniciais, em passos de 0,05. Eles não mudam a regra; só calibram o espaço disponível.

| Multiplicador | Unidades | Motivo |
|---:|---|---|
| **1,00** | Mono, Flare, Poly, Alpha, Beta, Gamma, Evoke, Incite, Emanate, Horizon, Mega | Sem arma de combate, arma leve ou suporte; precisam do kit completo |
| **0,95** | Obviate | Um canhão principal pesado, sem torres nativas |
| **0,90** | Avert, Zenith | Uma arma rotativa forte (fragmentos ou mísseis) |
| **0,85** | Quad, Oct | Bombardeiro de payload / capital sem arma, mas com escudo e reparo |
| **0,80** | Quell | Míssil de longo alcance + campo de supressão |
| **0,75** | Disrupt | Três mísseis + supressão em área |
| **0,70** | Antumbra | Três armas rotativas |
| **0,50** | Eclipse | Três armas rotativas de nível capital |

#### Efeito prático

| Unidade | Orçamento efetivo | Build máxima |
|---|---:|---|
| Mono | 1.152 | 1×1x1 |
| Avert | 4.147 | 1×2x2 |
| Obviate | 8.645 | 2×2x2 |
| Quad | 20.570 | 1×4x4 + 1×2x2 |
| Quell | 10.497 | 1×3x3 + 1×1x1 |
| Antumbra | 18.144 | 1×4x4 + 1×1x1 |
| Disrupt | 22.143 | 1×4x4 + 1×2x2 |
| Eclipse | 25.680 | 1×5x5 **ou** 1×4x4 + 1×3x3 |
| Oct | 68.000 | 1×5x5 + 2×4x4 + 2×2x2 |

#### Critérios usados

1. **Arma nativa pesada reduz o orçamento.** Quem já tem três torres não precisa de mais três.
2. **Suporte recebe 1,00.** Unidades de reparo e construção usam a torre para ganhar função, não para somar dano.
3. **Nenhuma unidade fica abaixo de 0,50.** O casco precisa conseguir instalar pelo menos uma torre relevante.
4. **Smite é restrita a Eclipse e Oct.** Antumbra e Disrupt ficam no 4x4, o que preserva a hierarquia de capital.
5. **O multiplicador é ajustável sem migration.** É um float em `UnitType`; mudar o valor não muda o formato do save.

---

## 20. Conclusão

As torres são o módulo mais pronto que o Orbital tem. Elas já sabem mirar, recarregar, consumir munição, aquecer e escolher alvos. O que falta não é tecnologia: é **encaixe, regra e curadoria**.

A regra de cobertura resolve o problema mais visível — a torre que engole a unidade — com duas medidas simples e uma checagem de arte. O caso Duo + Mono prova que a regra funciona no limite mais apertado: 44% de cobertura e 67% de largura, com a unidade ainda legível.

A proposta é conservadora de propósito:

- **poucas torres** no início;
- **poucos cascos** com slots no início;
- **uma contrapartida clara** por torre;
- **uma prova visual** antes de qualquer sistema.

Se o Duo parecer natural nas costas do Mono, o resto do sistema tem fundação. Se não parecer, nenhuma quantidade de código vai consertar.

> **A torre não é um prédio que a nave carrega. É uma parte da nave.**

---

## 21. Implementação — Fase A

Esta seção registra o que já foi implementado no código a partir das decisões deste relatório.

### 21.1 O que entrou

| Peça | Onde | Função |
|---|---|---|
| `TurretSlot` | `core/src/orbital/type/TurretSlot.java` | Descreve um encaixe dorsal: tamanho máximo e deslocamento. |
| `TurretWeapon` | `core/src/orbital/type/TurretWeapon.java` | Adapta uma torre de bloco para `Weapon` de unidade, copiando recarga, rotação, cone, recuo, som, padrão de tiro e projétil. |
| `UnitType.turretSlots` | `UnitType` | Lista de encaixes disponíveis no casco. |
| `UnitType.loadoutBudgetMultiplier` | `UnitType` | Multiplicador do orçamento dorsal. |
| `UnitType.dorsalBudget()` | `UnitType` | Orçamento efetivo: `50% × área × multiplicador`. |
| `UnitType.canMount()` | `UnitType` | Valida encaixe, silhueta (`C ≤ 50%`, `L ≤ 70%`) e orçamento. |
| `WeaponsComp.mountTurret()` | `WeaponsComp` | Monta uma torre removível em tempo de execução, respeitando o orçamento atual da unidade. |
| `WeaponsComp.removeWeapon()` | `WeaponsComp` | Remove apenas torres marcadas como removíveis. Armas nativas nunca são removidas. |
| `TurretLoadouts` | `core/src/orbital/content/TurretLoadouts.java` | Multiplicadores, áreas medidas e slots das 20 unidades aéreas. |
| `monoDuo` | `UnitTypes` | Variante de demonstração: casco Mono com uma torre Duo removível. |
| Plano de fábrica | `Blocks.airFactory` | Permite construir a variante `Mono (Duo)` para testar o sistema em jogo. |
| Faixa de torres no HUD | `HudFragment` | Mostra as torres da unidade selecionada, com cadeado para arma nativa, borda destacada para módulo removível, barras de recarga e calor, tooltip e destaque ao clicar. |

### 21.2 Regras preservadas no código

- **Arma nativa é fixa.** Só `TurretWeapon` pode ser removida; qualquer `Weapon` comum é permanente.
- **Torre adicionada é removível.** `TurretWeapon.removable` nasce `true`.
- **Arma fixa não custa orçamento.** O cálculo de `loadoutCost()` só soma `TurretWeapon` removíveis.
- **Unidade armada ainda recebe torre.** O orçamento é independente das armas nativas.
- **Quell e Disrupt mantêm arco.** O adaptador copia o padrão da torre; o arco nativo permanece nas armas de unidade.
- **Munição infinita no MVP.** O adaptador usa o projétil original da torre e depende apenas de recarga.
- **Silhueta manda.** Uma torre 2x2 não entra no Mono mesmo que o orçamento permitisse.
- **Multiplicador por unidade.** Todos os 20 cascos usam a tabela da seção 19.7.

### 21.3 Limites desta fase

- Ainda não há tela de preparação nem persistência de loadout.
- A faixa inferior da seção 10.3 está implementada no `HudFragment`, mas mostra apenas recarga e calor; munição por torre e nível Mk dependem dos sistemas correspondentes, que ainda não existem.
- A faixa é somente leitura: clicar destaca a torre, mas não remove nem troca o módulo.
- A remoção existe na API (`removeWeapon`), mas não tem botão na interface.
- A torre Duo usa o sprite de bloco; a arte dedicada de torre dorsal fica para a fase visual.
- Magazines independentes e munição por torre não entraram; o MVP usa recarga infinita.
- Torres especiais (`RepairTurret`, `BuildTurret`, `PointDefenseTurret`, `TractorBeamTurret`) ainda não têm adaptador próprio.

### 21.4 Validação

O teste `tests/src/test/java/TurretLoadoutSmokeTest.java` cobre:

- orçamento do Mono (1.152 px²) e encaixe do Duo;
- rejeição de 2x2 no Mono;
- variante `Mono (Duo)` com uma torre removível de custo 1.024 px²;
- armas nativas não removíveis;
- hierarquia de capital (Smite só em Eclipse/Oct);
- orçamento e slots presentes nas 20 unidades aéreas.

A faixa de HUD não tem teste automatizado de interface nesta fase; a validação foi feita por compilação de `:core` e `:desktop` e pelo smoke test do sistema dorsal.
