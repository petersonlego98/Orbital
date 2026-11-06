# Orbital v1.1.0 — Windows (instalador sem administrador)

Build `e0dbcfcd0f`. O instalador continua **sem exigir administrador**: instala em
`%LOCALAPPDATA%\Programs\Orbital`, cria atalhos na Área de Trabalho e no Menu Iniciar e
registra a desinstalação em `HKCU`. O JRE 21 vai embutido — não é preciso ter Java instalado.

**Download:** `Orbital-1.1.0-win64-usuario.zip` (instalador + `payload/`, ~143 MB)
`SHA-256: 8dd78d8f17f54f65e0e9d184a87782e3b1a9b4a4302d89c192513eb0bbc694c2`

1. Baixe o zip e confira a integridade com o `.sha256`.
2. Extraia mantendo `OrbitalSetup.exe` e a pasta `payload/` **juntos**.
3. Execute `OrbitalSetup.exe`.

---

### Novidade: módulos de suporte dorsais

Três categorias de projetores podem ser instalados no casco das unidades aéreas, um por
categoria, em âncoras físicas desenhadas sobre a silhueta real de cada casco:

| Categoria | Blocos | Efeito na unidade |
|---|---|---|
| **Reparo** | `mender`, `mend-projector` | campo que cura unidades aliadas próximas, com o mesmo `range`, `reload` e `healPercent` do bloco |
| **Overdrive** | `overdrive-projector`, `overdrive-dome` | status de velocidade com o `speedBoost` do bloco (projetor e domo mantêm suas forças diferentes) |
| **Escudo** | `force-projector` | escudo poligonal com `shieldHealth`, raio e número de lados do bloco |

- **Uma categoria por vez:** um casco que já tem o efeito nativamente (por exemplo `poly`
  com reparo nativo) não aceita o módulo equivalente, então efeitos nunca se acumulam.
- **Posicionamento validado:** âncoras e slots nunca saem da silhueta do casco nem se
  sobrepõem depois de desenhados; o orçamento dorsal e o tamanho do casco continuam
  mandando.
- **Toggle no HUD:** a barra inferior liga/desliga cada módulo sem desinstalar — desligar
  devolve o pool de escudo compartilhado, religar reinicializa.
- **Escala visual:** os sprites são reduzidos conforme o casco (um casco pequeno não fica
  com um prédio colado), mas áreas, custo e compatibilidade usam o tamanho real do bloco.

### Novidade: armas de unidades como módulos de combate

Armas nativas de unidades — **terrestres e navais inclusive** — agora podem ser montadas
como módulos de combate em unidades aéreas, no mesmo sistema de slots dorsais:

- **Uma arma por módulo:** casco que carrega a arma nos dois braços aparece uma única vez
  no catálogo.
- **Desenho unido:** o módulo desenha os dois braços como um desenho só — duas cópias da
  sprite, a segunda espelhada, ocupando a mesma área de uma sprite, para o módulo não
  estourar o casco.
- **Custo e espaço:** o tamanho vem do sprite da arma (1–5 blocos) e consome orçamento
  dorsal como um torro qualquer; slots cheios ou orçamento estourado rejeitam a instalação.
- **Catálogo limpo:** só armas de combate entram — ferramentas de construção, mineração,
  reparo e defesa pontual ficam de fora.

---

### Correções

- **Módulos de arma que não atiravam:** a metade espelhada de uma arma `mirror` carregava
  os flags de alternância e nunca disparava; `otherSide` ainda invertia o lado de outra
  arma do casco. Ambos zerados na cópia do módulo.
- **Cadência pela metade:** `UnitType.init()` dobra o `reload` do par espelhado; o módulo,
  sendo montagem única, agora desfaz essa compensação.
- **Catálogo duplicado:** a mesma arma era oferecida duas vezes quando estava nos dois
  braços da unidade.
- **Link do Discord:** o jogo apontava para `discord.gg/mindustry` no menu de links e para
  um convite antigo no diálogo do Discord. Agora ambos usam `discord.gg/afBWUZryF`.
- **Ícone do app no Windows:** era um redimensionamento da key-art e virava ruído em
  16–48 px (barra de tarefas, atalho, Menu Iniciar). Agora é desenhado em cada resolução
  (16–256 px) e o instalador também exibe o ícone.
- **Tela de carregamento:** substituída pelo tema espacial (espaço, nebulosas, estrelas,
  emblema orbital, barra única) e a porcentagem não sobrepõe mais a barra.
- **Travamento ao abrir:** o renderer de boot usava `Fill.circle`, que precisa do atlas —
  ausente nessa fase — e matava o lançamento no primeiro frame. Substituído por primitivas
  sem atlas.

---

### Verificação desta build

- 46/46 nos testes de loadout (`SupportLoadoutTest`, `TurretLoadoutSmokeTest`,
  `WeaponModuleTest`).
- Binário executado: boot em ~12 s até o menu, sem exceções no log.

> ⚠️ Build em desenvolvimento: ainda pode conter bugs. Reporte em
> <https://github.com/petersonlego98/Orbital/issues>
