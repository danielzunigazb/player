import { Backdrop, Bullet, Bullets, Enter, Eyebrow, Hairline, LeftColumn, Title } from "../dz";

/**
 * Layout every content scene shares: numbered eyebrow, title with whisper, hairline, then
 * bullets and/or custom content, with optional app screens on the right.
 */
export const Feature: React.FC<{
  eyebrow: string;
  title: React.ReactNode;
  titleSize?: number;
  width?: number;
  bullets?: Bullet[];
  screens?: React.ReactNode;
  children?: React.ReactNode;
}> = ({ eyebrow, title, titleSize, width, bullets, screens, children }) => (
  <Backdrop>
    <LeftColumn width={width}>
      <Enter at={0.15}>
        <Eyebrow>{eyebrow}</Eyebrow>
      </Enter>
      <Enter at={0.3} dur={0.6} y={16}>
        <Title size={titleSize}>{title}</Title>
      </Enter>
      <Hairline at={0.6} />
      {bullets && <Bullets items={bullets} at={0.9} />}
      {children}
    </LeftColumn>
    {screens}
  </Backdrop>
);
