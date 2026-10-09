import assert from "node:assert/strict";
import { test } from "node:test";
import { applyMove, canReorder, moveCommand, movedIndex, stillApplies } from "./queue.js";

/** A queue of [total] songs as the phone shows it: a window from [start], [current] playing. */
function stateOf(total, { start = 0, length = total - start, current = start, shuffle = false } = {}) {
  const queue = Array.from({ length }, (_, k) => ({ i: start + k, id: `song-${start + k}`, title: `Song ${start + k}` }));
  return { shuffle, queueStart: start, queueTotal: total, index: current, queue };
}

/** The phone's side: Player.moveMediaItem on the whole list. */
function moveMediaItem(list, from, to) {
  const moved = [...list];
  moved.splice(to, 0, moved.splice(from, 1)[0]);
  return moved;
}

test("reorders only with shuffle off and something to reorder", () => {
  assert.equal(canReorder(stateOf(5)), true);
  assert.equal(canReorder(stateOf(5, { shuffle: true })), false);
  assert.equal(canReorder(stateOf(1)), false);
  assert.equal(canReorder({ shuffle: false, queue: [] }), false);
  assert.equal(canReorder(null), false);
});

test("the command names list indices, not positions in the window", () => {
  const state = stateOf(300, { start: 40, length: 150, current: 60 });
  assert.deepEqual(moveCommand(state, 2, 7), { op: "move", fromIndex: 42, toIndex: 47 });
  assert.deepEqual(moveCommand(state, 7, 0), { op: "move", fromIndex: 47, toIndex: 40 });
});

test("no command for a drop in place, outside the window or while shuffled", () => {
  const state = stateOf(10);
  assert.equal(moveCommand(state, 3, 3), null);
  assert.equal(moveCommand(state, 3, 10), null);
  assert.equal(moveCommand(state, -1, 2), null);
  assert.equal(moveCommand(stateOf(10, { shuffle: true }), 1, 4), null);
});

test("moving down and up, like moveMediaItem", () => {
  assert.deepEqual([0, 1, 2, 3].map((k) => movedIndex(k, 0, 2)), [2, 0, 1, 3]);
  assert.deepEqual([0, 1, 2, 3].map((k) => movedIndex(k, 3, 0)), [1, 2, 3, 0]);
  assert.deepEqual([0, 1, 2, 3].map((k) => movedIndex(k, 1, 1)), [0, 1, 2, 3]);
});

test("the playing song keeps playing wherever it goes", () => {
  const state = stateOf(6, { current: 1 });
  const moved = applyMove(state, moveCommand(state, 1, 4));
  assert.equal(moved.index, 4);
  assert.equal(moved.queue[moved.index - moved.queueStart].id, "song-1");
  // And a song moved past it pushes it back one.
  const passed = applyMove(state, moveCommand(state, 0, 3));
  assert.equal(passed.queue[passed.index - passed.queueStart].id, "song-1");
});

test("a drag still shows on a newer state if those songs didn't move in the list", () => {
  const seen = stateOf(30, { start: 0, length: 30, current: 2 });
  const command = moveCommand(seen, 4, 9);
  // A heartbeat, or the next song: the window may shift, the list is the same.
  assert.equal(stillApplies({ ...seen }, seen, command), true);
  assert.equal(stillApplies(stateOf(30, { start: 3, length: 27, current: 3 }), seen, command), true);
  // A song added before them, the window no longer reaching them, or shuffle on: not anymore.
  const added = stateOf(31, { start: 0, length: 31, current: 2 });
  added.queue = [added.queue[0], { i: 1, id: "new" }, ...seen.queue.slice(1).map((item) => ({ ...item, i: item.i + 1 }))];
  assert.equal(stillApplies(added, seen, command), false);
  assert.equal(stillApplies(stateOf(30, { start: 6, length: 24, current: 6 }), seen, command), false);
  assert.equal(stillApplies({ ...seen, shuffle: true }, seen, command), false);
});

// Any queue, any window and any drag: what the monitor shows right away is what the phone will
// send once it has moved the song.
test("showing the move before the phone answers matches what it will send", () => {
  let seed = 7;
  const random = (n) => {
    // mulberry32: the same cases every run.
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return Math.floor((((t ^ (t >>> 14)) >>> 0) / 4294967296) * n);
  };
  for (let run = 0; run < 2000; run++) {
    const total = 2 + random(60);
    const start = random(total - 1);
    const length = 2 + random(total - start - 1);
    const current = start + random(length);
    const state = stateOf(total, { start, length, current });
    const command = moveCommand(state, random(length), random(length));
    if (!command) continue;

    const list = moveMediaItem(Array.from({ length: total }, (_, k) => `song-${k}`), command.fromIndex, command.toIndex);
    const shown = applyMove(state, command);
    const context = JSON.stringify({ total, start, length, current, command });
    assert.deepEqual(shown.queue.map((item) => item.id), list.slice(start, start + length), context);
    assert.deepEqual(shown.queue.map((item) => item.i), state.queue.map((item) => item.i), context);
    assert.equal(list[shown.index], `song-${current}`, context);
  }
});
