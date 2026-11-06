# Guia de Compilação e Padrão de Build Executável (.exe)

Este documento estabelece o padrão oficial para geração e manutenção de executáveis (`.exe`) do projeto **Orbital**, evitando a proliferação de builds duplicados ou obsoletos.

---

## 1. Localização Oficial do Executável Único

O projeto mantém **apenas um build executável oficial** em:

```
deploy/windows64/Orbital/Orbital.exe
```

- **Estrutura do diretório:**
  - `deploy/windows64/Orbital/Orbital.exe`: Inicializador nativo do jogo para Windows 64-bit.
  - `deploy/windows64/Orbital/app/Orbital.jar`: Fat JAR compilado com todo o código e assets do jogo.
  - `deploy/windows64/Orbital/app/Orbital.cfg`: Configurações de inicialização e argumentos da JVM.
  - `deploy/windows64/Orbital/runtime/`: JRE Java 21 embutido (permite execução em qualquer máquina Windows sem necessidade de ter Java pré-instalado).
  - `LICENSE`, `CREDITS.md`, `NOTICE.txt` e `THIRD_PARTY_LICENSES/`: licenças e créditos que devem acompanhar toda distribuição.

> [!IMPORTANT]
> Quaisquer outros diretórios de build temporários contendo executáveis (ex.: `desktop/build/exe`, `desktop/build/exe-testfeatures`, `desktop/build/exe-updated` ou `desktop/build/jpackage`) **não devem ser mantidos** e devem ser removidos.

---

## 2. Passo a Passo para Gerar um Novo Build

Quando houver alterações no código e for necessário atualizar o `Orbital.exe`:

### Passo 0: Regenerar ícone e texturas do tema (quando necessário)

A arte oficial está versionada em `core/assets-raw/keyart/orbital-keyart.jpg`.
O diretório `deploy/` é ignorado pelo Git, portanto **`deploy/orbital.ico` não
existe num clone novo** e o Passo 3 falharia sem ele. Regenere todos os
derivados a partir da arte-fonte com o conversor sem dependências externas
(JDK 17+, modo single-file):

```bash
java tools/ArtConverter.java
```

| Arquivo gerado | Uso |
|---|---|
| `deploy/orbital.ico` | ícone do `jpackage` e do instalador — 7 entradas 16–256 px, 32 bpp, BMP/DIB |
| `core/assets/icons/icon.png` | ícone da janela em `DesktopLauncher` |
| `core/assets/icons/icon.icns` | ícone macOS das tarefas `packr*` |
| `core/assets/ui/orbital-poster.png` | fundo do menu e da tela de carregamento |
| `core/assets/ui/orbital-wordmark.png` | logotipo do menu |
| `core/assets/ui/orbital-mark.png` | emblema circular da tela de carregamento |

O ícone do aplicativo (`icon.png`, `orbital.ico`, `icon.icns`) **não é
redimensionado a partir da key-art**: ele é desenhado pelo próprio
`ArtConverter` na resolução final de cada entrada, com o nível de detalhe
caindo conforme o tamanho (16/24/32 px ficam sem lua, sem faísca e com
traços mais grossos). O Windows exibe ícones de app em 16–48 px (barra de
tarefas, atalho, Menu Iniciar, barra de título, Alt+Tab); redimensionar a
key-art para esses tamanhos transformava o wordmark "ORBITAL" em ruído
cinza. O `jpackage` incorpora o `.ico` dentro do `Orbital.exe` e o
`windres` (instalador) dentro do `OrbitalSetup.exe`: ao trocar a arte,
**regere o `.exe` do jogo e o instalador**, senão os binários antigos
continuam com o ícone anterior.

As texturas `ui/` são propositalmente **soltas** (fora de `sprites/`): assim o
atlas empacotado e a geração de `Fonts.mergeFontAtlas()` permanecem intactos.
O `jpackage` incorpora o ícone dentro do `Orbital.exe`; ao atualizar apenas o
JAR de um app-image já existente, **substitua também o `.exe`**, caso contrário
o ícone antigo permanece no binário.

### Passo 1: Limpar builds e executáveis antigos
No PowerShell, dentro da raiz do projeto:

```powershell
Remove-Item -Path "desktop\build\exe*", "desktop\build\jpackage", "deploy\windows64" -Recurse -Force -ErrorAction SilentlyContinue
```

### Passo 2: Compilar o Fat JAR atualizado
Gere o `.jar` empacotado através do Gradle:

```powershell
.\gradlew.bat :desktop:dist
```

O arquivo resultante será gerado em:
`desktop/build/libs/Orbital.jar`

### Passo 3: Empacotar o executável único oficial com `jpackage`
Execute o comando do `jpackage` (incluso no JDK 21):

```powershell
jpackage --type app-image `
  --name "Orbital" `
  --input "desktop\build\libs" `
  --main-jar "Orbital.jar" `
  --main-class "orbital.desktop.DesktopLauncher" `
  --icon "deploy\orbital.ico" `
  --dest "deploy\windows64" `
  --java-options "-Djpackage.app-version=1.0" `
  --java-options "-XX:+ShowCodeDetailsInExceptionMessages"
```

### Passo 4: Copiar licenças, créditos e avisos
A GPLv3 exige que a licença e os avisos acompanhem os binários. Copie os arquivos versionados na raiz para a pasta do executável:

```powershell
$dest = "deploy\windows64\Orbital"
Copy-Item LICENSE, README.md, CREDITS.md, NOTICE.txt -Destination $dest -Force
Copy-Item THIRD_PARTY_LICENSES -Destination $dest -Recurse -Force
New-Item -ItemType Directory -Force -Path "$dest\docs" | Out-Null
Copy-Item docs\LICENCIAMENTO_ASSETS.md, docs\auditoria-licencas-2026-11.md, docs\BUILD_EXECUTAVEL.md -Destination "$dest\docs" -Force
```

Resultado esperado:

```
Orbital/
├── LICENSE
├── README.md
├── THIRD_PARTY_LICENSES/
│   ├── GPL-3.0.txt
│   ├── libraries.txt
│   ├── assets.txt
│   ├── fonts.txt
│   ├── creative-commons.txt
│   ├── OFL-1.1.txt
│   ├── MIT-ProggyCleanTT.txt
│   └── manifest.csv
├── docs/
│   ├── BUILD_EXECUTAVEL.md
│   ├── LICENCIAMENTO_ASSETS.md
│   └── auditoria-licencas-2026-11.md
├── CREDITS.md
├── NOTICE.txt
└── Orbital.exe
```

> Os builds gerados pelas tarefas Gradle `packrWindows64`, `packrLinux64` e `packrMacOS` já copiam esses arquivos automaticamente. O passo manual acima é necessário apenas no fluxo com `jpackage`.

---

## 3. Script de Automação Rápida (PowerShell)

Para executar todo o processo em um único comando:

```powershell
# 1. Limpeza
Remove-Item -Path "desktop\build\exe*", "desktop\build\jpackage", "deploy\windows64" -Recurse -Force -ErrorAction SilentlyContinue

# 2. Build do JAR
.\gradlew.bat :desktop:dist

# 3. Empacotamento do .exe
jpackage --type app-image --name "Orbital" --input "desktop\build\libs" --main-jar "Orbital.jar" --main-class "orbital.desktop.DesktopLauncher" --icon "deploy\orbital.ico" --dest "deploy\windows64" --java-options "-Djpackage.app-version=1.0" --java-options "-XX:+ShowCodeDetailsInExceptionMessages"

# 4. Licenças e créditos
$dest = "deploy\windows64\Orbital"
Copy-Item LICENSE, README.md, CREDITS.md, NOTICE.txt -Destination $dest -Force
Copy-Item THIRD_PARTY_LICENSES -Destination $dest -Recurse -Force
New-Item -ItemType Directory -Force -Path "$dest\docs" | Out-Null
Copy-Item docs\LICENCIAMENTO_ASSETS.md, docs\auditoria-licencas-2026-11.md, docs\BUILD_EXECUTAVEL.md -Destination "$dest\docs" -Force
```

---

## 5. Release no GitHub

O texto do release **fica versionado** em `docs/RELEASE_NOTES_<tag>.md` (ex.:
`docs/RELEASE_NOTES_v1.1.0.md`). Assim o release não depende de sessão de terminal e
tag, binário e notas não divergem.

1. Escreva/atualize `docs/RELEASE_NOTES_v<versao>.md` e faça commit.
2. Regenere o instalador (`bash installer/build.sh`) e compacte o zip de distribuição:

   ```powershell
   tar -a -cf build\installer\Orbital-<versao>-win64-usuario.zip -C build\installer OrbitalSetup.exe payload
   (Get-FileHash build\installer\Orbital-<versao>-win64-usuario.zip -Algorithm SHA256).Hash.ToLower()
   ```

   Guarde o SHA-256 dentro das notas.
3. Publique (ou atualize) o release a partir do arquivo:

   ```powershell
   .\tools\publish-release.ps1 -Version <versao> -Assets build\installer\Orbital-<versao>-win64-usuario.zip -Latest
   ```

O script lê as notas com `[System.IO.File]::ReadAllText` e envia o JSON como **bytes
UTF-8**. São duas regras que já quebraram release: `Get-Content` do PowerShell 5.1 lê
UTF-8 como ANSI (estragava os acentos) e passar uma `string` no `-Body` faz o
`Invoke-WebRequest` recodificar com o code page do console (e, se o valor vier de um
comando, serializa o objeto do provider no lugar do texto).

---

## 4. Cuidados com Aplicativos de Nuvem (ex.: TeraBox / OneDrive)

Arquivos temporários e pastas de build como `.gradle/`, `build/` e `deploy/windows64/` sofrem constantes escritas e exclusões. Certifique-se de que softwares de sincronização estejam pausados ou com exclusão de pastas configurada para evitar bloqueios de arquivo durante a compilação.

---

## 5. Checklist de Distribuição GPLv3

Antes de publicar qualquer binário, confirme que:

- [ ] `LICENSE` contém o texto da GPLv3.
- [ ] `README.md` descreve o fork, a origem e a licença do projeto.
- [ ] `THIRD_PARTY_LICENSES/GPL-3.0.txt` também está presente.
- [ ] `CREDITS.md` credita Anuken, a comunidade original e os colaboradores do fork.
- [ ] `NOTICE.txt` informa onde o código-fonte correspondente pode ser obtido.
- [ ] `THIRD_PARTY_LICENSES/libraries.txt` e `assets.txt` acompanham a instalação.
- [ ] `THIRD_PARTY_LICENSES/fonts.txt`, `creative-commons.txt`, `OFL-1.1.txt`,
      `MIT-ProggyCleanTT.txt`, `MIT-Original-Sounds.txt` e `manifest.csv`
      acompanham a instalação.
- [ ] `python tools/audit_assets.py --check` retorna código 0 (ou
      `.\gradlew.bat checkAssetLicenses`).
- [ ] O código-fonte correspondente à versão publicada está acessível no repositório do fork.

---

## 6. Instalador sem administrador

O `Orbital.exe` deste documento é um **app-image**, não um instalador. Para
distribuir ao usuário final sem exigir UAC, gere o instalador por usuário:

```bash
bash installer/build.sh
```

Ele produz `build/installer/OrbitalSetup.exe` mais a pasta
`build/installer/payload/` (cópia exata deste app-image). Os dois devem ser
distribuídos juntos. A instalação vai para `%LOCALAPPDATA%\Programs\Orbital`,
cria atalhos no Menu Iniciar/Área de Trabalho e registra o desinstalador em
`HKCU` — nada em `Program Files` ou `HKLM`.

Detalhes de toolchain (exceção autorizada ao `cl.exe`), opções de linha de
comando, validação e limitações: **[Instalador por usuário](INSTALADOR_SEM_ADMIN.md)**.
