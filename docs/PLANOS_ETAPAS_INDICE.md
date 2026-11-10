# Índice dos planos de implementação em etapas

**Data:** 2026-11-10
**Fontes adaptadas:** os três documentos de pesquisa de 2026-11-09 (baseline dos sistemas, pesquisa Dark Nebula/Hades' Star, pesquisa do motor Mindustry).
**Método:** cada plano parte de um documento de pesquisa e foi ancorado no estado real do código (`core/src/orbital/`), verificado nesta sessão. O que é **proposta de design** está rotulado como tal; o que é **fato do código** cita o arquivo.

---

## Os três planos

| Plano | Arquivo | Fonte | Escopo |
|---|---|---|---|
| **A — Economia do sistema de módulos** | [PLANO_ETAPAS_MODULOS.md](PLANO_ETAPAS_MODULOS.md) | `estado-fork-baseline-sistemas-existentes.md` | Fechar as pendências do sistema de módulos: auditoria documental, persistência de loadout, economia de tiers/custos, compatibilidade, balanceamento |
| **B — Experiência de jogo e retenção** | [PLANO_ETAPAS_EXPERIENCIA_JOGO.md](PLANO_ETAPAS_EXPERIENCIA_JOGO.md) | `pesquisa-dark-nebula-hades-star.md` | Traduzir as lições verificadas de Hades' Star/DN em etapas jogáveis: expedição com relógio real, economia de três moedas, progresso offline, ganchos diários, temporadas |
| **C — Infraestrutura de servidor persistente** | [PLANO_ETAPAS_INFRA_SERVIDOR.md](PLANO_ETAPAS_INFRA_SERVIDOR.md) | `pesquisa-motor-mindustry.md` | Construir o que o motor não tem (lacunas B1–B17): contas, banco de dados, estado de mundo compartilhado, economia servidor-autoritativa, alianças, chat, eventos, anti-cheat, sharding |

---

## Ordem recomendada

1. **A primeiro (A1–A2)** — é documentação e auditoria do que já existe; não quebra nada e alimenta todos os outros planos.
2. **C1–C2 em paralelo com A3+** — contas e banco são o alicerce de tudo que é persistente; começar cedo evita refazer.
3. **B após C1–C2** — os modos e ganchos de retenção dependem de persistência de conta para valer a pena; a economia de três moedas (B2) depende da carteira de servidor (C4).
4. **A5/A6, B6, C5–C8** — camadas de social, eventos e escalabilidade por último, quando houver base.

## Dependências entre planos

```
A1 (auditoria) ──► A2 (persistência local) ──► A3/A4 (economia/tiers) ──► A5 (balanceamento)
                                                        │
C1 (contas) ──► C2 (banco) ──► C3 (mundo compartilhado) ──► C4 (economia servidor) ──► B2
                              │
                              ├──► C5 (alianças/chat) ──► B5/B6 (social/temporadas)
                              ├──► C6 (progresso offline) ──► B3
                              └──► C7 (eventos/anti-cheat) ──► B7
A2 (formato de loadout) ──► C2 (schema do BD)
```

## Regras válidas para os três planos

- Nenhuma etapa declara sucesso sem evidência executada (build, teste ou captura). Segue a regra do projeto: apagar binário velho antes de recompilar.
- Nomes, lore e arte de Dark Nebula / Hades' Star **não** entram no código; só princípios e mecanismos (regra de identidade do baseline, seção 6).
- Alterações fora do escopo da etapa atual precisam ser sinalizadas antes de feitas.
