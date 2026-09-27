// What the relay decides, apart from Cloudflare's APIs so node --test can check it.

export const MAX_MESSAGE_BYTES = 64 * 1024;
export const MAX_WEBS = 8;
export const MAX_PER_SECOND = 30;

const ROOM = /^[A-Za-z0-9_-]{22}$/;

/** The room and role of a connection request, or null when the request isn't one. */
export function parseRequest(url) {
  const match = /^\/room\/([^/]+)$/.exec(url.pathname);
  if (!match || !ROOM.test(match[1])) return null;
  const role = url.searchParams.get("role");
  if (role !== "phone" && role !== "web") return null;
  return { room: match[1], role };
}

/**
 * Counts a message against its connection's one-second window. Returns the updated window and
 * whether the message may pass.
 */
export function allow(window, now) {
  if (!window || now - window.start >= 1000) return { window: { start: now, count: 1 }, ok: true };
  const count = window.count + 1;
  return { window: { start: window.start, count }, ok: count <= MAX_PER_SECOND };
}

/** Byte length of a WebSocket message, text or binary. */
export function sizeOf(message) {
  return typeof message === "string" ? new TextEncoder().encode(message).length : message.byteLength;
}

/** Who gets a message: the webs for the phone's, the phone for a web's. */
export function recipients(senderRole, sockets) {
  const target = senderRole === "phone" ? "web" : "phone";
  return sockets.filter((s) => s.role === target);
}

/** The presence note the relay sends everyone in the room, in the clear. */
export function peers(sockets) {
  return JSON.stringify({
    relay: "peers",
    phone: sockets.some((s) => s.role === "phone"),
    webs: sockets.filter((s) => s.role === "web").length,
  });
}
