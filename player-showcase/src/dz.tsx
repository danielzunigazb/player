// Daniel Zúñiga design system for the showcase: tokens, fonts and the small pieces every
// scene shares. Same values as the app's DzColors / DzType, so the video and the app match.
import React from "react";
import { cancelRender, continueRender, delayRender, Easing, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig } from "remotion";

export const C = {
  bg: "#0e0c0a",
  surface: "#181512",
  line: "#2f2923",
  ink: "#efe6d6",
  muted: "#a2978a",
  gold: "#d6a23e",
  verdigris: "#5cb09c",
  parchmentLine: "#d3c8b4",
};

export const MONO = "JetBrains Mono DZ";
export const SERIF = "Instrument Serif DZ";

// Load the app's own font files so the video never falls back to a system face.
const faces: [string, string, string, string][] = [
  [MONO, "jetbrains_mono_regular.ttf", "400", "normal"],
  [MONO, "jetbrains_mono_medium.ttf", "500", "normal"],
  [MONO, "jetbrains_mono_semibold.ttf", "600", "normal"],
  [MONO, "jetbrains_mono_extrabold.ttf", "800", "normal"],
  [SERIF, "instrument_serif_italic.ttf", "400", "italic"],
];
if (typeof document !== "undefined") {
  const handle = delayRender("fonts");
  Promise.all(
    faces.map(([family, file, weight, style]) => {
      const face = new FontFace(family, `url(${staticFile(`fonts/${file}`)})`, { weight, style });
      document.fonts.add(face);
      return face.load();
    }),
  )
    .then(() => continueRender(handle))
    // Surface the real font error instead of a generic delayRender timeout.
    .catch((err) => cancelRender(err));
}

/** Fast and dry, per the system's motion rules: ease-out, no bounce. */
export const EASE = Easing.bezier(0.16, 1, 0.3, 1);

/** 0→1 progress of an entrance that starts at `at` seconds and lasts `dur` seconds. */
export const useEnter = (at: number, dur = 0.5) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return interpolate(frame, [at * fps, (at + dur) * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: EASE,
  });
};

export const Enter: React.FC<{ at: number; dur?: number; y?: number; x?: number; children: React.ReactNode; style?: React.CSSProperties }> = ({
  at,
  dur = 0.5,
  y = 0,
  x = 0,
  children,
  style,
}) => {
  const p = useEnter(at, dur);
  return <div style={{ opacity: p, translate: `${x * (1 - p)}px ${y * (1 - p)}px`, ...style }}>{children}</div>;
};

export const Eyebrow: React.FC<{ children: React.ReactNode; color?: string }> = ({ children, color = C.muted }) => (
  <div style={{ fontFamily: MONO, fontWeight: 500, fontSize: 22, letterSpacing: "0.14em", textTransform: "uppercase", color }}>{children}</div>
);

export const Whisper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span style={{ fontFamily: SERIF, fontStyle: "italic", fontWeight: 400, color: C.gold, letterSpacing: 0, fontSize: "1.14em" }}>{children}</span>
);

/** The terminal's block cursor: gold, hard 1.1s blink. */
export const Cursor: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const on = Math.floor(frame / (0.55 * fps)) % 2 === 0;
  return (
    <span
      style={{ display: "inline-block", width: "0.5em", height: "0.82em", marginLeft: "0.12em", verticalAlign: "-0.04em", background: C.gold, opacity: on ? 1 : 0 }}
    />
  );
};

export const Title: React.FC<{ children: React.ReactNode; size?: number }> = ({ children, size = 92 }) => (
  <div style={{ fontFamily: MONO, fontWeight: 800, fontSize: size, lineHeight: 1.02, letterSpacing: "-0.035em", color: C.ink }}>{children}</div>
);

export const Hairline: React.FC<{ at: number }> = ({ at }) => {
  const p = useEnter(at, 0.5);
  return <div style={{ height: 1, width: "100%", background: C.line, transform: `scaleX(${p})`, transformOrigin: "left" }} />;
};

/** An app screenshot as a hard-edged panel with a 1px hairline, no device frame, no shadow. */
export const Screen: React.FC<{ src: string; left: number; at?: number; border?: string; top?: number; height?: number }> = ({
  src,
  left,
  at = 0.3,
  border = C.line,
  top = 90,
  height = 900,
}) => {
  const p = useEnter(at, 0.8);
  return (
    <div
      style={{
        position: "absolute",
        left,
        top,
        height,
        border: `1px solid ${border}`,
        overflow: "hidden",
        background: C.surface,
        opacity: p,
        translate: `0px ${60 * (1 - p)}px`,
      }}
    >
      <Img src={staticFile(`img/${src}`)} style={{ display: "block", height: "100%" }} />
    </div>
  );
};

export type Bullet = string | { text: string; color: string };

/** Staggered list; items may carry their own colour, and the gold arrow can be turned off. */
export const Bullets: React.FC<{ items: Bullet[]; at: number; arrow?: boolean }> = ({ items, at, arrow = true }) => (
  <div>
    {items.map((item, i) => {
      const { text, color } = typeof item === "string" ? { text: item, color: C.ink } : item;
      return (
        <Enter key={text} at={at + i * 0.3} dur={0.4} x={-10}>
          <div style={{ fontFamily: MONO, fontSize: 30, lineHeight: arrow ? 1.9 : 1.7, color }}>
            {arrow && <span style={{ color: C.gold }}>→ </span>}
            {text}
          </div>
        </Enter>
      );
    })}
  </div>
);

/** Muted body paragraph. */
export const Paragraph: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div style={{ fontFamily: MONO, fontSize: 30, lineHeight: 1.6, color: C.muted }}>{children}</div>
);

/** Left text column shared by the feature scenes. */
export const LeftColumn: React.FC<{ width?: number; children: React.ReactNode }> = ({ width = 900, children }) => (
  <div style={{ position: "absolute", left: 150, top: 0, bottom: 0, width, display: "flex", flexDirection: "column", justifyContent: "center", gap: 28 }}>
    {children}
  </div>
);

/** The ñ monogram: cut out of a gold block, legs bleeding off the bottom edge. */
export const Mark: React.FC<{ size: number }> = ({ size }) => {
  const id = React.useId().replace(/[^a-zA-Z0-9]/g, "");
  return (
    <svg width={size} height={size} viewBox="0 0 512 512">
      <defs>
        <mask id={id}>
          <rect width="512" height="512" fill="#fff" />
          <path
            fill="#000"
            d="M91.5 596.5V211.5H193V285H223.8L193 309.5Q193 260.5 221 232.5Q249 204.5 296.6 204.5Q352.6 204.5 386.5 243.7Q420.5 282.9 420.5 348V596.5H315.5V358.5Q315.5 328.4 299.8 312Q284 295.5 255.3 295.5Q227.3 295.5 211.9 312Q196.5 328.4 196.5 358.5V596.5ZM307.8 152Q285.4 152 271.8 145.7Q258.1 139.4 249 131Q239.9 122.6 231.9 116.3Q223.8 110 213.3 110Q204.2 110 198.2 116.3Q192.3 122.6 192.3 133.1V148.5H132.8V124Q132.8 87.6 154.2 63.8Q175.5 40 209.8 40Q232.2 40 245.8 46.3Q259.5 52.6 268.6 61Q277.7 69.4 285.8 75.7Q293.8 82 304.3 82Q314.1 82 319.7 76.8Q325.3 71.5 325.3 62.4V43.5H384.8V68Q384.8 104.4 363.8 128.2Q342.8 152 307.8 152Z"
          />
        </mask>
      </defs>
      <rect width="512" height="512" fill={C.gold} mask={`url(#${id})`} />
    </svg>
  );
};

/** Faint drifting grid behind every scene. */
export const Backdrop: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const frame = useCurrentFrame();
  return (
    <div style={{ position: "absolute", inset: 0, background: C.bg, overflow: "hidden", fontFamily: MONO }}>
      <div
        style={{
          position: "absolute",
          inset: "-120px 0 0 0",
          backgroundImage: `linear-gradient(${C.line} 1px, transparent 1px), linear-gradient(90deg, ${C.line} 1px, transparent 1px)`,
          backgroundSize: "120px 120px",
          opacity: 0.18,
          translate: `0px ${-((frame * 0.2) % 120)}px`,
        }}
      />
      {children}
    </div>
  );
};
