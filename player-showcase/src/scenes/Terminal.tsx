import { interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { C, MONO, Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

type Kind = "in" | "ok" | "dim" | "out";
type Line = { at: number; kind: Kind; text: string };

const TYPE_CPS = 16;
const SONG = { title: "De Música Ligera", artist: "Soda Stereo", durationSec: 211 };

// A session whose output matches Shell.kt line for line. `now` prints a snapshot taken when
// it runs: `seek 1:00` lands at t=3.5, so at t=4.5 the song is at 1:01, when LRCLIB's synced
// lyrics are on "De música ligera" (0:57.02 → 1:01.99).
const NOW_AT = 4.5;
const POSITION_AT_NOW = 60 + (NOW_AT - 3.5);
const bar = (fraction: number) => {
  const cells = Math.floor(Math.min(1, fraction) * 20);
  return "[" + "█".repeat(cells) + "░".repeat(20 - cells) + "]";
};
const clock = (sec: number) => `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, "0")}`;

const SESSION: Line[] = [
  { at: 1.0, kind: "in", text: "play soda stereo" },
  { at: 2.2, kind: "ok", text: "▶ Soda Stereo · 3 canciones" },
  { at: 2.8, kind: "in", text: "seek 1:00" },
  { at: 3.5, kind: "ok", text: "→ 1:00" },
  { at: 4.1, kind: "in", text: "now" },
  { at: NOW_AT, kind: "out", text: SONG.title },
  { at: NOW_AT + 0.05, kind: "dim", text: SONG.artist },
  {
    at: NOW_AT + 0.1,
    kind: "out",
    text: `${bar(POSITION_AT_NOW / SONG.durationSec)} ${clock(POSITION_AT_NOW)} / ${clock(SONG.durationSec)}`,
  },
  { at: NOW_AT + 0.15, kind: "dim", text: "▶" },
  { at: NOW_AT + 0.2, kind: "ok", text: "♪ De música ligera" },
  { at: 5.2, kind: "in", text: "sleep 30m" },
  { at: 5.9, kind: "ok", text: "☾ pausa en 30 min" },
  { at: 6.5, kind: "in", text: "sudo play eres" },
  { at: 7.6, kind: "dim", text: "no hace falta root aquí, brother." },
  { at: 7.7, kind: "ok", text: "▶ Eres — Café Tacvba" },
];

const COLORS: Record<Kind, string> = { in: C.ink, out: C.ink, ok: C.verdigris, dim: C.muted };

export const Terminal: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;

  return (
    <Feature
      eyebrow="04 — la terminal"
      title={<>Desde un <Whisper>prompt</Whisper></>}
      width={980}
      screens={<Screen src="terminal.png" left={1260} />}
    >
      <div style={{ fontFamily: MONO, fontSize: 26, lineHeight: 1.5, whiteSpace: "pre" }}>
        {SESSION.filter((l) => t >= l.at).map((l) => {
          if (l.kind === "in") {
            const typed = l.text.slice(0, Math.floor((t - l.at) * TYPE_CPS));
            return (
              <div key={l.at} style={{ color: C.ink, marginTop: l.at > 1 ? 8 : 0 }}>
                <span style={{ color: C.gold }}>$ </span>
                {typed}
              </div>
            );
          }
          const appear = interpolate(t, [l.at, l.at + 0.15], [0, 1], { extrapolateRight: "clamp" });
          return (
            <div key={l.at} style={{ opacity: appear, color: COLORS[l.kind] }}>
              {l.text}
            </div>
          );
        })}
      </div>
    </Feature>
  );
};
