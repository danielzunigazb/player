import { C, Enter, Paragraph, Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

export const Themes: React.FC = () => (
  <Feature
    eyebrow="07 — dos temas"
    title={<>Obsidiana <Whisper>y pergamino</Whisper></>}
    titleSize={80}
    width={640}
    screens={
      <>
        <Screen src="dz_home.png" left={840} />
        <Screen src="dz_home_light.png" left={1320} at={0.55} border={C.parchmentLine} />
      </>
    }
  >
    <Enter at={0.8}>
      <Paragraph>oro rúnico como único acento. jetbrains mono como voz. esquinas duras, cero sombras.</Paragraph>
    </Enter>
  </Feature>
);
