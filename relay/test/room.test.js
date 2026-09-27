import assert from "node:assert/strict";
import { test } from "node:test";
import { MAX_PER_SECOND, allow, parseRequest, peers, recipients, sizeOf } from "../src/room.js";

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
