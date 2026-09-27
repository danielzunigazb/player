// The monitor's side of the protocol in docs/monitor.md: random rooms and keys, AES-256-GCM
// sealing in the format the app uses, and the checks that drop replays and stale messages.
// Plain ES module over Web Crypto, so the same file runs in the browser and under node --test.

// Same address as RELAY_URL in app/build.gradle.kts. A page served from localhost may point at a
// local relay (wrangler dev) with ?relay=ws://localhost:8787.
export const RELAY = localRelay() ?? "wss://player.danzuniga.workers.dev";

function localRelay() {
  if (typeof location === "undefined" || location.hostname !== "localhost") return null;
  return new URLSearchParams(location.search).get("relay");
}
export const MAX_SKEW_MS = 2 * 60 * 1000;

export function toBase64Url(bytes) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fromBase64Url(text) {
  const binary = atob(text.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

const random = (n) => crypto.getRandomValues(new Uint8Array(n));

/** A new room: 16 random bytes, 22 characters. */
export const newRoom = () => toBase64Url(random(16));

/** A new key: 32 random bytes, 43 characters. */
export const newKey = () => toBase64Url(random(32));

export function importKey(text) {
  const raw = fromBase64Url(text);
  if (raw.length !== 32) throw new Error("bad key");
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
}

/** Encrypts [message] as base64url(iv || ciphertext+tag). [iv] is only fixed by tests. */
export async function seal(key, message, iv = random(12)) {
  const plain = new TextEncoder().encode(JSON.stringify(message));
  const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, plain));
  const out = new Uint8Array(iv.length + sealed.length);
  out.set(iv);
  out.set(sealed, iv.length);
  return toBase64Url(out);
}

/** The message inside [text], or null when it isn't ours (other key, altered, not JSON). */
export async function open(key, text) {
  try {
    const bytes = fromBase64Url(text);
    if (bytes.length < 12 + 16) return null;
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes.subarray(0, 12) }, key, bytes.subarray(12));
    const message = JSON.parse(new TextDecoder().decode(plain));
    return message && typeof message === "object" ? message : null;
  } catch {
    return null;
  }
}

/** Numbers what one sender sends: its id, a rising seq and the time. */
export class Outbox {
  constructor(from) {
    this.from = from;
    this.seq = 0;
  }

  stamp(message, now = Date.now()) {
    return { ...message, from: this.from, seq: ++this.seq, ts: now };
  }
}

/** Lets through only messages newer than the last one from the same sender, and not stale. */
export class Inbox {
  constructor() {
    this.last = new Map();
  }

  accept(message, now = Date.now()) {
    const { from, seq, ts } = message;
    if (typeof from !== "string" || !Number.isInteger(seq) || typeof ts !== "number") return false;
    if (Math.abs(now - ts) > MAX_SKEW_MS) return false;
    if (seq <= (this.last.get(from) ?? 0)) return false;
    this.last.set(from, seq);
    return true;
  }
}

/**
 * [message] as sent to the phone: a command names the phone's current sender id ([phone], the
 * last one heard) in `to`, so a phone that restarted ignores commands captured before. Until the
 * phone has been heard there's no id: commands go without, and the phone only takes `hello`.
 */
export function address(message, phone) {
  return message.type === "cmd" && phone ? { ...message, to: phone } : message;
}

/**
 * The phone a web's commands go to: the sender of the last message heard from it, forgotten when
 * the relay says the phone left (it comes back under a new id). While [id] is null, commands
 * would go without `to` and the phone would drop them: the monitor keeps its controls off.
 */
export class PhoneTarget {
  constructor() {
    this.id = null;
  }

  /** A message from the phone was accepted. */
  heard(message) {
    this.id = message.from;
  }

  /** The relay's presence note, or `{ phone: false }` when the connection drops. */
  peers(note) {
    if (!note.phone) this.id = null;
  }
}

const BASE32 ="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/**
 * The code the monitor shows under the QR and the phone shows before pairing, so the person can
 * tell it's this browser the phone is about to trust: the first 6 base32 characters of SHA-256
 * over the temporary key's 32 bytes. RemoteCrypto.pairingCode in the app does the same; both
 * check the same test vector.
 */
export async function pairingCode(key) {
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", fromBase64Url(key)));
  const bits = ((hash[0] << 24) | (hash[1] << 16) | (hash[2] << 8) | hash[3]) >>> 0;
  let code = "";
  for (let i = 0; i < 6; i++) code += BASE32[(bits >>> (27 - 5 * i)) & 31];
  return code;
}

/** The link a pairing QR carries; the room and key ride in the fragment, which no server sees. */
export const pairLink = (room, key) => `https://player.danzuniga.xyz/pair#r=${room}&k=${key}`;
