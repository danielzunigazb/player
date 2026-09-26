import { Bullets, C, Enter, Paragraph, Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

export const Share: React.FC = () => (
  <Feature
    eyebrow="05 — compartir"
    title={<>Comparte <Whisper>lo que suena</Whisper></>}
    width={680}
    titleSize={80}
    screens={
      <>
        <Screen src="share_song.png" left={880} />
        <Screen src="share_lyrics.png" left={1400} at={0.6} />
      </>
    }
  >
    <Bullets
      at={0.9}
      items={[
        { text: "historias: ig · snap · whatsapp", color: C.ink },
        { text: "hasta 4 líneas de letra", color: C.muted },
        { text: "share · share lyric", color: C.verdigris },
      ]}
    />
    <Enter at={2.1}>
      <Paragraph>solo sale la imagen, nunca el audio.</Paragraph>
    </Enter>
  </Feature>
);
