import { C, Enter, MONO, Whisper } from "../dz";
import { Feature } from "./Feature";

const SOURCES: [string, string][] = [
  [".lrc junto a la canción", C.muted],
  ["etiquetas del mp3 / flac", C.muted],
  ["lrclib.net · gratis, sin cuenta", C.verdigris],
];

export const Lyrics: React.FC = () => (
  <Feature eyebrow="03 — letras sincronizadas" title={<>Cada línea, <Whisper>a tiempo</Whisper></>} screen="lyrics_synced.png">
    <div>
      {SOURCES.map(([text, color], i) => (
        <Enter key={text} at={0.9 + i * 0.3} dur={0.4} x={-10}>
          <div style={{ fontFamily: MONO, fontSize: 30, lineHeight: 1.7, color }}>{text}</div>
        </Enter>
      ))}
    </div>
    <Enter at={2.0}>
      <div style={{ fontFamily: MONO, fontSize: 30, lineHeight: 1.6, color: C.muted }}>toca una línea y la canción salta ahí.</div>
    </Enter>
  </Feature>
);
