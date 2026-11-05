# Orbital — visão de jogo

**Tipo de documento:** conceito de produto e experiência do jogador; não é especificação técnica.
**Estado:** visão inicial consolidada com as decisões confirmadas por Péterson em 1 de novembro de 2026.
**Projeto:** Orbital, código aberto sob GPLv3.

---

## 1. Conceito em uma frase

**Construa colônias industriais em planetas, pilote sua nave em expedições de combate em tempo real e envie naves de apoio para minerar asteroides — tudo conectado a um inventário global do jogador.**

Orbital combina duas escalas de jogo:

- **Estratégica:** mapa estelar, planetas, produção e expansão.
- **Tática:** expedições em estrelas vermelhas, com combate pilotado e naves de apoio.

A referência de mapa estelar é o gênero de estratégia espacial. A identidade do jogo vem da produção construída no estilo Mindustry e da presença direta do jogador no combate.

---

## 2. Fantasia do jogador

O jogador é comandante de uma pequena presença espacial que cresce até se tornar uma rede de colônias. Ele não apenas escolhe onde enviar recursos: abre planetas, constrói sua produção e lidera pessoalmente expedições perigosas para conseguir materiais e fortalecer sua frota.

A experiência deve alternar entre:

1. **Planejar:** escolher um planeta, uma produção ou uma expedição.
2. **Construir:** organizar a produção local usando as possibilidades de construção do Mindustry.
3. **Preparar:** escolher armas, recursos e naves de apoio.
4. **Agir:** pilotar a nave principal e coordenar a equipe durante a expedição.
5. **Retornar:** levar os recursos recuperados ao inventário global e usá-los para expandir ou personalizar.

---

## 3. Princípios de design

### 3.1 Dois estilos de jogo, ligados pelo mesmo progresso

A construção planetária e as expedições não devem parecer jogos separados. A produção abastece as naves; as expedições trazem recursos que ampliam as opções de produção e personalização.

### 3.2 O jogador participa do combate

Nas estrelas vermelhas, o jogador controla diretamente a nave principal. O resultado depende da pilotagem, do equipamento escolhido e das decisões sobre as naves de apoio.

### 3.3 As naves menores têm função real

As naves de apoio não são apenas decoração. O jogador pode enviá-las para minerar asteroides e transportar a carga de volta. A sua presença cria uma decisão durante o combate: proteger a própria nave, cobrir as mineradoras ou priorizar a extração.

### 3.4 Produção é uma atividade jogável

Cada planeta abre um mapa local de produção. O jogador constrói e organiza a extração, o processamento, o transporte e o armazenamento, seguindo a experiência de construção do Mindustry.

### 3.5 Tempo real, sem turnos nas expedições

A estrela vermelha tem prazo real. **A contagem continua mesmo com o jogo fechado.** A expedição não espera o jogador voltar e não avança por turnos.

A decisão confirmada é sobre o relógio da estrela. Ainda não está decidido se as naves e o combate continuam simulando enquanto o jogo está fechado; este documento não assume isso.

---

## 4. Estrutura do mapa

### 4.1 Mapa estelar

O mapa estelar é a tela de navegação e visão geral. Nele, o jogador encontra sistemas, planetas e estrelas vermelhas disponíveis. A apresentação pode lembrar o gênero de mapa espacial, mas os nomes, a arte, a história e a identidade visual devem ser próprios de Orbital.

O mapa dá acesso a dois destinos principais:

- **Planetas:** abrem mapas locais de produção.
- **Estrelas vermelhas:** abrem expedições temporárias de combate e mineração.

### 4.1.1 Sistema inicial de Orbital

No mapa estelar, o sistema inicial de Orbital usa a estrela amarela já existente (`sun`) como centro e um primeiro planeta processualmente nomeado orbitando próximo a ela: **Quatia**.

Quatia é a primeira colônia jogável do protótipo. Ao selecioná-la, o jogador pode pousar em um setor inicial pacífico e construir uma produção local sobre um mapa próprio, com depósitos de cobre, chumbo, carvão e areia. Nesta primeira etapa há um único setor; ainda não existem outros planetas jogáveis nem uma economia interplanetária.

**Serpulo e Erekir deixaram de ser campanhas jogáveis.** Os corpos continuam existindo no mapa estelar como corpos visuais, mas sem campanha, setor jogável ou lançamento. A entrada de mapa no menu chama-se agora “Mapa Estelar”.

### 4.2 Planetas

Ao abrir um planeta, o jogador entra em um mapa de construção no qual pode organizar sua produção de recursos. O planeta é uma colônia jogável, não apenas um ícone no mapa.

A proposta aproveita a experiência de Mindustry: construir estruturas, organizar fluxos, manter a produção funcionando e adaptar a base às condições locais.

Diferenças entre planetas — recursos, perigos e oportunidades — devem dar razões para expandir. Os tipos exatos de planeta e a distribuição de recursos ainda não foram definidos.

### 4.3 Estrelas vermelhas

Uma estrela vermelha representa uma expedição com limite de tempo real. Ao entrar, o jogador leva uma nave principal e pode enviar naves menores para mineração e transporte de recursos.

A estrela vermelha é uma área de ação: há naves inimigas, asteroides e uma janela limitada para combater, extrair recursos e retornar a carga.

---

## 5. Expedição em estrela vermelha

### 5.1 Papel do jogador

O jogador pilota a nave principal e enfrenta as naves inimigas. A nave pode ser personalizada com armas e outros recursos de combate, efeitos e interações com as demais tropas.

A personalização deve mudar a forma de enfrentar a expedição — não apenas aumentar números. Exemplos de direções possíveis, ainda não aprovadas como lista final:

- escolher entre armas de foco, área ou controle;
- combinar efeitos que alteram movimento, defesa ou comportamento inimigo;
- usar naves aliadas para escoltar, cobrir a mineração ou distrair inimigos;
- escolher se a frota prioriza combate, extração ou retirada.

### 5.2 Naves de apoio

As naves menores podem ser enviadas para asteroides para minerar e carregar recursos. Elas criam uma segunda frente de decisão enquanto o jogador controla a nave principal.

O princípio confirmado é que os recursos minerados devem chegar ao **inventário global do jogador**. A forma exata de ordenar, proteger e recuperar essas naves ainda será definida.

### 5.3 Recompensa e retorno

O propósito da expedição é obter recursos que alimentem o progresso global. O retorno conecta o combate à produção planetária e à personalização de naves.

A regra de quando a carga é considerada segura — ao ser extraída, ao embarcar ou somente quando a nave retorna — ainda precisa ser definida para o produto final. No protótipo, a carga é considerada segura quando é recolhida do núcleo de Gier para o inventário global, seja manualmente durante a expedição ou automaticamente no momento da retirada.

### 5.4 Relógio da estrela

- A estrela é limitada por tempo real, não por turnos.
- O relógio continua correndo quando o jogo está fechado.
- A contagem começa quando o jogador inicia a expedição, não quando a estrela aparece.
- **Prazo vencido força a retirada:** a partida é encerrada e o jogador volta ao mapa estelar com um aviso. Não é uma derrota nem apaga progresso.

Valores usados no protótipo atual (Gier), ainda sujeitos a ajuste:

- duração de 30 minutos em tempo real;
- avisos na interface aos 10 minutos, 5 minutos e 1 minuto restantes;
- a base construída em Gier é salva antes da retirada e continua disponível na próxima expedição.

Essa última parte é uma simplificação do protótipo: a visão não define se, no produto final, a base permanece, é perdida ou sofre alguma consequência quando o prazo termina.

A interface deve informar claramente o prazo antes de o jogador iniciar uma expedição, e mostrar o tempo restante durante a expedição.

---

## 6. Recursos e progressão

### 6.1 Produção planetária

Os planetas fornecem uma atividade de construção e produção de recursos. As cadeias de produção devem ser compreensíveis no início e ganhar variedade conforme o jogador expande.

### 6.2 Recursos de expedição

As mineradoras enviadas às estrelas vermelhas coletam recursos de asteroides. O objetivo confirmado é transportar esses recursos para o inventário global do jogador.

### 6.3 Inventário global

O inventário global conecta a coleta nas expedições ao progresso do jogador. Ele serve como destino dos recursos transportados pelas naves de mineração.

No protótipo, o inventário global é um armazenamento persistente de itens, salvo nas configurações do jogo e independente do save do setor. Recolher a carga de Gier drena o núcleo e transfere os itens para esse inventário; o setor é salvo em seguida para que os recursos não sejam duplicados ao reentrar.

Ainda está em aberto se os recursos fabricados nos planetas também são imediatamente globais ou se permanecem locais até serem transportados. A decisão afeta a importância das rotas e da logística entre colônias.

### 6.4 Usos propostos para os recursos

Como direção de produto, os recursos podem sustentar:

- construção e expansão das colônias;
- fabricação ou melhoria de naves;
- personalização de armas e equipamentos;
- preparação de futuras expedições.

A lista de recursos e os custos de cada melhoria não foram definidos.

---

## 7. Loop principal do jogo

1. O jogador observa o mapa estelar e escolhe um planeta ou uma expedição.
2. Abre um planeta e desenvolve sua produção local.
3. Usa os recursos disponíveis para preparar a nave e escolher seu equipamento.
4. Inicia uma expedição em estrela vermelha, sabendo que o prazo continuará correndo mesmo se fechar o jogo.
5. Pilota a nave principal contra inimigos e envia naves menores para minerar asteroides.
6. Protege a extração e organiza o retorno da carga.
7. Usa os recursos recuperados no inventário global para desenvolver colônias e personalizar a frota.
8. Volta ao mapa estelar com novas opções de expansão e expedições.

O ciclo deve oferecer escolhas nos dois lados: **como construir melhor** e **como conduzir melhor a expedição**.

---

## 8. Diferença competitiva

Orbital usa a familiaridade do mapa espacial e da expansão entre planetas, mas propõe uma combinação própria:

- os planetas são mapas produtivos construídos pelo jogador;
- a expedição não é apenas uma ordem para a frota: o jogador pilota a nave principal;
- as naves menores realizam mineração e transporte sob pressão;
- as armas, os efeitos e a interação entre tropas fazem parte da preparação e do combate;
- o prazo real mantém as estrelas vermelhas como oportunidades limitadas, mesmo quando o jogo está fechado.

A intenção é competir com uma experiência diferente, não reproduzir nomes, história, arte ou apresentação específica de Hades' Star: DARK NEBULA.

---

## 9. Estado do protótipo

### Fase 1 — colônia inicial de produção

**Implementada no código; build desktop validado.** A primeira versão jogável está limitada à colônia de Quatia e preserva as campanhas antigas como referência visual, sem removê-las do conteúdo do projeto.

- Quatia pode ser selecionada no mapa estelar e aberta como uma colônia de produção.
- O setor inicial é pacífico e tem depósitos de cobre, chumbo, carvão e areia.
- O jogador recebe materiais iniciais e acesso às estruturas necessárias para começar a extrair, transportar e gerar energia.
- A cadeia de demonstração usa o conteúdo existente: carvão e areia alimentam a fundição de silício, que precisa de energia.
- O mapa e a partida continuam usando a construção e a simulação local do Mindustry.

### Fase 2 — expedição de Gier e inventário global

**Implementada no código; build desktop validado.** A expedição asteroidária usa o relógio em tempo real e conecta a coleta de Gier ao inventário global do jogador.

- Gier pode ser iniciada pela tela de expedição e aberta pelo mapa estelar.
- O prazo de 30 minutos continua correndo com o jogo fechado; avisos aparecem aos 10, 5 e 1 minutos restantes.
- Ao vencer o prazo, o jogador é retirado para o mapa estelar, com a base salva para a próxima expedição.
- O HUD mostra o tempo restante e permite recolher a carga do núcleo.
- A tela da expedição mostra o inventário global e a carga atualmente no núcleo de Gier.
- Recolher a carga drena o núcleo, transfere os itens para o inventário global e salva o setor imediatamente.
- Na retirada, a carga restante no núcleo é recolhida automaticamente antes do save, evitando duplicação.
- Finalizar a expedição fora de Gier avisa que a carga continua na base e só pode ser recolhida entrando na expedição.

O inventário global ainda não é consumido por custos de construção, fabricação de naves ou preparação de expedições. A personalização da nave principal, a economia de equipamentos e as naves de apoio dedicadas continuam como etapas posteriores. A pilotagem e a mineração usam os sistemas de unidade e `MinerAI` já existentes no motor.

### Próximo recorte de protótipo

O próximo ciclo deve dar uso ao inventário global: custos para preparar expedições, fabricação ou melhoria de naves e uma primeira distinção entre recursos locais e globais. Também é o momento de definir a regra definitiva de perda ou preservação da carga quando o prazo vence e de transformar as naves menores em uma decisão econômica explícita durante a expedição.

---

## 10. Decisões confirmadas e questões em aberto

### Confirmado por Péterson

| Decisão | Estado |
|---|---|
| O produto deve ser um concorrente, não uma cópia de Dark Nebula | Confirmado |
| O mapa tem uma visão espacial com planetas que podem ser abertos | Confirmado |
| Cada planeta abre um mapa de produção inspirado na construção do Mindustry | Confirmado |
| Estrelas vermelhas são expedições temporárias | Confirmado |
| O jogador controla diretamente a nave principal contra naves inimigas | Confirmado |
| Naves menores podem minerar asteroides e transportar recursos | Confirmado |
| Os recursos recuperados entram no inventário global do jogador | Confirmado |
| O combate deve permitir personalização com armas, efeitos e interações das tropas | Confirmado como direção; detalhes pendentes |
| A contagem da estrela continua com o jogo fechado | Confirmado |
| A contagem começa quando o jogador inicia a expedição | Confirmado |
| Prazo vencido força a retirada: a partida termina e o jogador volta ao mapa | Confirmado |
| O código permanece sob GPLv3 | Confirmado |

### Valores provisórios do protótipo

| Decisão | Valor atual |
|---|---|
| Duração da expedição em Gier | 30 minutos em tempo real |
| Avisos de tempo restante | 10 min, 5 min e 1 min |
| Base de Gier após a retirada | Continua salva para a próxima expedição |
| Coleta da carga em Gier | Manual pelo HUD/tela da expedição; automática antes da retirada |
| Inventário global | Persistente nas configurações, independente do save do setor |

### Ainda precisa ser decidido

1. Qual é a duração final de uma estrela vermelha (os 30 minutos são só do protótipo)?
2. No produto final, o que acontece com a nave principal e as naves de apoio no instante da retirada forçada? (No protótipo, a carga do núcleo é recolhida automaticamente e a partida é encerrada.)
3. No produto final, a base do jogador permanece após o prazo, é perdida ou sofre alguma consequência?
4. O jogador dá ordens às naves menores ou elas agem automaticamente segundo prioridades?
5. A produção de cada planeta fica local, vai ao inventário global ou exige transporte?
6. Quais recursos podem ser minerados em asteroides e produzidos nos planetas?
7. Quais tipos de armas, efeitos e interações entre tropas estarão disponíveis primeiro?
8. Uma nave destruída é perdida, resgatada ou reparada?
9. O combate será exclusivamente individual no primeiro lançamento ou terá cooperação mais tarde?
10. Qual é o escopo inicial de planetas e expedições?

---

## 11. Diretriz de identidade

A inspiração está na leitura de um mapa espacial e na fantasia de explorar estrelas. A identidade de Orbital deve vir de sua combinação própria: **colônias construídas, expedições pilotadas e naves de apoio com função econômica**.

Usar termos, personagens, símbolos, nomes ou arte do jogo de referência não faz parte desta visão. A licença GPLv3 do projeto é mantida; a identidade visual e narrativa de Orbital deve ser própria.
