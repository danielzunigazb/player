// The browser's connection to a room on the relay: seals what it sends, opens and checks what
// arrives, and reconnects by itself (1 s, 2 s, 4 s… up to 30 s) until stop().

import { Inbox, Outbox, importKey, newRoom, open, seal } from "./protocol.js";

export class Link {
  /**
   * @param relay  the relay's wss:// address
   * @param room   the room to join, as a web
   * @param key    the room's key (base64url)
   * @param events { onMessage(message), onPeers({ phone, webs }), onStatus("connecting"|"open"|"closed") }
   */
  constructor(relay, room, key, events) {
    this.url = `${relay}/room/${room}?role=web`;
    this.keyText = key;
    this.events = events;
    this.outbox = new Outbox(`web-${newRoom().slice(0, 8)}`);
    this.inbox = new Inbox();
    this.attempt = 0;
    this.stopped = false;
    this.socket = null;
  }

  async start() {
    this.key = await importKey(this.keyText);
    this.connect();
  }

  connect() {
    if (this.stopped) return;
    this.events.onStatus?.("connecting");
    const socket = new WebSocket(this.url);
    this.socket = socket;
    socket.onopen = () => {
      this.attempt = 0;
      this.events.onStatus?.("open");
    };
    socket.onmessage = (event) => this.handle(event.data);
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.events.onStatus?.("closed");
      this.events.onPeers?.({ phone: false, webs: 0 });
      if (this.stopped) return;
      const delay = Math.min(1000 * 2 ** Math.min(this.attempt, 5), 30_000);
      this.attempt++;
      this.retry = setTimeout(() => this.connect(), delay);
    };
  }

  async handle(data) {
    if (typeof data !== "string") return;
    if (data.startsWith("{")) {
      // The relay's own presence note, in the clear.
      try {
        const note = JSON.parse(data);
        if (note.relay === "peers") this.events.onPeers?.(note);
      } catch {
        // Not a note after all.
      }
      return;
    }
    const message = await open(this.key, data);
    if (message && this.inbox.accept(message)) this.events.onMessage?.(message);
  }

  /** Sends [message] sealed; false when not connected. */
  async send(message) {
    const socket = this.socket;
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    socket.send(await seal(this.key, this.outbox.stamp(message)));
    return true;
  }

  stop() {
    this.stopped = true;
    clearTimeout(this.retry);
    this.socket?.close(1000);
    this.socket = null;
  }
}
