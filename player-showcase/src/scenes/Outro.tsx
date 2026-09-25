import { Backdrop, C, Cursor, Enter, Eyebrow, Mark, MONO, Title, Whisper } from "../dz";

export const Outro: React.FC<{ version: string }> = ({ version }) => (
  <Backdrop>
    <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 34, textAlign: "center" }}>
      <Enter at={0.1} dur={0.6}>
        <Mark size={120} />
      </Enter>
      <Enter at={0.4} dur={0.6} y={12}>
        <Title size={110}>
          player <span style={{ color: C.muted, fontWeight: 500 }}>{version}</span>
          <Cursor />
        </Title>
      </Enter>
      <Enter at={0.9} dur={0.6}>
        <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 56, color: C.ink, letterSpacing: "-0.02em" }}>
          código como grimorio, <Whisper>hecho en linux</Whisper>
        </div>
      </Enter>
      <Enter at={1.5} dur={0.6}>
        <Eyebrow>github.com/danielzunigazb/player</Eyebrow>
      </Enter>
    </div>
  </Backdrop>
);
