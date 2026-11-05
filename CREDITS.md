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

## 4. Recursos de terceiros

| Recurso | Origem | Licença |
|---|---|---|
| Efeitos sonoros de clima, lasers e SFX (2020) | jalastram, newlocknew, dsmolenaers, Headphaze (Freesound.org) | Creative Commons (CC0 / CC BY / CC BY-SA, conforme o arquivo) |
| Sons de torretas de Erekir (2022) | Michel Baradari, Michael Klier (OpenGameArt.org) | Creative Commons (conforme a página original) |
| Conjunto "More sounds" (2025) | Lee Barkovich (OpenGameArt.org) | Creative Commons (conforme a página original) |
| Diversos blocos, explosões e loops | rubberduck | CC0 ("100 CC0 SFX", "100 CC0 SFX #2" e "50 CC0 Sci-Fi SFX") |
| "Synthetic explosion 1" e "Dark Magic Loop" | Iwan "qubodup" Gabovitch | Domínio público / CC0 |
| `coreLand.ogg`, `coreLaunch.ogg` | rubberduck (OpenGameArt) | CC0 |
| `drillImpact.ogg` | rubberduck (OpenGameArt) | CC0 |
| `shootLaser.ogg`, `shootSegment.ogg` ("Shooting_Sounds_008") | Jesus Lastra | Creative Commons (conforme a origem) |
| `environment/rain.ogg` ("Rain (loopable) 2") | Ylmir (OpenGameArt) | CC0 |
| Fonte `font_jp.woff` | Noto Sans JP (Adobe) | SIL OFL 1.1 |
| Fonte `monospace.woff` | Fira Code | SIL OFL 1.1 |
| Fonte `tech.ttf` | Darktech LDR, Michał "Neoqueto" Nowak | CC BY-SA 3.0 |
| Fonte `logic.ttf` | ProggyCleanTT, Tristan Grimmer | MIT |
| Fontes de ícones `font.woff` e `icon.ttf` | Fontello (Font Awesome, Typicons, Entypo, Zocial, Iconic, Elusive, MFG Labs, Brandico, ModernPics e ícones próprios) | Cada conjunto mantém a sua licença |

Detalhes completos, arquivos afetados e links:

- `THIRD_PARTY_LICENSES/assets.txt`
- `THIRD_PARTY_LICENSES/fonts.txt`
- `THIRD_PARTY_LICENSES/creative-commons.txt`
- `THIRD_PARTY_LICENSES/OFL-1.1.txt`
- `THIRD_PARTY_LICENSES/MIT-ProggyCleanTT.txt`
- `THIRD_PARTY_LICENSES/manifest.csv` (inventário arquivo por arquivo)
- `THIRD_PARTY_LICENSES/libraries.txt`

> **Auditoria de novembro de 2026:** os quatro sons com origem incompatível ou
> não confirmada (`rain.ogg`, `coreLand.ogg`, `coreLaunch.ogg` e
> `drillImpact.ogg`) foram substituídos por equivalentes CC0. O inventário
> completo está em `manifest.csv` e pode ser validado com
> `python tools/audit_assets.py --check`.
