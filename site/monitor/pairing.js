// The monitor's side of pairing (docs/monitor.md), apart from the page so node --test can check
// it. The phone scans the QR and shows a code; it asks for it with askCode, answers a wrong one
// with wrongCode (attemptsLeft) and, after the last, with pairFailed; the right one brings the
// welcome with the phone's room and key.

export const CODE_LENGTH = 6;

/** The code however it was typed: uppercase, without spaces or dashes. The phone does the same. */
export const normalizeCode = (text) => String(text).toUpperCase().replace(/[\s-]/g, "");

export const isCode = (code) => new RegExp(`^[A-Z0-9]{${CODE_LENGTH}}$`).test(code);

/** Before the phone has scanned the QR. */
export const START = { view: "scan" };

const ROOM = /^[A-Za-z0-9_-]{22}$/;
const KEY = /^[A-Za-z0-9_-]{43}$/;

/**
 * The next state after the phone's [message]:
 * - `scan`: the QR, waiting for the phone.
 * - `code`: the code input; `attemptsLeft` after a wrong one.
 * - `failed`: too many wrong codes; only a new QR helps.
 * - `paired`: `pairing` (room, key, name) to keep; the phone waits for `{ type: "paired" }`.
 */
export function pairingStep(state, message) {
  if (state.view === "failed" || state.view === "paired") return state;
  switch (message?.type) {
    case "askCode":
      // Asked again when the phone reconnects: keep what's on screen.
      return state.view === "code" ? state : { view: "code" };
    case "wrongCode":
      return { view: "code", attemptsLeft: Number(message.attemptsLeft) || 0 };
    case "pairFailed":
      return { view: "failed" };
    case "welcome":
      if (!ROOM.test(message.room) || !KEY.test(message.key)) return state;
      return { view: "paired", pairing: { room: message.room, key: message.key, name: String(message.name || "Teléfono") } };
    default:
      return state;
  }
}
