// The web monitor: pairs with a phone through a QR, then shows what it plays and sends it
// commands. Protocol in docs/monitor.md.

import { formatTime, nextRepeat, positionNow } from "./format.js";
import { Link } from "./link.js";
import { START, codeHint, normalizeCode, pairingStep } from "./pairing.js";
import { RELAY, newKey, newRoom, pairLink } from "./protocol.js";
import { applyMove, canReorder, moveCommand, stillApplies } from "./queue.js";

const STORE = "player-monitor";
const $ = (id) => document.getElementById(id);

function loadPairing() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORE));
    return saved?.room && saved?.key ? saved : null;
  } catch {
    return null;
  }
}

function savePairing(pairing) {
  try {
    localStorage.setItem(STORE, JSON.stringify(pairing));
  } catch {
    // Private window: it works until the tab closes.
  }
}

function forgetPairing() {
  try {
    localStorage.removeItem(STORE);
  } catch {
    // Nothing saved.
  }
}

function show(view) {
  for (const id of ["pair-view", "player-view"]) $(id).hidden = id !== view;
}

// ------------------------------------------------------------------ pairing

function startPairing() {
  show("pair-view");
  const room = newRoom();
  const key = newKey();
  const link = pairLink(room, key);
  const qr = qrcode(0, "M");
  qr.addData(link);
  qr.make();
  $("qr").innerHTML = qr.createSvgTag({ cellSize: 6, margin: 2, scalable: true });
  $("pair-status").textContent = "Escanea el código con la cámara del teléfono.";
  let pairing = START;
  renderCodeForm(pairing);

  const temporary = new Link(RELAY, room, key, {
    onPeers: ({ phone }) => {
      if (phone && pairing.view === "scan") $("pair-status").textContent = "Teléfono encontrado…";
    },
    onMessage: async (message) => {
      pairing = pairingStep(pairing, message);
      renderCodeForm(pairing);
      if (pairing.view === "failed") {
        temporary.stop();
      } else if (pairing.view === "paired") {
        savePairing(pairing.pairing);
        await temporary.send({ type: "paired" });
        // Let the confirmation leave before closing.
        setTimeout(() => {
          temporary.stop();
          startMonitor(loadPairing());
        }, 300);
      }
    },
    onStatus: (status) => {
      if (status === "closed" && pairing.view !== "failed") {
        $("pair-status").textContent = "Sin conexión con el relay, reintentando…";
      }
    },
  });
  $("code-form").onsubmit = (event) => {
    event.preventDefault();
    const code = normalizeCode($("code").value);
    // A code the phone can't have shown gets a hint, not one of the 3 tries.
    const hint = codeHint(code);
    if (hint) {
      $("code-error").textContent = hint;
      return;
    }
    $("code-error").textContent = "";
    temporary.send({ type: "code", code });
  };
  temporary.start();
}

/** The code input, once the phone asks for it (pairing.js has the states). */
function renderCodeForm(pairing) {
  const asking = pairing.view === "code";
  const wasHidden = $("code-form").hidden;
  $("code-form").hidden = !asking;
  if (pairing.view === "scan") $("code").value = "";
  if (asking) {
    $("pair-status").textContent = "";
    // This page's own address, to compare with the one the phone names.
    $("code-host").textContent = (location.host + location.pathname).replace(/\/$/, "");
    $("code-error").textContent = pairing.attemptsLeft
      ? `Ese no es. ${pairing.attemptsLeft === 1 ? "Queda 1 intento" : `Quedan ${pairing.attemptsLeft} intentos`}.`
      : "";
    if (pairing.attemptsLeft) $("code").select();
    if (wasHidden) $("code").focus();
  } else if (pairing.view === "failed") {
    $("pair-status").textContent = "Demasiados intentos: no se vinculó. Recarga la página para un código QR nuevo.";
  } else if (pairing.view === "paired") {
    $("pair-status").textContent = "Vinculado.";
  }
}

// ------------------------------------------------------------------ monitor

let link = null;
let state = null;
let receivedAt = 0;
let phoneHere = false;
let seeking = false;

function send(op, extra = {}) {
  link?.send({ type: "cmd", op, ...extra });
}

function startMonitor(pairing) {
  if (!pairing) return startPairing();
  show("player-view");
  $("phone-name").textContent = pairing.name || "Teléfono";
  link = new Link(RELAY, pairing.room, pairing.key, {
    onPeers: ({ phone }) => {
      const arrived = phone && !phoneHere;
      phoneHere = phone;
      renderPresence();
      if (arrived) send("hello");
    },
    onMessage: (message) => {
      // The first message tells where commands go: the controls turn on.
      renderPresence();
      if (message.type === "state") {
        state = message;
        receivedAt = Date.now();
        render();
      } else if (message.type === "art") {
        if (message.songId === state?.song?.id || !state) {
          $("art").src = message.jpeg ? `data:image/jpeg;base64,${message.jpeg}` : "";
          $("art").hidden = !message.jpeg;
          $("art-empty").hidden = !!message.jpeg;
        }
      } else if (message.type === "results") {
        renderResults(message);
      }
    },
    onStatus: () => renderPresence(),
  });
  link.start();
}

/** Commands need the phone's id (link.phone), known once it has said something. */
const phoneReady = () => phoneHere && !!link?.phone;

function renderPresence() {
  // Until the phone's id is known it would drop commands, so the controls stay off.
  const ready = phoneReady();
  $("presence").textContent = ready ? "conectado" : phoneHere ? "conectando…" : "desconectado · abre Player en el teléfono";
  $("presence").dataset.on = String(ready);
  $("controls").toggleAttribute("inert", !ready);
}

function render() {
  const song = state?.song;
  $("title").textContent = song?.title || "Nada sonando";
  $("artist").textContent = song?.artist || "";
  $("album").textContent = song?.album || "";
  $("play").textContent = state?.playing ? "‖" : "▶";
  $("play").setAttribute("aria-label", state?.playing ? "Pausar" : "Reproducir");
  $("shuffle").dataset.on = String(!!state?.shuffle);
  $("repeat").dataset.on = String(state?.repeat !== "off");
  $("repeat").textContent = state?.repeat === "one" ? "↻1" : "↻";
  $("volume").value = Math.round((state?.volume ?? 0) * 100);
  $("duration").textContent = formatTime(song?.durationMs);
  $("seek").max = song?.durationMs || 1;
  if (!song) {
    $("art").hidden = true;
    $("art-empty").hidden = false;
  }
  renderQueue();
  tick();
}

function tick() {
  if (!seeking) {
    const position = positionNow(state, receivedAt, Date.now());
    $("seek").value = position;
    $("position").textContent = formatTime(position);
  }
}

function renderQueue() {
  // A song being dragged keeps its place in the list until it's dropped; the drop renders.
  if (drag) return;
  const list = $("queue");
  const reorder = canReorder(state);
  // Each state redraws the list: a focused button keeps the focus, on the same song.
  const focused = list.contains(document.activeElement)
    ? { i: document.activeElement.closest("li")?.dataset.i, kind: document.activeElement.dataset.kind }
    : null;
  list.replaceChildren();
  for (const [offset, item] of (state?.queue ?? []).entries()) {
    const row = document.createElement("li");
    const current = state.queueStart + offset === state.index;
    row.dataset.current = String(current);
    row.dataset.i = String(item.i);
    if (reorder) row.append(dragHandle(item, offset));
    const play = document.createElement("button");
    play.dataset.kind = "play";
    play.className = "queue-song";
    play.innerHTML = `<span class="t"></span><span class="a"></span>`;
    play.querySelector(".t").textContent = item.title;
    play.querySelector(".a").textContent = item.artist;
    play.onclick = () => send("skipTo", { index: item.i });
    const remove = document.createElement("button");
    remove.dataset.kind = "remove";
    remove.className = "btn btn-ghost";
    remove.textContent = "×";
    remove.setAttribute("aria-label", `Quitar ${item.title} de la cola`);
    remove.onclick = () => send("remove", { index: item.i });
    // The song that's playing stays, like in the app's queue (the phone ignores it anyway).
    remove.hidden = current;
    row.append(play, remove);
    list.append(row);
  }
  if (focused) focusInQueue(focused.i, focused.kind);
  $("queue-hint").hidden = !(state?.shuffle && (state.queue?.length ?? 0) > 1);
  $("queue-count").textContent = state?.queueTotal ? `${state.queueTotal}` : "";
}

const focusInQueue = (i, kind) => $("queue").querySelector(`li[data-i="${i}"] [data-kind="${kind}"]`)?.focus();

// ------------------------------------------------------------------ reordering the queue

/** The song being dragged: its row, where it started and the state it was dragged in. */
let drag = null;

function dragHandle(item, offset) {
  const handle = document.createElement("button");
  handle.className = "btn btn-ghost queue-drag";
  handle.textContent = "⠿";
  handle.title = "Arrastra, o usa ↑ ↓";
  handle.setAttribute("aria-label", `Mover ${item.title} en la cola`);
  handle.dataset.kind = "drag";
  handle.onpointerdown = (event) => startDrag(event, offset);
  handle.onkeydown = (event) => {
    const to = offset + ({ ArrowUp: -1, ArrowDown: 1 }[event.key] ?? 0);
    if (to === offset || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    event.preventDefault();
    if (to < 0 || to >= state.queue.length || !phoneReady()) return;
    // The song takes the place of the one at [to]: the focus goes with it.
    const landing = state.queue[to].i;
    moveInQueue(state, offset, to);
    focusInQueue(landing, "drag");
  };
  return handle;
}

function startDrag(event, offset) {
  if (event.button !== 0 || drag || !canReorder(state) || !phoneReady()) return;
  // No text selection, and on touch screens the page doesn't scroll instead.
  event.preventDefault();
  const list = $("queue");
  // On the list, which stays put while its rows move around.
  list.setPointerCapture(event.pointerId);
  drag = { pointerId: event.pointerId, from: offset, row: event.currentTarget.closest("li"), seen: state };
  drag.row.dataset.dragging = "true";
  list.dataset.dragging = "true";
}

$("queue").onpointermove = (event) => {
  if (event.pointerId !== drag?.pointerId) return;
  const list = event.currentTarget;
  // Near an edge, the list scrolls to the songs out of view.
  const box = list.getBoundingClientRect();
  if (event.clientY < box.top + 32) list.scrollTop -= 12;
  else if (event.clientY > box.bottom - 32) list.scrollTop += 12;
  // The row goes before the first other row whose middle is below the pointer.
  const before =
    [...list.children].find((row) => {
      if (row === drag.row) return false;
      const rect = row.getBoundingClientRect();
      return event.clientY < rect.top + rect.height / 2;
    }) ?? null;
  if (drag.row.nextElementSibling !== before) list.insertBefore(drag.row, before);
};

function endDrag(drop) {
  if (!drag) return;
  const list = $("queue");
  const { pointerId, from, row, seen } = drag;
  const to = [...list.children].indexOf(row);
  drag = null;
  delete list.dataset.dragging;
  if (list.hasPointerCapture(pointerId)) list.releasePointerCapture(pointerId);
  if (drop && to !== from) moveInQueue(seen, from, to);
  else renderQueue();
}

$("queue").onpointerup = (event) => event.pointerId === drag?.pointerId && endDrag(true);
// The browser took the pointer, or the window went to the background mid-drag: no drop then.
$("queue").onpointercancel = (event) => event.pointerId === drag?.pointerId && endDrag(false);
$("queue").onlostpointercapture = (event) => event.pointerId === drag?.pointerId && endDrag(false);
window.addEventListener("blur", () => endDrag(false));

/**
 * Moves the song shown at [from] to [to] in the queue of [seen], the state the person looked at.
 * The list shows the move right away; the phone's next state confirms it.
 */
async function moveInQueue(seen, from, to) {
  const command = moveCommand(seen, from, to);
  if (!command) return renderQueue();
  const before = state;
  // Unless the list changed there meanwhile: then the phone's next state shows where it went.
  if (stillApplies(state, seen, command)) state = applyMove(state, command);
  renderQueue();
  const shown = state;
  const { op, ...extra } = command;
  if (!(await link?.send({ type: "cmd", op, ...extra })) && state === shown) {
    // Not connected: the phone never got it.
    state = before;
    renderQueue();
  }
}

function renderResults(message) {
  if (message.query !== $("search").value.trim()) return;
  const list = $("results");
  list.replaceChildren();
  if (!message.songs.length) {
    const empty = document.createElement("li");
    empty.className = "empty";
    empty.textContent = "Nada coincide.";
    list.append(empty);
    return;
  }
  const ids = message.songs.map((s) => s.id);
  for (const [index, song] of message.songs.entries()) {
    const row = document.createElement("li");
    const play = document.createElement("button");
    play.className = "queue-song";
    play.innerHTML = `<span class="t"></span><span class="a"></span>`;
    play.querySelector(".t").textContent = song.title;
    play.querySelector(".a").textContent = `${song.artist} · ${formatTime(song.durationMs)}`;
    play.onclick = () => send("playSongs", { ids, index });
    const next = document.createElement("button");
    next.className = "btn btn-ghost";
    next.textContent = "↳";
    next.title = "A continuación";
    next.onclick = () => send("playNext", { ids: [song.id] });
    const add = document.createElement("button");
    add.className = "btn btn-ghost";
    add.textContent = "+";
    add.title = "Al final de la cola";
    add.onclick = () => send("addToQueue", { ids: [song.id] });
    row.append(play, next, add);
    list.append(row);
  }
}

// ------------------------------------------------------------------ wiring

$("play").onclick = () => send(state?.playing ? "pause" : "play");
$("next").onclick = () => send("next");
$("previous").onclick = () => send("previous");
$("shuffle").onclick = () => send("shuffle", { on: !state?.shuffle });
$("repeat").onclick = () => send("repeat", { mode: nextRepeat(state?.repeat) });

$("seek").oninput = () => {
  seeking = true;
  $("position").textContent = formatTime(Number($("seek").value));
};
$("seek").onchange = () => {
  seeking = false;
  send("seek", { positionMs: Number($("seek").value) });
};

let volumeTimer = null;
$("volume").oninput = () => {
  clearTimeout(volumeTimer);
  volumeTimer = setTimeout(() => send("volume", { value: Number($("volume").value) / 100 }), 120);
};

let searchTimer = null;
$("search").oninput = () => {
  clearTimeout(searchTimer);
  const query = $("search").value.trim();
  if (!query) return $("results").replaceChildren();
  searchTimer = setTimeout(() => send("search", { query }), 250);
};

$("unpair").onclick = () => {
  endDrag(false);
  link?.stop();
  link = null;
  state = null;
  phoneHere = false;
  forgetPairing();
  startPairing();
};

document.addEventListener("keydown", (event) => {
  if (drag && event.key === "Escape") return endDrag(false);
  if (event.target.closest("input") || event.metaKey || event.ctrlKey) return;
  if ($("controls").hasAttribute("inert")) return;
  if (event.code === "Space") {
    event.preventDefault();
    $("play").click();
  } else if (event.key === "ArrowRight" && event.shiftKey) {
    send("next");
  } else if (event.key === "ArrowLeft" && event.shiftKey) {
    send("previous");
  }
});

setInterval(tick, 500);
startMonitor(loadPairing());
