# Créditos do Orbital

## 1. Projeto original: Mindustry

- **Criador original:** [Anuken](https://github.com/Anuken) (`anukendev@gmail.com`)
- **Comunidade:** centenas de colaboradores em código, traduções, arte, áudio, lógica, balanceamento e documentação.
- **Repositório oficial:** https://github.com/Anuken/Mindustry
- **Licença do projeto original:** GNU General Public License v3.0 (GPLv3).

O Orbital é um projeto independente derivado do código-fonte do Mindustry. O projeto
original não endossa nem mantém este fork.

## 2. Orbital (fork independente)

- **Desenvolvimento e novas mecânicas:** equipe Orbital e colaboradores do fork.
  - Customização dorsal de torretas em unidades aéreas (módulos removíveis).
  - Mapa estelar de navegação e setores orbitais (Quatia).
  - Ciclo de expedições cronometradas com transferência de carga para inventário global.
  - Modo dedicado para testes de novos recursos e balanceamento.
- **Repositório do fork / código-fonte correspondente:**
  https://github.com/petersonlego98/Orbital
- **Lista de tradutores e colaboradores:** `core/assets/contributors`.

## 3. Licenciamento

- O **código-fonte** do Orbital e os **recursos originais** do projeto
  (sprites, mapas, shaders, traduções, logo e conteúdo próprio do fork) são
  distribuídos sob a **GNU General Public License v3.0 (GPLv3)**.
- A **trilha sonora** (16 faixas em `core/assets/music/`) foi composta
  integralmente por Anuken e também está sob GPLv3.
- Alguns **efeitos sonoros, fontes e ícones** vieram de projetos de terceiros e
  mantêm as suas próprias licenças. A GPLv3 **não** relicencia esses arquivos.
- Consulte `LICENSE` e `THIRD_PARTY_LICENSES/GPL-3.0.txt` para o texto da GPLv3.
- O código-fonte correspondente à versão distribuída está disponível no repositório
  do fork indicado acima, conforme exigido pela GPLv3.

## 4. Recursos de terceiros e arquivos MIT do Orbital

| Recurso | Origem | Licença |
|---|---|---|
| 39 efeitos sonoros (`core/assets/sounds/**/*.wav`) | Orbital contributors; síntese procedural original | MIT (`THIRD_PARTY_LICENSES/MIT-Original-Sounds.txt`) |
| `sounds/explosions/blockExplodeExplosive.ogg` | Iwan "qubodup" Gabovitch | Domínio público; ver `manifest.csv` |
| 165 efeitos sonoros herdados do Mindustry | Anuken e colaboradores; ver inventário por arquivo | GPL-3.0-or-later conforme `manifest.csv` |
| Fonte `font_jp.woff` | Noto Sans JP (Adobe) | SIL OFL 1.1 |
| Fonte `monospace.woff` | Fira Code | SIL OFL 1.1 |
| Fonte `tech.ttf` | Cozette Vector, Samhain e contribuidores | MIT |
| Fonte `logic.ttf` | ProggyCleanTT, Tristan Grimmer | MIT |
| Ícones `bootstrap_icons` (29 glifos) | Bootstrap Icons, The Bootstrap Authors | MIT |
| Fontes de ícones `font.woff` e `icon.ttf` | Fontello (Font Awesome, Bootstrap Icons, Entypo, Zocial, Iconic, Elusive, MFG Labs e ícones próprios) | Cada conjunto mantém a sua licença |

Detalhes completos, arquivos afetados e links:

- `THIRD_PARTY_LICENSES/assets.txt`
- `THIRD_PARTY_LICENSES/fonts.txt`
- `THIRD_PARTY_LICENSES/creative-commons.txt`
- `THIRD_PARTY_LICENSES/OFL-1.1.txt`
- `THIRD_PARTY_LICENSES/MIT-ProggyCleanTT.txt`
- `THIRD_PARTY_LICENSES/MIT-Cozette.txt`
- `THIRD_PARTY_LICENSES/MIT-BootstrapIcons.txt`
- `THIRD_PARTY_LICENSES/MIT-Original-Sounds.txt`
- `THIRD_PARTY_LICENSES/manifest.csv` (inventário arquivo por arquivo)
- `THIRD_PARTY_LICENSES/libraries.txt`

> **Auditoria de novembro de 2026:** as 39 mídias sonoras antes declaradas CC0
> foram substituídas por novos arquivos WAV sintetizados para o Orbital e
> distribuídos sob MIT. Os 165 sons GPL-3.0-or-later e o som em domínio público
> foram preservados. A fonte
> `tech.ttf` e os glifos de ícones incompatíveis também foram substituídos por
> equivalentes MIT (Cozette e Bootstrap Icons). O inventário completo está em
> `manifest.csv` e pode ser validado sem regravá-lo com
> `python tools/audit_assets.py --check`.
