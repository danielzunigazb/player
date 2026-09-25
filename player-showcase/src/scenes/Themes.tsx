import { Backdrop, C, Enter, Eyebrow, LeftColumn, MONO, Screen, Title, Whisper } from "../dz";

export const Themes: React.FC = () => (
  <Backdrop>
    <LeftColumn width={640}>
      <Enter at={0.15}>
        <Eyebrow>05 — dos temas</Eyebrow>
      </Enter>
      <Enter at={0.3} dur={0.6} y={16}>
        <Title size={80}>
          Obsidiana <Whisper>y pergamino</Whisper>
        </Title>
      </Enter>
      <Enter at={0.8}>
        <div style={{ fontFamily: MONO, fontSize: 30, lineHeight: 1.6, color: C.muted }}>
          oro rúnico como único acento. jetbrains mono como voz. esquinas duras, cero sombras.
        </div>
      </Enter>
    </LeftColumn>
    <Screen src="dz_home.png" left={840} />
    <Screen src="dz_home_light.png" left={1320} at={0.55} border={C.parchmentLine} />
  </Backdrop>
);
