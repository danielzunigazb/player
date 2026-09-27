// Small pure helpers for the monitor, kept apart so node --test can check them.

/** "3:07", or "1:02:03" past an hour. */
export function formatTime(ms) {
  const total = Math.max(0, Math.floor((ms || 0) / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = String(total % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${s}` : `${m}:${s}`;
}

/**
 * Where the song is now: the position the phone sent, plus the time since it arrived while
 * playing. Measured with this computer's clock only, so the two clocks never need to agree.
 */
export function positionNow(state, receivedAt, now) {
  if (!state?.song) return 0;
  const duration = state.song.durationMs || Infinity;
  const elapsed = state.playing ? Math.max(0, now - receivedAt) : 0;
  return Math.min(state.positionMs + elapsed, duration);
}

/** The next repeat mode, in the app's order: off → all → one. */
export const nextRepeat = (mode) => ({ off: "all", all: "one" })[mode] ?? "off";

