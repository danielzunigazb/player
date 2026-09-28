import assert from "node:assert/strict";
import { test } from "node:test";
import { Link } from "./link.js";
import { Outbox, PhoneTarget, importKey, newKey, newRoom, open, seal } from "./protocol.js";

const note = (phone) => JSON.stringify({ relay: "peers", phone, webs: 1 });

/** A Link fed by hand, without a relay: what it sends lands in [sent], opened. */
async function linkWithoutRelay() {
  const keyText = newKey();
  const key = await importKey(keyText);
  const link = new Link("ws://unused", newRoom(), keyText, {});
  link.key = key;
  const sent = [];
  link.socket = { readyState: WebSocket.OPEN, send: async (text) => sent.push(await open(key, text)) };
  const phone = (id) => new Outbox(id);
  const from = async (outbox) => link.handle(await seal(key, outbox.stamp({ type: "state" })));
  return { link, sent, phone, from };
}

test("the phone's id is unknown until it speaks, and forgotten when it leaves", () => {
  const target = new PhoneTarget();
  target.peers({ phone: true });
  assert.equal(target.id, null);
  target.heard({ from: "phone-aaaaaaaa" });
  assert.equal(target.id, "phone-aaaaaaaa");
  target.peers({ phone: true, webs: 2 });
  assert.equal(target.id, "phone-aaaaaaaa");
  target.peers({ phone: false });
  assert.equal(target.id, null);
});

test("a link knows where to send only after the phone's first message, again after it returns", async () => {
  const { link, sent, phone, from } = await linkWithoutRelay();
  await link.handle(note(true));
  assert.equal(link.phone, null);

  await from(phone("phone-aaaaaaaa"));
  assert.equal(link.phone, "phone-aaaaaaaa");
  await link.send({ type: "cmd", op: "next" });

  await link.handle(note(false));
  assert.equal(link.phone, null);
  await link.handle(note(true));
  assert.equal(link.phone, null);

  await from(phone("phone-bbbbbbbb"));
  assert.equal(link.phone, "phone-bbbbbbbb");
  await link.send({ type: "cmd", op: "pause" });
  await new Promise((resolve) => setTimeout(resolve, 10));

  assert.deepEqual(sent.map((m) => [m.op, m.to]), [["next", "phone-aaaaaaaa"], ["pause", "phone-bbbbbbbb"]]);
});
