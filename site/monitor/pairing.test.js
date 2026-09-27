import assert from "node:assert/strict";
import { test } from "node:test";
import { CODE_ALPHABET, START, codeHint, isCode, normalizeCode, pairingStep } from "./pairing.js";

const room = "AAAAAAAAAAAAAAAAAAAAAA";
const key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
const run = (...messages) => messages.reduce(pairingStep, START);

test("waits on the QR until the phone asks for its code", () => {
  assert.deepEqual(run(), { view: "scan" });
  assert.deepEqual(run({ type: "state" }), { view: "scan" });
  assert.deepEqual(run({ type: "askCode" }), { view: "code" });
});

test("a wrong code shows the tries left, and asking again keeps them", () => {
  const state = run({ type: "askCode" }, { type: "wrongCode", attemptsLeft: 2 });
  assert.deepEqual(state, { view: "code", attemptsLeft: 2 });
  assert.deepEqual(pairingStep(state, { type: "askCode" }), state);
});

test("too many wrong codes end it; nothing afterwards pairs", () => {
  const failed = run({ type: "askCode" }, { type: "wrongCode", attemptsLeft: 1 }, { type: "pairFailed" });
  assert.deepEqual(failed, { view: "failed" });
  assert.deepEqual(pairingStep(failed, { type: "welcome", room, key, name: "Pixel" }), failed);
  assert.deepEqual(pairingStep(failed, { type: "askCode" }), failed);
});

test("the welcome brings the pairing to keep", () => {
  assert.deepEqual(run({ type: "askCode" }, { type: "welcome", room, key, name: "Pixel" }), {
    view: "paired",
    pairing: { room, key, name: "Pixel" },
  });
});

test("a welcome with a malformed room or key is ignored", () => {
  assert.deepEqual(run({ type: "askCode" }, { type: "welcome", room: "short", key }), { view: "code" });
  assert.deepEqual(run({ type: "askCode" }, { type: "welcome", room, key: "short" }), { view: "code" });
});

test("reads the code however it was typed", () => {
  assert.equal(normalizeCode(" ab3 k9z "), "AB3K9Z");
  assert.equal(normalizeCode("ab3-k9z"), "AB3K9Z");
  assert.equal(isCode("AB3K9Z"), true);
  assert.equal(isCode("AB3K9"), false);
  assert.equal(isCode("AB3K9Z7"), false);
  assert.equal(isCode("AB3K9!"), false);
});

test("only codes the phone could show pass; look-alikes get a hint, not a try", () => {
  assert.equal(CODE_ALPHABET, "ABCDEFGHJKMNPQRSTUVWXYZ23456789");
  for (const c of "0O1IL") assert.equal(isCode(`AB3K9${c}`), false, c);
  assert.equal(codeHint("AB3K9Z"), null);
  assert.match(codeHint("AB3K9O"), /nunca muestra O/);
  assert.match(codeHint("0B3K1Z"), /nunca muestra 0, 1/);
  assert.match(codeHint("AB3K9"), /6 caracteres/);
  assert.match(codeHint("AB3K9Ñ"), /letras y números/);
});
