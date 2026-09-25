import { Backdrop, C, Enter, Eyebrow, LeftColumn, MONO, Title, Whisper } from "../dz";

const TAGS: [string, string?][] = [
  ["android auto", C.gold],
  ["widget"],
  ["ecualizador · bass boost"],
  ["temporizador"],
  ["cola reordenable"],
  ["playlists inteligentes"],
  ["retoma donde quedaste"],
  ["búsqueda por voz"],
  ["72 tests · ci verde", C.verdigris],
];

export const Extras: React.FC = () => (
  <Backdrop>
    <LeftColumn width={1600}>
      <Enter at={0.15}>
        <Eyebrow>06 — y además</Eyebrow>
      </Enter>
      <Enter at={0.3} dur={0.6} y={16}>
        <Title>
          Hecho para <Whisper>usarse a diario</Whisper>
        </Title>
      </Enter>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 18, maxWidth: 1500 }}>
        {TAGS.map(([label, color], i) => (
          <Enter key={label} at={0.8 + i * 0.15} dur={0.3}>
            <div
              style={{
                fontFamily: MONO,
                border: `1px solid ${color ?? C.line}`,
                color: color ?? C.muted,
                padding: "14px 24px",
                fontSize: 26,
                letterSpacing: "0.14em",
                textTransform: "uppercase",
              }}
            >
              {label}
            </div>
          </Enter>
        ))}
      </div>
    </LeftColumn>
  </Backdrop>
);
