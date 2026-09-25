import { useCurrentFrame, useVideoConfig } from "remotion";
import { Backdrop, C, Cursor, Enter, Eyebrow, Mark, MONO, Title, Whisper, useEnter } from "../dz";

export const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const word = "player";
  const typed = word.slice(0, Math.max(0, Math.min(word.length, Math.floor((frame / fps - 0.9) * 9))));
  const mark = useEnter(0.1, 0.7);
  return (
    <Backdrop>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 34, textAlign: "center" }}>
        <div style={{ opacity: mark, scale: String(0.9 + 0.1 * mark) }}>
          <Mark size={150} />
        </div>
        <Title size={150}>
          {typed}
          <Cursor />
        </Title>
        <Enter at={2.0} dur={0.6} y={12}>
          <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 64, color: C.ink, letterSpacing: "-0.02em" }}>
            un music player <Whisper>a mi medida</Whisper>
          </div>
        </Enter>
        <Enter at={2.8} dur={0.6}>
          <Eyebrow>android · kotlin · jetpack compose · media3</Eyebrow>
        </Enter>
      </div>
    </Backdrop>
  );
};
