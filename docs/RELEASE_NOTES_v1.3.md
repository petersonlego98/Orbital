# Orbital v1.3 — Windows e Android (Petrônio, módulos avançados e construção manual)

Release **v1.3 reconstruída**: os anexos abaixo substituem o APK antigo sem assinatura e adicionam o instalador Windows, que faltava nesta release. Ambos foram gerados do código mais recente do repositório.

`versionName` **1.3** / `versionCode` **130** no Android; instalador e JAR reportam **1.3.0.0**.

**Sistemas operacionais:** Windows 10/11 64 bits (JRE 21 embutido, sem exigir administrador); Android 7.0 (API 24) ou superior.

### Windows

**Download:** `Orbital-1.3-win64-usuario.zip` (~155 MB — `OrbitalSetup.exe` + `payload/`)
`SHA-256: 02d7805405758c2eb786157365c397446e73217289677d3b05ccf9e9049fdb8e`

1. Baixe o zip e confira a integridade com o `.sha256`.
2. Extraia mantendo `OrbitalSetup.exe` e a pasta `payload/` **juntos**.
3. Execute `OrbitalSetup.exe`: instala em `%LOCALAPPDATA%\Programs\Orbital`, cria atalhos e registra o desinstalador em `HKCU` — **sem UAC**.

### Android

**Download:** `Orbital-1.3-android-debug.apk` (~93 MB)
`SHA-256: e0d67dcd88a92a7b7e53fdf00e032aa95c3b1d4e2b276c2dcf08e052c17c3211`

1. Baixe o APK no celular (ou copie pelo `adb`).
2. Libere *instalar de fontes desconhecidas* para o navegador/gerenciador de arquivos.
3. Instale e abra em paisagem.

> O APK é **assinado** com a chave automática de depuração do Android (esquema v2) — `apksigner verify` confirma. O id do pacote é `com.orbital.peterson.debug`, então ele convive com a versão `com.orbital.peterson`. O AAB de release continua pendente de keystore próprio.

---

### Novidades incluídas nesta build

- **Petrônio (PTN)**: minério universal T6 (dureza 6, dourado) que substitui todos os depósitos e funciona como curinga em qualquer receita — o item real é gasto primeiro e o Petrônio completa a diferença. Botão no menu principal abre o diálogo explicativo (EN/pt_BR), com sprites recriados pela paleta da referência.
- **Evolução de fábricas**: fábricas de unidades aéreas sobem de T1 a T5 trocando o sprite pelo do reconstrutor correspondente; a evolução substitui **todas** as unidades da linha do jogador.
- **Módulos de arma combinados**: armas nativas agrupadas por tipo de tiro com níveis T selecionáveis, barra de recarga individual e controle automático por número/auto-alvo.
- **Construção manual**: a automação de construção foi removida por completo — não há mais planejador de cadeias, rotas automáticas, builders de IA ou ferramenta "a unidade constrói" no modo Novidades. O jogador coloca cada bloco; unidades construtoras e o comando CONSTRUIR continuam funcionando.
- **Menus enxutos**: todos os blocos de distribuição (esteiras, dutos, tubulações, unloaders etc.) saíram do menu e do editor; mass-driver permanece como módulo dorsal, e os pisos de minério continuam intactos. O menu de construções inimigas no modo Novidades agora é **opcional**, ativado em *Configurações → Construções → Construções inimigas* (desligado por padrão).
- **Modo Novidades com construção gratuita**, tutorial, Ferramentas de teste, painel recolhível e catálogo modal de unidades/estruturas.
- **Battle Royale 8x8 sem núcleos**: 8 regiões, módulos no chão, tela de preparação, controle mobile twin-stick e HUD dedicado.
- **Android**: escala automática por DPI, diálogos que respeitam barras de sistema e layout adaptado ao modo de teste.
- **Privacidade**: política dentro do jogo (*Sobre → Política de Privacidade*) — sem telemetria, rastreamento ou relatório remoto de erros.
- **Comparativo**: `docs/RELATORIO_COMPARATIVO_DARK_NEBULA.md` documenta equivalências e diferenças em relação ao Dark Nebula.

### Battle Royale / Android (da build original v1.3)

- Mapa sem núcleos, vitória por última esquadra viva; módulos de suporte e armas spawnam no setor e podem ser coletados por clique/toque.
- Stick esquerdo move e direito mira/dispara; eixo Y corrigido; arrastar a tela move só a câmera.
- Armas de cura escolhem sozinhas o aliado ferido mais próximo; módulos instalados após o spawn reabilitam o autofire.
- Escala por densidade da tela no Android; correções de layout na carga, expedição e preparação.

### Verificação desta build

- Suíte completa: **803 testes, 5 falhas preexistentes** de leitura de saves `.msav` antigos; nenhuma falha nova.
- `:desktop:dist` e `:android:assembleDebug` concluídos; `apksigner verify` aprovado (v2, 1 signer).
- App-image Windows gerado com `jpackage` (JDK 21, heap limitado a 1536 MB), 373 arquivos no payload, instalador `asInvoker` sem UAC.
- Binários: `Orbital.jar`, `Orbital.exe`, `OrbitalSetup.exe`, APK debug assinado — todos com SHA-256 conferido.
- Nenhuma chave de assinatura ou conta de serviço está versionada.

> ⚠️ Build em desenvolvimento: ainda pode conter bugs. Reporte em
> <https://github.com/petersonlego98/Orbital/issues>
