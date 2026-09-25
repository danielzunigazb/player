import { interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { Backdrop, C, Enter, Eyebrow, Hairline, LeftColumn, MONO, Screen, Title, Whisper } from "../dz";

type Line = { at: number; kind: "in" | "ok" | "dim" | "out" | "bar" | "np"; text?: string };

// A real session: each input types at 16 chars/s, then its output lands.
const SESSION: Line[] = [
  { at: 1.0, kind: "in", text: "play soda stereo" },
  { at: 2.2, kind: "ok", text: "▶ Soda Stereo · 3 canciones" },
  { at: 2.8, kind: "in", text: "sleep 30m" },
  { at: 3.5, kind: "ok", text: "☾ pausa en 30 min" },
  { at: 4.1, kind: "in", text: "now" },
  { at: 4.5, kind: "np" },
  { at: 4.6, kind: "bar" },
  { at: 4.8, kind: "ok", text: "♪ De música ligera" },
  { at: 5.6, kind: "in", text: "sudo play eres" },
  { at: 6.6, kind: "dim", text: "no hace falta root aquí, brother." },
];

export const Terminal: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;

  // The `now` progress bar keeps running while the scene is on screen.
  const frac = Math.min(1, (58 + Math.max(0, t - 4.6) * 6) / 211);
  const cells = Math.floor(frac * 20);
  const secs = Math.round(frac * 211);
  const bar = "[" + "█".repeat(cells) + "░".repeat(20 - cells) + "]";

  return (
    <Backdrop>
      <LeftColumn width={980}>
        <Enter at={0.15}>
          <Eyebrow>04 — la terminal</Eyebrow>
        </Enter>
        <Enter at={0.3} dur={0.6} y={16}>
          <Title>
            Desde un <Whisper>prompt</Whisper>
          </Title>
        </Enter>
        <Hairline at={0.6} />
        <div style={{ fontFamily: MONO, fontSize: 28, lineHeight: 1.55, whiteSpace: "pre", color: C.ink }}>
          {SESSION.filter((l) => t >= l.at).map((l) => {
            const appear = interpolate(t, [l.at, l.at + 0.2], [0, 1], { extrapolateRight: "clamp" });
            if (l.kind === "in") {
              const n = Math.min(l.text!.length, Math.floor((t - l.at) * 16));
              return (
                <div key={l.at}>
                  <span style={{ color: C.gold }}>$ </span>
                  {l.text!.slice(0, n)}
                </div>
              );
            }
            if (l.kind === "np")
              return (
                <div key={l.at} style={{ opacity: appear }}>
                  De Música Ligera <span style={{ color: C.muted }}>— Soda Stereo</span>
                </div>
              );
            if (l.kind === "bar")
              return (
                <div key={l.at} style={{ opacity: appear }}>
                  {bar} <span style={{ color: C.muted }}>{`${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, "0")} / 3:31`}</span>
                </div>
              );
            const color = l.kind === "ok" ? C.verdigris : l.kind === "dim" ? C.muted : C.ink;
            return (
              <div key={l.at} style={{ opacity: appear, color }}>
                {l.text}
              </div>
            );
          })}
        </div>
      </LeftColumn>
      <Screen src="terminal.png" left={1260} />
    </Backdrop>
  );
};
