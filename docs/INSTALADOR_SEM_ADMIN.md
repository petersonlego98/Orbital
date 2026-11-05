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
| Atalhos | **Área de Trabalho** (`FOLDERID_Desktop`) — obrigatório; **Menu Iniciar** (`FOLDERID_Programs`) — melhor esforço, falha gera aviso e não aborta. |
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
├── payload/                       # 375 arquivos / 235,8 MB
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
3. Copia os 375 arquivos de `payload/` com barra de progresso.
4. Copia a si mesmo como `Uninstall.exe`.
5. Cria `Orbital.lnk` na **Área de Trabalho** (obrigatório). Se falhar, a
   instalação é interrompida com código `18`.
6. Tenta criar `Orbital.lnk` no **Menu Iniciar**. Se falhar, registra
   `WARNING=start-menu-shortcut` no log e continua — a Área de Trabalho é o
   atalho preferido.
7. Registra o desinstalador em `HKCU` (mantido, para constar em Aplicativos e
   recursos).

> **COM é por thread.** A cópia roda numa thread de trabalho para não travar a
> janela. `CoCreateInstance` (usado para gerar `.lnk`) só funciona se COM for
> inicializado **nessa mesma thread**; por isso `createShortcut` usa um escopo
> RAII próprio de `CoInitializeEx`/`CoUninitialize`.

### Desinstalar

Basta **dar duplo clique em `Uninstall.exe`** na pasta de instalação, ou usar
**Aplicativos e recursos**. O executável detecta o próprio nome e entra no modo
de remoção automaticamente; `--uninstall` continua aceito. `--install` força o
comportamento de instalador caso seja necessário reinstalar a partir dessa
cópia.

A remoção apaga atalhos, a chave `HKCU` e todos os arquivos. Como um processo
não pode apagar o próprio `.exe`, o desinstalador cria um `.cmd` oculto em
`%TEMP%` (sem janela visível) que apaga `Uninstall.exe` e a pasta já vazia, e
em seguida apaga a si mesmo. O helper é iniciado com diretório de trabalho em
`%TEMP%` e também faz `cd /d "%~dp0"`: se herdasse a pasta de instalação como
CWD, o Windows manteria o diretório aberto e `rmdir` falharia.

---

## 6. Opções de linha de comando

| Opção | Efeito |
|---|---|
| `--uninstall` | executa a remoção em vez da instalação. Implícito quando o executável se chama `Uninstall.exe`. |
| `--install` | força o fluxo de instalação mesmo quando o arquivo se chama `Uninstall.exe`. |
| `--silent` | sem janelas; resultado gravado via `--log`. |
| `--self-delete` | **CI**: em `--silent`, agenda o mesmo `.cmd` oculto de auto-remoção usado no modo interativo. |
| `--worker` | **CI**: executa a instalação em thread de trabalho, reproduzindo o modelo da GUI sem abrir janela. |
| `--target <dir>` | substitui o destino padrão (usado em CI). |
| `--log <arquivo>` | gravar log UTF-8 com `RESULT=<código>` e `STATUS=ok\|failed`. |
| `--no-shortcuts` | não cria/remove atalhos. |
| `--no-register` | não cria/remove a chave `HKCU`. |
| `--shortcut-dir <dir>` | **CI**: grava dois `.lnk` de teste nesse diretório em vez das pastas reais do shell. |
| `--registry-key <nome>` | **CI**: usa `HKCU\...\Uninstall\<nome>` em vez de `Orbital`. |

Em `--silent` **sem** `--self-delete`, o `Uninstall.exe` é mantido no diretório
para tornar a validação determinística. A remoção completa ocorre no modo
interativo e, em CI, com `--self-delete`.

Códigos de saída: `0` sucesso; `1` cancelado pelo usuário; `10`–`21` falhas
específicas (payload ausente = `11`, falha de cópia = `15`,
**atalho obrigatório da Área de Trabalho = `18`**, registro = `19`, remoção
incompleta = `21`). Falha no Menu Iniciar **não** gera código de erro.

---

## 7. Validação executada (5 de novembro de 2026)

| Verificação | Resultado |
|---|---|
| `bash installer/build.sh` | concluído sem avisos do compilador/linkador. |
| Manifesto embutido | 1 manifesto, `asInvoker`, `uiAccess="false"`, sem `requireAdministrator`. |
| Dependências do `.exe` | somente DLLs do sistema; nenhuma `libgcc`/`libstdc++` externa. |
| Instalação silenciosa (sem atalhos/registro) | `RESULT=0`, **374 arquivos copiados** na primeira medição (375 após adicionar este documento ao deploy), 0 divergências de SHA-256 contra `payload/`. |
| Reinstalação sobre instalação anterior | `RESULT=0` — confirmou a correção do arquivo somente leitura e, depois, do diretório somente leitura. |
| Conteúdo instalado | `Orbital.jar` com 39 WAVs / 182 OGGs e `MIT-Original-Sounds.txt` presente. |
| Instalação completa com ganchos de CI | 2 `.lnk` válidos (magic `4C 00 00 00`, apontando para `Orbital.exe`); chave `HKCU` criada com `DisplayName=Orbital` e `UninstallString` correto. |
| Instalação em **thread de trabalho** (`--worker`) | `RESULT=0`, **375 arquivos**, ambos os `.lnk` criados — comprova que a correção de COM cobre o caminho da GUI. |
| Falha forçada no Menu Iniciar | diretório colocado no lugar do `.lnk`: `RESULT=0`, `WARNING=start-menu-shortcut`, atalho da Área de Trabalho criado, chave `HKCU` presente. A instalação **não** aborta. |
| Falha forçada na Área de Trabalho | `RESULT=18` — atalho preferido continua obrigatório. |
| Desinstalação completa com ganchos de CI | `RESULT=0`; atalhos e chave de teste removidos; restou apenas `Uninstall.exe` (modo silencioso). |
| Reprodução do erro de desinstalação | `Uninstall.exe` sem argumentos devolvia `RESULT=11` (`payload/Orbital.exe nao encontrado`) e não removia nada — confirmado sobre a cópia instalada no perfil. |
| Auto-detecção corrigida | mesmo cenário agora `MODE=uninstall`, `RESULT=0`, arquivos de teste removidos. |
| `--install` | `MODE=install`, `RESULT=11` — a detecção pode ser revertida explicitamente. |
| Helper oculto (`--self-delete`) | pasta de teste removida em ~1,6 s, nenhum `.cmd` restante em `%TEMP%`. |
| Ciclo completo com duplo clique simulado | instalar (`--worker`) → `Uninstall.exe` sem `--uninstall` → 376 arquivos, atalhos e chave de teste removidos, pasta apagada. |
| Efeitos colaterais reais | nenhum nos testes: todos usaram `--shortcut-dir`/`--registry-key`; os atalhos reais existentes pertencem à instalação do usuário e não foram tocados. |
| Janelas | nenhuma janela aberta nos testes (modo silencioso). |

### 7.1 Falha relatada e correção

O usuário reportou **falha na criação do ícone no Menu Iniciar** ao executar o
instalador gráfico. Causa raiz: `CoInitializeEx` era chamado apenas na thread
principal de `wWinMain`, enquanto `doInstall` roda numa `std::thread`;
`CoCreateInstance` retornava `CO_E_NOTINITIALIZED` e o código anterior
transformava isso em erro fatal `17`. Os testes silenciosos rodavam na thread
principal e, por isso, não reproduziam o problema.

Correções aplicadas:

1. `ComScope` (RAII) inicializa COM **dentro de `createShortcut`**, na thread
   que realmente cria o `.lnk`.
2. A Área de Trabalho passou a ser criada **primeiro** e é o único atalho
   obrigatório (`18`).
3. O Menu Iniciar virou **melhor esforço**: falha gera aviso no log e não
   aborta a instalação.
4. `removeTree` passou a limpar `FILE_ATTRIBUTE_READONLY` também dos
   **diretórios** — descoberto porque `build/installer/` herda esse atributo e
   `std::filesystem::remove` recusava remover até uma pasta vazia.
5. Nova opção `--worker` permite validar o modelo de threads da GUI em CI, sem
   abrir janela.

**Não validado:** clique real na interface gráfica (a lógica de threads é
coberta por `--worker` e o helper por `--self-delete`), fluxo de UAC em outro
perfil de usuário e execução do jogo após a instalação. Esses pontos exigem
sessão interativa.

### 7.2 Segunda falha relatada: desinstalação

O usuário acionou a desinstalação por **duplo clique em `Uninstall.exe`** e
obteve erro. O estado da máquina permaneceu intacto: 376 arquivos, chave
`HKCU` e ambos os atalhos — ou seja, **`doUninstall` nunca chegou a rodar**.

Causa raiz: `Uninstall.exe` é apenas uma cópia renomeada de
`OrbitalSetup.exe`, mas só removia o programa quando recebia `--uninstall`.
Sem esse argumento, entrava no fluxo de instalação e falhava com código `11`,
pois não existe `payload/` dentro da pasta já instalada. O erro foi
reproduzido executando a própria cópia instalada com `--silent` e um
`--target` de teste: `RESULT=11`, nenhum arquivo alterado.

Correções aplicadas:

1. `wWinMain` compara o nome do executável com `Uninstall.exe` e ativa
   `uninstall` automaticamente. `--install` desativa essa detecção.
2. `scheduleSelfDelete` passa `%TEMP%` como `lpCurrentDirectory` de
   `CreateProcessW` e o `.cmd` executa `cd /d "%~dp0"`. Antes, o helper
   herdava a pasta de instalação como CWD e `rmdir` não conseguia apagá-la.
3. Nova opção `--self-delete` permite exercitar o helper em `--silent`, sem
   abrir janela — foi assim que a remoção completa foi validada.

> A instalação existente no perfil contém o **`Uninstall.exe` antigo**. Para
> usar a correção é necessário reinstalar com o `OrbitalSetup.exe` novo (que
> substitui a pasta inteira e, portanto, também o desinstalador).

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
