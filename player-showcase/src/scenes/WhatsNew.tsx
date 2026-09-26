import { C, Enter, MONO, Screen, Whisper } from "../dz";
import { Feature } from "./Feature";

export type NewsItem = { title: string; detail: string };

/**
 * What changed in this version, read by `npm run render` from docs/releases/v<version>.md
 * (one item per "###" section), so every release's video opens with its own news.
 */
export const WhatsNew: React.FC<{ version: string; items: NewsItem[] }> = ({ version, items }) => (
  <Feature
    eyebrow={`novedades · ${version}`}
    title={<>Lo nuevo en <Whisper>{version}</Whisper></>}
    width={1080}
    screens={<Screen src="settings.png" left={1300} at={0.6} />}
  >
    <div style={{ display: "flex", flexDirection: "column", gap: 26, marginTop: 10 }}>
      {items.map((item, i) => (
        <Enter key={item.title} at={0.9 + i * 0.35} dur={0.4} x={-12}>
          <div style={{ fontFamily: MONO, display: "flex", gap: 22, alignItems: "baseline" }}>
            <span style={{ color: C.gold, fontSize: 26, minWidth: 44 }}>{String(i + 1).padStart(2, "0")}</span>
            <div>
              <div style={{ color: C.ink, fontSize: 36, fontWeight: 600, letterSpacing: "-0.01em" }}>{item.title}</div>
              <div style={{ color: C.muted, fontSize: 24, marginTop: 6, maxWidth: 960 }}>{item.detail}</div>
            </div>
          </div>
        </Enter>
      ))}
    </div>
  </Feature>
);
