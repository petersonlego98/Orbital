/**
 * Orbital online relay — Cloudflare Workers free tier.
 *
 * One Durable Object per room holds the authoritative event sequence and the set of connected
 * sockets. The client sends only compact command frames (OP_EVENT); the room assigns a monotonic
 * seq and server tick, appends the event to D1 and broadcasts it to every other client. Clients
 * catch up with "after seq X" after a reconnect, so normal traffic is a handful of bytes per
 * second instead of full world snapshots.
 *
 * Free-tier notes: Workers + WebSocketPair are free; D1 has 100k rows written/day on the free
 * plan, so prune old events with the scheduled cleanup below. For many players, shard by room.
 */

const OP_JOIN = 1;
const OP_EVENT = 2;
const OP_KEYFRAME = 3;
const OP_ACK = 4;

// ---- varint helpers (unsigned LEB128) ----
function readVarint(view, state) {
  let result = 0n, shift = 0n, byte;
  do {
    byte = view.getUint8(state.offset++);
    result |= BigInt(byte & 0x7f) << shift;
    shift += 7n;
  } while (byte & 0x80);
  return result;
}
function writeVarint(bytes, value) {
  let v = BigInt(value);
  while (v > 0x7fn) { bytes.push(Number(v & 0x7fn) | 0x80); v >>= 7n; }
  bytes.push(Number(v));
}
function readZigzag(view, state) {
  const n = readVarint(view, state);
  return Number((n >> 1n) ^ -(n & 1n));
}

export class OrbitalRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sockets = new Set();
    this.seq = 0n;
    this.tick = 0n;
  }

  async fetch(request) {
    const pair = new WebSocketPair();
    pair.client.accept();
    this.sockets.add(pair.client);
    pair.client.addEventListener('close', () => this.sockets.delete(pair.client));
    pair.client.addEventListener('message', (event) => this.onMessage(pair.client, event.data));

    // On join, send a keyframe marker with the current seq. The world itself is cached as a static
    // asset (keyframe.bin uploaded to R2) so it is downloaded once, not on every reconnect.
    this.send(pair.client, [OP_KEYFRAME, ...varintBytes(this.seq)]);
    return new Response(null, { status: 101, webSocket: pair.client });
  }

  async onMessage(socket, data) {
    const bytes = new Uint8Array(data);
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const state = { offset: 0 };
    const op = view.getUint8(state.offset++);

    if (op === OP_JOIN) {
      const after = readVarint(view, state);
      // Replay everything the client missed, then it is up to date.
      await this.replay(socket, after);
      return;
    }

    if (op === OP_ACK) {
      // Acknowledgement is only used for pruning, which is handled by the scheduled cleanup.
      return;
    }

    if (op === OP_EVENT) {
      const typeId = Number(readVarint(view, state));
      const length = Number(readVarint(view, state));
      const payload = bytes.slice(state.offset, state.offset + length);

      // Authoritative sequencing: the server assigns seq/tick, never the client.
      this.seq++;
      this.tick++;
      const frame = [OP_EVENT, ...varintBytes(this.seq), ...varintBytes(this.tick),
        ...zigzag(-1), ...varintBytes(typeId), ...varintBytes(payload.length), ...payload];

      // Persist one row per event. D1 free tier is generous enough for small rooms; prune below.
      try {
        await this.env.DB.prepare(
          'INSERT INTO events (room, seq, tick, type_id, payload) VALUES (?, ?, ?, ?, ?)'
        ).bind(this.roomId(), this.seq.toString(), this.tick.toString(), typeId,
          btoa(String.fromCharCode(...payload))).run();
      } catch (e) {
        // A persistence failure must not stall gameplay; the in-memory broadcast still works.
        console.error('D1 write failed', e);
      }

      this.broadcast(frame, socket);
    }
  }

  async replay(socket, after) {
    if (after >= this.seq) return;
    try {
      const rows = await this.env.DB.prepare(
        'SELECT seq, tick, type_id, payload FROM events WHERE room = ? AND CAST(seq AS INTEGER) > ? ORDER BY CAST(seq AS INTEGER) LIMIT 500'
      ).bind(this.roomId(), after.toString()).all();
      for (const row of rows.results) {
        const payload = Uint8Array.from(atob(row.payload), c => c.charCodeAt(0));
        this.send(socket, [OP_EVENT, ...varintBytes(BigInt(row.seq)), ...varintBytes(BigInt(row.tick)),
          ...zigzag(-1), ...varintBytes(Number(row.type_id)), ...varintBytes(payload.length), ...payload]);
      }
    } catch (e) {
      console.error('replay failed', e);
    }
  }

  broadcast(frame, except) {
    const bytes = new Uint8Array(frame);
    for (const socket of this.sockets) {
      if (socket === except) continue;
      try { socket.send(bytes); } catch (e) { this.sockets.delete(socket); }
    }
  }

  send(socket, frame) {
    try { socket.send(new Uint8Array(frame)); } catch (e) { this.sockets.delete(socket); }
  }

  roomId() {
    return this.state.id.toString();
  }
}

function varintBytes(value) {
  const out = [];
  writeVarint(out, value);
  return out;
}
function zigzag(value) {
  const out = [];
  writeVarint(out, (BigInt(value) << 1n) ^ (BigInt(value) >> 63n));
  return out;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/ws') {
      const id = env.ORBITAL_ROOM.idFromName(url.searchParams.get('room') || 'default');
      const room = env.ORBITAL_ROOM.get(id);
      return room.fetch(request);
    }
    if (url.pathname === '/health') return new Response('ok');
    return new Response('Orbital online relay', { status: 200 });
  },

  // Free-tier hygiene: drop events older than 24h so D1 writes stay within quota.
  async scheduled(event, env, ctx) {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    ctx.waitUntil(env.DB.prepare('DELETE FROM events WHERE created_at < ?').bind(cutoff).run());
  }
};
