/**
 * Orbital online relay — 100% Cloudflare free tier.
 *
 * No Durable Objects (paid) and no D1 writes (100k/day cap). Each room keeps its
 * event log and the set of connected clients in the Worker's memory. Clients
 * open one long-lived SSE stream (GET /events) and post commands (POST /event).
 * One SSE connection is one Worker request, so daily request usage stays tiny.
 *
 * Trade-off: if the Worker is evicted, in-memory events are lost and clients
 * reconnect from their last applied seq (the game can request a keyframe).
 */

const OP_JOIN = 1;
const OP_EVENT = 2;
const OP_KEYFRAME = 3;
const OP_ACK = 4;

/** Max events retained per room (memory hygiene). */
const MAX_EVENTS = 1000;
/** How often the SSE loop checks for new events (ms). */
const POLL_MS = 200;

/** room name -> { seq, tick, events: [], clients: Set<function> } */
const rooms = new Map();

function getRoom(name){
  let room = rooms.get(name);
  if(!room){
    room = {seq: 0n, tick: 0n, events: [], clients: new Set()};
    rooms.set(name, room);
  }
  return room;
}

// ---- varint helpers (unsigned LEB128) ----
function readVarint(view, state){
  let result = 0n, shift = 0n, byte;
  do{
    byte = view.getUint8(state.offset++);
    result |= BigInt(byte & 0x7f) << shift;
    shift += 7n;
  }while(byte & 0x80);
  return result;
}
function writeVarint(bytes, value){
  let v = BigInt(value);
  while(v > 0x7fn){ bytes.push(Number(v & 0x7fn) | 0x80); v >>= 7n; }
  bytes.push(Number(v));
}
function readZigzag(view, state){
  const n = readVarint(view, state);
  return Number((n >> 1n) ^ -(n & 1n));
}
function varintBytes(value){
  const out = [];
  writeVarint(out, value);
  return out;
}
function zigzag(value){
  const out = [];
  writeVarint(out, (BigInt(value) << 1n) ^ (BigInt(value) >> 63n));
  return out;
}

function eventFrame(event){
  return [OP_EVENT, ...varintBytes(event.seq), ...varintBytes(event.tick),
    ...zigzag(-1), ...varintBytes(event.typeId),
    ...varintBytes(event.payload.length), ...event.payload];
}

export default{
  async fetch(request){
    const url = new URL(request.url);
    const name = url.searchParams.get('room') || 'default';

    if(url.pathname === '/health') return new Response('ok');

    if(url.pathname === '/events'){
      //Long-lived Server-Sent Events stream: one request per client, no polling
      //requests from the client and no paid Durable Object.
      const room = getRoom(name);
      let cursor = BigInt(url.searchParams.get('after') || '0');
      const encoder = new TextEncoder();
      let send;
      let closed = false;

      let timer;
      const stream = new ReadableStream({
        start(controller){
          send = (frame) =>{
            if(closed) return;
            try{
              controller.enqueue(encoder.encode('data: ' + btoa(String.fromCharCode(...frame)) + '\n\n'));
            }catch(e){
              closed = true;
            }
          };
          room.clients.add(send);
          //Tell the client which seq the current world corresponds to.
          send([OP_KEYFRAME, ...varintBytes(room.seq)]);

          //setInterval (not an async pull loop) keeps the Worker responsive: each tick is a
          //separate callback and awaiting network time is free on the Workers free plan.
          timer = setInterval(() =>{
            if(closed) return;
            for(const event of room.events){
              if(event.seq > cursor){
                send(eventFrame(event));
                cursor = event.seq;
              }
            }
          }, POLL_MS);
        },
        cancel(){
          closed = true;
          if(timer) clearInterval(timer);
          if(send) room.clients.delete(send);
        }
      });

      //Cloudflare cancels the request when the client disconnects; release the timer then too.
      request.signal.addEventListener('abort', () =>{
        closed = true;
        if(timer) clearInterval(timer);
        if(send) room.clients.delete(send);
      });

      return new Response(stream, {
        headers: {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache, no-transform',
          'Connection': 'keep-alive',
          'X-Accel-Buffering': 'no'
        }
      });
    }

    if(url.pathname === '/event' && request.method === 'POST'){
      const room = getRoom(name);
      const bytes = new Uint8Array(await request.arrayBuffer());
      const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
      const state = {offset: 0};
      const op = view.getUint8(state.offset++);

      if(op === OP_JOIN || op === OP_ACK){
        //Join cursor is carried by /events?after=; ACK is only a pruning hint.
        return new Response('ok');
      }

      if(op === OP_EVENT){
        const typeId = Number(readVarint(view, state));
        const length = Number(readVarint(view, state));
        const payload = bytes.slice(state.offset, state.offset + length);

        //Authoritative sequencing: server assigns seq/tick, never the client.
        room.seq++;
        room.tick++;
        const event = {seq: room.seq, tick: room.tick, typeId, payload};
        room.events.push(event);
        if(room.events.length > MAX_EVENTS) room.events.shift();

        const frame = eventFrame(event);
        for(const client of room.clients) client(frame);
        return new Response('ok');
      }

      return new Response('bad op', {status: 400});
    }

    return new Response('Orbital online relay (free tier, SSE)', {status: 200});
  }
};
