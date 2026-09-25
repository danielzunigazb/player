import { Backdrop, Bullets, Enter, Eyebrow, Hairline, LeftColumn, Screen, Title } from "../dz";

/** Shared layout for a feature: numbered eyebrow, title with whisper, bullets, and the app screen. */
export const Feature: React.FC<{
  eyebrow: string;
  title: React.ReactNode;
  bullets?: string[];
  screen: string;
  children?: React.ReactNode;
}> = ({ eyebrow, title, bullets, screen, children }) => (
  <Backdrop>
    <LeftColumn>
      <Enter at={0.15}>
        <Eyebrow>{eyebrow}</Eyebrow>
      </Enter>
      <Enter at={0.3} dur={0.6} y={16}>
        <Title>{title}</Title>
      </Enter>
      <Hairline at={0.6} />
      {bullets && <Bullets items={bullets} at={0.9} />}
      {children}
    </LeftColumn>
    <Screen src={screen} left={1260} />
  </Backdrop>
);
