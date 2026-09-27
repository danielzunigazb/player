import assert from "node:assert/strict";
import { test } from "node:test";
import { Inbox, MAX_SKEW_MS, Outbox, address, fromBase64Url, importKey, newKey, newRoom, open, seal, toBase64Url } from "./protocol.js";

// The same vector is checked by the app's RemoteCryptoTest: both sides must agree byte for byte.
const VECTOR = {
  key: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
  iv: "oKGio6Slpqeoqaqr",
  message: { type: "cmd", op: "next", from: "web-1", seq: 1, ts: 1790000000000 },
};

test("rooms and keys have the lengths the relay and the app expect", () => {
  assert.match(newRoom(), /^[A-Za-z0-9_-]{22}$/);
  assert.match(newKey(), /^[A-Za-z0-9_-]{43}$/);
  assert.notEqual(newRoom(), newRoom());
});

test("base64url round-trips any bytes", () => {
  const bytes = Uint8Array.from({ length: 256 }, (_, i) => i);
  assert.deepEqual(fromBase64Url(toBase64Url(bytes)), bytes);
});

test("seals and opens a message", async () => {
  const key = await importKey(newKey());
  const message = { type: "state", playing: true, song: { title: "Canción" } };
  assert.deepEqual(await open(key, await seal(key, message)), message);
});

test("a message sealed with another key, or altered, doesn't open", async () => {
  const key = await importKey(newKey());
  const other = await importKey(newKey());
  const sealed = await seal(key, { type: "cmd", op: "pause" });
  assert.equal(await open(other, sealed), null);
  const flipped = sealed.slice(0, 20) + (sealed[20] === "A" ? "B" : "A") + sealed.slice(21);
  assert.equal(await open(key, flipped), null);
  assert.equal(await open(key, "not base64 at all!"), null);
  assert.equal(await open(key, ""), null);
});

test("matches the vector the app checks", async () => {
  const key = await importKey(VECTOR.key);
  const sealed = await seal(key, VECTOR.message, fromBase64Url(VECTOR.iv));
  assert.equal(sealed, VECTOR_SEALED);
  assert.deepEqual(await open(key, sealed), VECTOR.message);
});

test("drops replays, old seqs and stale messages, per sender", () => {
  const now = 1_790_000_000_000;
  const web = new Outbox("web-1");
  const inbox = new Inbox();
  const first = web.stamp({ type: "cmd" }, now);
  const second = web.stamp({ type: "cmd" }, now);
  assert.equal(inbox.accept(first, now), true);
  assert.equal(inbox.accept(first, now), false);
  assert.equal(inbox.accept(second, now), true);
  assert.equal(inbox.accept(new Outbox("web-2").stamp({}, now), now), true);
  assert.equal(inbox.accept(new Outbox("web-3").stamp({}, now - 3 * 60 * 1000), now), false);
  assert.equal(inbox.accept({ type: "cmd" }, now), false);
});

test("drops messages from more than 2 minutes in the future", () => {
  const now = 1_790_000_000_000;
  const inbox = new Inbox();
  assert.equal(inbox.accept(new Outbox("phone-a").stamp({}, now + MAX_SKEW_MS + 1), now), false);
  assert.equal(inbox.accept(new Outbox("phone-b").stamp({}, now + MAX_SKEW_MS), now), true);
});

test("a phone that restarted under a new id is heard again from seq 1", () => {
  const now = 1_790_000_000_000;
  const inbox = new Inbox();
  const before = new Outbox("phone-aaaaaaaa");
  for (let i = 0; i < 50; i++) inbox.accept(before.stamp({ type: "state" }, now), now);
  assert.equal(inbox.accept(new Outbox("phone-bbbbbbbb").stamp({ type: "state" }, now), now), true);
});

test("commands go to the phone's current id; other messages aren't addressed", () => {
  assert.deepEqual(address({ type: "cmd", op: "next" }, "phone-bbbbbbbb"), { type: "cmd", op: "next", to: "phone-bbbbbbbb" });
  assert.deepEqual(address({ type: "cmd", op: "hello" }, null), { type: "cmd", op: "hello" });
  assert.deepEqual(address({ type: "paired" }, "phone-bbbbbbbb"), { type: "paired" });
});

const VECTOR_SEALED = "oKGio6SlpqeoqaqrnToIVDWuIIVABuq3JVbisQCOYzL80joYviIE4A3EGCPoVDCazQ9iH3O-d614WLnIazkyO0DqK0l4bjtulEC1gISMtRKaCxcv0Ky-OrHIXDAjrcnA";
