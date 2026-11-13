# Orbital v1.2.0 — Windows (instalador por usuário) e Android (APK)

Build `84087f5ef8`. O instalador continua **sem exigir administrador**: instala em
`%LOCALAPPDATA%\Programs\Orbital`, cria atalhos na Área de Trabalho e no Menu Iniciar e
registra a desinstalação em `HKCU`. O JRE 21 vai embutido — não é preciso ter Java instalado.

**Sistemas operacionais:** Windows 10/11 64 bits; Android 5.0 (API 21) ou superior.

### Windows

**Download:** `Orbital-1.2.0-win64-usuario.zip` (instalador + `payload/`, ~148 MB)
`SHA-256: 3946baaab3cc65f47977b59d5957eaaa8827e826b486e4b1909d118516739809`

1. Baixe o zip e confira a integridade com o `.sha256`.
2. Extraia mantendo `OrbitalSetup.exe` e a pasta `payload/` **juntos**.
3. Execute `OrbitalSetup.exe`.

### Android

**Download:** `Orbital-1.2.0-android-debug.apk` (~83 MB, `versionName` 1.2.0,
`versionCode` 120)
`SHA-256: 91b65e5ae292484b3331231ae2ba6822687f20d43ddd42cfd5b4d4489d899630`

1. Baixe o APK no celular (ou copie pelo `adb`).
2. Libere *instalar de fontes desconhecidas* para o navegador/gerenciador de arquivos.
3. Instale. O app abre em **paisagem**; a interface móvel tem escala própria em
   *Settings → Graphics → UI Scaling* (padrão 125%).

> ⚠️ Build de depuração: o id do pacote é `orbital.android.debug`. O release
> assinado continua pendente de keystore.

---

### Novidade: módulos iguais formam um grupo só, com selo ×N

Dois Duo montados no mesmo casco passaram a ser **um módulo com duas cópias**, e a interface
trata os dois como um só objeto:

- **Uma célula por grupo** na barra do HUD e no menu de peças, com o selo "×N" no canto quando
  há mais de uma cópia — cada cópia alimenta os pools do casco, então o número diz quantas
  instâncias a célula representa.
- **As teclas numéricas apontam para grupos**: apertar "1" escolhe todas as torres do grupo
  "Duo", não apenas uma montagem.
- **A mira é do grupo**: automático ou manual vale para todas as cópias iguais de uma vez.
  Armas **diferentes** seguem independentes — mudar uma não muda a outra.
- `WeaponsComp.moduleGroups()`/`moduleCopies()`/`sameModule()` são a identidade usada pela
  barra, pelo menu e pelos atalhos.

### Novidade: mira automática e manual, por módulo

- O dono da mira é escolhido **por módulo**: arma do casco e `wave`/`tsunami` nascem manuais,
  módulos em automático e módulos de suporte se mantêm automáticos.
- `TurretLoadoutDialog` tem um botão por arma instalada (`Icon.filter` = automático,
  `Icon.move` = manual) e o modo passa a valer para **todas as cópias iguais**.
- A mira do piloto não descarta mais o alvo que um módulo automático acabou de escolher — a
  arma automática não aponta mais para o mouse.

### Novidade: painel do modo de teste nasce no tamanho certo

- **Botão de recolher/expandir** no cabeçalho: recolhido, só o cabeçalho fica na tela — o menu
  inteiro (unidades, montar peças, peças da nave e construções inimigas) é escondido.
- **Área das naves aliadas em largura cheia**: a grade ocupa a largura inteira do painel, numa
  linha própria acima das duas colunas de baixo, sem tocar em "Construções inimigas". O número
  de colunas agora vem da largura real do painel, e não da largura da tela.
- O botão é grande o suficiente para toque (48 no celular, 40 no desktop) e tem fundo visível.

### Melhorias em módulos e recursos

- **Pool de líquido compartilhado**: todas as capacidades de líquido dos módulos montados
  (torres, suporte, unidades) são somadas ao pool da casca, e o HUD mostra o total.
- **Torres de líquido atiram**: `wave` e `tsunami` disparam a munição líquida do bloco, com o
  mesmo projétil, dano e status, cobrando o jato do pool do casco.
- **Reparo cura unidades aéreas**: os módulos de reparo montados no casco passaram a atender a
  asa com que ele voa.
- **Energia é leitura, não gasto**: a taxa do bloco só dimensiona a barra; sem isso as torres
  líquidas esvaziavam o pool em segundos e ficavam escuras para sempre.
- **Seleção de líquido desde o primeiro módulo**: o seletor aparece com um módulo montado, sem
  precisar instalar um líquido antes.

---

### Correções

- **HUD — grade de naves cortada**: o número de colunas era calculado pela largura da tela e
  estourava o painel, então o último ícone de cada fileira ficava escondido atrás da barra de
  rolagem. Agora as colunas e o tamanho da célula vêm da largura que o painel tem de verdade.
- **HUD — botão de recolher não fazia nada**: o painel é montado uma única vez
  (`Group.fill` roda o callback só na construção), então o estado do botão nunca era aplicado.
  O conteúdo passou a viver numa tabela própria, reconstruída quando o estado muda.
- **HUD — o painel não escondia o menu inteiro**: recolhido, ainda sobravam a seção de peças e a
  de construções inimigas na tela. Agora só o cabeçalho fica visível.
- **HUD — área das unidades pequena**: a grade ocupava só a coluna da esquerda do painel, que
  tinha duas colunas; a área à direita dela ficava vazia.
- **Android — menus desalinhados**: diálogos que preenchem a tela eram centralizados contra as
  barras do sistema e empurrados para cima, saindo do topo.
- **Android — dupla escala por `Scl`** no menu de módulos, que roubava uma coluna de cartões e
  cortava os textos nas duas pontas.
- **Mira automática seguia o mouse** em vez de escolher o próprio alvo do módulo.

---

### Verificação desta build

- Suítes de módulos e HUD sem falhas: `ModuleControlTest` (15), `ModuleSupplyTest` (22),
  `SupportModuleEffectsTest` (26), `SupportLoadoutTest` (43), `WallLoadoutTest` (22),
  `AutoAimAimingTest` (4), `ModuleTutorialTest` (4), `TestNewFeaturesModeTest` (12),
  `TurretLoadoutDialogTest` (7), `TurretLoadoutSmokeTest` (7), `WeaponModuleTest` (8),
  `TurretAmmoCompatibilityTest` (6), `TurretModuleShootingTest` (3).
- Binários executados: `OrbitalSetup.exe` gerado com o payload do app-image (373 arquivos) e o
  APK conferido com `aapt` (`versionCode` 120, `versionName` 1.2.0).

> ⚠️ Build em desenvolvimento: ainda pode conter bugs. Reporte em
> <https://github.com/petersonlego98/Orbital/issues>