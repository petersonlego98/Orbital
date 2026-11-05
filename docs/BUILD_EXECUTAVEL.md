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
