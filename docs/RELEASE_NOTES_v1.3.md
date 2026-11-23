# Orbital v1.3 — Battle Royale 8x8 sem núcleos, DPI no Android e política de privacidade

Build de release com `versionName` **1.3** e `versionCode` **130** no Android. O instalador do Windows também passa a reportar 1.3.0.0.

**Sistemas operacionais:** Windows 10/11 64 bits; Android 7.0 (API 24) ou superior.

> ⚠️ **Anexos sem assinatura:** o APK desta release e o AAB 1.3 são *unsigned*. Assine com a sua própria chave antes de instalar ou publicar na Play Store.

**Download:** `Orbital-1.3-unsigned.apk` (~90,5 MB — `versionName` 1.3, `versionCode` 130)
`SHA-256: d5b65fb1b46244305190584286d73fdf1600809f059d42f071a5a4e5329710d3`

---

### Battle Royale 8x8 reworkado

- **Mapa sem núcleos**: o setor 512x512 tem 8 regiões de spawn, uma por time. Vence o último time com unidades vivas — o motor não usa mais a vitória por núcleos.
- **Módulos no chão**: torres, placas e peças de suporte aparecem espalhados pelo setor (incluindo lotes periódicos). Clique/toque para instalar; o **X vermelho** na célula do HUD devolve o módulo ao chão. A IA também coleta módulos.
- **Tela de preparação**: escolha uma nave, equipe os módulos e só então entre na batalha.
- **Controle mobile twin-stick**: o stick esquerdo move, o direito mira e dispara. Arrastar a tela move apenas a câmera. O eixo Y do stick foi corrigido (empurrar para cima agora é +Y).
- **HUD 8x8**: barra de módulos com armas fixas e recarga individual, botão de menu dentro da caixa de status, contagem de unidades inimigas e menos botões de construção/paleta.
- **Armas de cura** escolhem sozinhas o aliado ferido mais próximo. Módulos montados depois do spawn voltam a habilitar o autofire.
- Minério variado por região (cobre, chumbo e carvão) e possessão de outras naves desligada.
- O servidor inicia a partida de forma autoritativa; clientes recebem o estado.

### Android

- **Escala automática por DPI**: o launcher informa a densidade da tela e a interface mantém o mesmo tamanho físico em diferentes dispositivos — antes a opção só existia no Windows.
- O interruptor aparece junto ao slider de escala (o menu Gráficos Avançados não existe no mobile).
- Correções de layout: tela de carregamento, expedição e preparação em coluna limitada; diálogos respeitam as barras de sistema; grade do modo de teste se adapta à altura disponível.

### Privacidade

- **Política de privacidade dentro do jogo** (`core/assets/privacy.html`, também em `docs/PRIVACIDADE.html`), acessível em *Sobre → Política de Privacidade*.
- O texto declara explicitamente: sem telemetria, sem rastreamento de comportamento e sem relatório remoto de erros.

### Verificação desta build

- `:android:bundleRelease` e `:android:assembleRelease` concluídos com sucesso.
- Manifest gerado conferido: `versionCode=130`, `versionName=1.3`.
- Suítes de teste incluem `BattleRoyaleModeTest`, `BattleRoyaleLootTest`, `BattleRoyaleHudTest` e `DpiPreferenceTest`.
- Nenhuma chave de assinatura ou conta de serviço está versionada; o workflow de release usa secrets do GitHub.

> ⚠️ Build em desenvolvimento: ainda pode conter bugs. Reporte em
> <https://github.com/petersonlego98/Orbital/issues>
