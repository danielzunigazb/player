import { DurableObject } from "cloudflare:workers";
import { MAX_MESSAGE_BYTES, MAX_WEBS, allow, parseRequest, peers, recipients, sizeOf } from "./room.js";

/**
 * Relay for Player's web monitor: joins a phone and the browsers paired with it in a room and
 * passes their messages along. Messages are end-to-end encrypted; the relay can't read them and
 * keeps nothing. See docs/monitor.md.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/") return new Response("player relay\n");
    const target = parseRequest(url);
    if (!target) return new Response("not found\n", { status: 404 });
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("expected a websocket\n", { status: 426 });
    }
    const room = env.ROOMS.get(env.ROOMS.idFromName(target.room));
    return room.fetch(request);
  },
};

export class Room extends DurableObject {
  async fetch(request) {
    const { role } = parseRequest(new URL(request.url));
    if (role === "phone") {
      // A phone that reconnects replaces its old connection.
      for (const old of this.ctx.getWebSockets("phone")) old.close(4000, "replaced");
    } else if (this.ctx.getWebSockets("web").length >= MAX_WEBS) {
      return new Response("room full\n", { status: 429 });
    }
    const [client, server] = Object.values(new WebSocketPair());
    // Hibernatable: an idle room costs nothing while its sockets stay open.
    this.ctx.acceptWebSocket(server, [role]);
    server.serializeAttachment({ role, window: null });
    this.announce();
    return new Response(null, { status: 101, webSocket: client });
  }

  webSocketMessage(ws, message) {
    if (sizeOf(message) > MAX_MESSAGE_BYTES) return;
    const state = ws.deserializeAttachment();
    const { window, ok } = allow(state.window, Date.now());
    ws.serializeAttachment({ ...state, window });
    if (!ok) return;
    for (const { socket } of recipients(state.role, this.sockets())) {
      try {
        socket.send(message);
      } catch {
        // Closing already; its close handler updates everyone.
      }
    }
  }

  webSocketClose(ws) {
    this.announce(ws);
  }

  webSocketError(ws) {
    this.announce(ws);
  }

  sockets(except) {
    return this.ctx
      .getWebSockets()
      .filter((socket) => socket !== except && socket.readyState === WebSocket.OPEN)
      .map((socket) => ({ socket, role: socket.deserializeAttachment().role }));
  }

  announce(except) {
    const sockets = this.sockets(except);
    const note = peers(sockets);
    for (const { socket } of sockets) {
      try {
        socket.send(note);
      } catch {
        // Same as above.
      }
    }
  }
}
