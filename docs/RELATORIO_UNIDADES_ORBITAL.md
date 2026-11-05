# Relatório — Unidades de Orbital: da superfície ao espaço

**Tipo:** análise de produto e documento de conceito  
**Data:** 3 de novembro de 2026  
**Base:** código do protótipo Orbital e documento `docs/VISAO_DE_JOGO_ORBITAL.md`  
**Relacionado:** `docs/RELATORIO_SISTEMA_DE_SETORES.md`  
**Público:** design, produção e liderança técnica  
**Leitura:** não exige conhecimento de programação; o anexo final é destinado à equipe técnica.

---

## 1. Resumo executivo

O Orbital herdou do Mindustry um catálogo de **62 definições de unidade**. Elas foram desenhadas para três domínios: **terra, água e ar**. A ficção do Orbital, porém, é espacial. Manter os três domínios exigiria mapas com água, caminhos terrestres, esteiras, pernas mecânicas e regras de afogamento que não conversam com a fantasia de colônias e naves.

A situação atual já aponta para o caminho oposto:

- **Quatia** é uma colônia pacífica, sem ondas de combate.
- **Gier** é a única frente de combate ativa e suas ondas são **exclusivamente aéreas**.
- **Serpulo e Erekir** continuam no mapa como corpos visuais; suas campanhas foram desativadas.
- As **38 unidades terrestres e navais** continuam carregadas no motor, mas não têm uso na experiência atual.

A proposta deste documento é transformar essa limitação em sistema:

> **Manter todas as unidades aéreas como base da frota e desmontar as unidades terrestres e navais em módulos, armas e atributos que possam ser acoplados a elas.**

Em outras palavras: o chão e o mar não desaparecem. Eles viram equipamento.

**O que isso resolve:**

- reduz o jogo a um único domínio de movimento, mais coerente com o espaço;
- reaproveita o melhor das 38 unidades dormentes: habilidades, armas, silhuetas e identidade;
- cria personalização real para a nave principal e as naves de apoio;
- evita mapas navais, pathfinding terrestre e conteúdo que não será usado;
- aproveita habilidades que já existem no motor e são genéricas o bastante para qualquer unidade.

**O que ainda falta:**

- um sistema de **encaixes de módulo** por unidade;
- custo, peso e limite de módulos;
- tela de preparação da nave;
- persistência da configuração;
- regras de balanceamento e contrapartidas;
- limpeza das referências antigas a unidades terrestres e navais.

**Recomendação em cinco fases:**

1. **Provar** a ideia em Gier, que já é uma frente aérea, com três chassis e três habilidades.
2. **Criar o encaixe**: slots, custo, tela de preparação e persistência.
3. **Montar o arsenal** com 12 módulos e re-tematizar armas e efeitos.
4. **Aposentar** as unidades terrestres e navais como conteúdo jogável, preservando sua identidade.
5. **Escalar** para a nave principal, naves de apoio e progressão de frota.

**A frase que resume a proposta:**

> **O casco é a unidade aérea. O módulo é a memória do chão e do mar.**

---

## 2. A ideia em uma página

### 2.1 O princípio

Hoje, uma unidade é um pacote fechado: corpo, arma, habilidade e movimento. A proposta separa esse pacote em três camadas:

```text
CASCO (unidade aérea)
  ├─ ARMA 1          → dano direto
  ├─ ARMA 2          → dano alternativo
  ├─ MÓDULO ativo    → habilidade com tempo de recarga
  ├─ MÓDULO passivo  → efeito permanente
  └─ PORÃO / SENSOR  → carga, alcance, utilidade
```

- **Casco:** a unidade aérea que o jogador pilota. Define silhueta, vida, velocidade, quantidade de encaixes e papel base.
- **Arma:** o que causa dano. As armas das unidades terrestres e navais são re-tematizadas e passam a ocupar encaixes de arma.
- **Módulo:** o que dá uma habilidade ou um comportamento especial. É aqui que entram as habilidades das unidades que não voam.
- **Encaixe (slot):** o espaço disponível no casco. Um casco leve tem poucos encaixes; um casco capital tem vários.
- **Preparação (loadout):** a tela em que o jogador escolhe casco, armas e módulos antes da expedição.

### 2.2 O que muda e o que permanece

| Elemento | Hoje | Proposta |
|---|---|---|
| Domínio das unidades jogáveis | Terra, água e ar | Apenas ar |
| Unidades terrestres | Mechs, walkers, tanques e hover | Desmontadas em módulos, armas e atributos |
| Unidades navais | Barcos e navios de apoio | Desmontadas em módulos e em comportamento de nave capital |
| Unidades aéreas | Combate e suporte | Mantidas e re-tematizadas |
| Habilidades | Presas ao corpo da unidade | Acopláveis a cascos aéreos |
| Armas | Por unidade | Re-tematizadas e organizadas em encaixes |
| Efeitos | Poeira, água, esteira, passo | Trilha de íons, vácuo, faíscas, propulsão |
| Progressão | Árvore de pesquisa antiga | Blueprints de casco e módulo |

### 2.3 Regras de design

1. **Todo módulo precisa mudar uma decisão.** Nada de “+10% de dano” como módulo principal. O jogador deve escolher entre formas de lutar, não entre números maiores.
2. **Um escudo, um reparo, uma supressão.** Limites evitam combinações invencíveis e mantêm a leitura da batalha.
3. **Peso e energia têm custo.** Módulos pesados reduzem velocidade; módulos energéticos consomem carga ou geram calor.
4. **O módulo carrega a origem.** O nome e o ícone lembram a unidade que o originou. Isso dá lore e facilita o aprendizado.
5. **Nada de água, esteira ou perna.** O que depende do chão ou do mar é aposentado ou reinterpretado.
6. **Poucos módulos ativos por nave.** No máximo dois botões ativos, para não transformar o combate em uma barra de cooldowns.
7. **A nave principal não é uma unidade qualquer.** Ela precisa de identidade própria, mas pode nascer de um casco aéreo existente.

### 2.4 Um exemplo concreto

**Interceptador “Flare” com dois módulos:**

- **Módulo Oxynoe — Sobrecarga:** aumenta a cadência dos aliados próximos por alguns segundos.
- **Módulo Tecta — Escudo defletor:** cria um arco frontal que deflecte projéteis.

Resultado: uma nave rápida que entra, acelera o grupo e sai antes de ser cercada. O jogador não escolheu “+20% de vida”; escolheu uma forma de lutar.

**Nave capital “Eclipse” com três módulos:**

- **Módulo Conquer — Aríete:** causa dano de colisão ao avançar.
- **Módulo Aegires — Campo de energia:** dano contínuo ao redor e cura aliados.
- **Módulo Navanax — Supressão:** enfraquece escudos e reparos inimigos.

Resultado: uma nave de cerco que quebra linhas defensivas, mas é lenta e vulnerável a interceptadores.

### 2.5 Glossário rápido

| Termo | Significado |
|---|---|
| **Casco** | A unidade aérea base, sem equipamento. |
| **Módulo** | Equipamento que concede uma habilidade ou efeito passivo. |
| **Encaixe** | Espaço no casco para uma arma ou módulo. |
| **Preparação** | Tela de escolha de casco, armas e módulos. |
| **ECM** | Guerra eletrônica: supressão, lentidão, enfraquecimento. |
| **Ponto de defesa** | Arma que abate mísseis e projéteis inimigos. |
| **Blueprint** | Projeto desbloqueado que permite fabricar um casco ou módulo. |

---

## 3. Como as unidades funcionam hoje

### 3.1 Três domínios, uma herança

| Domínio | Famílias | Como se move | O que o motor exige |
|---|---|---|---|
| **Terra** | Mechas, walkers, tanques, hover | Pernas, esteiras, levitação | Terreno, pathfinding, passos, colisão |
| **Água** | Barcos de ataque e de apoio | Flutuação | Mapas com água, rastros, profundidade |
| **Ar** | Caças, bombardeiros, gunships, suporte | Voo | Nenhum terreno especial |

Essa divisão é a origem de quase todo o catálogo: cada família existe para resolver um problema de terreno diferente. No espaço, esse problema deixa de existir.

### 3.2 Tiers e papéis

As unidades seguem uma progressão de **cinco níveis** (tiers), do mais simples ao mais poderoso. Dentro de cada domínio, elas se dividem em:

- **ataque:** dano direto, cerco, assalto;
- **suporte:** reparo, escudo, sobrecarga, supressão;
- **transporte:** carga, unidades, construção;
- **especial:** suicídio, minas, invocação de drones, alvo de treino.

O jogador reconhece o papel pela silhueta e pelo tamanho. Essa leitura é um ativo que deve ser preservado.

### 3.3 O que é uma “habilidade” hoje

No motor, “habilidade” não é só o botão especial. Existem duas camadas:

- **Habilidades declaradas:** escudo, campo de reparo, campo de força, supressão, sobrecarga, invocação de drones. São componentes genéricos que podem ser anexados a qualquer unidade.
- **Habilidades implícitas:** comportamentos que vêm do corpo ou do movimento. Exemplos: o crawler explodir ao morrer, o tanque amassar construções, o walker causar onda de choque ao pisar, o navio só existir na água.

A distinção é importante para a proposta:

> **As habilidades declaradas são as mais fáceis de virar módulo. As implícitas exigem transformar comportamento em sistema — ou aposentar.**

### 3.4 O que o Orbital usa hoje

| Corpo | Situação | Unidades em uso |
|---|---|---|
| **Quatia** | Colônia pacífica, sem ondas | Nenhuma unidade de combate |
| **Gier** | Expedição de 30 minutos | Apenas unidades aéreas nas ondas |
| **Serpulo** | Campanha desativada, corpo visual | Unidades carregadas, sem uso |
| **Erekir** | Campanha desativada, corpo visual | Unidades carregadas, sem uso |
| **Notva, Verilus, Tantros** | Corpos visuais ou inacessíveis | Nenhuma |

**O dado mais importante:** Gier já chama o gerador de ondas com a opção “apenas aéreas”. As ondas podem sortear **Flare, Horizon, Zenith, Antumbra, Eclipse e Quad** — todas aéreas. Ou seja, a única frente de combate do jogo já é aérea.

Isso significa que:

- a ideia não começa do zero;
- o jogador já enfrenta um roster aéreo;
- as 38 unidades terrestres e navais estão dormentes;
- transformá-las em módulos dá função a conteúdo que hoje não aparece.

### 3.5 Resumo numérico

| Categoria | Quantidade | Situação |
|---|---:|---|
| Unidades terrestres | 28 | Dormentes no Orbital |
| Unidades navais | 10 | Dormentes no Orbital |
| Unidades aéreas | 20 | Base da frota proposta |
| Unidades internas/especiais | 4 | Logística, teste e funcionamento interno |
| **Total de definições** | **62** | Herdado do motor |

---

## 4. Catálogo — unidades terrestres

A linha terrestre é a maior e a mais variada. Ela cobre assalto, cerco, suporte, tanques, walkers e criaturas. Na proposta, cada unidade contribui com uma **identidade** — não necessariamente com um módulo exclusivo.

### 4.1 Mechas, walkers e tanques

| Unidade | Família | Papel atual | O que preservar na conversão |
|---|---|---|---|
| **Dagger** | Mecha | Assalto leve | Canhão cinético básico; ideal para arma inicial de interceptor. |
| **Mace** | Mecha | Assalto antiterrestre | Lança-chamas vira projetor de plasma curto alcance. |
| **Fortress** | Mecha | Cerco | Artilharia vira morteiro de cerco lento e pesado. |
| **Scepter** | Mecha | Assalto pesado | Campo de escudo + projéteis elétricos; vira escudo regenerativo e arma de arco. |
| **Reign** | Mecha | Cerco pesado | Projéteis perfurantes e fragmentos; vira canhão capital. |
| **Nova** | Mecha de apoio | Reparo | Campo de reparo + laser curativo; base do módulo de reparo em área. |
| **Pulsar** | Mecha de apoio | Escudo/reparo | Escudo regenerativo + raio em cadeia; base de escudo e reparo. |
| **Quasar** | Mecha de apoio | Defesa | Bolha de força + feixe curativo; base da bolha de contenção. |
| **Vela** | Mecha de apoio | Reparo/construção | Feixe contínuo + feixe de reparo; base do nano-reparo e da oficina de bordo. |
| **Corvus** | Walker | Cerco | Laser gigante que fere e cura; vira arma de feixe capital. |
| **Atrax** | Walker | Escaramuça | Jato de escória vira plasma corrosivo. |
| **Crawler** | Mecha suicida | Ogiva | Explode ao morrer; base do módulo de ogiva de impacto. |
| **Spiroct** | Walker legado | Assalto | Projéteis de seiva; pode virar arma bio/ácida ou ser aposentado. |
| **Arkyid** | Walker legado | Artilharia | Seiva + artilharia; referência para arma híbrida. |
| **Toxopid** | Walker legado | Cerco | Estilhaços + artilharia; referência para arma de fragmentação. |
| **Merui** | Walker Erekir | Artilharia leve | Morteiro móvel; vira arma de bombardeio. |
| **Cleroi** | Walker Erekir | Generalista | Canhão básico; vira arma equilibrada de escolta. |
| **Anthicus** | Walker Erekir | Míssil | Lança mísseis/drones; base da baía de drones. |
| **Tecta** | Walker Erekir | Escudo | Escudo direcional + mísseis; base do escudo defletor. |
| **Collaris** | Walker Erekir | Artilharia pesada | Artilharia de fragmentos; vira arma de saturação. |
| **Stell** | Tanque Erekir | Assalto | Canhão leve; vira arma cinética de casco médio. |
| **Locus** | Tanque Erekir | Precisão | Railgun; vira arma perfurante de longo alcance. |
| **Precept** | Tanque Erekir | Cerco | Projéteis explosivos com fragmentos; vira arma de área. |
| **Vanquish** | Tanque Erekir | Assalto pesado | Canhão + armas secundárias + aríete; base do módulo aríete. |
| **Conquer** | Tanque Erekir | Cerco | Canhão massivo + aríete; base do aríete pesado. |
| **Elude** | Hover Erekir | Escaramuça | Disparo homing + rastro; base do skimmer e do rastro iônico. |
| **Latum** | Crawler neoplasm | Enxame | Spawn de Renale ao morrer + regeneração; base do enxame póstumo. |
| **Renale** | Crawler neoplasm | Enxame | Corpo a corpo; pode virar drone de enxame ou ser aposentado. |

### 4.2 O que a linha terrestre ensina

- **Mechas** são a expressão mais clara de “assalto”: braços, passo pesado, armas frontais.
- **Walkers** trazem a ideia de cerco e de terreno acidentado; no espaço, viram naves de suporte pesado.
- **Tanques** são a melhor fonte de **armadura, aríete e canhão pesado**.
- **Hover** é a ponte conceitual para o voo: o Elude já parece uma nave baixa.
- **Neoplasm** é a exceção biológica; pode virar facção alienígena, relíquia ou ser aposentado.

---

## 5. Catálogo — unidades navais

A linha naval é a mais surpreendente para a proposta: **metade dela é de suporte**. Ela contém reparo, sobrecarga, ponto de defesa, campo de energia, supressão e EMP — exatamente o tipo de habilidade que a frota aérea precisa.

| Unidade | Papel atual | O que preservar na conversão |
|---|---|---|
| **Risso** | Barco de ataque leve | Canhão básico + mísseis; vira arma inicial de nave média. |
| **Minke** | Fragata | Flak + artilharia; vira arma antiaérea e de bombardeio. |
| **Bryde** | Destróier | Artilharia pesada + mísseis + escudo; vira casco de escolta. |
| **Sei** | Encouraçado | Barragem de mísseis + canhões pesados; vira nave de linha. |
| **Omura** | Couraçado | Railgun de longo alcance; vira arma capital. |
| **Retusa** | Barco de reparo | Feixe de reparo + laser curativo + minas; base do nano-reparo e das minas. |
| **Oxynoe** | Suporte | Sobrecarga + plasma + ponto de defesa; base de dois módulos. |
| **Cyerce** | Suporte | Feixe de reparo + mísseis de plasma; base do reparo e da arma de plasma. |
| **Aegires** | Suporte pesado | Campo de energia + dois pontos de defesa; base do campo de energia e do interceptador. |
| **Navanax** | Suporte capital | Canhão EMP + supressão + laser de plasma; base da supressão e do EMP. |

### 5.1 Por que a linha naval é tão valiosa

- **Cinco das dez unidades são de suporte.** Isso dá ao jogo um repertório pronto de reparo, escudo, sobrecarga, ponto de defesa e supressão.
- **As naves são lentas e viram de lado.** O comportamento de “navio” — armas que giram independentemente do corpo — é perfeito para **naves capitais** no espaço.
- **As armas são pesadas e de longo alcance.** Railgun, artilharia e mísseis já estão na escala certa para combate espacial.
- **Os efeitos de água podem ser trocados por trilhas de íons.** A silhueta e o movimento permanecem; só o rastro muda.

---

## 6. Catálogo — unidades aéreas

Todas as 20 unidades aéreas são mantidas. Algumas viram chassis jogáveis; outras viram suporte, logística ou legado. A distinção importante é entre **manter no motor** e **colocar no roster inicial**.

### 6.1 Combate

| Unidade | Papel atual | Proposta espacial |
|---|---|---|
| **Flare** | Interceptador leve | Interceptador inicial: rápido, frágil, canhão triplo. |
| **Horizon** | Bombardeiro | Bombardeiro antinavio: bombas de queda, sem alvo aéreo. |
| **Zenith** | Gunship de mísseis | Gunship de mísseis guiados; escolta de nave capital. |
| **Antumbra** | Gunship pesado | Plataforma de mísseis e canhão; nave de linha média. |
| **Eclipse** | Fortaleza voadora | Nave capital/boss: laser, flak e enorme resistência. |
| **Avert** | Interceptador Erekir | Interceptador rápido de fragmentos; **hoje bloqueado no ambiente espacial**. |
| **Obviate** | Atacante de arco | Caçador de escudos: orbe elétrico + raios; **hoje bloqueado no ambiente espacial**. |
| **Quell** | Bombardeiro de mísseis | Bombardeiro com supressão e mísseis que viram drones. |
| **Disrupt** | Bombardeiro pesado | Bombardeiro capital com supressão dupla e enxame de mísseis. |

### 6.2 Apoio e logística

| Unidade | Papel atual | Proposta espacial |
|---|---|---|
| **Mono** | Drone de mineração | Drone mineiro inicial; pode virar módulo de mineração. |
| **Poly** | Drone de construção/reparo | Drone de apoio: constrói, repara e minera. |
| **Mega** | Drone de construção/payload | Drone de logística: carga, construção e reparo. |
| **Quad** | Transporte pesado legado | Transporte de carga e unidades; útil para módulos de porão. |
| **Oct** | Transporte superpesado legado | Transporte capital com escudo e reparo; referência para nave-mãe. |
| **Evoke** | Suporte Erekir | Drone de reparo e construção com payload. |
| **Incite** | Suporte Erekir | Versão intermediária do drone de reparo. |
| **Emanate** | Suporte Erekir | Versão pesada do drone de reparo. |

### 6.3 Núcleo legado

| Unidade | Papel atual | Proposta espacial |
|---|---|---|
| **Alpha** | Drone de núcleo | Drone inicial de mineração/construção; tutorial. |
| **Beta** | Drone de núcleo | Evolução do Alpha. |
| **Gamma** | Drone de núcleo | Evolução final do drone de núcleo. |

### 6.4 Unidades internas

| Unidade | Papel | Observação |
|---|---|---|
| **Block** | Corpo interno de blocos e torres | Não é uma unidade jogável. |
| **Manifold** | Drone de carga de bloco | Logística interna; pode virar módulo de porão. |
| **Assembly Drone** | Drone de montagem | Funcionamento interno de fábricas. |
| **Dummy** | Alvo de treino | Ferramenta de teste. |

### 6.5 O roster aéreo em Gier

As ondas de Gier podem sortear:

- **Flare** — interceptor;
- **Horizon** — bombardeiro;
- **Zenith** — gunship;
- **Antumbra** — gunship pesado;
- **Eclipse** — capital;
- **Quad** — transporte usado como variação de alto tier.

Isso é importante: **o jogador já enfrenta um roster aéreo**. A proposta não introduz um domínio novo; ela torna o domínio existente jogável e personalizável.

---

## 7. A conversão para o espaço

### 7.1 Armas

A conversão não precisa inventar 62 armas novas. Ela precisa **re-tematizar** as famílias que já existem.

| Hoje | No espaço | Exemplos de origem |
|---|---|---|
| Projétil básico | Canhão cinético / coilgun | Dagger, Stell, Cleroi |
| Artilharia | Morteiro / torpedo de cerco | Fortress, Collaris, Merui |
| Míssil | Míssil guiado | Zenith, Sei, Antumbra |
| Laser | Lança de feixe | Quasar, Corvus, Eclipse |
| Raio | Arco elétrico / EMP | Scepter, Obviate, Pulsar |
| Railgun | Railgun perfurante | Locus, Omura |
| Flak | Flak / defesa de ponto | Minke, Eclipse |
| Bomba | Bomba orbital | Horizon, Quell |
| Líquido (escória) | Plasma corrosivo | Atrax, Oxynoe |
| Seiva | Bioarma / dreno | Spiroct, Arkyid |
| Estilhaço | Ogiva de fragmentação | Toxopid, Collaris |
| EMP | Pulso eletromagnético | Navanax |
| Feixe de reparo | Nano-reparo | Retusa, Cyerce, Vela |
| Ponto de defesa | Interceptador | Oxynoe, Aegires |
| Invocação de unidade | Baía de drones/mísseis | Anthicus, Quell, Disrupt |

**Regra:** cada arma deve ter uma **assinatura visual** clara. O jogador precisa reconhecer “isso é cinético”, “isso é energia”, “isso é plasma”, “isso é míssil” sem abrir a ficha.

### 7.2 Efeitos

Os efeitos são a camada mais barata de trocar e a que mais muda a sensação de tema.

| Hoje | No espaço |
|---|---|
| Rastro de água | Trilha de íons / exaustão |
| Poeira do passo | Faíscas e detritos |
| Marca de esteira | Nenhuma; no máximo, scorch de pouso |
| Fumaça de explosão | Gás ventado, detritos e flash |
| Afogamento | Exposição ao vácuo ou removido |
| Som de passo | Rumor de propulsão |
| Explosão com onda de ar | Explosão estilizada de vácuo, com flash e detritos |

**Cuidado:** o vácuo não transmite som. O jogo pode usar som estilizado, mas a mixagem deve lembrar “rádio, casco, motor” em vez de “rua, esteira, água”.

### 7.3 Atributos

| Atributo | Como é hoje | O que fazer |
|---|---|---|
| **Voo** | Só unidades aéreas | Passa a ser o padrão da frota. |
| **Baixa altitude** | Alguns caças e bombardeiros | Pode virar “camada orbital”: alvos baixos e altos. |
| **Naval** | Só na água | Vira “nave capital” ou é removido. |
| **Pernas/esteiras** | Movimento terrestre | Aposentar ou usar só como referência visual. |
| **Amassar** | Tanque passa por cima | Vira módulo de aríete. |
| **Onda de choque do passo** | Walker pisando | Vira módulo de impacto. |
| **Afogar** | Água | Remover. |
| **Restrição de ambiente** | Erekir não pode em espaço | Revisar para as unidades aéreas escolhidas. |
| **Virar de lado** | Navios com armas independentes | Perfeito para naves capitais. |
| **Carga** | Transporte | Vira módulo de porão. |
| **Construção/mineração** | Drones de apoio | Viram módulos de oficina/mineração. |
| **Antiaéreo** | Algumas armas | Vira papel de interceptor. |
| **Imunidades** | Fogo, derretimento | Viram módulos de blindagem. |

### 7.4 O que não converte diretamente

Alguns comportamentos são **dependentes do chão ou da água** e não devem ser copiados:

- movimento naval e afogamento;
- esteiras e marcas de esteira;
- pernas e pathfinding de walker;
- colisão de tanque como está;
- poças de líquido e regeneração por líquido;
- IA de suicídio presa ao chão;
- mapas navais.

Esses itens devem ser **aposentados** ou **reimplementados** como módulos novos. O anexo marca cada um.

---

## 8. Catálogo de módulos

### 8.1 Princípio

Não transformar cada unidade em um módulo. O objetivo é capturar a **identidade**, não clonar o catálogo. Uma unidade pode contribuir com:

- uma habilidade;
- uma arma;
- um atributo passivo;
- ou apenas uma referência visual.

O catálogo abaixo propõe **12 módulos centrais**, **6 secundários** e **2 opcionais**. É um ponto de partida, não uma lista fechada.

### 8.2 Módulos centrais

| Módulo | Origem | Efeito | Tipo | Prontidão |
|---|---|---|---|---|
| **Nano-reparo** | Retusa, Cyerce, Vela | Feixe contínuo que repara aliados e construções. | Ativo | **Pronto** |
| **Campo de reparo** | Nova, Poly, Oct | Aura que repara unidades ao redor. | Passivo | **Pronto** |
| **Escudo regenerativo** | Scepter, Pulsar, Bryde | Escudo que se recarrega com o tempo. | Passivo | **Pronto** |
| **Bolha de contenção** | Quasar, Oct | Campo de força amplo ao redor da nave. | Ativo/passivo | **Pronto** |
| **Escudo defletor** | Tecta | Arco frontal que deflecte projéteis. | Ativo | **Pronto** |
| **Campo de energia** | Aegires | Aura de dano contínuo + cura de aliados. | Passivo | **Pronto** |
| **Sobrecarga** | Oxynoe | Aumenta velocidade/cadência dos aliados próximos. | Ativo | **Pronto** |
| **Supressão** | Navanax, Quell, Disrupt | Enfraquece escudos, reparos e velocidade inimiga. | Passivo | **Pronto** |
| **Interceptador** | Oxynoe, Aegires | Abate mísseis e projéteis inimigos. | Passivo | **Pronto** |
| **Ogiva de impacto** | Crawler | Autodestruição com explosão em área. | Ativo | **Adaptar** |
| **Aríete** | Vanquish, Conquer | Dano de colisão ao avançar contra o alvo. | Ativo | **Adaptar** |
| **Baía de drones** | Anthicus, Quell, Disrupt, Latum | Lança drones ou mísseis auxiliares. | Ativo | **Pronto** |

### 8.3 Módulos secundários

| Módulo | Origem | Efeito | Tipo | Prontidão |
|---|---|---|---|---|
| **Mina de proximidade** | Retusa | Solta minas que explodem ao se aproximar. | Ativo | **Adaptar** |
| **Onda de choque** | Tecta, Collaris | Dano em área ao pousar ou avançar. | Ativo | **Adaptar** |
| **Enxame póstumo** | Latum | Solta drones quando a nave é destruída. | Passivo | **Pronto** |
| **Blindagem reativa** | Tanques, Atrax | Aumenta resistência e imunidades. | Passivo | **Pronto** |
| **Porão de carga** | Quad, Oct, Mega | Transporta recursos, blocos ou unidades. | Passivo | **Pronto** |
| **Oficina de bordo** | Vela, Retusa, Cyerce, Evoke | Constrói e repara estruturas em campo. | Ativo | **Pronto** |

### 8.4 Módulos opcionais

| Módulo | Origem | Efeito | Tipo | Prontidão |
|---|---|---|---|---|
| **Regeneração** | Neoplasm | Autocura lenta e constante. | Passivo | **Pronto** |
| **Rastro iônico** | Elude | Rastro visual e sensação de skimmer. | Passivo | **Pronto/cosmético** |

### 8.5 Legenda de prontidão

- **Pronto:** a habilidade ou arma já existe de forma genérica e pode ser anexada a uma unidade aérea com ajustes de dados.
- **Adaptar:** existe, mas está presa a movimento, terreno ou arma específica; precisa de uma camada de conversão.
- **Novo:** não existe equivalente direto; exigiria sistema novo.

### 8.6 O que não modularizar

- **Afogamento, esteira, perna e poça de líquido.** São dependentes de terreno e não fazem sentido no espaço.
- **Cada arma como um módulo.** Armas devem ocupar encaixes de arma; módulos são habilidades e comportamentos.
- **Cada unidade como um módulo.** 38 módulos seria buraco de UI e de balanceamento.
- **Aumentos puros de número.** “+10% de dano” não é uma escolha; é uma armadilha de progressão.
- **A árvore de pesquisa antiga.** A progressão deve ser de blueprints e marcos, não de nós do Mindustry.

### 8.7 Exemplos de build

| Casco | Módulos | Fantasia |
|---|---|---|
| **Flare** | Sobrecarga + Escudo defletor | Interceptador de hit-and-run. |
| **Zenith** | Baía de drones + Nano-reparo | Gunship de escolta com enxame. |
| **Antumbra** | Campo de energia + Blindagem reativa | Nave de linha que aguenta e punir. |
| **Eclipse** | Aríete + Supressão + Bolha de contenção | Capital de cerco que quebra defesas. |
| **Oct** | Porão de carga + Oficina de bordo | Nave-mãe de logística e reparo. |

---

## 9. O que fazer com as unidades terrestres e navais

### 9.1 Três opções

| Opção | O que acontece | Prós | Contras |
|---|---|---|---|
| **A. Desmontar e aposentar** | Deixam de ser jogáveis; viram módulos, armas e referências. | Simplicidade, foco no espaço, menos conteúdo morto. | Perda de silhuetas icônicas no campo. |
| **B. Facção inimiga de superfície** | Continuam como inimigos em mapas especiais. | Variedade de inimigos, justifica terreno. | Exige mapas terrestres/navais e mais balanceamento. |
| **C. Skins e relíquias** | A arte vira skin, destroço ou módulo visual. | Barato, preserva identidade, dá lore. | Não é conteúdo jogável por si só. |

**Recomendação:** **A + C**. Desmontar como conteúdo jogável e preservar a identidade em módulos, skins, destroços e inimigos pontuais. A opção B só vale se houver uma decisão de produto clara para mapas de superfície.

### 9.2 A narrativa que explica a ideia

> **As unidades terrestres e navais são projetos da guerra antiga. A frota espacial recupera esses projetos e os adapta em módulos.**

Isso resolve a pergunta “por que um tanque virou um módulo de nave?”:

- a blindagem do Conquer vira **blindagem reativa**;
- o canhão do Omura vira **railgun capital**;
- o crawler vira **ogiva de impacto**;
- o sistema de reparo da Retusa vira **nano-reparo**;
- o escudo da Tecta vira **escudo defletor**.

O módulo carrega o nome e o ícone da origem. O jogador aprende a história do jogo pelo equipamento.

### 9.3 O que preservar da arte

- silhuetas de tanques e navios como ícones de módulo;
- torres e canhões como modelos de arma;
- cores de facção e efeitos de disparo;
- sons de motor, recarga e impacto;
- nomes próprios como “Conquer”, “Omura”, “Retusa”, “Tecta”.

---

## 10. Como o jogador vê isso

### 10.1 Tela de preparação

O jogador escolhe:

1. **Casco:** Flare, Zenith, Eclipse etc.
2. **Armas:** uma ou duas, conforme o casco.
3. **Módulos:** ativos e passivos, dentro do limite de encaixes.
4. **Carga:** recursos, drones ou suprimentos.
5. **Custo:** o que será retirado do inventário global.

A tela deve mostrar:

- silhueta da nave;
- encaixes vazios e ocupados;
- barras de vida, velocidade, energia e peso;
- comparação antes/depois;
- custo e tempo de fabricação;
- avisos de incompatibilidade.

### 10.2 Durante o combate

- **Dois botões ativos no máximo.** Mais que isso vira microgestão.
- **Ícones passivos** para escudo, reparo, supressão e ponto de defesa.
- **Feedback claro** quando um módulo ativa, entra em recarga ou é anulado por EMP.
- **Leitura de papel:** cor e ícone diferentes para interceptor, bombardeiro, gunship, capital e suporte.

### 10.3 Progressão

- **Cascos** evoluem por tier, como hoje.
- **Módulos** são desbloqueados por blueprints, marcos ou recuperação em expedições.
- **Não há árvore de pesquisa antiga.** A progressão é uma coleção de projetos, não uma teia de nós.
- **O inventário global** passa a ter um sorvedouro claro: fabricar e melhorar módulos.

### 10.4 Legibilidade

O jogador precisa reconhecer o papel da nave em um relance:

- **Flare/Avert:** pequeno, rápido, dois motores;
- **Horizon/Quell:** silhueta maior, porão visível;
- **Zenith/Antumbra:** muitos mísseis;
- **Eclipse/Oct:** silhueta de capital;
- **Mono/Poly/Mega:** drones pequenos, sem arma pesada.

---

## 11. Balanceamento e riscos

### 11.1 Matriz de papéis

| Papel | Forte contra | Fraco contra |
|---|---|---|
| **Interceptador** | Bombardeiros, transportes | Capitais, flak |
| **Bombardeiro** | Capitais, estruturas | Interceptadores |
| **Gunship** | Alvos médios, escolta | Supressão, capitais |
| **Capital** | Cerco, linhas defensivas | Torpedos, aríete, enxame |
| **Suporte** | Buffs, reparo, escudo | Assassinos, EMP |
| **ECM** | Escudos, reparos | Cinético puro |

### 11.2 Contrapartidas

- **EMP/supressão** desliga escudos e reduz reparo.
- **Ponto de defesa** abate mísseis e drones.
- **Blindagem** reduz dano cinético.
- **Aríete** pune capitais lentos.
- **Interceptador** pune bombardeiros.
- **Saturação** vence ponto de defesa; por isso ele precisa de energia/calor.

### 11.3 Limites de combinação

- **Um escudo por nave.**
- **Um reparo por nave.**
- **Uma supressão por nave.**
- **Retornos decrescentes** para módulos repetidos.
- **Peso** reduz velocidade.
- **Energia/calor** limita tempo de uso.
- **Teto de drones** por nave e por time.

Sem esses limites, escudo + reparo + supressão vira uma combinação quase invencível.

### 11.4 Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Perda de terreno | Todas as naves viram uma bola de estatísticas | Asteroides, detritos, nebulosas, camadas de altitude e rotas |
| Combinações dominantes | PvP e PvE quebram | Limites, contrapartidas e testes |
| UI pesada | Jogador não entende a preparação | Começar com 6 módulos e 2 ativos |
| Conteúdo legado | Waves, fábricas e tech tree ainda apontam para o antigo | Limpeza faseada |
| Saves antigos | Unidades removidas quebram save | Migração e aliases |
| Arte/áudio | 38 unidades órfãs | Reuso em módulos, skins e destroços |
| Restrição de ambiente | Avert/Obviate bloqueados no espaço | Revisar a flag |
| Performance | Muitos drones/mísseis | Tetos e otimização |
| Clareza | Módulos parecem “power-ups” genéricos | Origem, ícone e efeito visível |
| Narrativa | “Por que um tanque virou módulo?” | Lore de projetos antigos |
| Multiplayer | Sincronização de módulos | Decidir cedo se é prioridade |
| Localização | Nomes em inglês | Padronizar PT/EN desde o início |

### 11.5 O risco maior

O risco maior não é técnico. É de **produto**:

> Se todos voam, o jogo perde a diferença natural entre “terrestre”, “naval” e “aéreo”. A diferença precisa ser reconstruída com **papéis, módulos, altitude e obstáculos** — não com tipos de terreno.

Se essa reconstrução não acontecer, o combate vira uma disputa de números. A proposta de módulos existe justamente para evitar isso.

---

## 12. Roadmap recomendado

| Fase | Entrega | Valor | Esforço | Risco |
|---|---|---|---|---|
| **0. Inventário** | Lista fechada de unidades, módulos e decisões | Alto | Baixo | Baixo |
| **1. Prova aérea** | 3 chassis + 3 habilidades em Gier | Alto | Baixo/Médio | Baixo |
| **2. Encaixe** | Slots, custo, tela de preparação e persistência | Alto | Médio/Alto | Médio |
| **3. Arsenal** | 12 módulos + armas/efeitos re-tematizados | Alto | Alto | Médio |
| **4. Limpeza** | Aposentar terrestres/navais, waves e tech tree | Médio | Médio | Alto |
| **5. Frota** | Nave principal, apoio, progressão e balanceamento | Alto | Alto | Alto |

### 12.1 Fase 1 — a prova mais barata

Escolher três chassis que cubram três fantasias:

- **Flare** — interceptor;
- **Zenith** — gunship;
- **Eclipse** — capital.

Anexar três habilidades que já existem:

- **Escudo regenerativo** (Scepter/Pulsar/Bryde);
- **Nano-reparo** (Retusa/Cyerce/Vela);
- **Supressão** (Navanax/Quell/Disrupt).

Testar em Gier, que já é uma frente aérea. O objetivo não é balancear o jogo; é provar que a ideia é legível e divertida.

### 12.2 Quick wins

- Usar a habilidade **ArmorPlateAbility**, que já existe e não está em nenhuma unidade, como primeiro módulo passivo.
- Usar **UnitSpawnAbility**, também livre, como primeira baía de drones.
- Usar **MoveLightningAbility**, livre, como rastro elétrico de um interceptor.
- Reaproveitar o HUD de escudo que já existe para mostrar módulos ativos.
- Usar o inventário global como custo de fabricação de módulos.

### 12.3 O que não fazer primeiro

- Não criar 38 módulos.
- Não remodelar todas as unidades.
- Não remover as unidades terrestres antes de limpar waves, fábricas e tech tree.
- Não tentar balancear PvP antes de decidir se o jogo é single-player.
- Não criar uma árvore de pesquisa nova antes de validar o encaixe.

---

## 13. Perguntas abertas

1. **O roster jogável é 100% aéreo ou só a nave principal?** As naves de apoio também voam, mas podem ter regras próprias.
2. **As unidades terrestres e navais continuam como inimigas?** Em mapas de superfície ou apenas como relíquias?
3. **Quantos encaixes cada casco tem?** Leve, médio, pesado e capital precisam de números distintos.
4. **Os módulos podem ser trocados no meio da expedição?** Ou só na colônia?
5. **A nave principal é um casco existente ou um novo?** Eclipse, Oct ou um novo design?
6. **As naves de apoio são unidades separadas ou módulos de drone?** A visão pede naves menores com função econômica.
7. **A altitude vira mecânica?** `lowAltitude` já existe; pode virar camada orbital.
8. **Como o jogador entende a origem dos módulos?** Nome, ícone, lore ou os três?
9. **O jogo será single-player?** Módulos e supressão mudam muito o balanceamento PvP.
10. **O que acontece com Serpulo e Erekir?** Continuam como corpos visuais ou saem do mapa?
11. **Qual é o papel do inventário global?** Só construção ou também fabricação de módulos?
12. **Quantos módulos existem no lançamento?** 6, 12 ou 20?

---

## 14. Anexo técnico — onde a ideia toca o código

### 14.1 Mapa técnico

| Área | Arquivo principal | Papel |
|---|---|---|
| Definição das unidades | `core/src/orbital/content/UnitTypes.java` | 62 definições, armas, habilidades e atributos |
| Tipo de unidade | `core/src/orbital/type/UnitType.java` | Vida, armadura, velocidade, voo, armas e habilidades |
| Habilidades | `core/src/orbital/entities/abilities/` | 16 habilidades genéricas |
| Armas de suporte | `core/src/orbital/type/weapons/` | Feixe de reparo, ponto de defesa |
| Projéteis | `core/src/orbital/entities/bullet/` | Cinético, laser, míssil, rail, EMP, flak |
| Tipos especiais | `core/src/orbital/type/unit/` | Tanque, Erekir, míssil, neoplasm |
| Ondas | `core/src/orbital/game/Waves.java` | Tabelas e geração procedural |
| Gier | `core/src/orbital/maps/planet/AsteroidGenerator.java` | Ondas apenas aéreas |
| Fábricas e montagem | `core/src/orbital/content/Blocks.java` | Produção de unidades terrestres/navais |
| Árvores de pesquisa | `core/src/orbital/content/ErekirTechTree.java` e `SerpuloTechTree.java` | Referências antigas |
| Preparação | `core/src/orbital/ui/dialogs/LaunchLoadoutDialog.java` | Loadout de base, não de unidade |
| HUD | `core/src/orbital/ui/fragments/HudFragment.java` | Escudo, habilidades, payload |
| Saves | `core/src/orbital/game/Saves.java` | Persistência de unidades e setores |
| Menu | `core/src/orbital/graphics/MenuRenderer.java` | Unidades aéreas no fundo do menu |

### 14.2 Flags e campos que precisam de atenção

| Campo | Hoje | Efeito | Ação proposta |
|---|---|---|---|
| `flying` | Só unidades aéreas | Define o domínio | Padrão da frota |
| `lowAltitude` | Alguns caças/bombardeiros | Camada baixa | Pode virar altitude orbital |
| `naval` | Unidades de água | Movimento restrito | Remover ou reinterpretar |
| `envDisabled = Env.space` | Avert/Obviate bloqueados | Não podem ser usados no espaço | Revisar para a frota |
| `crushDamage` | Tanques | Dano ao passar por cima | Virar módulo aríete |
| `legSplashDamage` | Tecta/Collaris | Dano ao pisar | Virar onda de choque |
| `canDrown` | Unidades terrestres | Afogamento | Remover |
| `faceTarget` | Navios viram de lado | Armas independentes | Manter em naves capitais |
| `payloadCapacity` | Transportes | Carga | Módulo de porão |
| `buildSpeed` / `mineSpeed` | Drones | Construção/mineração | Módulos de oficina/mineração |
| `targetAir` | Variado | Alvo aéreo | Papel de interceptor/antiaéreo |
| `abilities` | Lista genérica | Habilidades | Base dos módulos |
| `weapons` | Lista genérica | Armas | Base dos encaixes de arma |
| `Waves.generate(..., airOnly, ...)` | Gier usa `airOnly=true` | Ondas aéreas | Regra global do Orbital |

### 14.3 Inventário de habilidades do motor

| Habilidade | Onde aparece hoje | Situação | Potencial |
|---|---|---|---|
| **ShieldRegenFieldAbility** | Scepter, Pulsar, Bryde | Usada | Escudo regenerativo |
| **RepairFieldAbility** | Nova, Poly, Oct | Usada | Campo de reparo |
| **ForceFieldAbility** | Quasar, Oct | Usada | Bolha de contenção |
| **ShieldArcAbility** | Tecta | Usada | Escudo defletor |
| **EnergyFieldAbility** | Aegires | Usada | Campo de energia |
| **StatusFieldAbility** | Oxynoe | Usada | Sobrecarga |
| **SuppressionFieldAbility** | Navanax, Quell, Disrupt | Usada | Supressão |
| **SpawnDeathAbility** | Latum | Usada | Enxame póstumo |
| **MoveEffectAbility** | Elude | Usada | Rastro iônico |
| **RegenAbility** | Neoplasm | Usada | Regeneração |
| **LiquidExplodeAbility** | Neoplasm | Usada | Adaptar ou aposentar |
| **LiquidRegenAbility** | Neoplasm | Usada | Aposentar (só chão) |
| **ArmorPlateAbility** | Nenhuma | Livre | Blindagem reativa |
| **UnitSpawnAbility** | Nenhuma | Livre | Baía de drones |
| **MoveLightningAbility** | Nenhuma | Livre | Rastro elétrico |
| **EmptyDataAbility** | Rede/IO | Técnica | Não é gameplay |

**Leitura:** 12 das 16 habilidades já aparecem em alguma unidade. Três estão livres e são candidatas diretas a módulo. A base técnica é maior do que a ausência de um sistema de encaixes sugere.

### 14.4 IDs por família

- **Terrestres (28):** `dagger`, `mace`, `fortress`, `scepter`, `reign`, `nova`, `pulsar`, `quasar`, `vela`, `corvus`, `atrax`, `crawler`, `spiroct`, `arkyid`, `toxopid`, `merui`, `cleroi`, `anthicus`, `tecta`, `collaris`, `stell`, `locus`, `precept`, `vanquish`, `conquer`, `elude`, `latum`, `renale`.
- **Navais (10):** `risso`, `minke`, `bryde`, `sei`, `omura`, `retusa`, `oxynoe`, `cyerce`, `aegires`, `navanax`.
- **Aéreas (20):** `flare`, `horizon`, `zenith`, `antumbra`, `eclipse`, `avert`, `obviate`, `quell`, `disrupt`, `mono`, `poly`, `mega`, `quad`, `oct`, `evoke`, `incite`, `emanate`, `alpha`, `beta`, `gamma`.
- **Internas (4):** `block`, `manifold`, `assemblyDrone`, `dummy`.

### 14.5 Checklist de migração

1. Fechar a lista de cascos aéreos jogáveis.
2. Corrigir a restrição de ambiente de Avert e Obviate.
3. Definir o modelo de dados de módulo: encaixes, custo, peso, energia.
4. Criar a tela de preparação e a persistência por nave.
5. Implementar os 6 módulos do MVP.
6. Re-tematizar armas e efeitos de 3 a 6 cascos.
7. Aplicar a regra “apenas aéreas” nas ondas do Orbital.
8. Desativar fábricas e tech tree de unidades terrestres/navais.
9. Criar migração/aliases para saves antigos.
10. Revisar localização, arte, áudio e ícones.
11. Testar combinações: escudo + reparo + supressão, ponto de defesa + mísseis, aríete + capital.
12. Documentar a origem de cada módulo para o jogador.

---

## 15. Conclusão

O sistema de unidades do Orbital já tem uma base rica: 62 definições, 16 habilidades, armas variadas e um roster aéreo que cobre interceptação, bombardeio, gunship, capital, suporte e logística. O problema não é falta de conteúdo. É excesso de conteúdo preso a domínios que o jogo não usa.

A proposta de **frota aérea + módulos** resolve esse descompasso:

- mantém o que já funciona e já aparece em Gier;
- dá função às 38 unidades terrestres e navais;
- cria personalização real, alinhada à visão de “a personalização deve mudar a forma de enfrentar a expedição”;
- evita mapas navais e pathfinding terrestre;
- aproveita habilidades genéricas que o motor já oferece.

O caminho recomendado é começar pequeno:

1. **Provar** com três chassis e três habilidades em Gier.
2. **Encaixar** com slots, custo e tela de preparação.
3. **Expandir** para 12 módulos e re-tematizar armas e efeitos.
4. **Limpar** o conteúdo legado.
5. **Escalar** para a nave principal e a frota.

O que não deve ser feito é tentar preservar os três domínios ao mesmo tempo. Isso manteria o jogo preso a uma estrutura que a ficção espacial não sustenta.

> **O chão e o mar não somem: viram equipamento.**
