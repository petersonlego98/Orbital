# Instalador por usuário (sem administrador)

Documento técnico do instalador do Orbital para Windows. Ele instala o app-image
de `deploy/windows64/Orbital/` **sem prompt de UAC e sem privilégios de
administrador**.

> Documento de engenharia. Não substitui revisão jurídica nem assinatura de
> código.

---

## 1. Por que não precisa de administrador

| Ponto | Implementação |
|---|---|
| Manifesto do executável | `requestedExecutionLevel level="asInvoker" uiAccess="false"` embutido em `OrbitalSetup.exe`. |
| Destino | `%LOCALAPPDATA%\Programs\Orbital` (pasta do usuário). |
| Atalhos | Menu Iniciar e Área de Trabalho do usuário (`FOLDERID_Programs`, `FOLDERID_Desktop`). |
| Registro | apenas `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\Orbital`. |
| O que **não** é feito | nenhuma escrita em `Program Files`, `HKLM`, `System32`, serviços, drivers, PATH global ou tarefas agendadas. |

O `Orbital.exe` gerado pelo `jpackage` já tem manifesto `asInvoker`; o problema
histórico era apenas **onde** os arquivos eram colocados. O instalador resolve
isso copiando o app-image intacto para uma pasta gravável pelo usuário.

O registro em `HKCU\...\Uninstall` faz o Orbital aparecer em **Aplicativos e
recursos** com o comando de desinstalação
`"%LOCALAPPDATA%\Programs\Orbital\Uninstall.exe" --uninstall`.

---

## 2. Exceção de toolchain (importante)

A regra fixa do projeto é **compilar sempre com `cl.exe` do MSVC**. Nesta
máquina não há `cl.exe`, WiX (`candle.exe`/`light.exe`), Inno Setup, NSIS nem
7-Zip. Foi verificado que `jpackage --type exe --win-per-user-install` falha com
`Can not find WiX tools (light.exe, candle.exe)`.

**Péterson autorizou explicitamente** o uso de **MinGW-w64 `g++` + `windres`**
para este instalador. A regra do `cl.exe` continua válida para os demais
projetos; se o MSVC for instalado, o fonte C++ é portável e pode ser recompilado
com `cl.exe`.

Versões usadas na validação: `g++ 16.2.0` (MinGW-W64 UCRT), `windres 2.47`.

---

## 3. Arquivos

```
installer/
├── build.sh                       # compila + prepara o payload (bash)
└── src/
    ├── main.cpp                   # instala/desinstala, GPL-3.0-or-later
    ├── app.rc                     # manifesto + VERSIONINFO
    └── orbital_setup.manifest     # asInvoker + Common Controls 6

build/installer/                   # saída, ignorada pelo Git
├── OrbitalSetup.exe               # ~3,4 MB, estático
├── payload/                       # 374 arquivos / 235,8 MB
│   ├── Orbital.exe
│   ├── app/Orbital.jar
│   ├── runtime/
│   └── LICENSE, CREDITS.md, NOTICE.txt, THIRD_PARTY_LICENSES/, docs/
└── default-manifest.o
```

O executável é linkado estaticamente (`-static -static-libgcc
-static-libstdc++`). As únicas dependências são DLLs do sistema
(`KERNEL32`, `USER32`, `GDI32`, `SHELL32`, `OLE32`, `ADVAPI32`, `COMCTL32` e o
UCRT do Windows 10+). Não é necessário instalar MinGW nem Java na máquina de
destino: o JRE 21 já vem em `payload/runtime/`.

---

## 4. Como gerar e distribuir

Pré-requisito: o app-image oficial deve estar atualizado
(`docs/BUILD_EXECUTAVEL.md`, `:desktop:dist` + `jpackage`).

```bash
bash installer/build.sh
```

Para distribuir, compacte **juntos** `OrbitalSetup.exe` e a pasta `payload/`:

```
Orbital-1.0-win64-usuario.zip
├── OrbitalSetup.exe
└── payload/
    └── ...
```

O instalador procura `payload/` ao lado de si mesmo. Se a pasta estiver
ausente, ele encerra com código `11` e mensagem de erro — não tenta baixar nada.

---

## 5. Uso

### Instalar

Executar `OrbitalSetup.exe` (duplo clique, sem "Executar como administrador"):

1. Confirma o destino `%LOCALAPPDATA%\Programs\Orbital`.
2. Se já existir uma instalação, remove a anterior (inclusive arquivos
   somente leitura, como o `Orbital.exe` do `jpackage`).
3. Copia os 374 arquivos de `payload/` com barra de progresso.
4. Copia a si mesmo como `Uninstall.exe`.
5. Cria `Orbital.lnk` no Menu Iniciar e na Área de Trabalho.
6. Registra o desinstalador em `HKCU`.

### Desinstalar

Pelo **Aplicativos e recursos**, pelo atalho do Menu Iniciar **ou** executando
`Uninstall.exe --uninstall` na pasta de instalação. Remove atalhos, a chave
`HKCU` e todos os arquivos. Como um processo não pode apagar o próprio `.exe`,
o desinstalador cria um `.cmd` oculto em `%TEMP%` (sem janela visível) que
apaga `Uninstall.exe` e a pasta já vazia, e em seguida apaga a si mesmo.

---

## 6. Opções de linha de comando

| Opção | Efeito |
|---|---|
| `--uninstall` | executa a remoção em vez da instalação. |
| `--silent` | sem janelas; resultado gravado via `--log`. |
| `--target <dir>` | substitui o destino padrão (usado em CI). |
| `--log <arquivo>` | gravar log UTF-8 com `RESULT=<código>` e `STATUS=ok\|failed`. |
| `--no-shortcuts` | não cria/remove atalhos. |
| `--no-register` | não cria/remove a chave `HKCU`. |
| `--shortcut-dir <dir>` | **CI**: grava dois `.lnk` de teste nesse diretório em vez das pastas reais do shell. |
| `--registry-key <nome>` | **CI**: usa `HKCU\...\Uninstall\<nome>` em vez de `Orbital`. |

Em `--silent`, o `Uninstall.exe` é **mantido** no diretório para tornar a
validação determinística. A remoção completa ocorre no modo interativo.

Códigos de saída: `0` sucesso; `1` cancelado pelo usuário; `10`–`21` falhas
específicas (payload ausente = `11`, falha de cópia = `15`, atalho = `17/18`,
registro = `19`, remoção incompleta = `21`).

---

## 7. Validação executada (5 de novembro de 2026)

| Verificação | Resultado |
|---|---|
| `bash installer/build.sh` | concluído sem avisos do compilador/linkador. |
| Manifesto embutido | 1 manifesto, `asInvoker`, `uiAccess="false"`, sem `requireAdministrator`. |
| Dependências do `.exe` | somente DLLs do sistema; nenhuma `libgcc`/`libstdc++` externa. |
| Instalação silenciosa (sem atalhos/registro) | `RESULT=0`, **374 arquivos copiados**, 0 divergências de SHA-256 contra `payload/`. |
| Reinstalação sobre instalação anterior | `RESULT=0` — confirmou a correção do arquivo somente leitura. |
| Conteúdo instalado | `Orbital.jar` com 39 WAVs / 182 OGGs e `MIT-Original-Sounds.txt` presente. |
| Instalação completa com ganchos de CI | 2 `.lnk` válidos (magic `4C 00 00 00`, apontando para `Orbital.exe`); chave `HKCU` criada com `DisplayName=Orbital` e `UninstallString` correto. |
| Desinstalação completa com ganchos de CI | `RESULT=0`; atalhos e chave de teste removidos; restou apenas `Uninstall.exe` (modo silencioso). |
| Efeitos colaterais reais | nenhum: chave `HKCU\...\Uninstall\Orbital` ausente e nenhum atalho real criado. |
| Janelas | nenhuma janela aberta nos testes (modo silencioso). |

**Não validado:** clique real na interface gráfica, fluxo de UAC em outro
perfil de usuário, desinstalação interativa com o `.cmd` de auto-remoção, e
execução do jogo após a instalação. Esses pontos exigem sessão interativa.

---

## 8. Limitações conhecidas

- **Não assinado.** O SmartScreen pode exibir aviso na primeira execução.
  Assinatura de código exigiria certificado e está fora deste escopo.
- **Sem atualização automática.** Reinstalar substitui a pasta inteira; dados
  do usuário ficam fora dela (`%APPDATA%`/documentos do jogo), mas não há
  migração de versão.
- **Somente por usuário.** Não há instalação para todos os usuários da máquina.
- **Windows 10/11 x64.** Requer o Universal CRT (presente nesses sistemas).
- **Sem compressão.** O pacote tem ~236 MB; é um zip/pasta, não um `.exe`
  autoextraível.
- O texto legal e os créditos são copiados intactos de `payload/`; o
  instalador não altera licenças de terceiros.

---

## 9. Licenciamento

O código-fonte do instalador (`installer/`) é parte do Orbital e está sob
**GPL-3.0-or-later**. Os arquivos em `payload/` mantêm as licenças registradas
em `THIRD_PARTY_LICENSES/manifest.csv` (GPL-3.0-or-later, MIT, SIL OFL 1.1,
CC BY 4.0, CC BY-SA 4.0 e domínio público). Nenhuma licença é alterada pela
cópia.

Documentos relacionados: `docs/BUILD_EXECUTAVEL.md`,
`docs/PROPOSTA_MIT_ASSETS.md`, `THIRD_PARTY_LICENSES/assets.txt`.
