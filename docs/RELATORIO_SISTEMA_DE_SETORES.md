# Relatório — Sistema de Setores do Orbital

**Tipo:** análise de produto e reaproveitamento  
**Data:** 2 de novembro de 2026  
**Base:** código do protótipo Orbital e documento `docs/VISAO_DE_JOGO_ORBITAL.md`  
**Público:** design, produção e liderança técnica  
**Leitura:** não exige conhecimento de programação; o anexo final é destinado à equipe técnica.

---

## 1. Resumo executivo

O sistema de setores é a estrutura que organiza o mundo de Orbital em três escalas: **mapa estelar**, **planeta** e **mapa local de construção**. Ele já resolve boa parte dos problemas de uma campanha de colônias: guardar o progresso de cada lugar, exibir o mapa, liberar novos destinos, preparar o pouso, simular produção enquanto o jogador está em outro lugar, gerar ondas de inimigos e registrar estatísticas.

Hoje, porém, Orbital usa apenas uma fração pequena desse sistema:

- **Quatia:** um único setor pacífico, com mapa próprio e produção local.
- **Gier:** um único setor de asteroide, com expedição de 30 minutos, ondas e inventário global.
- **Serpulo e Erekir:** continuam no mapa como corpos visuais; suas campanhas, setores e árvores de pesquisa ainda existem no código, mas não têm gerador e não são jogáveis.
- **Notva, Verilus e Tantros:** corpos visuais ou inacessíveis, sem campanha ativa.

O maior ativo não é uma tela ou um bloco isolado, e sim o **modelo de setor**: uma ficha persistente de estado + um save completo do mapa, com regras próprias. Esse modelo permite escalar de uma colônia para dezenas de colônias sem carregar todos os mapas ao mesmo tempo.

O maior risco é que o sistema foi desenhado para a campanha do Mindustry. Ele é fortemente acoplado a lançamento entre setores, bases inimigas, árvore de pesquisa e “launch pads”. Para Orbital, a recomendação é manter o modelo e adaptar as regras, não copiar a campanha antiga.

**Recomendação em três fases:**

1. Consolidar Quatia como colônia-modelo e generalizar a expedição de Gier para várias estrelas.
2. Transformar o setor em colônia de fato: identidade, painel, produção e logística.
3. Adicionar simulação offline, ameaças e economia de longo prazo.

---

## 2. Como o sistema funciona hoje

### 2.1 As três escalas

```text
Mapa estelar
  └─ Planeta
       ├─ Setor A  →  mapa local + save + ficha
       ├─ Setor B  →  mapa local + save + ficha
       └─ Setor C  →  mapa local + save + ficha
```

- **Mapa estelar:** planetas, luas e asteroides; o jogador escolhe onde entrar.
- **Planeta:** uma grade de setores (ou um único setor, no caso de Quatia e Gier). Cada setor tem estado próprio.
- **Mapa local:** a partida de construção e combate. Só um setor é carregado por vez.

### 2.2 Ficha e save

Cada setor tem duas partes:

| Parte | O que guarda | Onde fica |
|---|---|---|
| **Ficha resumida** | Nome, ícone, recursos conhecidos, estoque, produção, exportação/importação, ondas, tempo, tentativas | Configurações do jogo |
| **Save do mundo** | Terreno, construções, unidades, regras e histórico da partida | Arquivo separado por setor |

Quando o jogador entra, o jogo combina os dois. Quando sai, grava os dois. É isso que permite voltar exatamente para a base construída.

### 2.3 O relógio do universo

O “Universo” mantém o tempo global e executa turnos de 2 minutos. A cada turno:

- setores com base e sem ataque acumulam produção;
- exportações são transferidas para o destino;
- setores sob ataque ficam congelados;
- pode ser sorteada uma invasão.

Isso só acontece **com o jogo aberto** e, na prática, só para o **planeta atual**. A exceção é o relógio da expedição, que usa tempo real e continua com o jogo fechado.

### 2.4 Estados de um setor

| Estado | O que significa na prática |
|---|---|
| **Sem base** | Setor desconhecido, bloqueado ou apenas inimigo. Pode ser alvo de pouso. |
| **Com base** | Colônia ativa. Produz, guarda recursos e pode ser visitada. |
| **Sob ataque** | Setor com ondas ou base inimiga ativa. Fica congelado enquanto o jogador não entra. |
| **Em jogo** | O mapa local está carregado; a simulação completa está rodando. |
| **Bloqueado** | Exige desbloqueio, escudo ou pré-requisito. A UI mostra o motivo. |

---

## 3. Catálogo de funções

**Legenda de estado:**

- **Ativo** — usado hoje no Orbital.
- **Disponível** — pronto no motor, mas sem uso atual no Orbital.
- **Parcial** — existe, mas precisa de ajuste para o produto.
- **Legado** — pertence à campanha antiga; não recomendado como base.

### 3.1 Navegação e mapa

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Mapa estelar 3D | Planetas, órbitas, zoom, rotação e seleção | Ativo | Alto |
| Vista do sistema | Câmera no sol, com planetas e órbitas ao redor | Ativo | Médio |
| Grade de setores | Divide o planeta em dezenas ou centenas de setores vizinhos | Disponível | Alto |
| Setor único | Planeta ou asteroide com um só setor, sem grade | Ativo | Alto |
| Seleção e inspeção | Hover, clique, duplo clique e painel do setor | Ativo | Alto |
| Lista e busca de colônias | Lista de setores com base, ordenada por ataque e tempo | Ativo | Alto |
| Destaques visuais | Setor atual, selecionado, atacado, bloqueado, escudo e rotas | Parcial | Alto |
| Apresentação de novo setor | Aviso e animação ao desbloquear um setor | Disponível | Médio |

### 3.2 Criação e variação de mundos

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Gerador procedural | Terreno, minérios, bases, regras e clima por planeta | Ativo em Quatia | Alto |
| Gerador de asteroide | Campo de asteroides, ambiente espacial e spawns | Ativo em Gier | Alto |
| Gerador em branco | Mapa autoral com controle total do conteúdo | Ativo em Quatia | Alto |
| Preset de setor | Mapa nomeado, com dificuldade, ondas e regras próprias | Disponível | Alto |
| Tamanho e semente | Mapa reproduzível e tamanho por setor | Ativo | Médio |
| Projeção 3D → 2D | Traduz a posição do planeta no mapa local | Ativo | Médio |
| Editor de setor | Gera um setor por planeta, ID e semente | Disponível | Baixo |

### 3.3 Persistência e ciclo de vida

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Save por setor | Um arquivo por setor, com retomada exata | Ativo | Alto |
| Autosave e backup | Gravação periódica e cópia de segurança | Ativo | Alto |
| Preview do save | Miniatura do mapa salvo | Ativo | Médio |
| Ficha do setor | Resumo persistente independente do save do mundo | Ativo | Alto |
| Retomada, abandono e limpeza | Continuar, abandonar ou apagar o estado do setor | Ativo | Alto |
| Remapeamento de saves | Move o save quando o setor muda de ID | Legado | Baixo |
| Importar/exportar save | Troca de arquivos e checagem de mods ausentes | Disponível | Médio |

### 3.4 Economia e logística

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Estoque do núcleo | Itens e capacidade de armazenamento | Ativo | Alto |
| Produção e consumo | Médias de produção bruta e líquida | Ativo/Parcial | Alto |
| Exportação e importação | Estatísticas por item e por rota | Parcial | Alto |
| Transferência entre setores | Envio automático por launch pads | Disponível | Médio |
| Redirecionar destino | Muda para onde um setor exporta | Disponível | Médio |
| Simulação por turnos | Produção acumulada a cada 2 minutos | Ativo | Alto |
| Rotas no mapa | Arcos de importação e exportação | Ativo | Médio |
| Recursos conhecidos | Varredura do terreno para listar o que existe no setor | Ativo | Médio |

### 3.5 Progressão e conquista

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Setor inicial | Ponto de entrada do planeta | Ativo | Alto |
| Último setor | Marca o fim da campanha | Legado | Baixo |
| Desbloqueio por pesquisa | Setor exige um nó da árvore de tecnologia | Legado | Médio |
| Objetivos | Condições para liberar um setor | Legado | Médio |
| Escudos | Setor protegido até outro ser capturado | Disponível | Médio |
| Captura por ondas | Vencer a onda-alvo | Disponível | Alto |
| Captura por ataque | Destruir as bases inimigas | Disponível | Alto |
| Ataque após ondas | Vira modo ataque ao limpar as ondas | Disponível | Médio |
| Ameaça | Cálculo por bases vizinhas e dificuldade | Ativo na exibição | Médio |
| Histórico do setor | Tentativas, tempo, ondas e se já foi capturado | Ativo | Médio |

### 3.6 Pouso, lançamento e preparação

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Regras de pouso | Adjacência, base, escudo e desbloqueio | Parcial | Alto |
| Origem automática | Escolhe um setor próximo com recursos | Disponível | Alto |
| Pouso grátis | Entrada sem setor de origem, usada no início | Ativo | Alto |
| Loadout | Escolha de esquema de base e recursos | Disponível | Alto |
| Capacidade de carga | Limite por núcleo e por planeta | Disponível | Alto |
| Consumo de recursos | Retira os itens do setor de origem | Disponível | Alto |
| Lançamento interplanetário | Acelerador entre planetas | Legado | Baixo |
| Animação de lançamento | Nave subindo e pousando | Disponível | Médio |

### 3.7 Combate e ameaças

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Ondas procedurais | Tipos de unidade, tiers, escudos e chefes | Ativo em Gier | Alto |
| Modo ataque | Base inimiga com núcleo para destruir | Disponível | Alto |
| Base inimiga | Geração de base e pontos de spawn | Disponível | Alto |
| Invasões | Ataque aleatório a um setor com base | Disponível | Alto |
| Setor atacado | Congela produção e avisa o jogador | Parcial | Alto |
| Perda e reconstrução | Dano, entulho e planos para reconstruir | Disponível | Alto |
| Dificuldade | Casual, fácil, normal, difícil e eradicação | Ativo/Parcial | Alto |
| Avisos de chefe | Antecedência de ondas especiais | Ativo | Médio |
| IA de construção/RTS | Inimigo constrói e ataca de forma autônoma | Disponível | Médio |

### 3.8 Ambiente e apresentação

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Ambiente por planeta | Terrestre, espaço, fogo, água e outros | Ativo | Alto |
| Clima | Neve, chuva, neblina, areia e esporos | Disponível | Médio |
| Dia e noite | Iluminação conforme a posição orbital | Ativo em Quatia | Alto |
| Atmosfera, nuvens e cores | Identidade visual de cada corpo | Ativo | Médio |
| Música | Tema de lançamento e ambiente | Ativo | Médio |
| Efeitos de captura e ruína | Poeira, entulho e destruição | Disponível | Médio |

### 3.9 Interface e feedback

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Painel do setor | Estatísticas completas do setor | Ativo | Alto |
| Nome e ícone | Personalização da colônia | Ativo | Alto |
| Abandonar | Remove a base e limpa o estado | Ativo | Médio |
| Notificações | Novo setor, ataque, captura e perda | Ativo | Alto |
| Dificuldade | Tela de regras da campanha | Ativo/Parcial | Médio |
| Tela de expedição | Status, tempo, inventário global e carga | Ativo | Alto |
| HUD de expedição | Relógio e botão de coleta | Ativo | Alto |
| Saves com preview | Lista de saves com miniatura | Ativo | Médio |
| Submissões da comunidade | Links para mapas enviados pela comunidade | Legado | Baixo |
| Tela de campanha concluída | Aviso de fim de campanha | Legado | Baixo |

### 3.10 Expedição em tempo real e inventário global

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Relógio em tempo real | Prazo persistido nas configurações, continua com o jogo fechado | Ativo | Alto |
| Avisos de tempo | Avisos aos 10, 5 e 1 minutos restantes | Ativo | Alto |
| Retirada forçada | Encerra a partida e volta ao mapa quando o prazo vence | Ativo | Alto |
| Coleta manual de carga | Move o núcleo para o inventário global | Ativo | Alto |
| Coleta automática | Recolhe a carga antes da retirada forçada | Ativo | Alto |
| Inventário global | Armazenamento do jogador, independente do save do setor | Ativo | Alto |
| Bloqueio de entrada | Só entra em Gier com expedição ativa | Ativo | Alto |
| Várias expedições | Ainda não existe | Ausente | Alto |
| Relógio por estrela | Ainda não existe; o relógio é único e fixo em Gier | Ausente | Alto |

### 3.11 Estatísticas, eventos e ferramentas

| Função | O que já existe | Estado | Potencial |
|---|---|---|---|
| Estatísticas por planeta | Tempo jogado, setores capturados/perdidos, ondas, construções e unidades | Ativo | Médio |
| Eventos de setor | Lançamento, captura, invasão, loadout e turno | Ativo | Alto |
| Hooks de gerador | Ações ao capturar, perder e antes de salvar | Ativo | Médio |
| Ferramentas de editor | Geração de setor e seleção de setor | Disponível | Baixo |
| Multiplayer | Sincronização básica de regras e captura | Parcial | Baixo |
| Conquistas/Steam | Conquistas ligadas à campanha antiga | Legado | Baixo |

---

## 4. Retrato atual do Orbital

| Corpo | Tipo | Setores | Jogável? | Situação |
|---|---|---:|---|---|
| **Quatia** | Planeta | 1 | Sim | Colônia pacífica de produção; mapa autoral com cobre, chumbo, carvão e areia |
| **Gier** | Asteroide | 1 | Sim | Expedição de 30 minutos; ondas, coleta de carga e inventário global |
| **Serpulo** | Planeta | Grade 3 | Não | Campanha antiga desativada; continua como corpo visual |
| **Erekir** | Planeta | Grade 2 | Não | Campanha antiga desativada; continua como corpo visual |
| **Notva** | Asteroide | 1 | Não | Corpo visual inacessível |
| **Verilus** | Asteroide | 1 | Não | Corpo visual inacessível |
| **Tantros** | Planeta | Grade 2 | Não | Oculto e inacessível |
| **Sol** | Estrela | 0 | Não | Centro visual do sistema |

**O que está realmente em uso hoje:**

- mapa estelar e seleção de planetas;
- setor único com mapa autoral;
- save por setor, ficha do setor, autosave e preview;
- painel de estatísticas, nome e ícone;
- geração de ondas em Gier;
- iluminação de dia/noite em Quatia;
- relógio de expedição, coleta de carga e inventário global;
- notificações e HUD.

**O que está pronto no motor, mas dorme no Orbital:**

- grade de setores com dezenas de territórios;
- presets de setor com desbloqueio e objetivos;
- loadout de pouso e consumo de recursos;
- logística entre setores;
- invasões e perda de setor;
- geração de base inimiga;
- clima;
- captura por ondas ou por ataque;
- tela de campanha concluída;
- editor de setores.

**O que é legado e não deve ser a base do produto:**

- campanhas, presets e árvores de pesquisa de Serpulo e Erekir;
- submissões de setores da comunidade;
- remapeamento de saves antigos;
- conquistas e estatísticas presas à campanha antiga;
- launch pads e aceleradores como estão;
- textos e conteúdo específicos do Mindustry.

---

## 5. O que podemos aproveitar

### Prioridades

| Prioridade | Oportunidade | Valor | Esforço | Risco |
|---|---|---|---|---|
| 1 | Generalizar a expedição para várias estrelas | Alto | Médio | Médio |
| 2 | Transformar o setor em colônia de fato | Alto | Médio/Alto | Médio |
| 3 | Simulação de colônia em segundo plano/offline | Alto | Alto | Alto |
| 4 | Logística entre colônias | Alto | Alto | Alto |
| 5 | Ameaças e defesa de colônias | Alto | Médio/Alto | Alto |
| 6 | Preparação de expedição com loadout | Alto | Médio | Médio |
| 7 | Progressão e desbloqueio | Médio | Médio | Médio |
| 8 | Painel e identidade da colônia | Alto | Baixo/Médio | Baixo |
| 9 | Quick wins de save, stats e notificações | Médio | Baixo | Baixo |

### 5.1 Generalizar a expedição para várias estrelas

**O que já existe:** relógio em tempo real, avisos, retirada forçada, coleta de carga, inventário global.

**O que falta:** o relógio é único, fixo em Gier e espalhado por textos e fluxos. Para Orbital, cada estrela vermelha precisa de sua própria expedição: duração, prazo, avisos, estado e setor.

**Como aproveitar:**

- transformar o relógio em um dado de expedição, com setor, início, duração e status;
- permitir uma ou várias expedições ativas, conforme decisão de produto;
- mostrar a lista de estrelas com prazo e status no mapa;
- manter a retirada forçada e a coleta automática;
- generalizar os textos e remover a referência fixa a Gier.

**Esforço:** médio.  
**Risco:** duplicação de carga e inconsistência de save se a coleta e a gravação não forem tratadas juntas.

### 5.2 Transformar o setor em colônia de fato

**O que já existe:** setor com nome, ícone, save, estatísticas, recursos e histórico.

**O que falta:** o setor ainda é um “contêiner de mapa”, não uma colônia com identidade e evolução. Também falta decidir se um planeta é uma colônia ou várias colônias-setor.

**Como aproveitar:**

- usar o setor como unidade de colônia;
- manter nome, ícone, painel e histórico;
- usar adjacência, escudos e ameaça como território, se o planeta tiver vários setores;
- adicionar especialização, nível ou população em uma camada separada, sem sobrecarregar o mapa local;
- decidir o vocabulário do produto: “setor”, “colônia” ou “colônia-setor”.

**Esforço:** médio/alto.  
**Risco:** confusão conceitual e UI pesada se o jogador tiver dezenas de colônias.

### 5.3 Simulação de colônia em segundo plano/offline

**O que já existe:** turnos de 2 minutos, produção aproximada, estoque com limite e congelamento de setores atacados.

**O que falta:** a simulação só roda com o jogo aberto, só no planeta atual e usa a última média registrada, não uma simulação real das fábricas.

**Como aproveitar:**

- persistir o instante da última simulação;
- ao abrir o jogo, calcular o tempo passado até um limite;
- simular todos os planetas, não só o atual;
- usar taxas resumidas por colônia, não o mapa inteiro;
- definir teto de acúmulo e regras para ataques offline;
- mostrar um resumo de “enquanto você estava fora”.

**Esforço:** alto.  
**Risco:** exploração de tempo, economia desbalanceada e perda de confiança se o jogador não entender o que aconteceu.

### 5.4 Logística entre colônias

**O que já existe:** destino de exportação, estatísticas de importação/exportação, arcos no mapa e transferência por launch pads.

**O que falta:** a logística é intra-planeta, depende de blocos legados e não tem transporte físico entre planetas.

**Como aproveitar:**

- manter a ideia de rota e destino;
- estender para planetas diferentes;
- usar naves como transporte físico, com capacidade, tempo e combustível;
- substituir os launch pads por uma interface de rotas;
- mostrar rotas e gargalos no mapa estelar.

**Esforço:** alto.  
**Risco:** complexidade de economia e performance; muitas rotas podem poluir a interface.

### 5.5 Ameaças e defesa de colônias

**O que já existe:** cálculo de ameaça, chance de invasão, setor congelado, dano e reconstrução.

**O que falta:** as invasões não estão ligadas às colônias de Orbital e o estado “sob ataque” é binário e permanente enquanto o jogador está fora.

**Como aproveitar:**

- usar a ameaça por bases vizinhas e dificuldade;
- criar avisos antes do ataque;
- definir período de carência e teto de perdas;
- permitir defesa com frota ou torres;
- escolher consequências: perda de recursos, perda de construções ou perda total;
- usar o dano e o entulho para contar a história da batalha.

**Esforço:** médio/alto.  
**Risco:** frustração e save-scumming se as regras não forem claras.

### 5.6 Preparação de expedição com loadout

**O que já existe:** escolha de esquema, recursos, capacidade e consumo da origem.

**O que falta:** o loadout é de base, não de nave/frota, e não consome o inventário global.

**Como aproveitar:**

- manter a ideia de preparar antes de partir;
- trocar o esquema de base por módulos/armas da nave;
- consumir recursos do inventário global;
- mostrar capacidade de carga e custo;
- salvar a última preparação por nave.

**Esforço:** médio.  
**Risco:** UI complexa; se o custo não for claro, o jogador não entende por que não pode partir.

### 5.7 Progressão e desbloqueio

**O que já existe:** setores que exigem desbloqueio, objetivos, escudos e avisos de novo setor.

**O que falta:** a progressão atual é a árvore de pesquisa do Mindustry, que não conversa com a visão de Orbital.

**Como aproveitar:**

- manter a estrutura de “condição para liberar”;
- trocar a árvore por missões, nível de colônia ou marcos;
- usar escudos para criar território;
- usar o aviso de novo setor como recompensa visual.

**Esforço:** médio.  
**Risco:** remover a árvore antiga sem quebrar conteúdo legado; exige limpeza cuidadosa.

### 5.8 Painel e identidade da colônia

**O que já existe:** painel completo, nome, ícone, tempo, tentativas, ondas, produção, exportação, importação e estoque.

**O que falta:** o painel é de campanha, não de colônia; a UI é monolítica e cheia de elementos antigos.

**Como aproveitar:**

- reaproveitar o painel como base do “centro de comando” da colônia;
- remover ou esconder elementos de campanha antiga;
- adicionar status de expedição, ameaça e recursos globais;
- manter o rename e o ícone como identidade do jogador;
- simplificar para mobile.

**Esforço:** baixo/médio.  
**Risco:** a tela do mapa é grande e mistura muitas funções; mudanças exigem cuidado.

### 5.9 Quick wins

- Usar o preview de save para dar personalidade às colônias.
- Expor o painel de estatísticas como “histórico da colônia”.
- Usar notificações de ataque/captura para dar vida ao mapa.
- Usar o editor de setores para testar novas colônias.
- Usar os eventos para plugar economia, conquistas e sons sem alterar o núcleo.

---

## 6. O que não recomendo reaproveitar

| Item | Por quê |
|---|---|
| Campanhas de Serpulo e Erekir | Conteúdo legado, mapas e progressão que não fazem parte da visão de Orbital |
| Árvore de pesquisa do Mindustry | Não conversa com colônias, naves e expedições |
| Presets antigos | úteis como referência, mas não como conteúdo do produto |
| Submissões da comunidade | Links externos sem função no jogo |
| Remapeamento de saves antigos | Complexidade para um problema que Orbital não terá se começar limpo |
| Launch pads e aceleradores como estão | Logística frágil, intra-planeta e sujeita a exploits |
| Dano de setor completo | Pode ser simplificado para uma consequência de ataque mais legível |
| Relógio fixo em Gier | Precisa ser generalizado, não estendido |
| Conquistas/Steam antigas | Presas à campanha antiga |
| Textos e conteúdo do Mindustry | Identidade e localização precisam ser próprias |

---

## 7. Lacunas e riscos

| Lacuna | Impacto | Recomendação |
|---|---|---|
| Simulação só com o jogo aberto | Colônias não produzem enquanto o jogador está fora | Definir se haverá simulação offline e com qual teto |
| Economia só de itens | Não cobre energia, líquidos, população ou naves | Decidir se a economia será só de itens ou se terá novas camadas |
| Logística dependente de launch pads | Não serve para rotas entre planetas | Criar logística explícita com naves e capacidade |
| Estado “sob ataque” binário | Setor fica congelado e sem produção | Separar “ameaçado”, “sob ataque” e “ocupado” |
| Relógio único e fixo em Gier | Não escala para várias estrelas | Generalizar por expedição/setor |
| Save em dois lugares | Risco de inconsistência entre ficha e mundo | Tratar coleta e gravação como operação única |
| UI monolítica | Mudanças no mapa são arriscadas | Extrair painéis e fluxos aos poucos |
| Conteúdo legado carregado | Confusão e manutenção desnecessária | Isolar ou remover campanhas antigas |
| Falta de modelo de frota/colônia | O setor não expressa a fantasia do jogo | Criar camada de colônia e camada de frota |
| Localização misturada | Textos em português e inglês | Padronizar i18n desde cedo |
| Economia sem sorvedouros | Inventário global acumula sem uso | Definir custos de expansão, naves e expedições |
| Performance com muitos setores | Muitos arcos e painéis podem pesar | Testar limites e virtualizar a lista |
| Eventos incompletos | Alguns fluxos não têm gancho confiável | Revisar eventos antes de plugar sistemas |
| Multiplayer parcial | Se não for prioridade, adiciona complexidade | Decidir cedo se o jogo será single-player |

---

## 8. Perguntas abertas para decisão

1. **Planeta = uma colônia ou várias colônias-setor?** A resposta muda o mapa, a UI e a progressão.
2. **As colônias produzem com o jogo fechado?** Por quanto tempo e com qual teto?
3. **As colônias podem ser atacadas ou perdidas enquanto o jogador está fora?** Com aviso, carência ou defesa automática?
4. **Os recursos são locais, globais ou transportados?** Qual é o papel do inventário global?
5. **Quantas expedições podem ficar ativas ao mesmo tempo?** O relógio é por estrela ou global?
6. **O que acontece com a frota e a carga quando o prazo vence?** Perde, resgata ou mantém?
7. **A progressão será árvore, missões ou nível de colônia?**
8. **O jogo será single-player ou terá multiplayer?**
9. **Serpulo e Erekir continuam como corpos visuais ou serão removidos?**
10. **Qual é o papel dos setores múltiplos?** Território, rotas ou apenas organização?

---

## 9. Anexo técnico — onde cada função vive

Este anexo é para a equipe de desenvolvimento; não é necessário para a leitura do relatório.

| Área | Arquivo principal | Papel |
|---|---|---|
| Setor | `core/src/orbital/type/Sector.java` | Estado, vizinhança, ameaça, itens, save e regras do setor |
| Ficha do setor | `core/src/orbital/game/SectorInfo.java` | Produção, exportação, importação, ondas, histórico e persistência resumida |
| Preset | `core/src/orbital/type/SectorPreset.java` | Setor autoral, dificuldade, desbloqueio, captura e regras |
| Planeta | `core/src/orbital/type/Planet.java` | Grade, setores, regras, estatísticas, órbitas e lançamento |
| Grade | `core/src/orbital/graphics/g3d/PlanetGrid.java` | Subdivisão esférica e vizinhança dos setores |
| Universo | `core/src/orbital/game/Universe.java` | Relógio global, turnos, produção, exportação e invasões |
| Saves | `core/src/orbital/game/Saves.java` e `core/src/orbital/io/SaveVersion.java` | Save por setor, autosave, preview e metadados |
| Mapa e UI | `core/src/orbital/ui/dialogs/PlanetDialog.java` | Mapa estelar, seleção, painel do setor, lançamento e estatísticas |
| Pouso e loadout | `core/src/orbital/core/Control.java` e `core/src/orbital/ui/dialogs/LaunchLoadoutDialog.java` | Entrada no setor, esquema de base e consumo de recursos |
| Ondas | `core/src/orbital/game/Waves.java` e `core/src/orbital/ai/WaveSpawner.java` | Geração e execução das ondas |
| Dano de setor | `core/src/orbital/maps/SectorDamage.java` | Dano e reconstrução após perda |
| Expedição | `core/src/orbital/game/ExpeditionClock.java` | Relógio em tempo real, avisos, retirada e coleta |
| Inventário global | `core/src/orbital/game/GlobalInventory.java` | Armazenamento persistente do jogador |
| Conteúdo | `core/src/orbital/content/Planets.java` e `SectorPresets.java` | Definição dos corpos e setores |
| Geradores | `core/src/orbital/maps/generators/` e `core/src/orbital/maps/planet/` | Mapas procedurais, asteroidais e autorais |
| Regras | `core/src/orbital/game/CampaignRules.java` e `Difficulty.java` | Dificuldade, invasões, fog, RTS AI e regras de campanha |
| Estatísticas | `core/src/orbital/game/CampaignStats.java` | Tempo, setores, ondas, construções e unidades |
| Eventos | `core/src/orbital/game/EventType.java` | Ganchos de lançamento, captura, invasão, loadout e turno |
| HUD | `core/src/orbital/ui/fragments/HudFragment.java` | Ondas, avisos, expedição e coleta |
| Menu | `core/src/orbital/ui/fragments/MenuFragment.java` | Acesso ao mapa estelar e à expedição |
| Editor | `core/src/orbital/editor/SectorGenerateDialog.java` | Geração de setor para teste |

### Maturidade por área

| Área | Maturidade | Comentário |
|---|---|---|
| Fundação (setor, planeta, save) | Alta | Base sólida e já usada em Quatia/Gier |
| Economia e logística | Média | Funciona, mas é aproximada e dependente de launch pads |
| Progressão | Baixa para Orbital | Estrutura existe, conteúdo é legado |
| Expedição | Média | Protótipo funcional, mas fixo em Gier |
| Interface | Média/Alta | Poderosa, porém monolítica e cheia de legado |
| Combate/ondas | Alta | Gerador e execução prontos; falta conteúdo Orbital |
| Simulação offline | Baixa | Não existe; só o relógio da expedição continua fechado |
| Multiplayer | Baixa/Média | Sincronização parcial; decidir se é prioridade |

---

## 10. Conclusão

O sistema de setores já é uma fundação forte para Orbital. Ele resolve persistência, mapa, progressão, economia resumida, expedições e parte do combate. O caminho mais seguro não é criar um sistema novo, e sim **reaproveitar o modelo de setor** e adaptar as camadas que hoje pertencem à campanha do Mindustry.

A ordem recomendada é:

1. **Consolidar** Quatia e generalizar a expedição.
2. **Dar identidade** ao setor como colônia.
3. **Conectar** colônias por logística e ameaças.
4. **Escalar** com simulação offline e economia de longo prazo.

O que não deve ser feito é arrastar a campanha antiga inteira. Serpulo, Erekir, presets, árvore de pesquisa e launch pads devem servir como referência e, no máximo, como base pontual — não como esqueleto do produto.
