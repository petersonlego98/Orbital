# Substituição de Assets Incompatíveis por Equivalentes MIT

Plano e registro da substituição dos recursos cuja licença não é compatível com
a distribuição GPLv3 do Orbital. A regra adotada é: **o que já é GPLv3
permanece; o que é incompatível ou de licença não confirmada é substituído por
um equivalente MIT.**

> Documento técnico. Não substitui revisão jurídica.

---

## 1. Escopo

| Achado | Licença atual | Situação | Substituto |
|---|---|---|---|
| `core/assets/fonts/tech.ttf` | Darktech LDR — CC BY-SA 3.0 | Incompatível com GPLv3 | Cozette Vector — MIT |
| 27 ícones Typicons | CC BY-SA 3.0 | Incompatível com GPLv3 | Bootstrap Icons — MIT |
| `github_` (Brandico) | licença do projeto de origem, não confirmada | Risco | Bootstrap Icons `github` — MIT |
| `warning` (ModernPics) | licença do projeto de origem, não confirmada | Risco | Bootstrap Icons `exclamation-triangle` — MIT |

Permanecem sem alteração:

- código, música, sprites, mapas, shaders e traduções — GPLv3;
- sons GPL-3.0-or-later e o arquivo em domínio público — preservados. Os 39
  sons antes declarados CC0 foram migrados para WAV originais sob MIT em
  `docs/PROPOSTA_MIT_ASSETS.md`;
- Font Awesome — CC BY 4.0 (compatível com GPLv3);
- Entypo — CC BY-SA 4.0 (compatível com GPLv3);
- Iconic, Elusive e MFG Labs — SIL OFL 1.1;
- Zocial — MIT;
- ícones `custom_icons` do projeto — GPLv3.

---

## 2. Pesquisa das fontes substitutas

### 2.1 Cozette — fonte para `tech.ttf`

- Projeto: https://github.com/the-moonwitch/Cozette
- Licença: MIT
- Copyright: `(c) 2020-2025 Ines <ines@moonwit.ch>`
- Arquivo usado: `CozetteVector.ttf` (release `v.1.30.0`)
- Motivo: fonte vetorial monoespaçada, estética de terminal/tecnologia,
  compatível com o uso em rótulos da árvore de pesquisa. Cobertura de
  maiúsculas, minúsculas, números e pontuação usada pelo jogo.

### 2.2 Bootstrap Icons — ícones substitutos

- Projeto: https://github.com/twbs/icons
- Licença: MIT
- Copyright: `(c) 2019-2024 The Bootstrap Authors`
- Pacote usado: `bootstrap-icons@1.13.1` (npm), arquivos SVG em `icons/`
- Motivo: conjunto amplo, ícones lineares de 16×16, com equivalentes diretos
  para todos os 29 glifos substituídos.

---

## 3. Mapa de substituição dos ícones

Os códigos Unicode e os nomes CSS usados pelo jogo são preservados. Apenas o
desenho do glifo muda.

| Código | Nome no jogo | Origem antiga | Bootstrap Icons |
|---|---|---|---|
| U+E807 | `home` | Typicons | `house` |
| U+E808 | `image` | Typicons | `image` |
| U+E809 | `star` | Typicons | `star` |
| U+E80B | `resize` | Typicons | `arrows-angle-expand` |
| U+E80F | `wrench` | Typicons | `wrench-adjustable` |
| U+E813 | `add` | Typicons | `plus-lg` |
| U+E816 | `edit` | Typicons | `pencil` |
| U+E819 | `chart-bar` | Typicons | `bar-chart` |
| U+E81A | `plane-outline` | Typicons | `airplane` |
| U+E827 | `map` | Typicons | `map` |
| U+E823 | `rotate` | Typicons | `arrow-repeat` |
| U+E829 | `play` | Typicons | `play-fill` |
| U+E806 | `pause` | Typicons | `pause-fill` |
| U+E811 | `list` | Typicons | `list` |
| U+E818 | `move` | Typicons | `arrows-move` |
| U+E835 | `undo` | Typicons | `arrow-counterclockwise` |
| U+E836 | `redo` | Typicons | `arrow-clockwise` |
| U+E839 | `right-open-out` | Typicons | `box-arrow-right` |
| U+E83A | `right-open` | Typicons | `chevron-right` |
| U+E83B | `waves` | Typicons | `water` |
| U+E810 | `power_` | Typicons | `power` |
| U+E832 | `rain` | Typicons | `cloud-rain` |
| U+E838 | `cloud-sun` | Typicons | `cloud-sun` |
| U+E83C | `drizzle` | Typicons | `cloud-drizzle` |
| U+E840 | `cloud-flash` | Typicons | `cloud-lightning` |
| U+E841 | `cloud-wind` | Typicons | `wind` |
| U+E843 | `chart-alt` | Typicons | `graph-up` |
| U+F308 | `github_` | Brandico | `github` |
| U+26A0 | `warning` | ModernPics | `exclamation-triangle` |

---

## 4. Procedimento de execução

1. Baixar os arquivos MIT de origem:
   - `CozetteVector.ttf` (release oficial);
   - pacote npm `bootstrap-icons` com os SVGs.
2. Substituir `core/assets/fonts/tech.ttf` por `CozetteVector.ttf`.
3. Substituir os 27 glifos Typicons, o `github_` e o `warning` em:
   - `core/assets/fonts/icon.ttf`;
   - `core/assets/fonts/font.woff` (fonte principal, que contém os mesmos
     glifos porque é usada para os ícones pequenos).
4. Preservar códigos Unicode, nomes CSS e avanços horizontais dos glifos.
5. Atualizar `core/assets-raw/fontgen/config.json` para marcar os glifos
   substituídos como `bootstrap_icons`.
6. Adicionar os textos de licença:
   - `THIRD_PARTY_LICENSES/MIT-Cozette.txt`;
   - `THIRD_PARTY_LICENSES/MIT-BootstrapIcons.txt`.
7. Atualizar regras, manifesto e documentação:
   - `tools/asset_license_rules.json`;
   - `THIRD_PARTY_LICENSES/manifest.csv`;
   - `assets.txt`, `fonts.txt`, `CREDITS.md`, `NOTICE.txt`, `README.md`;
   - relatórios de auditoria.
8. Validar com `python tools/audit_assets.py --check` e
   `.\gradlew.bat checkAssetLicenses`.
9. Recompilar, atualizar `deploy/windows64/Orbital/` e testar o `.exe`.

---

## 5. Critérios de aceite

- [x] Nenhuma referência a CC BY-SA 3.0 em fontes ou ícones do jogo (manifesto).
- [x] `tech.ttf` substituída por Cozette Vector, com licença MIT registrada.
- [x] 29 glifos substituídos em `icon.ttf` e `font.woff` por Bootstrap Icons (MIT).
- [x] Nomes e códigos de ícones inalterados.
- [x] Manifesto sem `REVISAR`.
- [x] `audit_assets.py --check` retorna 0 sem regravar o manifesto.
- [x] Compilação concluída (`./gradlew build -x test`, BUILD SUCCESSFUL).
- [x] Deploy contém os 11 textos de licença.
- [ ] Abrir o jogo em sessão interativa e conferir ícones e fonte `tech`.

---

## 6. Registro de execução

| Etapa | Situação |
|---|---|
| Pesquisa e escolha dos substitutos MIT | Concluída |
| Documentação do plano | Concluída |
| Substituição de `tech.ttf` | Aplicada; validação visual pendente |
| Substituição dos 29 glifos | Aplicada; validação visual pendente |
| Atualização de configuração e licenças | Aplicada |
| Manifesto e validação automática | Atualizado; auditoria local passou |
| Build | Concluído (`./gradlew build -x test`, BUILD SUCCESSFUL); testes não executados |
| Atualização do deploy | Aplicada: 6 documentos e 11 textos de licença sincronizados localmente |
| Commit | Concluído (`6459a03b3d` e seguintes) |
| Push / release | Concluído: `origin/master` atualizado e release público v1.0.0 publicado |
| Validação interativa | Pendente: abrir o jogo e conferir ícones/fonte/áudio |
