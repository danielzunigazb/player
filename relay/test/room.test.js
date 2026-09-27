import assert from "node:assert/strict";
import { test } from "node:test";
import {
  MAX_MESSAGE_BYTES,
  MAX_PER_SECOND,
  MAX_WEBS,
  admit,
  allow,
  forward,
  parseRequest,
  peers,
  recipients,
  sizeOf,
} from "../src/room.js";

const room = "AAAAAAAAAAAAAAAAAAAAAA";

test("accepts a room of 22 base64url characters and a known role", () => {
  assert.deepEqual(parseRequest(new URL(`https://r/room/${room}?role=phone`)), { room, role: "phone" });
  assert.deepEqual(parseRequest(new URL(`https://r/room/${room}?role=web`)), { room, role: "web" });
});

test("rejects other paths, rooms and roles", () => {
  for (const path of [
    `/room/${room}`,
    `/room/${room}?role=admin`,
    `/room/short?role=web`,
    `/room/${room}x?role=web`,
    `/room/AAAAAAAAAAAAAAAAAAAAA+?role=web`,
    `/room/${room}/x?role=web`,
    `/rooms/${room}?role=web`,
  ]) {
    assert.equal(parseRequest(new URL(`https://r${path}`)), null, path);
  }
});

test("lets through at most the per-second budget, then a new second starts over", () => {
  let window = null;
  let passed = 0;
  for (let i = 0; i < MAX_PER_SECOND + 5; i++) {
    const result = allow(window, 1000);
    window = result.window;
    if (result.ok) passed++;
  }
  assert.equal(passed, MAX_PER_SECOND);
  assert.equal(allow(window, 2000).ok, true);
});

test("measures text in bytes, not characters", () => {
  assert.equal(sizeOf("ñ"), 2);
  assert.equal(sizeOf(new ArrayBuffer(10)), 10);
});

test("a phone's messages go to the webs and a web's to the phone", () => {
  const sockets = [{ role: "phone" }, { role: "web" }, { role: "web" }];
  assert.deepEqual(recipients("phone", sockets), [sockets[1], sockets[2]]);
  assert.deepEqual(recipients("web", sockets), [sockets[0]]);
});

test("the presence note says whether the phone is there and how many webs", () => {
  assert.deepEqual(JSON.parse(peers([{ role: "web" }, { role: "web" }])), { relay: "peers", phone: false, webs: 2 });
  assert.deepEqual(JSON.parse(peers([{ role: "phone" }])), { relay: "peers", phone: true, webs: 0 });
});

test("a room takes up to 8 webs; the ninth is turned away", () => {
  const webs = (n) => Array.from({ length: n }, () => ({ role: "web" }));
  assert.equal(MAX_WEBS, 8);
  assert.deepEqual(admit("web", [{ role: "phone" }, ...webs(7)]), { ok: true, replace: [] });
  assert.equal(admit("web", [{ role: "phone" }, ...webs(8)]).ok, false);
});

test("a new phone always gets in and replaces the old one, however many webs there are", () => {
  const old = { role: "phone" };
  const webs = Array.from({ length: 8 }, () => ({ role: "web" }));
  assert.deepEqual(admit("phone", [old, ...webs]), { ok: true, replace: [old] });
  assert.deepEqual(admit("phone", webs), { ok: true, replace: [] });
});

test("passes messages of up to 64 KB and drops bigger ones", () => {
  assert.equal(MAX_MESSAGE_BYTES, 64 * 1024);
  assert.equal(forward("A".repeat(MAX_MESSAGE_BYTES), null, 0).ok, true);
  assert.equal(forward("A".repeat(MAX_MESSAGE_BYTES + 1), null, 0).ok, false);
  assert.equal(forward(new ArrayBuffer(MAX_MESSAGE_BYTES + 1), null, 0).ok, false);
});

test("passes 30 messages a second per connection and drops the 31st", () => {
  assert.equal(MAX_PER_SECOND, 30);
  let window = null;
  for (let i = 0; i < 30; i++) {
    const result = forward("abc", window, 5000 + i);
    assert.equal(result.ok, true, `message ${i + 1}`);
    window = result.window;
  }
  assert.equal(forward("abc", window, 5999).ok, false);
  assert.equal(forward("abc", window, 6000).ok, true);
});

test("drops a client's text that passes for the relay's own note", () => {
  const forged = JSON.stringify({ relay: "peers", phone: true, webs: 0 });
  assert.equal(forward(forged, null, 0).ok, false);
  assert.equal(forward("{", null, 0).ok, false);
  // A dropped fake doesn't count against the budget.
  assert.equal(forward(forged, null, 0).window, null);
  assert.equal(forward("oKGio6SlpqeoqaqrnToIVDWu", null, 0).ok, true);
});
