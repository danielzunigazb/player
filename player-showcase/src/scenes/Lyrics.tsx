import { Bullets, C, Enter, Paragraph, Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

export const Lyrics: React.FC = () => (
  <Feature
    eyebrow="03 — letras sincronizadas"
    title={<>Cada línea, <Whisper>a tiempo</Whisper></>}
    screens={<Screen src="lyrics_synced.png" left={1260} />}
  >
    <Bullets
      at={0.9}
      arrow={false}
      items={[
        { text: ".lrc junto a la canción", color: C.muted },
        { text: "etiquetas del mp3 / flac", color: C.muted },
        { text: "lrclib.net · gratis, sin cuenta", color: C.verdigris },
      ]}
    />
    <Enter at={2.0}>
      <Paragraph>toca una línea y la canción salta ahí.</Paragraph>
    </Enter>
  </Feature>
);
