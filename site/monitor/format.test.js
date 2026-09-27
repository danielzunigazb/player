import assert from "node:assert/strict";
import { test } from "node:test";
import { formatTime, nextRepeat, positionNow } from "./format.js";

test("formats times like the app", () => {
  assert.equal(formatTime(0), "0:00");
  assert.equal(formatTime(187_000), "3:07");
  assert.equal(formatTime(3_723_000), "1:02:03");
  assert.equal(formatTime(undefined), "0:00");
});

test("the position advances with this computer's clock only while playing, up to the end", () => {
  const song = { durationMs: 200_000 };
  assert.equal(positionNow({ song, playing: true, positionMs: 10_000 }, 1_000, 6_000), 15_000);
  assert.equal(positionNow({ song, playing: false, positionMs: 10_000 }, 1_000, 6_000), 10_000);
  assert.equal(positionNow({ song, playing: true, positionMs: 199_000 }, 0, 60_000), 200_000);
  assert.equal(positionNow({ song: null }, 0, 0), 0);
});

test("repeat cycles off → all → one like the app", () => {
  assert.equal(nextRepeat("off"), "all");
  assert.equal(nextRepeat("all"), "one");
  assert.equal(nextRepeat("one"), "off");
});
