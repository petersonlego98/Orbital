# Controles do jogador e módulos dorsais — análise, implementação e manual

**Tipo:** relatório de implementação
**Data:** 11 de novembro de 2026 · **revisão 12 de novembro de 2026:** §3.11 registra o
dimensionamento atual do menu de módulos/encaixes no Android e revoga três medidas do §3.8.
**Revisão 13 de novembro de 2026:** células, numeração e mira passam a valer **por grupo de
módulos idênticos**, com o selo "×N" nas células com mais de uma cópia (§3.5 e §3.6).
**Escopo:** controles de toque para Android, controle físico (gamepad), teclas numéricas dos
módulos, escolha de arma para atirar e regra de mira automática/manual.
**Rótulos:** **[FATO]** = verificado no código, com arquivo/linha; **[IMPLEMENTADO]** = entregue
nesta sessão, com teste; **[DECISÃO]** = escolha de design tomada aqui; **[PENDÊNCIA]** = aberto.

---

## 1. O que foi pedido e o que foi entregue

| Pedido | Estado | Onde |
|---|---|---|
| Analisar e documentar a implementação | **[IMPLEMENTADO]** | este documento |
| Controles para o usuário no Android, com direção analógica | **[IMPLEMENTADO]** | `orbital.ui.AnalogStick`, `orbital.input.MobileInput` |
| Manter o controle original | **[IMPLEMENTADO]** | `MobileInput.updateMovement` (caminho antigo intacto) |
| Não ter delay ao mexer o mapa | **[IMPLEMENTADO]** | direção direta + câmera com caixa morta |
| Poder usar controles (gamepad) | **[IMPLEMENTADO]** | `orbital.input.ControllerInput`, bindings novas |
| Teclas de número para escolher o módulo | **[IMPLEMENTADO]** | `Binding.moduleToggle01..10`, `InputHandler.selectModule` |
| Escolher qual arma usar para atirar | **[IMPLEMENTADO]** | seleção por módulo (`WeaponsComp.selectModule`) |
| Clicar no módulo escolhido deixa só ele atirando | **[IMPLEMENTADO]** | seleção pelo toque na faixa (`HudFragment.turretCell`) |
| Mira automática ou manual | **[IMPLEMENTADO]** | `WeaponsComp.autoAimModules`, `TurretLoadoutDialog` |
| Só módulos de suporte automáticos, exceto wave e tsunami | **[IMPLEMENTADO]** | `SupportMount.autoTarget`, `LiquidSupportAbility` |

---

## 2. Como o controle funcionava antes (linha de base)

### 2.1 Android

**[FATO]** `MobileInput` (`core/src/orbital/input/MobileInput.java`) é o manipulador de toque.
O movimento da unidade não vinha de nenhum botão: em `updateMovement` (linha ~1115, antes desta
sessão) a unidade **perseguia o centro da câmera**:

```java
targetPos.set(Core.camera.position);
...
movement.set(targetPos).sub(player).limit(speed);
movement.setAngle(Mathf.slerp(movement.angle(), unit.vel.angle(), 0.05f));
unit.movePref(movement);
```

O cursor/dedo movia a câmera em `pan()` e a unidade corria atrás dela. A câmera em si era
imediata (`Core.camera.position.x -= deltaX`, 1:1 com o dedo).

**[DECISÃO]** O "delay" que o jogador sente não estava na câmera: era a **indireção** entre o
dedo e a nave. O dedo move a câmera, a câmera define um alvo, e a unidade só então acelera
atrás desse alvo (`limit(speed)` + suavização de direção pelo `slerp`). Em toques curtos, a
nave chega depois. Some com o toque e o atraso permanente.

### 2.2 Armas e módulos

**[FATO]** `WeaponsComp.controlWeapons()` (`core/src/orbital/entities/comp/WeaponsComp.java`)
ligava **todas** as armas controláveis de uma vez, com um único `player.shooting`:

```java
void controlWeapons(boolean rotate, boolean shoot){
    for(WeaponMount mount : mounts){
        if(mount.weapon.controllable){
            mount.rotate = rotate;
            mount.shoot = shoot;
        }
    }
}
```

**[FATO]** `MobileInput.updateMovement` mirana com `unit.aim(Core.input.mouseWorldX(), ...)`
(mira manual no cursor) ou, com a configuração `autotarget`, escolhia **um** alvo para a
unidade inteira (`Units.closestTarget`) e apontava tudo nele. Não existia seleção por arma.

**[FATO]** Módulos de suporte (`SupportMount`) rodam habilidades próprias (`RepairFieldAbility`,
`StatusFieldAbility`, `ForceFieldAbility`, `PointDefenseAbility`, `TractorAbility`,
`MassDriverAbility`) e têm apenas um `active` binário, alternado pela faixa do HUD.

**[FATO]** `LiquidSupportAbility` (`wave`/`tsunami`) buscava sozinho o inimigo mais próximo e
jorrava líquido nele — ou seja, já era automático.

**[FATO]** As teclas numéricas (`num1`..`num0`) já pertenciam ao menu de construção
(`Binding.blockSelect01..10`, `PlacementFragment.gridUpdate`) e aos grupos de controle
(`DesktopInput`, só em `commandMode`).

---

## 3. O que foi implementado

### 3.1 Direção analógica (Android)

**Arquivo novo:** `core/src/orbital/ui/AnalogStick.java` — anel arrastável com knob. O knob é
normalizado: meia inclinação = meia velocidade. Segurar fora do anel ainda dirige (o vetor é
grampo no aro), e soltar zera.

**`MobileInput`** ganhou estes membros públicos:

| Membro | Papel |
|---|---|
| `analogVector` | vetor local do stick, escrito pelo widget |
| `analogActive` | true enquanto há dedo com inclinação |
| `syncAnalog()` | chamado pelo widget a cada down/drag/up |
| `directSteering()` | true quando o stick ou o gamepad está dirigindo |
| `directVector(Vec2)` | vetor de direção (stick, senão gamepad) |
| `followCameraBox(Posc)` | câmera com caixa morta em volta da unidade |

**`updateMovement`** agora tem dois caminhos:

```java
boolean direct = directSteering();
if(direct){
    directVector(directVec);
    float tilt = Math.min(directVec.len(), 1f);
    movement.set(directVec).setLength(speed * tilt);   // sem suavização, sem etapa intermediária
    followCameraBox(unit);
}else{
    //caminho original: arrasta o mapa, a unidade persegue o centro da câmera
    movement.set(targetPos).sub(player).limit(speed);
    movement.setAngle(Mathf.slerp(movement.angle(), unit.vel.angle(), 0.05f));
    ...
}
```

**[DECISÃO]** Por que os dois caminhos em vez de substituir:
- o pedido foi **manter o controle original**;
- o caminho antigo continua sendo o que o codigo de arrastar (`pan`) alimenta, então arrastar
  o mapa com outro dedo continua funcionando enquanto o stick dirige;
- `pan()` não precisou de nenhuma mudança: ele já era imediato. O que mudou é que a **unidade
  já depende do dedo direto**, não da câmera.

**[DECISÃO]** Câmera com caixa morta (`followCameraBox`): quando a unidade sai de um retângulo
de 28% da largura/altura da câmera, a câmera a segue (`lerp 0.25`); dentro da caixa, a câmera
não se move. Assim a nave nunca sai da tela e o jogador que arrasta o mapa com o outro dedo
nunca é puxado de volta.

**[FATO]** O stick é criado em `HudFragment.build` (canto inferior direito), visível só em
`mobile`, com `shown()`, fora de menu/editor e com jogador vivo:
`t.visible(() -> mobile && shown() && !state.isMenu() && !state.isEditor() && !player.dead())`.

### 3.2 Controle físico (gamepad)

**Arquivo novo:** `core/src/orbital/input/ControllerInput.java`.

**[FATO]** `arc.input.KeyCode` (jar `arc-core`, pacote `arc.input`) expõe tanto os nomes no
estilo Xbox (`controllerA`, `controllerLStickXAxis`, `controllerdPadUp`, ...) quanto os no
estilo PlayStation (`buttonA`, `buttonR2`, ...), e `arc.Input` tem `axis(KeyCode)` e
`keyDown(KeyCode)`. Um `KeyBind` rebindable aceita **uma** tecla só, então o helper lê todas
as grafias e devolve zero quando nada está pressionado — o estado normal sem controle.

| Método | Significado |
|---|---|
| `firing()` | qualquer tecla de disparo (A / RT / RB, nas duas grafias) |
| `moveX()/moveY()` | analógico esquerdo + d-pad como reserva |
| `moving()` | se há direção |
| `aim()/aimX()/aimY()` | analógico direito, com zona morta de 0.15 |
| `movement(Vec2)` | escrita direta no vetor, sem alocar |

**Onde entra:**
- `MobileInput.updateMovement`: eixo esquerdo entra no mesmo caminho do stick analógico
  (`directSteering()`); analógico direito define o ponto de mira a `max(unit.range(), 80)` de
  distância; a tecla de face dispara (`player.shooting`).
- `MobileInput.update` (bloco de pan por eixos): o eixo do controle é somado ao de WASD quando
  a unidade não está sendo dirigida por ele.
- `Binding.controllerFire` (`KeyCode.controllerA`, categoria `gamepad`): declarado para o
  diálogo de teclas mostrar que o controle existe, embora o disparo seja lido por
  `ControllerInput.firing()`.
- `InputHandler.reset()` e nada mais: o controle não guarda estado.

### 3.3 Teclas de número e escolha do módulo que atira

**`Binding`** ganhou `moduleToggle01..10` = `num1..num0`, categoria `modules`.

**[DECISÃO]** Conflito com o menu de construção: as mesmas teclas numéricas selecionam blocos.
A resolução é por prioridade, em `InputHandler.update()`:

```java
if(state.isGame() && !player.dead() && !commandMode && !Core.scene.hasKeyboard()
&& !Core.scene.hasField() && !Core.scene.hasDialog() && !locked()
&& !(player.isBuilder() && !TestNewFeaturesMode.active() && ui.hudfrag.shown())){
    ... // as teclas numéricas escolhem o módulo que atira
}
```

Ou seja: **o menu de construção tem prioridade** (quebrá-lo seria pior). As teclas numéricas
dos módulos ficam livres exatamente onde o menu de blocos está escondido — o ambiente de teste
de módulos (`TestNewFeaturesMode.active()`) e qualquer jogador que não seja construtor. No
toque, as células numeradas da faixa fazem o mesmo gesto (ver 3.5).

### 3.4 Escolher o módulo: só o escolhido atira

**[DECISÃO]** O pedido do dono: *clicar no número ou no módulo escolhido deixa ele selecionado
para ser o único a atirar*. Antes o gesto ligava/desligava cada módulo sozinho, e o jogador
tinha de desligar todos os outros para sobrar um. Agora o gesto **seleciona**: o módulo
escolhido atira e os outros ficam montados e calados.

**`WeaponsComp`** (estado novo, `transient`, nunca serializado nem sincronizado):

```java
public transient ObjectSet<Weapon> disarmedModules = new ObjectSet<>();
public transient @Nullable Weapon selectedModule;
public transient ObjectSet<Weapon> autoAimModules = new ObjectSet<>();
```

| Método | Comportamento |
|---|---|
| `moduleCount()` | quantos módulos há, na ordem da faixa: armas (inclusive as fixas do casco), depois suportes |
| `moduleName(int)` | nome do módulo na posição |
| `moduleFires(int)` | true quando a posição é um módulo de arma — os que atiram |
| `selectedModule()` | a arma que o piloto escolheu, ou `null` quando todas atiram |
| `moduleSelected(int)` | true quando a posição é a escolhida |
| `selectModule(int)` | **o gesto**: escolhe a arma (só ela atira); a mesma posição de novo solta a escolha |
| `moduleActive(int)` | atira agora? arma: não está em `disarmedModules`; suporte: `SupportMount.active` |
| `toggleModule(int)` | arma: delega para `selectModule`; suporte: liga/desliga sem desmontar |
| `moduleAuto(int)` / `setModuleAuto(int, boolean)` | modo de mira do módulo |
| `weaponAimsSelf(Weapon)` / `setWeaponAimsSelf(...)` | idem, por arma (usado pelo diálogo) |
| `weaponDisarmed(Weapon)` | arma calada (não escolhida) |
| `releaseSelection()` | solta a escolha: todos os módulos de arma voltam a atirar |
| `applyDisarmed(WeaponMount)` | **o gate**: `mount.shoot = false` |

**Regras:**
- escolher uma arma silencia as outras (entram em `disarmedModules`) e arma a escolhida;
- escolher a mesma posição de novo solta a escolha — volta o estado inicial, todos atiram;
- um módulo calado **continua montado**: mantém slot, área dorsal, categoria, orçamento e
  continua acompanhando a mira; só não dispara;
- módulo de suporte não atira: neles o mesmo gesto continua ligando/desligando o efeito;
- a escolha morre com a arma (`removeWeapon`) e com o casco (`setupWeapons`,
  `InputHandler.reset()`).

**[FATO]** O gate roda dentro de `WeaponsComp.update()`, antes de `mount.weapon.update(...)`:

```java
for(WeaponMount mount : mounts){
    applyDisarmed(mount);                    // módulo não escolhido nunca dispara
    if(!moduleHasAmmo(mount)) continue;
    int shots = mount.totalShots;
    mount.weapon.update(self(), mount);
    if(mount.totalShots > shots) spendAmmo(mount.weapon);
}
```

Como é o último ponto antes da arma rodar, nenhum caminho — controlador do jogador, IA, mira
manual ou alvo automático — consegue disparar por um módulo não escolhido. O módulo calado
**continua montado**: mantém slot, área dorsal, categoria e rotação.

**Ciclo de vida:** `setupWeapons` limpa os três estados (casco novo, seleção nova),
`removeWeapon` limpa a arma escolhida dos três e, se ela era a escolhida, solta a seleção
(módulo removido não deixa o casco calado atrás de si), e `InputHandler.reset()` solta a
seleção no início de cada rodada.

### 3.5 Faixa do HUD: números e escolha por toque

`rebuildTurretBar` numera as células na mesma ordem das teclas. A numeração é **por grupo de
módulos idênticos** (13/11): dois Duo montados são um grupo, ocupam uma célula e um número só.

```java
int index = 0;
for(Weapon group : unit.moduleGroups()){
    int moduleIndex = index++;
    WeaponMount mount = unit.representativeMountOf(group);
    if(mount == null) continue;
    int copies = unit.moduleCopies(group);
    modules.add(turretCell(unit, mount, moduleIndex, copies)).size(cellSize).pad(cellPad)
        .tooltip(turretTooltip(unit, moduleIndex, group, copies));
}
for(SupportMount mount : unit.supportMounts) ... supportCell(unit, mount, index++) ...
```

- `moduleBadge` desenha o número no canto da célula (1..9, depois 0).
- `turretCell` ganhou um selo "×N" no canto quando o grupo tem mais de uma cópia: cada cópia
  alimenta o pool do casco, então o número diz quantas instâncias a célula representa.
- `turretCell` ganhou `cell.clicked(() -> selectModule(unit, index))` — tocar **escolhe** o
  módulo: a partir daí ele é o único a atirar e os outros ficam cinza (borda e ícone
  acinzentados, fundo escuro). A escolha leva uma estrela no canto do ícone e borda da cor de
  destaque com espessura maior. Tocar a mesma célula de novo solta a escolha e todos os módulos
  voltam a atirar juntos. O realce do módulo no casco foi substituído por esse gesto, porque
  escolher o que atira era o pedido.
- `supportCell` continua alternando o efeito pelo mesmo caminho (`selectModule`, que delega
  para `toggleModule` nas posições de suporte), agora também com o badge numérico.
- Placas de armadura (`wallCell`) não são numeradas nem alternáveis: uma placa defende enquanto
  está montada.
- O rótulo do número vem de `moduleKeyName(index)` = `(index + 1) % 10`, então a 10ª célula é
  "0", igual à tecla.
- As células se repintam a cada frame (`cell.update`), então a estrela e o cinza acompanham a
  escolha feita pelo teclado sem reconstruir a faixa.

**[DECISÃO]** O canto inferior esquerdo é da faixa de módulos no toque. O botão **Comando**
(`MobileInput.buildUI`) ficava nesse canto e a faixa passava por cima dele (`padLeft` de 175
unidades), o que espremia as duas coisas. Agora a faixa ocupa o canto e o botão de comando é
uma coluna **acima** dela: `MobileInput` mede a altura da faixa com
`HudFragment.moduleBarHeight()` e abre um `spacerY` desse tamanho acima do fundo preto do
inset. Sem faixa na tela (casco sem encaixes), a altura medida é 0 e o botão volta ao canto.

### 3.8 Menu de módulos: quatro cartões por linha e altura limitada

> **Nota de 12/11/2026:** este parágrafo é o registro da sessão de 11/11. As medidas de
> largura, de legenda e de `cardMetrics()` aqui descritas foram **revistas**; o estado atual e o
> motivo estão no §3.11. O histórico fica para não perder a cronologia dos diagnósticos.

**[DECISÃO]** Dois problemas de encaixe no Android, ambos corrigidos em
`TurretLoadoutDialog`, e ambos medidos com um probe de layout de janela real
(`ORBITAL_PROBE=1`, hoje removido):

1. **Cartões.** O card media 295/`Scl.scl(1)` unidades e a contagem de colunas escalava a
   largura do trilho duas vezes (`slotWidth() * Scl.scl(1f)`), o que roubava uma coluna e
   deixava só 3 por linha. Agora o card é `max(84/`Scl.scl(1)`, catalogueWidth()/4 - 8)` e
   `columns()` devolve **4** no toque: os quatro preenchem a linha, então o nome do módulo e o
   tipo de tiro têm espaço e não quebam no meio da palavra. `catalogueWidth()` subtrai o trilho
   **com o que a barra de rolagem dele toma** e sem escalar — trilho e palco estão nas mesmas
   unidades, e escalar o trilho aqui cobrava a largura duas vezes.
2. **Altura.** Um menu mais alto que a tela é cortado nas duas pontas: some o título em cima e
   os encaixes em baixo. O conteúdo agora tem `maxHeight(altura da cena - margens -
   Scl.scl(140))`, o trilho de encaixes rolha na própria coluna (`columns.pane`) em vez de
   empurrar o menu, e o menu abre no começo (`pane.setScrollYForce(0)` em `show`) — o offset
   guardado era do loadout anterior e escondia o nome do casco e o orçamento.

**O trilho continuou desalinhado depois disso**, e o probe mostrou por quê: o `ScrollPane`
do trilho media `slotWidth()` e a barra de rolagem vertical tomava ~24 unidades da área
útil, então a área ficava menor que o conteúdo do trilho (340) e o `ScrollPane`
**centralizava** o widget dentro da área (`slots` aparecia em `x=-11` e vazava dos dois
lados). Agora o painel mede `slotWidth() + railScrollWidth()` (a largura da barra vem do
`ScrollPaneStyle.vScroll`) e a tabela do painel é alinhada à esquerda: medido, `slots` fica
em `x=0, w=340`, com as caixas de 104 e as fileiras de 330 dentro da coluna.

As legendas das caixas de encaixe também não cabiam: `width(boxSize - 12)` com
`fontScale(1)` no toque quebrava "Sobrecarga" no meio ("Sobrecar ga"). Agora a legenda é
`width(boxSize - 6)` com `fontScale(0.6)` no toque, e o nome quebra entre palavras.
`cardMetrics()` passou a medir a colina do catálogo pelo `list.getWidth()` (o painel já
desenhado) em vez de calcular pela largura do palco: no Android os dois números não são iguais, e
um card calculado pelo palco grande demais era cortado na quarta coluna.

### 3.9 O menu que fugia da tela no Android

**[FATO]** `BaseDialog` herda de `arc.scene.ui.Dialog`, e o `Dialog.act()` chama
**`centerWindow()` todo frame** (enquanto `center && !isMovable && sem ações`). O
`centerWindow()` centraliza a caixa entre `Core.scene.marginLeft/Right/Top/Bottom`. No Android
essas margens são as barras do sistema, e um diálogo que **preenche a tela** centrado contra
elas é empurrado para cima por metade do inset — o título e as primeiras linhas saem por cima
da tela. É o "menu desalinhado" das fotos: o mesmo menu, deslocado.

**[DECISÃO]** `BaseDialog` agora liga `setCentered(false)` e sobrescreve `centerWindow()` para
`setPosition(0, 0)`: um diálogo que preenche o palco já está onde deve estar, e a margem do
sistema não pode deslocá-lo. Vale para todos os diálogos do jogo (todos passam por
`BaseDialog`).

### 3.10 Mira automática não aponta para o mouse

**[FATO]** O usuário relatou nos dois sistemas: com a mira automática ligada, a arma aponta para
onde o mouse está. A ordem real de um frame é: entidades primeiro (a arma automática escolhe o
alvo e mira nele) e **o manipulador de entrada escreve a mira do piloto depois**, todo frame
(`DesktopInput.updateMovement` / `MobileInput`). Um módulo automático que herdasse essa escrita
ficaria apontando para o mouse sempre que não tivesse alvo próprio — e esse ponto de mira é o
que um **laser contínuo desenha** (`PointLaserBulletType` usa `b.aimX/aimY`) e do que uma
bala com `scaleLife` mede a vida.

**[FATO]** Medido com teste de frame real (`AutoAimAimingTest`): o módulo automático fica a 3°
do inimigo e a 114° do mouse — a rotação já era a do alvo. O que estava errado era o **ponto de
mira herdado**.

**[IMPLEMENTADO]** `WeaponsComp.aim(x, y, clearTarget)` não escreve mais `mount.aimX/aimY` de um
mount que escolhe o próprio alvo (`mountSelfTargets`): a mão do piloto é entrada de mira de
módulo manual. Um módulo automático mantém a mira que já tinha (o alvo, quando achou) e sem
alvo não passa a seguir o cursor.

### 3.11 Cuidado permanente: o menu de módulos e encaixes no Android (12/11/2026)

**[FATO]** Três rodadas de screenshots do mesmo menu ("Torres da unidade") no Android, todas
com o mesmo sintoma (texto de descrição cortado à direita, primeiro card cortado à esquerda,
quarto card sob o trilho). A causa dominante, confirmada no bytecode do `arc-core`, é de
**unidade de medida**:

| Onde | Unidade |
|---|---|
| `Core.graphics.getWidth()`, `pane.getWidth()`, `list.getWidth()` | **pixel bruto** da cena (`UI.resize` → `scene.resize(largura, altura)` sem divisão) |
| `Cell.width()`, `.size()`, `.pad()` | **unidade de célula**: `Cell.scl(v)` é `Scl.scl(v)`, aplicado no layout |

Passar um pixel bruto direto para `Cell.width()` **escala duas vezes**. Medido no dispositivo:
`boxSize = 104` no código vira ~127 px na tela e `.width(360)` do trilho vira ~430 px
(`Scl ≈ 1,2`); os cards saíam ~20% maiores que o painel, a tabela de conteúdo ficava mais
larga que a tela e o diálogo era cortado dos dois lados.

**[DECISÃO]** Regra a seguir em qualquer mudança desse menu, **somente no Android**:

1. **Converter antes de usar.** `cataloguePaneWidth()` devolve unidades de célula:
   largura bruta do painel (ou estimativa) **dividida por `Scl.scl(1f)`**. `catalogueWidth()`
   (estimativa antes do primeiro layout) subtrai da tela o trilho **já escalado**
   (`Scl.scl(slotWidth() + railScrollWidth() + 48f)`). `textWidth()`, `cardMetrics()` e
   `lockedLine()` consomem esse valor sem aplicar Scl de novo.
2. **Texto longo sempre com largura explícita.** `Cell.wrap()` chama
   `Label.setWrap(true, false)` (`wrapFit = false`); nesse modo `getPrefWidth()` devolve a
   **largura da linha única** e um parágrafo sem `.width()` infla a tabela além do painel.
   `Cell.width(w)` define `minWidth = maxWidth = scl(w)` e o layout limita coluna **e** ator.
   Para centralizar o texto de um label com wrap, usar `labelAlign(Align.center)` — o
   `.center()` da célula só posiciona o ator.
3. **Medir depois do layout.** `show()` e o `Core.app.post()` reconstróem antes do primeiro
   layout, quando `pane.getWidth()` ainda é 0. O `act()` (somente `mobile`, campo
   `builtForPaneWidth`) reconstrói uma vez quando a largura real aparece, e de novo em rotação
   ou tela dividida. Sem isso, a estimativa da tela inteira "gruda" e os cards nunca cabem.
4. **Não escalar o que já é célula.** O trilho de encaixes (`slotWidth()`, `boxSize`,
   `railScrollWidth()` nas larguras de célula) já é internamente consistente: **não mexer nele**
   para corrigir o catálogo. Foi restrição explícita desta sessão.
5. **Windows intacto.** No desktop `cardWidth = 168f` é fixo — por isso o defeito nunca
   apareceu lá — e `columns()` foi mantido com a fórmula original. A única mudança visível no
   Windows é a tradução. Não "aproveitar" para ajustar a geometria do desktop.

**[IMPLEMENTADO]** Arquivos desta rodada: `core/src/orbital/ui/dialogs/TurretLoadoutDialog.java`
(conversão de unidades, `act()`, larguras dos textos e cards) e
`core/assets/bundles/bundle_pt_BR.properties` (`weapon.module.available`, `weapon.module.hint`,
`weapon.module.noavailable`, que apareciam em inglês). O Android fica com **4 cards por linha**;
o desktop segue com colunas dinâmicas.

**[FATO]** Receita de verificação usada, a repetir em qualquer mudança desse menu:
`./gradlew :android:assembleDebug` → conferir o **tamanho** do APK (~86,7 MB; um daemon que
morre no `packageDebug` deixou um APK parcial de 7 MB com saída enganosa) →
`apksigner verify` → `javap` na classe para confirmar a divisão por Scl → `unzip -p` no bundle
para confirmar as traduções dentro do APK. Apagar o APK anterior antes de recompilar; se o
`packageDebug` travar no `zip-cache`, `./gradlew --stop`, remover o intermediário e reconstruir
com `--no-daemon`. O aceite final é **screenshot no dispositivo**: layout de UI não é coberto
pela suíte de testes headless.

### 3.6 Mira automática x manual

**[DECISÃO]** Dono da mira, por módulo:

| Tipo de módulo | Padrão | Quem escolhe o alvo |
|---|---|---|
| Arma/módulo de casco | **manual** | o jogador aponta, o módulo atira lá |
| Arma/módulo em automático | automático | o próprio módulo, dentro do seu alcance e filtros de projétil |
| Módulo de suporte | **automático** | a Ability do módulo |
| `wave` / `tsunami` | **manual** | o piloto: só jorra enquanto a nave está atirando, na direção da mira |

**Implementação da arma automática:** o bloco de auto-mira foi extraído de `Weapon.update`
para `Weapon.updateAutoTarget(unit, mount, mountX, mountY)` e a condição ficou:

```java
if(!controllable && autoTarget || unit.mountSelfTargets(mount)){
    updateAutoTarget(unit, mount, mountX, mountY);
}
```

**[FATO]** `mountSelfTargets` vive em `WeaponsComp` e consulta `autoAimModules`. O bloco
extraído preserva o `retarget`/`targetSwitchInterval`, o `checkTarget`, o `Predict.intercept`
e a observação original de que "estado de tiro não é afetado" — só o alvo passa a ser do
módulo.

**[FATO]** `WeaponsComp.aim(x, y, clearTarget)` não zera mais `mount.target` de um módulo
automático: sem isso, a mira do piloto (chamada todo frame) jogaria fora o alvo que o módulo
acabou de achar e ele re-buscaria a cada frame.

**[FATO]** O único lugar onde o estado do piloto é aplicado é `InputHandler.update()` (teclas)
e a faixa do HUD (toque); o próprio dialog `TurretLoadoutDialog.rebuildInstalled` tem um botão
por arma instalada (`Icon.filter` = automático, `Icon.move` = manual, estado `checked`).  

**A identidade de módulo** é `WeaponsComp.sameModule()`:

| Caso | Identidade |
|---|---|
| Módulo de torre (`TurretWeapon`) | o próprio bloco (`turret`) — dois Duo montados são o mesmo módulo |
| Módulo de arma de casco (`WeaponModules`) | a arma de origem (`dorsalSourceWeapon`) no mesmo casco de origem |
| Arma nativa do casco | nunca entra em grupo: cada montagem é seu próprio suporte |

`setWeaponAimsSelf(weapon, auto)` e `setModuleAuto(index, auto)` aplicam o modo a **todas** as
cópias iguais montadas (`WeaponsComp.sameModules`); `weaponAimsSelf`/`mountSelfTargets` continuam
por montagem, e `Weapon.update` lê o conjunto já populado — assim todas as cópias giram para o
próprio alvo. Armas **diferentes** seguem independentes — mudar uma não muda a outra.

**Tudo isso agora é coberto por `AutoAimAimingTest.everyIdenticalModuleFollowsTheAimModeTogether`.**

### 3.7 `wave` e `tsunami` manuais

**`SupportMount.autoTarget`** (novo, `boolean`, padrão `true`): o construtor aplica
`this.autoTarget = category != SupportModuleCategory.liquid;` — a categoria líquida nasce
manual, as outras nascem automáticas.

**[FATO]** A Ability tem sua própria cópia do modo (`LiquidSupportAbility.autoTarget`), e
`SupportMount.setAutoTarget(boolean)` atualiza as duas juntas — nunca uma sem a outra. Sem
isso, alternar a mira no diálogo mudaria a linha do loadout e não o jato.
`WeaponsComp.setModuleAuto` chama esse método, não o campo.

**`LiquidSupportAbility`** (novo `autoTarget`, padrão `false` no campo, espelhando o módulo):

```java
boolean manual = !autoTarget;
if(manual && !unit.isShooting()){ target = null; drawing = false; return; }
...
if(manual){
    aimX = unit.aimX(); aimY = unit.aimY();     // a mira da nave
    target = null;
}else{
    Unit enemy = Units.closestEnemy(...);        // o comportamento antigo
    ...
}
float dst = clamp(unit.dst(aimX, aimY), 4f, range);
float life = bullet.speed > 0.01f ? dst / bullet.speed / bullet.lifetime : 1f;
bullet.create(unit, unit.team, mx, my, angle, 1f, life);
```

Ou seja, no modo manual o jato:
1. só sai enquanto a nave está atirando (`unit.isShooting()`, que o
   `controlWeapons` do piloto ou a IA alimentam);
2. aponta para onde a nave aponta;
3. nunca passa do alcance do módulo — e a vida do projétil é escalada pela distância, então o
   jato para onde o piloto mirou em vez de viajar o alcance inteiro;
4. a metade de suporte (apagar fogo de aliados, status em área) também só roda quando a nave
   está atirando.

O desenho do traço entre disparos (`draw`) passa a usar o ponto mirado no modo manual.

---

## 4. Como o jogador usa

| Ação | Toque (Android) | Teclado/mouse | Controle físico |
|---|---|---|---|
| Mover a nave | stick analógico (canto inferior direito) | WASD | analógico esquerdo / d-pad |
| Mover o mapa | arrastar em qualquer lugar | arrastar com botão do meio / setas com pan | — |
| Escolher o módulo que atira | tocar a célula numerada da faixa (estrela) | tecla numérica do módulo | — |
| Voltar a atirar com todos | tocar a mesma célula / tecla outra vez | idem | — |
| Mira manual | arrastar no mapa / mirar e tocar | mouse | analógico direito |
| Disparar | toque longo no mapa | botão esquerdo | A / RT / RB |
| Mira automática | alternar no diálogo de módulos ou no diálogo da arma | idem | — |
| Comando de unidades | botão **Comando**, acima da faixa de módulos | idem | — |
| Liga/desliga suporte | tocar a célula numerada | tecla numérica | — |

A faixa de módulos fica no canto inferior esquerdo, acima dos botões de construção, e mostra
para cada módulo: ícone, número, barra de recarga (arma) ou estado ativo/inativo (suporte). O
módulo escolhido leva a estrela e a borda acesa; os demais módulos de arma ficam cinza enquanto
existe uma escolha. O menu de módulos mostra o catálogo com **quatro cartões por linha** no
toque e cabe inteiro na tela, com o trilho de encaixes rolando na própria coluna.

### 4.1 Onde ficam as opções no menu

**Configurações → Controles** (`KeybindDialog`) tem duas partes:

1. **Controles do Orbital** — três interruptores no topo (trabalham só com toque):
   - **Direção analógica** (`analogstick`): mostra/oculta o stick. Desligado, o controle
     original de arrastar o mapa é o único.
   - **Teclas numéricas dos módulos** (`module-hotkeys`): liga/desliga as teclas `num1..num0`.
   - **Controle de jogo** (`gamepad`): chave geral; desligada, nenhuma tecla ou eixo de
     controle chega ao jogo (`ControllerInput.enabled()`).
2. **As teclas rebindable**, agora com duas categorias novas: **Módulos**
   (`moduleToggle01..10`, as teclas numéricas) e **Controle de jogo** (`controller_fire`, o
   botão que atira). A lista aparece no Android também — antes ela estava escondida em
   `SettingsMenuDialog.rebuildMenu` quando `!mobile || keyboard`.

**[FATO]** A chave de fogo do controle é rebindable e continua sendo lida junto de todas as
grafias do Arc (`ControllerInput.firing()` testa `Binding.controllerFire` antes dos
`KeyCode` brutos), então mudar no menu tem efeito.

---

## 5. Testes

**Arquivo novo:** `tests/src/test/java/AutoAimAimingTest.java` (3 testes, todos passando): dirige
o pipeline na ordem real de um frame — armas primeiro, `unit.aim(mouse)` depois — e prova que o
módulo automático mira no próprio alvo (3° do inimigo, 114° do mouse), que o manual continua
seguindo o piloto e que o automático mantém a mira quando o alvo desaparece.

**Arquivo novo:** `tests/src/test/java/ControlsMenuTest.java` (4 testes, todos passando).

| Teste | O que prova |
|---|---|
| `controlSwitchLabelsExist` | título, hint, os 3 interruptores (nome + tooltip), as 2 categorias novas e as 11 teclas existem nos bundles EN e pt-BR |
| `everyModuleKeyHasALabel` | as 10 teclas de módulo estão rotuladas uma a uma |
| `theAnalogSwitchIsReadByDefault` | os 3 interruptores nascem ligados e são lidos por chave, sem forçar valor no settings |
| `theGamepadSwitchGatesEveryControllerRead` | com a chave do controle desligada, `firing()`, `moveX/moveY`, `moving()` e `aim()` ficam neutros |

**Arquivo novo:** `tests/src/test/java/ModuleControlTest.java` (15 testes, todos passando).

| Teste | O que prova |
|---|---|
| `moduleNumberingCountsWeaponsBeforeSupportModules` | a numeração segue a ordem da faixa: armas primeiro, suportes depois |
| `aSingleModuleFiresPickedOrReleased` | com um módulo só, escolher e soltar não muda o que se vê: ele atira nos dois casos |
| `aPickedModuleIsTheOnlyOneThatFires` | escolher um módulo deixa só ele atirando; os outros ficam montados e calados |
| `aPickedModuleStaysMountedAndKeepsTracking` | o módulo calado não é desmontado, ainda acompanha a mira e nunca dispara |
| `pickingAnotherModuleMovesTheFireControl` | uma segunda escolha passa o tiro para outro módulo |
| `pickingTheSameModuleAgainLetsEveryModuleFire` | a mesma posição de novo solta a escolha: todos atiram juntos |
| `removingThePickedModuleLetsTheRestFireAgain` | o módulo escolhido não sobrevive à arma; os demais voltam a atirar |
| `aSupportModuleIsSwitchedNotPicked` | suporte continua em liga/desliga e nunca vira o módulo que atira |
| `removingAModuleDropsItsPilotSelection` | remover o módulo limpa a seleção e os modos do piloto |
| `aManualModuleWaitsForThePilotAndAutoAimsOnItsOwn` | manual não busca alvo nem atira sozinho; automático busca e atira |
| `anAutomaticModuleKeepsItsTargetWhileThePilotAimsElsewhere` | a mira do piloto não destrói o alvo do módulo automático |
| `thePilotSelectionBelongsToTheUnit` | casco novo nasce sem escolha e manual, seja o que for |
| `supportModulesAreAutomaticExceptTheLiquidJets` | suporte automático; `wave` manual; trocar mira não desliga |
| `theLiquidJetWaitsForTheHullToFire` | o jato de `wave` só sai com a nave atirando, paga a piscina e segue a mira |
| `indexOutOfRangeIsRefused` | índice fora da faixa é recusado em todos os métodos |

O harness dirige o pipeline na ordem real: controlador → `applyDisarmed` →
`weapon.update`. Ele arma os mounts controláveis a cada frame (o que o controlador do jogador
faz em jogo, com `controlWeapons`) e repõe o alvo a cada chamada, para que uma corrida longa
não fique sem munição nem sem inimigo.

Regressão verificada, suíte por suíte: `ModuleControlTest` (15), `ControlsMenuTest` (4),
`TurretModuleShootingTest` (3), `TurretLoadoutSmokeTest` (7), `TurretLoadoutDialogTest` (7),
`WeaponModuleTest` (8), `ModuleSupplyTest` (17), `WallLoadoutTest` (22),
`TurretAmmoCompatibilityTest` (6), `TestNewFeaturesModeTest` (11), `SupportLoadoutTest` (43) e
`SupportModuleEffectsTest` (24). O APK de depuração
compila: `.\gradlew.bat :android:assembleDebug` → `android/build/outputs/apk/debug/android-debug.apk`.

**[FATO]** Os testes de líquido de `SupportModuleEffectsTest` e `SupportLoadoutTest` foram
atualizados: como `wave` agora é pilotado, eles ligam o estado de tiro do casco
(`unit.isShooting(true)`) antes de ticar o módulo, que é exatamente o que o piloto e a IA
escrevem em jogo.

**[FATO]** 35 falhas de `ApplicationTests` (saves `load*Save`, validade de setores legados de
Serpulo/Erekir e `buildingDestruction`) são **pré-existentes** nesta árvore: estão listadas em
`build/failures-baseline.txt` (produzido antes desta sessão, onde `buildingDestruction` já
faltava na lista, mas a contagem de 35 do relatório é a mesma de hoje) e não têm relação com os
controles. `DiagnoseSupportModulesTest.java` (arquivo de diagnóstico de outra sessão, sem
commit) também não compilava; recebeu o mínimo para compilar (import de `Units`, `contains`
com predicado).

---

## 6. Ondas de diff (resumo por arquivo)

| Arquivo | Mudança |
|---|---|
| `core/src/orbital/input/Binding.java` | `moduleToggle01..10`, `controllerFire` |
| `core/src/orbital/input/ControllerInput.java` | **novo** — leitura de gamepad |
| `core/src/orbital/input/MobileInput.java` | stick, direção direta, câmera com caixa morta, gamepad |
| `core/src/orbital/input/InputHandler.java` | teclas numéricas dos módulos, `selectModule` (e `toggleModule` como atalho), reset |
| `core/src/orbital/entities/comp/WeaponsComp.java` | `disarmedModules`, `selectedModule`, `autoAimModules`, API de módulos, gate, `aim` sem limpar alvo automático |
| `core/src/orbital/type/Weapon.java` | `updateAutoTarget` extraído; condição estendida |
| `core/src/orbital/entities/units/SupportMount.java` | `autoTarget` |
| `core/src/orbital/entities/abilities/LiquidSupportAbility.java` | modo manual de `wave`/`tsunami` |
| `core/src/orbital/ui/AnalogStick.java` | **novo** — widget do stick |
| `core/src/orbital/ui/fragments/HudFragment.java` | stick, badges, escolha por toque (estrela), tooltips |
| `core/src/orbital/ui/dialogs/TurretLoadoutDialog.java` | botão de mira por arma instalada |
| `core/assets/bundles/bundle*.properties` | `module.toggle`, `module.select`, `module.hint.key`, `module.hint.select`, `module.hud.selected/silent/all`, `module.aim.*` |
| `core/src/orbital/ui/dialogs/KeybindDialog.java` | seção "Controles do Orbital" com os 3 interruptores; `analogKey`, `moduleHotkeysKey`, `controllerKey` |
| `core/src/orbital/ui/dialogs/SettingsMenuDialog.java` | Controles visível no Android também |
| `tests/src/test/java/ControlsMenuTest.java` | **novo** — rótulos dos bundles e a chave do gamepad |
| `core/assets/bundles/bundle*.properties` | `module.toggle`, `module.hint.key`, `module.aim.*`, `controls.orbital.*`, `setting.analogstick/module-hotkeys/gamepad`, `category.modules/gamepad`, `keybind.module_toggle_*`, `keybind.controller_fire` |
| `tests/src/test/java/ModuleControlTest.java` | **novo** |
| `tests/src/test/java/SupportModuleEffectsTest.java` | testes de líquido passam a ligar o tiro do casco |
| `tests/src/test/java/SupportLoadoutTest.java` | idem |
| `tests/src/test/java/DiagnoseSupportModulesTest.java` | correção mínima de compilação (import e `contains` com predicado) |

---

## 7. Pendências e decisões em aberto

1. **[PENDÊNCIA] Persistência da seleção.** A escolha do módulo que atira e o modo de mira são
   do piloto e da unidade, e morrem com os dois (`transient`, sem save e sem rede). Persistir
   loadout é tarefa da etapa A2 de `PLANO_ETAPAS_MODULOS.md`.
2. **[DECISÃO] Volta ao "todos atiram".** Como um casco nasce sem escolha, esgotar as opções
   não travaria o jogador: tocar/teclar a mesma posição solta a escolha e todos os módulos
   voltam a atirar juntos. Foi a resposta do dono à pergunta "e quando clicar de novo?".
2. **[PENDÊNCIA] Mais de 10 módulos.** Os `Binding` cobrem 10 (`num1..num0`); acima disso as
   células ficam sem tecla (o toque continua funcionando).
3. **[DECISÃO] Gatilho do jato líquido manual.** Um casco **com armas** só jorra enquanto
   está atirando (`unit.isShooting()`); um casco **sem arma alguma** não tem gatilho de fogo
   para segurar, então o módulo volta a agir sozinho — evita módulo morto (`hasWeapons()`).
   A metade de suporte (apagar aliados, status em área) nunca espera o gatilho.
4. **[DECISÃO] IA e multiplayer.** Numa partida em rede, só o piloto local vê sua seleção:
   `disarmedModules`/`autoAimModules` são estado local, sem pacote nem save.
5. **[PENDÊNCIA] Gamepad no Android:** `ControllerInput` lê os `KeyCode` que o Arc entrega;
   se um controle específico não reportar os eixos (`controllerLStickXAxis`), o d-pad e as
   teclas de face continuam funcionando. Validar em aparelho real.
6. **[PENDÊNCIA] Aim do gamepad** é um ponto a `max(unit.range(), 80)` da unidade; um alcance
   por arma ficaria melhor, mas depende da UI de mira.

---

## 8. Nota sobre a convivência com outras sessões

**[FATO]** Esta árvore recebeu trabalho paralelo nas mesmas áreas (módulos de suporte que
miram como o bloco, com `AimingModule`/`ModuleDock`, e testes novos em
`SupportModuleEffectsTest`). Onde as duas visões se cruzaram, a regra pedida pelo dono do
projeto foi mantida e os testes do outro lado ajustados com comentário:

- `wave`/`tsunami` continuam sendo a exceção pilotada entre os módulos de suporte
  (`SupportMount.autoTarget = category != liquid`).
- Os testes da outra sessão que assumiam `wave` automático foram reescritos para a regra
  pilotada (`liquidJetWaitsForThePilotWhileTheSupportHalfRuns`,
  `supportModulesDefaultToAutoTargetLikeTheirBlocks`,
  `turretModulesAimLikeTheirBlock`), mantendo a verificação da máquina de rotação/mira.
