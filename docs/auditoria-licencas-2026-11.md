# Auditoria de Licenças dos Assets — Novembro de 2026

Relatório da auditoria solicitada sobre o conteúdo herdado do Mindustry e dos
recursos adicionados ao Orbital. Complementa `docs/LICENCIAMENTO_ASSETS.md`.

---

## 1. Conclusão

**Nem todo o conteúdo herdado estava sob GPLv3.** O código-fonte e a música
estão; parte dos efeitos sonoros, das fontes e dos ícones tinha licenças
próprias e alguns arquivos vinham de fontes incompatíveis com a GPLv3.

Após esta auditoria:

- 4 sons incompatíveis ou sem origem clara foram substituídos por equivalentes
  CC0;
- todos os assets passaram a ter licença registrada em
  `THIRD_PARTY_LICENSES/manifest.csv`;
- os textos de licença aplicáveis foram incluídos no projeto;
- a documentação deixou de afirmar que todos os assets são GPLv3;
- foi criada verificação automática (`tools/audit_assets.py --check`).

---

## 2. Metodologia

1. Inventário de `core/assets` (música, sons, fontes, ícones, sprites, mapas).
2. Leitura de metadados dos áudios com `mutagen`.
3. Leitura de metadados das fontes com `fontTools`.
4. Análise do histórico Git para identificar os commits que introduziram sons e
   fontes de terceiros.
5. Consulta às páginas de origem (Freesound, OpenGameArt, FontStruct, SIL).
6. Cruzamento com `core/assets/contributors` e `core/assets-raw/fontgen/config.json`.

---

## 3. Resultado por categoria

### 3.1 Música — GPLv3

16 faixas em `core/assets/music/`:

```
boss1  boss2  editor  fine  game1  game2  game3  game4
game5  game6  game7  game8  game9  land  launch  menu
```

- Todas compostas por **Anuken**.
- Metadados indicam produção em **LMMS**.
- Nenhuma tag de artista de terceiro.
- Nenhuma indicação de música externa no repositório.

**Situação:** GPLv3, sem pendências.

### 3.2 Sprites, mapas, shaders, cubemaps e traduções — GPLv3

Contribuições de Anuken e da comunidade do Mindustry, mantidas no repositório
original. As contribuições próprias do Orbital também são GPLv3.

**Situação:** GPLv3, sem pendências identificadas.

### 3.3 Efeitos sonoros

205 arquivos em `core/assets/sounds/`.

**Sons mantidos com atribuição:**

| Origem | Licença | Observação |
|---|---|---|
| jalastram / Jesus Lastra | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| newlocknew | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| dsmolenaers | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| Headphaze | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| Michel Baradari | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| Michael Klier | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| Lee Barkovich | Creative Commons / contribuição ao Mindustry | Listado em `contributors` |
| rubberduck | CC0 1.0 | Pacotes “100 CC0 SFX”, “50 CC0 Sci-Fi SFX”, “100 CC0 SFX #2” |
| Iwan “qubodup” Gabovitch | Domínio público / CC0 | “Synthetic explosion 1”, “Dark Magic Loop” |

**Sons substituídos nesta auditoria:**

| Arquivo | Origem antiga | Problema | Substituto |
|---|---|---|---|
| `sounds/environment/rain.ogg` | SoundJay.com | Termos incompatíveis com GPLv3 | “Rain (loopable) 2”, de Ylmir (OpenGameArt, CC0) |
| `sounds/block/coreLand.ogg` | Gregor Quendel / Designed Mecha | Origem comercial sem concessão clara | `explosion_01.ogg`, de rubberduck (OpenGameArt, CC0) |
| `sounds/block/coreLaunch.ogg` | Gregor Quendel / Designed Mecha | Origem comercial sem concessão clara | `rocket_01.ogg`, de rubberduck (OpenGameArt, CC0) |
| `sounds/block/drillImpact.ogg` | Joseph Krebs | Licença não confirmada | `sfx100v2_stones_01.ogg`, de rubberduck (OpenGameArt, CC0) |

Os substitutos tiveram os metadados de autor, licença e origem gravados no
próprio `.ogg`.

**Situação:** sem itens `REVISAR`.

### 3.4 Fontes

| Arquivo | Família | Autor | Licença | Situação |
|---|---|---|---|---|
| `font_jp.woff` | Noto Sans JP Medium | Adobe | SIL OFL 1.1 | OK |
| `monospace.woff` | Fira Code Medium | Fira Code Project | SIL OFL 1.1 | OK |
| `tech.ttf` | Darktech LDR | Michał “Neoqueto” Nowak | CC BY-SA 3.0 | OK (obra separada) |
| `logic.ttf` | ProggyCleanTT | Tristan Grimmer | MIT | OK |
| `font.woff` | fonte principal do Mindustry mesclada com ícones Fontello | Anuken/comunidade + Fontello | GPLv3 + licenças dos ícones | OK (agregada) |
| `icon.ttf` | fonte de ícones Fontello | Fontello e autores dos conjuntos | licenças por conjunto | OK (agregada) |

Textos incluídos:

- `THIRD_PARTY_LICENSES/OFL-1.1.txt`
- `THIRD_PARTY_LICENSES/MIT-ProggyCleanTT.txt`
- `THIRD_PARTY_LICENSES/creative-commons.txt` (links oficiais das licenças CC)

### 3.5 Ícones

`core/assets-raw/fontgen/config.json` contém 138 glifos:

| Origem | Glifos | Licença |
|---|---|---|
| `custom_icons` | 53 | GPLv3 (projeto) |
| `fontawesome` | 46 | OFL 1.1 / CC BY 4.0 |
| `typicons` | 27 | CC BY-SA 3.0 |
| `zocial` | 3 | MIT |
| `iconic` | 2 | OFL 1.1 |
| `entypo` | 2 | CC BY-SA 4.0 |
| `elusive` | 2 | OFL 1.1 |
| `mfglabs` | 1 | OFL 1.1 |
| `brandico` | 1 | licença do projeto de origem |
| `modernpics` | 1 | licença do projeto de origem |

A fonte de ícones é uma **obra agregada**: cada conjunto mantém a sua licença.
Ela não é relicenciada como GPLv3, e a GPLv3 do jogo não remove as obrigações
dos conjuntos originais. A atribuição completa está em
`THIRD_PARTY_LICENSES/assets.txt`, seção 5.

**Recomendação futura:** ao adicionar novos ícones, usar apenas conjuntos
OFL/MIT/CC0/CC BY 4.0 ou desenhar ícones próprios, reduzindo a mistura de
licenças na fonte agregada.

---

## 4. Correções aplicadas

1. `THIRD_PARTY_LICENSES/assets.txt` reescrito com a situação real.
2. Criados `fonts.txt`, `creative-commons.txt`, `OFL-1.1.txt` e
   `MIT-ProggyCleanTT.txt`.
3. Criado `THIRD_PARTY_LICENSES/manifest.csv`.
4. Criado `tools/audit_assets.py` e `tools/asset_license_rules.json`.
5. Tarefa Gradle `checkAssetLicenses`.
6. Substituição dos 4 sons listados na seção 3.3.
7. Atualização de `CREDITS.md`, `NOTICE.txt`, `README.md` e
   `docs/BUILD_EXECUTAVEL.md`.
8. Atualização do deploy em `deploy/windows64/Orbital/`.

---

## 5. Verificação

```powershell
python tools/audit_assets.py --check
```

O comando retorna `0` quando todos os assets têm licença registrada e não há
itens marcados como `REVISAR`.

---

## 6. Observações finais

- A licença GPLv3 cobre o código e os assets originais do projeto.
- Materiais de terceiros mantêm as suas licenças; a distribuição é feita como
  obra agregada.
- A fonte `tech.ttf` (CC BY-SA 3.0) é distribuída como obra separada; para
  eliminar totalmente o share-alike, a alternativa é substituí-la por uma fonte
  OFL/MIT.
- A fonte de ícones Fontello é o ponto de maior complexidade por reunir vários
  conjuntos; a documentação por conjunto é a mitigação adotada.
