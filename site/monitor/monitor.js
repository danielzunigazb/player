// The web monitor: pairs with a phone through a QR, then shows what it plays and sends it
// commands. Protocol in docs/monitor.md.

import { formatTime, nextRepeat, positionNow } from "./format.js";
import { Link } from "./link.js";
import { CODE_LENGTH, START, isCode, normalizeCode, pairingStep } from "./pairing.js";
import { RELAY, newKey, newRoom, pairLink } from "./protocol.js";

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
    if (!isCode(code)) {
      $("code-error").textContent = `El código tiene ${CODE_LENGTH} caracteres.`;
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

function renderPresence() {
  // Commands need the phone's id (link.phone), known once it has said something; until then the
  // phone would drop them, so the controls stay off.
  const ready = phoneHere && !!link?.phone;
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
  const list = $("queue");
  list.replaceChildren();
  for (const [offset, item] of (state?.queue ?? []).entries()) {
    const row = document.createElement("li");
    const current = state.queueStart + offset === state.index;
    row.dataset.current = String(current);
    const play = document.createElement("button");
    play.className = "queue-song";
    play.innerHTML = `<span class="t"></span><span class="a"></span>`;
    play.querySelector(".t").textContent = item.title;
    play.querySelector(".a").textContent = item.artist;
    play.onclick = () => send("skipTo", { index: item.i });
    const remove = document.createElement("button");
    remove.className = "btn btn-ghost";
    remove.textContent = "×";
    remove.setAttribute("aria-label", `Quitar ${item.title} de la cola`);
    remove.onclick = () => send("remove", { index: item.i });
    // The song that's playing stays, like in the app's queue (the phone ignores it anyway).
    remove.hidden = current;
    row.append(play, remove);
    list.append(row);
  }
  $("queue-count").textContent = state?.queueTotal ? `${state.queueTotal}` : "";
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
  link?.stop();
  link = null;
  state = null;
  phoneHere = false;
  forgetPairing();
  startPairing();
};

document.addEventListener("keydown", (event) => {
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
