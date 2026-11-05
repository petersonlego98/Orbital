# Plano de Licenciamento de Assets

Este documento define o processo oficial para manter a conformidade de licenças
dos recursos (música, sons, fontes, ícones, sprites e mapas) distribuídos com o
**Orbital**.

> Este é um plano técnico. Ele não substitui aconselhamento jurídico.

---

## 1. Objetivo

Garantir que todo asset redistribuído com o jogo tenha:

1. origem identificada;
2. licença registrada;
3. atribuição preservada;
4. compatibilidade com a distribuição GPLv3 do projeto;
5. texto ou link da licença incluído no pacote.

O código e os assets originais do Orbital/Mindustry permanecem sob **GPLv3**.
Materiais de terceiros **não** são relicenciados pela GPLv3: cada um mantém a
sua licença.

---

## 2. Matriz de decisão

| Licença | Compatível com o pacote GPLv3? | Ação |
|---|---|---|
| GPLv3 | Sim | Manter |
| CC0 1.0 | Sim | Manter |
| CC BY 4.0 | Sim (com atribuição) | Manter |
| CC BY-SA 4.0 | Sim (com atribuição e share-alike) | Manter |
| SIL OFL 1.1 | Sim para fontes | Manter com o texto OFL |
| MIT | Sim | Manter |
| CC BY 3.0 / CC BY-SA 3.0 | Não (incompatível com GPLv3) | Substituir ou isolar |
| Domínio público | Sim | Manter |
| Termos próprios (ex.: SoundJay) | Não | Substituir |
| Desconhecida | Risco | Verificar ou substituir |

---

## 3. Fases

### Fase 0 — Congelar o estado

- Criar uma tag antes de qualquer substituição em lote.
- Não mesclar upstream do Mindustry durante a execução.
- Registrar o inventário em `docs/auditoria-licencas-2026-11.md`.

### Fase 1 — Inventário automatizado

- Manter o script `tools/audit_assets.py`.
- Manter as regras em `tools/asset_license_rules.json`.
- Gerar `THIRD_PARTY_LICENSES/manifest.csv` com todos os assets.
- O script deve falhar (`--check`) se existir asset sem licença definida.

### Fase 2 — Classificação

Para cada asset de terceiro, registrar:

- arquivo;
- autor;
- URL de origem;
- licença exata;
- status (`OK`, `SUBSTITUIDO`, `REVISAR`).

### Fase 3 — Substituição

Substituir imediatamente:

- assets com termos incompatíveis (ex.: SoundJay);
- assets com licença NC;
- assets sem origem/licença identificável.

Procedimento:

1. escolher um substituto CC0/CC BY 4.0/GPLv3;
2. manter o mesmo nome e formato `.ogg`;
3. gravar metadados de origem e licença no próprio arquivo;
4. atualizar o manifesto e a documentação;
5. testar o jogo;
6. commitar em lote por categoria.

### Fase 4 — Fontes e ícones

- Fontes OFL: manter e incluir `OFL-1.1.txt`.
- Fonte MIT: manter e incluir o texto MIT.
- Fonte CC BY-SA 3.0 (`tech.ttf`): manter como obra separada, com atribuição e
  link da licença; ela não relicencia o jogo.
- Fonte de ícones Fontello: tratar como **obra agregada**. Cada conjunto de
  ícones mantém a sua licença; a lista completa fica em
  `THIRD_PARTY_LICENSES/assets.txt`.
- Novos ícones devem vir de conjuntos OFL/MIT/CC0/CC BY 4.0 ou ser desenhados
  pelo próprio projeto.

### Fase 5 — Documentação e empacotamento

- Atualizar `THIRD_PARTY_LICENSES/assets.txt`, `fonts.txt`,
  `creative-commons.txt` e `OFL-1.1.txt`.
- Atualizar `CREDITS.md`, `NOTICE.txt` e `README.md`.
- Garantir que o `jpackage` copie toda a pasta `THIRD_PARTY_LICENSES/`.
- Conferir o checklist em `docs/BUILD_EXECUTAVEL.md`.

### Fase 6 — Automação

- Tarefa Gradle `checkAssetLicenses` executando `tools/audit_assets.py --check`.
- Bloquear merge de PR que adicione asset sem entrada de licença.
- Modelo de contribuição: arquivo, autor, URL, licença e comprovação.

### Fase 7 — Validação final

- [ ] Manifesto sem `REVISAR`.
- [ ] Nenhum asset com licença incompatível.
- [ ] Textos de licença presentes.
- [ ] Jogo compila, abre e carrega sem crash.
- [ ] Deploy contém todos os documentos.
- [ ] Documentação afirma corretamente o que é GPLv3 e o que é de terceiros.

---

## 4. Critérios de aceite

1. `python tools/audit_assets.py --check` retorna código 0.
2. `THIRD_PARTY_LICENSES/manifest.csv` cobre 100% dos assets.
3. Nenhum item com status `REVISAR`.
4. O binário oficial em `deploy/windows64/Orbital/Orbital.exe` abre normalmente.
5. `LICENSE`, `NOTICE.txt`, `CREDITS.md` e `THIRD_PARTY_LICENSES/` acompanham o
   deploy.

---

## 5. Registro de execução

| Fase | Situação |
|---|---|
| 0 — Congelar | Concluída (auditoria registrada) |
| 1 — Inventário | Concluída (`tools/audit_assets.py` + `manifest.csv`) |
| 2 — Classificação | Concluída |
| 3 — Substituição | Concluída (4 sons substituídos) |
| 4 — Fontes/ícones | Concluída (atribuições e licenças registradas) |
| 5 — Documentação | Concluída |
| 6 — Automação | Concluída (tarefa Gradle) |
| 7 — Validação | Concluída |

Detalhes em `docs/auditoria-licencas-2026-11.md`.
