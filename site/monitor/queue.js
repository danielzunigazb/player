// Reordering the queue from the monitor, apart from the page so node --test can check it.
//
// The phone's state carries a window of the queue in play order, each item with `i`, its index in
// the list; `move` (fromIndex, toIndex) is Player.moveMediaItem on those indices. With shuffle on,
// moving in the list keeps the play order (QueueShuffleOrder.cloneAndMove), so a drag would show
// no change: like the app's queue, the monitor only reorders with shuffle off.

/** Whether [state]'s queue can be reordered: shuffle off and more than one song shown. */
export const canReorder = (state) => !!state && !state.shuffle && (state.queue?.length ?? 0) > 1;

/**
 * The `move` command that takes the song shown at [fromOffset] to [toOffset] in [state]'s queue
 * window, or null when there's nothing to move.
 */
export function moveCommand(state, fromOffset, toOffset) {
  if (!canReorder(state) || fromOffset === toOffset) return null;
  const from = state.queue[fromOffset];
  const to = state.queue[toOffset];
  if (!from || !to) return null;
  return { op: "move", fromIndex: from.i, toIndex: to.i };
}

/**
 * Where list index [k] ends up after Player.moveMediaItem([fromIndex], [toIndex]): the moved item
 * lands on [toIndex] and the ones it passed shift by one towards where it was.
 */
export function movedIndex(k, fromIndex, toIndex) {
  if (k === fromIndex) return toIndex;
  if (fromIndex < toIndex && k > fromIndex && k <= toIndex) return k - 1;
  if (toIndex < fromIndex && k >= toIndex && k < fromIndex) return k + 1;
  return k;
}

/**
 * Whether [command], made on the queue of [seen], moves the same songs in [state]: the phone may
 * have sent a newer state meanwhile (a heartbeat, the next song), and the list between the two
 * indices is still the same then. Not if songs were added, removed or moved there.
 */
export function stillApplies(state, seen, { fromIndex, toIndex }) {
  if (!canReorder(state) || !canReorder(seen)) return false;
  const now = new Map(state.queue.map((item) => [item.i, item.id]));
  const then = new Map(seen.queue.map((item) => [item.i, item.id]));
  for (let k = Math.min(fromIndex, toIndex); k <= Math.max(fromIndex, toIndex); k++) {
    if (!now.has(k) || now.get(k) !== then.get(k)) return false;
  }
  return true;
}

/**
 * [state] as the phone will send it after [command], to show the move before it answers. Only
 * when the command [stillApplies] to it: its window is in list order then.
 */
export function applyMove(state, { fromIndex, toIndex }) {
  const shift = (k) => movedIndex(k, fromIndex, toIndex);
  return {
    ...state,
    index: shift(state.index),
    queue: state.queue.map((item) => ({ ...item, i: shift(item.i) })).sort((a, b) => a.i - b.i),
  };
}
