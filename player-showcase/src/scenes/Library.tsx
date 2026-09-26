import { Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

export const Library: React.FC = () => (
  <Feature
    eyebrow="01 — biblioteca"
    title={<>Tu <Whisper>música</Whisper></>}
    bullets={[
      "escaneo local, sin cuentas ni nube",
      "canciones, álbumes, artistas, carpetas",
      "búsqueda que ignora acentos",
      "favoritas, más escuchadas, playlists",
    ]}
    screens={<Screen src="dz_home.png" left={1260} />}
  />
);
