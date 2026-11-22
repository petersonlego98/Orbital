# Orbital online — Cloudflare free tier

Event-sourced relay: the client sends only commands (compact varint frames), the Worker assigns an
authoritative `seq`/`tick`, persists one row per event in D1 and broadcasts it. Clients catch up
after a disconnect by sending their last applied `seq`. No full-state snapshot per tick — that is
what keeps the session in the kbps range.

## Deploy (one time)

```bash
cd deploy/cloudflare
wrangler login
wrangler deploy
```

That's it — no D1 database, no Durable Object, no paid resource. The SSE endpoint is
`https://orbital-online.<your-subdomain>.workers.dev/events?room=default`.

The Java client is `SseClient` (same connect/send/receive API as the old `WsClient`), used by
`OnlineClient`. Commands are POSTed to `/event?room=default`.

## Client

Java side (in `core`):

- `net/events/EventRecord`, `EventCodec`, `EventStore`, `EventRecorder`, `EventTypeIds` — the
  append-only log and compact codec.
- `net/online/WsClient`, `OnlineClient` — WebSocket transport and event envelope.

Enable the server-side log with `-Dorbital.eventlog=1 -Dorbital.eventlog.dir=<folder>`.

## Free tier limits (100% free, no payment method needed)

- Workers: **100k requests/day**. One long-lived SSE connection is one request, so normal play
  uses a handful of requests per day — nowhere near the cap.
- D1: **not used**. The old design wrote one row per event and would hit the 100k writes/day cap
  in minutes. Room state now lives in Worker memory (last 1000 events per room).
- Durable Objects: **not used** (paid on the current pricing).
- Trade-off: if the Worker is evicted, in-memory events are lost; clients reconnect from their
  last applied seq and the game requests a keyframe. There is no persistent history.

`wrangler.toml` contains only `name`, `main` and `compatibility_date` — nothing billable.

## Authoritative mode (implemented)

Enable on the server with `-Dorbital.authoritative=1` (optionally `-Dorbital.tickrate=60`). When
on:

- `Logic.update()` advances `state.tick` with `FixedStep` — whole, equal ticks instead of the
  variable `delta * 60f`. Given the same elapsed time, every run produces the same tick sequence.
- `NetServer.sync()` stops sending full-state snapshots and flushes the event log instead, via
  `EventReplicator` (one batched, reliable binary frame per tick).
- The server must also start the log with `-Dorbital.eventlog=1`.

Both flags are opt-in: without them the legacy variable-step loop and ArcNet snapshots are
byte-for-byte unchanged. The clients still need the matching apply side (keyframe + event replay)
before an end-to-end match can run — that is the next step.

## Client apply side (implemented)

- `EventApplier` receives batches, drops duplicates from reconnects, applies in commit order and
  flags gaps that need a resync.
- `WorldEventApplier` maps event ids to real world changes (tile/build/unit/config/wave/game over).
- `OnlineSession` installs the binary handler on the client, applies events and acknowledges the
  cursor; `NetClient.update()` uses it instead of the legacy UDP snapshot path when authoritative
  mode is on.
- 18 online tests pass (codec, store, fixed step, mode, applier).

## Keyframe / recovery (implemented)

- `KeyframeStore` writes the full world (via `NetworkIO.writeWorld`) as numbered `.kf` files,
  named by the event seq they correspond to, and prunes old ones.
- `KeyframeManager` writes one at session start and every 500 events, using any connected player
  as the world source.
- A client that asks for recovery (`orbital.keyframe.request`) receives the newest keyframe at or
  before its cursor plus the world payload; `OnlineSession` loads it and resumes replay from there.
- 20 online tests pass (codec, store, fixed step, mode, applier, keyframes).

## Deterministic RNG (implemented)

- `TickRng` re-seeds both global RNGs (`GlobalVars.rand` and `Mathf.rand`) from
  `hash(seed0, seed1, tick)` at the start of every tick — covers ~330 call sites without rewriting
  them. Opt-in via authoritative mode; single-player is unchanged.
- `Logic.update()` now runs the full simulation **once per fixed tick** (the previous code advanced
  the tick by N but simulated once, which diverged between 30fps and 60fps clients).
- `NetClient` no longer overwrites the seed mid-tick in authoritative mode; `Team`/`Pathfinder`/
  `SuppressionFieldAbility` no longer use identity `hashCode()` as a seed (stable `team.id`/`unit.id`).
- 25 online tests pass (codec, store, fixed step, mode, applier, keyframes, tick RNG).

## Server-side input authority (implemented)

- `InputCommand` carries only intentions (aim, movement vector, shoot/boost/build flags), ~10-16
  bytes per tick, never the resulting position.
- `InputSender` samples the local player each tick and sends via the binary channel.
- `InputReplicator` buffers commands by tick on the server; `InputSimulator` derives the unit's
  state from the command and the unit's own physics. A modified client can no longer teleport or
  set an impossible velocity — the legacy `strict` distance check is no longer the only authority.
- `Logic` applies buffered inputs at the start of each authoritative tick, after `TickRng`.
- 29 online tests pass (adds input round-trip and buffer dedupe/fallback).

## Unique sector per 4-player squad (implemented)

- `SquadSectorRegistry` persists `squadId -> {seed, uuids[], createdAt}` under a settings key and
  is saved immediately on assignment. A group of up to 4 players always gets the same sector seed;
  a different group gets a different seed, checked against a used-seed set to avoid collisions.
- `SquadAssigner` assigns the connecting player to a squad and its team slot (sharded/crux/malis/green).
- `SquadSectorGenerator` generates a 256x256 4-quadrant sector from the squad seed — terrain, lakes
  and deposits all differ per seed. `SquadSectorSession` saves it as `squad-<id>.msav` so a reload
  never changes the map.
- Server commands: `squad <id>` generates/persists the sector, `squads` lists groups and seeds.
- 31 online tests pass (adds quadrant/team/seed-difference checks).

## Not implemented yet

- End-to-end match test across the Cloudflare relay (needs a deployed Worker and D1).
- Real client UI toggle for authoritative mode (today it is activated by `-Dorbital.authoritative=1`).
- Multiple squads simulated simultaneously in one JVM (the engine has one global world; use one
  server process per squad, or the Cloudflare room as the cross-process session id).
