# Orbital online — Cloudflare free tier

Event-sourced relay: the client sends only commands (compact varint frames), the Worker assigns an
authoritative `seq`/`tick`, persists one row per event in D1 and broadcasts it. Clients catch up
after a disconnect by sending their last applied `seq`. No full-state snapshot per tick — that is
what keeps the session in the kbps range.

## Deploy (one time)

```bash
cd deploy/cloudflare
wrangler login
wrangler d1 create orbital-events        # copy the printed database_id into wrangler.toml
wrangler d1 execute orbital-events --file=schema.sql
wrangler deploy
```

The WebSocket endpoint is `wss://orbital-online.<your-subdomain>.workers.dev/ws?room=default`.

## Client

Java side (in `core`):

- `net/events/EventRecord`, `EventCodec`, `EventStore`, `EventRecorder`, `EventTypeIds` — the
  append-only log and compact codec.
- `net/online/WsClient`, `OnlineClient` — WebSocket transport and event envelope.

Enable the server-side log with `-Dorbital.eventlog=1 -Dorbital.eventlog.dir=<folder>`.

## Free tier limits

- Workers: 100k requests/day — fine for a small room.
- D1: 100k rows written/day; the hourly cron deletes events older than 24h.
- Durable Objects: **not free** on the current Cloudflare pricing. The `wrangler.toml` uses one
  Durable Object per room; for a strictly-free deployment, replace `OrbitalRoom` with a plain
  Worker that keeps the seq in D1 and uses no in-memory socket set (broadcast needs Durable
  Objects, so true multiplayer relay on the free plan requires a different design — e.g. polling
  `/events?after=SEQ` at 5 Hz from each client, which still stays in kbps).

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

## Not implemented yet

- RNG per tick instead of the global `GlobalVars.rand`.
- Full re-simulation of player input on the server (validation today is the legacy `strict` check).
