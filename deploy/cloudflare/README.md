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

## Not implemented yet

- Fixed-timestep authoritative re-simulation on the server (the engine currently uses variable
  delta and a global RNG — see `docs/PLANO_ETAPAS_INFRA_SERVIDOR.md`).
- Keyframe generation/replay (`SaveIO` + `NetworkIO` can be reused).
- Replacing the legacy ArcNet `sync()` (`NetServer.java:1310-1423`).
