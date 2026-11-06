# Proposta — `buildSpeed` e Build Tower como implementação futura

**Tipo:** documento de projeto, sem implementação  
**Status:** proposta futura; nada descrito aqui existe no código além do que já está marcado como “hoje”  
**Data:** 6 de novembro de 2026  
**Fontes:** `core/src/orbital/content/UnitTypes.java`, `core/src/orbital/content/Blocks.java`, `core/src/orbital/entities/comp/BuilderComp.java`, `core/src/orbital/world/blocks/defense/BuildTurret.java`, `core/src/orbital/type/UnitType.java`, `core/src/orbital/type/SupportModuleCategory.java`  
**Relação:** complementa `docs/RELATORIO_CORRESPONDENCIAS_ARMAS_MODULOS.md`, seção 6.5.

---

## 1. Objetivo

Registrar, em um documento próprio, como `buildSpeed` e o Build Tower poderiam virar uma **implementação futura** de suporte à construção, sem confundir isso com o que o sistema atual já faz.

O documento separa duas coisas que hoje são frequentemente tratadas juntas:

1. **`buildSpeed`** — uma estatística de unidade que define a velocidade de construção, desconstrução e reparo de estruturas.
2. **Build Tower (`build-tower`)** — um bloco 3x3 do mapa que constrói por conta própria, consumindo energia e nitrogênio.

### 1.1 O que este documento não faz

- Não altera código.
- Não cria blocos, habilidades ou categorias.
- Não muda os valores atuais de `buildSpeed`.
- Não transforma o Build Tower em módulo dorsal por decreto.
- Não redefine a decisão da seção 5 do relatório principal: as unidades continuam nativas até uma implementação aprovada.

---

## 2. O que existe hoje

### 2.1 `buildSpeed`

`buildSpeed` é declarado em `core/src/orbital/type/UnitType.java`:

```java
/** building speed multiplier; <0 to disable. */
buildSpeed = -1f,
```

O valor padrão é `-1`, ou seja, a unidade **não constrói**. Qualquer valor maior que zero habilita a construção. O `BuilderComp` usa essa estatística em pontos centrais:

```java
// BuilderComp.canBuild()
return type.buildSpeed > 0 && buildSpeedMultiplier > 0;
```

```java
// BuilderComp.updateBuildLogic()
float bs = 1f / entity.buildCost * type.buildSpeed * buildSpeedMultiplier * state.rules.buildSpeed(team);
```

A leitura prática é:

- `buildSpeed` é o fator principal de velocidade;
- `buildSpeedMultiplier` é um modificador dinâmico, usado por status e por efeitos externos;
- `state.rules.buildSpeed(team)` aplica regras globais ou de time;
- o custo da construção entra como divisor.

O alcance de construção também é uma propriedade do tipo:

```java
/** range at which this unit can build */
buildRange = Vars.buildingRange,
```

O padrão é **220 px**. O Build Tower sobrescreve esse valor no seu construtor interno, usando o próprio `range` do bloco.

Além da velocidade, `buildSpeed > 0` também influencia:

- a exibição de `Stat.buildSpeed` na ficha da unidade;
- a disponibilidade dos comandos `rebuildCommand` e `assistCommand`;
- o desenho do feixe de construção (`drawBuildBeam`);
- a validação de planos e a animação de construção.

#### Valores atuais no catálogo

Valores declarados em `UnitTypes.java` na data deste documento:

| Unidade | `buildSpeed` | Perfil |
|---|---:|---|
| Nova | 0,3 | Construtora lenta, focada em reparo. |
| Pulsar | 0,5 | Suporte leve de construção. |
| Quasar | 1,1 | Ritmo próximo do normal. |
| Retusa | 1,5 | Boa construtora naval. |
| Oxynoe | 2,0 | Construtora rápida. |
| Cyerce | 2,0 | Construtora rápida. |
| Vela | 3,0 | Muito rápida. |
| Aegires | 3,0 | Muito rápido. |
| Navanax | 3,5 | O mais rápido entre as unidades navais listadas. |
| Poly | 0,4 | Construtora aérea básica. |
| Mega | 2,6 | Construtora aérea avançada. |
| Quad | 2,5 | Construtora aérea pesada. |
| Oct | 4,0 | Maior valor do catálogo. |
| Alpha | 0,5 | Construtora de facção. |
| Beta | 0,75 | Construtora de facção. |
| Gamma | 1,0 | Construtora de facção. |
| Evoke | 1,2 | Unidade-núcleo construtora. |
| Incite | 1,4 | Unidade-núcleo construtora. |
| Emanate | 1,5 | Unidade-núcleo construtora. |

> Valores podem ser alterados por patches de mods; a tabela reflete o conteúdo-base.

### 2.2 Build Tower

O Build Tower está em `core/src/orbital/content/Blocks.java`:

```java
buildTower = new BuildTurret("build-tower"){{
    requirements(Category.effect, with(Items.silicon, 150, Items.oxide, 40, Items.thorium, 60));
    outlineColor = Pal.darkOutline;

    range = 200f;
    size = 3;
    buildSpeed = 1.5f;

    consumePower(3f);
    consumeLiquid(Liquids.nitrogen, 3f / 60f);
}};
```

Hoje ele funciona assim:

- é um `BaseTurret`/`BuildTurret`, não uma `Ability`;
- ocupa **3x3** e tem alcance de **200 px**;
- cria internamente uma unidade construtora invisível (`UnitType("turret-unit-build-tower")`) com `buildSpeed=1.5` e `buildRange=200`;
- procura planos de construção do time dentro do alcance;
- pode seguir o plano de outro construtor próximo e imitar a função;
- só consome energia e nitrogênio quando há planos ativos e quando não está suprimido;
- é **suprimível** (`suppressable = true`): sob supressão, a eficiência cai a zero;
- não é uma peça dorsal e não passa por `SupportModuleCategory`.

O ponto importante é que o Build Tower não “dá `buildSpeed`” para uma unidade. Ele **é** uma instalação que executa planos de construção com uma unidade interna própria.

---

## 3. Por que isso não é suporte dorsal hoje

As categorias atuais são `repair`, `overdrive` e `force`, com a proposta `field` registrada em outro documento.

- `buildSpeed` não é uma `Ability`; é uma estatística de casco.
- O Build Tower não é um bloco de suporte dorsal; é um bloco de mapa com consumo de energia e líquido.
- `SupportModuleCategory.of` não reconhece `build-tower`, e `SupportModuleCategory.ofAbility` nunca verá `buildSpeed`, porque não existe ability envolvida.
- O sistema de módulos dorsais foi desenhado para efeitos instalados no casco. O Build Tower depende de uma âncora de mapa, de planos do time e de uma unidade interna.

Por isso, tratar `buildSpeed` e Build Tower como “mais uma categoria” sem desenho próprio criaria uma promessa que o código atual não sustenta.

---

## 4. Direção proposta

A proposta separa o assunto em duas trilhas independentes. Elas podem ser aprovadas em momentos diferentes.

### 4.1 Princípios

1. **Construção é logística, não combate.** O objetivo é acelerar obra e logística, não substituir `repair`, `overdrive`, `force` ou `field`.
2. **Não transformar qualquer casco em construtor.** Um módulo de construção deve **acelerar quem já constrói**, não dar `buildSpeed` a uma unidade de combate que hoje tem `buildSpeed <= 0`.
3. **Um módulo por categoria.** A regra existente do enum é mantida.
4. **Sem consumo de energia/líquido em módulo dorsal.** Unidades não têm rede elétrica nem tanque de nitrogênio. Módulos dorsais devem funcionar por recarga, aura ou status.
5. **Build Tower continua sendo bloco de mapa.** Se houver transporte pela unidade, o caminho natural é o sistema de payload, não uma âncora dorsal.

### 4.2 Trilha A — categoria `build` para `buildSpeed`

Proposta de uma nova categoria de suporte dorsal chamada **`build`**.

| Bloco provisório | Efeito proposto | Observação |
|---|---|---|
| `build-assist-projector` | Aplica um status de construção em aliados próximos, com `buildSpeedMultiplier` maior que 1. | Só afeta unidades com `buildSpeed > 0`. |
| `build-range-projector` | Aumenta o alcance de construção de aliados próximos. | Exige refatorar `buildRange`, que hoje é lido direto de `UnitType`. |

Regras propostas:

- o módulo **não habilita** construção em unidades com `buildSpeed <= 0`;
- o bônus é multiplicativo, para não transformar uma unidade lenta em construtora de elite sem investimento;
- o efeito pode ser uma aura periódica, no mesmo espírito de `StatusFieldAbility`;
- a categoria `build` não deve ser automaticamente exclusiva com `repair`; um casco construtor pode querer reparo e construção ao mesmo tempo. A decisão final fica para o balanceamento;
- `build-range-projector` só deve existir depois que o alcance de construção virar um estado modificável por unidade.

**Por que começaria por `build-assist-projector`:** ele reaproveita o caminho já existente de `buildSpeedMultiplier` e de status, sem mexer na leitura de `buildRange`.

### 4.3 Trilha B — Build Tower

O Build Tower não deve ser convertido diretamente em módulo dorsal. Há três caminhos possíveis:

| Opção | O que seria | Vantagem | Custo/risco |
|---|---|---|---|
| **B1 — Manter como bloco de mapa** | Nada muda no Build Tower; a implementação futura é só de balanceamento e exposição. | Zero risco estrutural; preserva o comportamento atual. | Não resolve “levar uma torre de construção” em uma unidade. |
| **B2 — Transporte por payload** | A unidade carrega o bloco Build Tower como payload e o implanta no mapa. | Reaproveita o sistema de payload e mantém o Build Tower como bloco real. | Depende de capacidade 3x3, regras de pickup/deploy e balanceamento de custo. |
| **B3 — Módulo “relay de construção”** | Uma ability dorsal que executa ou delega planos de construção em alcance limitado. | Dá mobilidade real ao conceito. | Exige nova ability, integração com `BuilderComp`, planos do time, supressão e possivelmente IA. É a opção mais cara. |

**Recomendação:** seguir B1 primeiro e avaliar B2 depois. B3 só deve ser considerada se o design pedir explicitamente uma unidade que constrói “como uma torre”, porque ela duplica parte do `BuildTurret` e cria um segundo caminho de execução de planos.

Uma versão conservadora de B3 seria um módulo que **assiste** construtores próximos, sem criar planos próprios. Isso evita duplicar a lógica de `BuildTurret` e mantém o módulo no papel de suporte.

---

## 5. Regras de compatibilidade propostas

1. **Unidade sem `buildSpeed` não recebe bônus de construção.** Isso evita divisão por zero em `StatusComp.statusBuildSpeed` e impede que um módulo de logística vire um “construtor universal”.
2. **Um módulo `build` por unidade.** A regra de uma peça por categoria continua valendo.
3. **Build Tower não entra em `build`, `repair`, `overdrive`, `force` ou `field`.** Ele continua sendo um bloco de mapa.
4. **Supressão continua válida.** Se o módulo usar uma aura, a supressão deve poder desligá-la como desliga o Build Tower.
5. **Sem energia/líquido em módulo dorsal.** Custos de energia e nitrogênio ficam restritos ao Build Tower.
6. **A categoria `build` não deve ser apresentada como “reparo”.** Reparo devolve vida a unidades; construção ergue e conserta estruturas.
7. **A categoria `build` não substitui `field`.** `field` cobre escudo, energia e supressão; `build` cobre logística de obra.

---

## 6. Esboço técnico

> **Ilustrativo.** Nada abaixo foi aplicado.

### 6.1 Enum

```java
public enum SupportModuleCategory{
    repair,
    overdrive,
    force,
    field,
    build; // proposta futura

    public static @Nullable SupportModuleCategory of(Block block){
        if(block == null || block.name == null) return null;

        return switch(block.name){
            case "mender", "mend-projector" -> repair;
            case "overdrive-projector", "overdrive-dome" -> overdrive;
            case "force-projector" -> force;
            case "shield-regen-projector", "energy-field-projector", "suppression-projector" -> field;
            //proposta: módulos de construção
            case "build-assist-projector", "build-range-projector" -> build;
            default -> null;
        };
    }
}
```

`ofAbility` não precisa mapear `buildSpeed`, porque não há ability nativa. Se for criada uma ability de assistência de construção, ela deve ser mapeada explicitamente para `build` para bloquear um módulo equivalente.

### 6.2 Status de construção

O caminho mais barato é um `StatusEffect` com `buildSpeedMultiplier`:

```java
// StatusEffect já possui:
public float buildSpeedMultiplier = 1f;

// StatusComp já multiplica esse valor em:
buildSpeedMultiplier *= entry.effect.buildSpeedMultiplier;
```

Um `build-assist-projector` poderia aplicar esse status em aliados próximos usando o mesmo padrão de `StatusFieldAbility`. A diferença é que o status precisa ser aplicado **somente** a unidades com `buildSpeed > 0`.

### 6.3 Alcance de construção

`BuilderComp` lê `type.buildRange` diretamente em quatro pontos:

- `updateBuildLogic`;
- `activelyBuilding`;
- `drawBuilding`;
- `drawBuildingBeam`.

Por isso, `build-range-projector` não é um efeito trivial: seria necessário introduzir um valor de alcance por unidade (ou uma estatística) e trocar essas leituras. Sem essa refatoração, o módulo só poderia aumentar a velocidade, não o alcance.

### 6.4 Integração com `SupportMount`

`SupportMount.createAbility` hoje reconhece apenas `MendProjector`, `OverdriveProjector` e `ForceProjector`. A trilha `build` exigiria:

- um caso para o bloco de construção;
- criação da ability/status correspondente;
- remoção limpa do status quando o módulo for desinstalado ou desativado;
- validação de que a unidade de fato tem `buildSpeed > 0`.

### 6.5 Build Tower por payload

A opção B2 não passa por `SupportMount`. Ela usaria o sistema de payload já existente para carregar e implantar o bloco. Os pontos a validar são:

- capacidade de payload suficiente para um bloco 3x3;
- regras de `pickupUnits`, `vulnerableWithPayloads` e ambiente;
- custo e tempo de implantação;
- interação com supressão e com planos do time.

---

## 7. Roadmap sugerido

| Fase | Entrega | Dependência |
|---|---|---|
| **0** | Este documento e a decisão de escopo. | Nenhuma. |
| **1** | `build-assist-projector` como primeiro módulo da categoria `build`. | Status com `buildSpeedMultiplier` e integração em `SupportMount`. |
| **2** | Balanceamento dos valores de `buildSpeed` e dos bônus do módulo. | Fase 1. |
| **3** | `build-range-projector`, se o alcance virar estado por unidade. | Refatoração de `buildRange`. |
| **4** | Build Tower transportável por payload (B2), se o design pedir. | Regras de payload e capacidade. |
| **5** | Módulo “relay de construção” (B3), somente se B2 não bastar. | Nova ability e integração com planos/IA. |

### 7.1 Critérios de aceite da fase 1

- uma unidade com `buildSpeed > 0` constrói mais rápido com o módulo ativo;
- uma unidade com `buildSpeed <= 0` **não** passa a construir;
- o bônus não se acumula com uma segunda peça `build`;
- desativar ou remover o módulo devolve a velocidade original;
- o efeito não interfere em `repair`, `overdrive`, `force` ou `field`;
- save/load e sincronização continuam funcionando;
- a supressão desliga o efeito quando aplicável.

---

## 8. Questões em aberto

1. O bônus de `build` deve ser aura para aliados ou apenas auto-buff no casco?
2. `build` deve ser cumulativo com `repair`, ou o casco deve escolher entre logística e sustentação?
3. O `build-assist-projector` deve afetar apenas unidades, ou também acelerar construções já em andamento?
4. O Build Tower deve continuar exclusivo de mapa, ou o design quer mesmo uma versão móvel?
5. Se houver versão móvel, ela deve executar planos próprios (B3) ou apenas assistir construtores próximos?
6. O alcance de construção deve virar estatística por unidade, ou continua sendo propriedade de `UnitType`?

---

## 9. Resumo da decisão

- **Hoje:** `buildSpeed` é função de casco e o Build Tower é bloco de mapa; nenhum dos dois é módulo dorsal.
- **Futuro proposto:** criar a categoria `build` para módulos que aceleram construtores existentes, começando por `build-assist-projector`.
- **Build Tower:** manter como bloco de mapa; se precisar ser transportável, usar payload antes de criar uma ability complexa.
- **Fora de escopo:** dar `buildSpeed` a qualquer unidade, transformar o Build Tower em peça dorsal ou duplicar a lógica de planos do time sem necessidade.
