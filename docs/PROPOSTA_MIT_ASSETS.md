# Proposta de migração dos assets para licenças MIT

**Status:** migração dos 39 efeitos CC0 implementada em 5 de novembro de 2026, após aprovação das amostras pelo usuário. O restante do documento registra a pesquisa e a decisão que antecederam a execução.

## Registro de implementação

- Substituídos 39 arquivos CC0 `.ogg` por efeitos sonoros originais `.wav`, licenciados sob MIT e registrados no manifesto. Os nomes-base e usos dos sons foram preservados; nenhum dos 165 efeitos GPL-3.0-or-later nem o efeito em domínio público foi alterado.
- Gerador reproduzível: `tools/generate_original_mit_sounds.py`. A saída de staging e os OGG antigos movidos para recuperação ficam em `build/mit-sound-stage/`, ignorado pelo Git.
- Para carregar os WAVs, `FileTree` procura `.wav` após `.ogg`/`.mp3`; `DataAssetType` aceita WAV para som e música. O código Arc consultado usa `wavLoadBytes` para decodificar `Sound.load`.
- `THIRD_PARTY_LICENSES/MIT-Original-Sounds.txt`, `tools/asset_license_rules.json`, `manifest.csv`, créditos, avisos e documentação foram atualizados.
- A auditoria de licença retornou código 0: 39 sons MIT, nenhum som CC0, 165 GPL-3.0-or-later e um em domínio público. A regra por caminho preserva a atribuição do efeito em domínio público sem depender de tags opcionais. A verificação da CLI foi corrigida para não regravar o manifesto quando chamada com `--check`.
- Ainda pendente: validação audiovisual dentro do jogo e revisão do pacote final de distribuição. Nenhum commit foi criado ainda.

## Objetivo e regra definida

Preservar os arquivos cuja licença declarada é GPL-3.0-or-later. Substituir sons atualmente declarados CC0 por sons originais cuja titularidade e licença MIT possam ser documentadas. Para fontes e ícones incompatíveis ou sem licença comprovável, usar equivalentes MIT com fonte oficial e aviso de copyright. Outras licenças que o inventário trata como compatíveis com GPLv3 (por exemplo, OFL 1.1 e CC BY 4.0) não são objeto de remoção nesta proposta, salvo decisão posterior.

O usuário escolheu: (1) MIT estrito para substitutos de áudio; (2) substituir também os CC0 existentes; (3) se não houver pacote MIT com licença de cada arquivo comprovável, propor criação/gravação original, mantendo a função sonora de cada evento.

## Estado local observado

A leitura de `THIRD_PARTY_LICENSES/manifest.csv` encontrou 247 linhas de inventário, incluindo 39 efeitos sonoros declarados `CC0-1.0`, 2 fontes MIT e ícones distribuídos em conjuntos com MIT, OFL 1.1, CC BY 4.0, CC BY-SA 4.0 e GPL-3.0-or-later. As contagens refletem o manifesto local no momento da leitura, não uma verificação jurídica independente da origem de cada arquivo.

A documentação local já registra como realizadas quatro substituições de som por CC0 (`rain.ogg`, `coreLand.ogg`, `coreLaunch.ogg`, `drillImpact.ogg`), substituição de `tech.ttf` por Cozette e substituição de 29 glifos por Bootstrap Icons. O pedido atual altera o critério para áudio: os 39 sons CC0 listados no manifesto passam a ser candidatos à migração MIT, inclusive os quatro já substituídos.

O working tree tinha modificações e arquivos não rastreados antes deste documento, inclusive arquivos de licença, fontes, configuração e documentação relacionados à auditoria. Não foram sobrescritos nem limpos; a proposta não pressupõe que o conteúdo alterado já tenha sido validado ou distribuído.

## Inventário inicial dos sons CC0 candidatos

Fonte de inventário: `THIRD_PARTY_LICENSES/manifest.csv` (39 registros). Os nomes abaixo são caminhos relativos a `core/assets/`.

- `sounds/block/`: `blockBreak1.ogg`, `blockBreak2.ogg`, `blockBreak3.ogg`, `blockRepair.ogg`, `blockRotate.ogg`, `click.ogg`, `coreLand.ogg`, `coreLaunch.ogg`, `door.ogg`, `drillImpact.ogg`, `shockwaveTower.ogg`, `unitCreate.ogg`.
- `sounds/environment/`: `rain.ogg`.
- `sounds/explosions/`: `unitExplode1.ogg`, `unitExplode2.ogg`, `unitExplode3.ogg`, `wreckFall.ogg`, `wreckFallBig.ogg`.
- `sounds/loops/`: `loopCombustion.ogg`, `loopHover.ogg`, `loopHover2.ogg`, `loopMachine.ogg`, `loopMachine2.ogg`, `loopMachineSpin.ogg`, `loopMalign.ogg`, `loopThruster.ogg`, `loopUnitBuilding.ogg`.
- `sounds/shoot/`: `shootArtillery.ogg`, `shootArtillerySap.ogg`, `shootArtillerySapBig.ogg`, `shootArtillerySmall.ogg`, `shootCyclone.ogg`, `shootDisperse.ogg`, `shootHorizon.ogg`, `shootReign.ogg`, `shootRipple.ogg`, `shootScatter.ogg`, `shootScepter.ogg`.
- `sounds/ui/`: `waveSpawn.ogg`.

**Nota:** os outros 166 sons, conforme o manifesto, aparecem como GPL-3.0-or-later e não devem ser tocados por esta migração. Antes de qualquer substituição, confirmar que arquivo, licença, atribuição e comportamento em jogo correspondem ao manifesto.

## Pesquisa preliminar e recomendações

### Áudio

A busca inicial por pacotes de efeitos sonoros MIT não encontrou resultado com evidência suficiente de que a licença MIT se aplique aos arquivos de áudio individualmente. Foi encontrado `react-sounds`, mas a página consultada informa que o pacote MIT contém somente wrappers JavaScript e que os áudios ficam hospedados em CDN; isso não prova licença MIT para os próprios sons. Portanto, ele não é aprovado como fonte para os assets.

**Proposta:** não importar sons externos por inferência a partir da licença do software que os distribui. Produzir gravações ou sínteses originais para cada uma das 39 funções, manter arquivos-fonte editáveis do processo e registrar autoria, titular dos direitos, licença MIT e metadados por arquivo. Os eventos devem manter a intenção funcional (por exemplo, `shootArtillery` continua sendo disparo de artilharia), mas a semelhança estética e os detalhes de cada áudio ainda precisam ser avaliados pelo usuário. Nenhum áudio será gerado ou trocado antes de uma aprovação de amostras e do método de produção.

### Fonte de terminal

A documentação atual declara `core/assets/fonts/tech.ttf` como Cozette Vector, MIT. O repositório oficial de Cozette informa licença MIT e encaminha para suas releases a obtenção do arquivo `CozetteVector.ttf`:

- Projeto/licença: https://github.com/the-moonwitch/Cozette
- Licença: https://github.com/the-moonwitch/Cozette/blob/main/LICENSE
- Releases: https://github.com/the-moonwitch/Cozette/releases

A versão `v1.30.0` é citada nos registros locais, mas a fonte consultada também mostra atividade/revisões posteriores; a proposta é confirmar hash, versão efetivamente incorporada e cobertura de caracteres antes de considerar essa etapa validada. Não trocar novamente a fonte sem comparar visualmente no jogo.

### Ícones

Bootstrap Icons é publicado sob MIT pelo projeto oficial e disponibiliza ícones SVG no diretório `icons/`:

- Projeto: https://github.com/twbs/icons
- Licença: https://github.com/twbs/icons/blob/main/LICENSE
- SVGs: https://github.com/twbs/icons/tree/main/icons

O mapeamento de 29 glifos existente em `docs/SUBSTITUICAO_ASSETS_MIT.md` pode servir como proposta-base; não é confirmação de validação visual. Preservar nomes CSS e pontos Unicode; comparar tamanho, preenchimento, alinhamento e legibilidade de cada ícone em contexto.

### Outros conjuntos de ícones

O manifesto declara um glifo Font Awesome sob CC BY 4.0 e um Entypo sob CC BY-SA 4.0, licenças que a documentação local classifica como compatíveis com GPLv3. Como o pedido foi para substituir itens incompatíveis, a recomendação inicial é mantê-los com suas atribuições, salvo se o usuário decidir que deseja MIT uniforme para todos os recursos não-GPL. Essa decisão não foi presumida.

## Etapas realizadas e pendentes

1. Confirmados os 39 caminhos no manifesto inicial e identificados os seus eventos no jogo.
2. Preparadas amostras representativas de tiro, impacto, loop mecânico, chuva e interface; o usuário aprovou o estilo.
3. Gerados os 39 WAVs de forma determinística em Python puro, sem usar amostras ou bibliotecas de áudio de terceiros.
4. Movidos os OGGs CC0 anteriores para a área local ignorada de staging e instalados os WAVs originais nos mesmos diretórios, com extensão compatível com o decoder WAV de Arc.
5. Atualizados licença MIT, regras, manifesto, créditos, avisos, suporte de carregamento e documentação.
6. Verificada a consistência do inventário em memória: 39 sons MIT; 0 sons CC0; 166 sons GPL preservados; validação retornou 0.
7. Pendente: compilar/testar o jogo, verificar audivelmente cada categoria em contexto, atualizar/reconstruir o pacote de distribuição e revisar o diff antes do commit.

## Critérios de aceite e estado

- [x] Os 39 sons substitutos são gerados localmente sem amostras de terceiros e têm licença MIT registrada.
- [x] Nenhum dos 166 sons GPL-3.0-or-later foi alterado.
- [x] O usuário aprovou as amostras representativas antes da substituição.
- [x] O manifesto registra 39 sons MIT e nenhum CC0; a auditoria retornou código 0.
- [x] Fontes/ícones MIT já existentes têm seus textos de licença referenciados na documentação.
- [ ] Compilação/teste do jogo e audição dentro do jogo.
- [ ] Atualização e revisão do pacote final de distribuição.
- [ ] Revisão do diff e commit.

## Pendências

Não se decidiu uniformizar para MIT os glifos CC BY 4.0/CC BY-SA 4.0 atualmente declarados compatíveis com GPLv3. A titularidade e a validade legal do aviso MIT das gravações/sínteses precisam ser confirmadas pelo responsável pelo projeto antes de distribuir uma versão final; este documento não é parecer jurídico.

## Limites desta pesquisa

Este documento registra pesquisa técnica e evidência de origem, não é parecer jurídico. A inclusão de um arquivo em repositório MIT não basta para provar que seus assets audiovisuais estão cobertos pela licença MIT; é necessário verificar a licença e a titularidade de cada asset. As páginas oficiais citadas foram consultadas em 5 de novembro de 2026.
