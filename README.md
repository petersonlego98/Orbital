# Orbital

**Uma nova experiência de automação e exploração espacial.**

[![Licença: GPLv3](https://img.shields.io/badge/licen%C3%A7a-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)

Orbital é um **fork independente do Mindustry**, focado em construir colônias
industriais em planetas, personalizar naves com módulos e participar de
expedições de combate e mineração em tempo real.

O projeto preserva a base de automação, construção, mapas, mods e multiplayer
herdada do Mindustry, mas desenvolve uma identidade e uma experiência próprias.

> **Estado atual:** protótipo jogável em desenvolvimento. Mecânicas, conteúdo e
> balanceamento podem mudar sem aviso.

---

## Sobre o jogo

O jogador assume o papel de comandante de uma pequena presença espacial. A
experiência alterna entre duas escalas:

- **Estratégica:** mapa estelar, planetas, setores, produção e expansão.
- **Tática:** expedições em estrelas vermelhas, com nave pilotada, combate,
  mineração de asteroides e retorno de recursos.

A produção planetária abastece a frota; as expedições trazem recursos para o
inventário global e para a expansão das colônias.

## Principais funcionalidades

- **Mapa estelar próprio**, com o sistema inicial de Orbital e o planeta
  **Quatia** como primeira colônia jogável.
- **Expedições em tempo real**, com prazo que continua correndo mesmo com o
  jogo fechado.
- **Inventário global**, que conecta os recursos obtidos nas expedições ao
  progresso planetário.
- **Torres dorsais modulares** em unidades aéreas: encaixes com orçamento,
  slots e limite de cobertura. A primeira demonstração usa a torre **Duo** nas
  unidades **Mono** e **Flare**.
- **Modo Teste Novos Recursos**, um mapa isolado para testar unidades, torres e
  balanceamento sem criar um save de campanha.
- **Base herdada do Mindustry** para automação, transporte, defesa, editor de
  mapas, mods e multiplayer.

Atualmente, **Quatia** é o setor pacífico de produção e **Gier** é a frente de
expedição ativa. As campanhas originais de **Serpulo** e **Erekir** estão
desativadas; os corpos continuam no mapa estelar como elementos visuais.

---

## Origem do projeto

Orbital é um fork do [Mindustry](https://github.com/Anuken/Mindustry),
criado originalmente por **Anuken** e desenvolvido por centenas de
colaboradores da comunidade.

- O código original e os recursos originais do Mindustry são distribuídos sob a
  **GNU General Public License v3.0 (GPLv3)**.
- Orbital mantém a mesma licença para o código e para os recursos próprios, e
  preserva os créditos e avisos do projeto original.
- Alguns efeitos sonoros, fontes e ícones de terceiros mantêm as suas próprias
  licenças (Creative Commons, SIL OFL e termos específicos) e não são
  relicenciados pela GPLv3. Veja `THIRD_PARTY_LICENSES/assets.txt`.
- Este fork **não é afiliado, patrocinado ou endossado por Anuken**.

Consulte [CREDITS.md](CREDITS.md) para os créditos completos.

---

## Como executar

### Requisitos

- **JDK 17 ou superior**.
- Gradle Wrapper incluído no repositório.
- Conexão com a internet na primeira compilação para baixar dependências.

### Rodar em modo de desenvolvimento

Windows:

```powershell
.\gradlew.bat :desktop:run
```

Linux/macOS:

```bash
./gradlew :desktop:run
```

### Gerar o JAR executável

Windows:

```powershell
.\gradlew.bat :desktop:dist
```

Linux/macOS:

```bash
./gradlew :desktop:dist
```

O arquivo é gerado em `desktop/build/libs/Orbital.jar`.

### Gerar o executável Windows (.exe)

O processo completo com `jpackage` e a cópia dos arquivos legais está descrito
em [docs/BUILD_EXECUTAVEL.md](docs/BUILD_EXECUTAVEL.md).

### Rodar os testes

```powershell
.\gradlew.bat :tests:test
```

---

## Estrutura do repositório

| Caminho | Conteúdo |
|---|---|
| `core/` | Código principal do jogo, conteúdo e assets. |
| `desktop/` | Launcher e empacotamento para desktop. |
| `server/` | Servidor dedicado. |
| `tests/` | Testes automatizados. |
| `tools/` | Ferramentas de build e geração de assets. |
| `annotations/` | Processadores de anotação usados na compilação. |
| `docs/` | Visão de jogo, relatórios de design e guias de build. |
| `THIRD_PARTY_LICENSES/` | Licenças de bibliotecas e recursos de terceiros. |

Os documentos de design ficam em:

- [Visão de jogo](docs/VISAO_DE_JOGO_ORBITAL.md)
- [Sistema de setores](docs/RELATORIO_SISTEMA_DE_SETORES.md)
- [Torres como módulos](docs/RELATORIO_TORRES_COMO_MODULOS.md)
- [Unidades de Orbital](docs/RELATORIO_UNIDADES_ORBITAL.md)
- [Licenciamento de assets](docs/LICENCIAMENTO_ASSETS.md)
- [Auditoria de licenças (novembro de 2026)](docs/auditoria-licencas-2026-11.md)
- [Substituição de assets incompatíveis por MIT](docs/SUBSTITUICAO_ASSETS_MIT.md)
- [Migração dos efeitos sonoros para MIT](docs/PROPOSTA_MIT_ASSETS.md)
- [Instalador sem administrador](docs/INSTALADOR_SEM_ADMIN.md)

---

## Licença e conformidade GPLv3

Orbital é **software livre** distribuído sob a
**[GNU General Public License v3.0](LICENSE)**.

Isso significa que você pode:

- executar o jogo para qualquer finalidade;
- estudar e modificar o código-fonte;
- redistribuir cópias do jogo;
- redistribuir versões modificadas.

Desde que respeite a GPLv3, incluindo a manutenção da mesma licença, a
preservação dos avisos de copyright e a disponibilização do código-fonte
correspondente.

### Distribuição de binários

Quem redistribuir o Orbital em formato binário deve:

1. Incluir o texto completo da licença em `LICENSE` e
   `THIRD_PARTY_LICENSES/GPL-3.0.txt`.
2. Manter `CREDITS.md`, `NOTICE.txt` e a pasta `THIRD_PARTY_LICENSES/`.
3. Disponibilizar o código-fonte correspondente à versão distribuída.
4. Preservar os créditos de Anuken, da comunidade do Mindustry e dos
   colaboradores do Orbital.
5. Não impor restrições adicionais sobre os direitos concedidos pela GPLv3.

O código-fonte correspondente deste fork está disponível em:

**https://github.com/petersonlego98/Orbital**

### Bibliotecas e assets

O projeto utiliza bibliotecas de terceiros com licenças próprias, incluindo
Arc, Rhino, LWJGL, FreeType, lz4-java, Steamworks4j, JLine e o backend SDL.

Os assets também têm origens distintas:

- **Música:** todas as 16 faixas são de Anuken e estão sob GPLv3.
- **Sprites, mapas, shaders e traduções:** GPLv3.
- **Efeitos sonoros:** 165 arquivos GPL-3.0-or-later de Anuken e colaboradores,
  39 WAV sintetizados para o Orbital sob MIT e um arquivo em domínio público.
  Consulte o manifesto arquivo por arquivo.
- **Fontes:** Noto Sans JP e Fira Code (SIL OFL 1.1), Cozette e ProggyCleanTT
  (MIT) e a fonte de ícones Fontello/Bootstrap Icons.
- **Ícones:** Font Awesome, Bootstrap Icons, Entypo, Zocial, Iconic, Elusive,
  MFG Labs e ícones próprios.

Consulte:

- [THIRD_PARTY_LICENSES/libraries.txt](THIRD_PARTY_LICENSES/libraries.txt)
- [THIRD_PARTY_LICENSES/assets.txt](THIRD_PARTY_LICENSES/assets.txt)
- [THIRD_PARTY_LICENSES/fonts.txt](THIRD_PARTY_LICENSES/fonts.txt)
- [THIRD_PARTY_LICENSES/creative-commons.txt](THIRD_PARTY_LICENSES/creative-commons.txt)
- [THIRD_PARTY_LICENSES/OFL-1.1.txt](THIRD_PARTY_LICENSES/OFL-1.1.txt)
- [THIRD_PARTY_LICENSES/MIT-ProggyCleanTT.txt](THIRD_PARTY_LICENSES/MIT-ProggyCleanTT.txt)
- [THIRD_PARTY_LICENSES/MIT-Cozette.txt](THIRD_PARTY_LICENSES/MIT-Cozette.txt)
- [THIRD_PARTY_LICENSES/MIT-BootstrapIcons.txt](THIRD_PARTY_LICENSES/MIT-BootstrapIcons.txt)
- [THIRD_PARTY_LICENSES/MIT-Original-Sounds.txt](THIRD_PARTY_LICENSES/MIT-Original-Sounds.txt)
- [THIRD_PARTY_LICENSES/manifest.csv](THIRD_PARTY_LICENSES/manifest.csv)

O inventário de licenças pode ser verificado com:

```powershell
python tools/audit_assets.py --check
```

### Sem garantia

O Orbital é distribuído na esperança de que seja útil, mas **sem nenhuma
garantia**, sem sequer a garantia implícita de comercialização ou adequação a
um determinado propósito. Consulte a GPLv3 para os termos completos.

---

## Créditos

- **Mindustry original:** Anuken e colaboradores da comunidade.
- **Orbital:** equipe Orbital e colaboradores do fork.
- **Tradutores e contribuidores:** lista em `core/assets/contributors`.

Créditos detalhados: [CREDITS.md](CREDITS.md).

---

## Links

- **Repositório do Orbital:** https://github.com/petersonlego98/Orbital
- **Repositório original do Mindustry:** https://github.com/Anuken/Mindustry
- **Licença GPLv3:** https://www.gnu.org/licenses/gpl-3.0.html

---

## Contribuindo

Contribuições são bem-vindas. Antes de abrir uma issue ou pull request:

1. Verifique se a proposta está alinhada com a visão de jogo em
   `docs/VISAO_DE_JOGO_ORBITAL.md`.
2. Preserve os créditos e avisos de licença existentes.
3. Mantenha o código e os assets próprios sob GPLv3 e preserve as licenças e
   atribuições dos recursos de terceiros.
4. Descreva claramente o problema ou a funcionalidade proposta.

Para bugs, inclua versão, sistema operacional, passos para reproduzir e, se
possível, o log da sessão.
