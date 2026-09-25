import "./index.css";
import { Composition, Folder } from "remotion";
import { PlayerShowcase, SCENES, TOTAL_FRAMES } from "./PlayerShowcase";

export const RemotionRoot: React.FC = () => (
  <>
    <Folder name="PlayerShowcase-Scenes">
      {SCENES.map(({ id, component, frames }) => (
        <Composition key={id} id={id} component={component} durationInFrames={frames} fps={30} width={1920} height={1080} />
      ))}
    </Folder>
    <Composition id="PlayerShowcase" component={PlayerShowcase} durationInFrames={TOTAL_FRAMES} fps={30} width={1920} height={1080} />
  </>
);
