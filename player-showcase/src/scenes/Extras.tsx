import { C, Enter, MONO, Whisper } from "../dz";
import { Feature } from "./Feature";

export const Extras: React.FC<{ tests: number }> = ({ tests }) => {
  const tags: [string, string?][] = [
    ["android auto", C.gold],
    ["español · english"],
    ["etiquetas completadas"],
    ["avisa de versiones nuevas"],
    ["widget"],
    ["ecualizador · bass boost"],
    ["temporizador"],
    ["cola reordenable"],
    ["playlists inteligentes"],
    ["retoma donde quedaste"],
    ["búsqueda por voz"],
    [`${tests} tests · ci verde`, C.verdigris],
  ];
  return (
    <Feature eyebrow="08 — y además" title={<>Hecho para <Whisper>usarse a diario</Whisper></>} width={1600}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 18, maxWidth: 1500 }}>
        {tags.map(([label, color], i) => (
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
    </Feature>
  );
};
