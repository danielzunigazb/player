import { Bullets, C, Enter, Paragraph, Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

export const Monitor: React.FC = () => (
  <Feature
    eyebrow="06 — monitor web"
    title={<>Manéjalo <Whisper>desde la PC</Whisper></>}
    width={600}
    titleSize={74}
    screens={<Screen src="monitor.png" left={800} top={215} height={650} />}
  >
    <Bullets
      at={0.9}
      items={[
        { text: "escanea un qr y listo", color: C.ink },
        { text: "controles, cola y búsqueda", color: C.muted },
        { text: "arrastra para ordenar la cola", color: C.muted },
        { text: "cifrado de punta a punta", color: C.verdigris },
      ]}
    />
    <Enter at={2.1}>
      <Paragraph>el teléfono lejos, la música en tus audífonos.</Paragraph>
    </Enter>
  </Feature>
);
