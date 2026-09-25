import { Whisper } from "../dz";
import { Feature } from "./Feature";

export const NowPlaying: React.FC = () => (
  <Feature
    eyebrow="02 — el reproductor"
    title={<>Preciso, con <Whisper>algo de magia</Whisper></>}
    bullets={[
      "portada como bloque, sin sombras",
      "barra de progreso con cursor de bloque",
      "el artista como susurro en serif",
      "cola, velocidad, temporizador, ecualizador",
    ]}
    screen="dz_now_playing.png"
  />
);
