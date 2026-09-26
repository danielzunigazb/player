import { Composition, Folder } from "remotion";
import { DEFAULT_PROPS, PlayerShowcase, SCENES, ShowcaseProps, TOTAL_FRAMES } from "./PlayerShowcase";

const SceneById: React.FC<ShowcaseProps & { id: string }> = ({ id, ...props }) => <>{SCENES.find((s) => s.id === id)!.render(props)}</>;

export const RemotionRoot: React.FC = () => (
  <>
    <Folder name="PlayerShowcase-Scenes">
      {SCENES.map(({ id, frames }) => (
        <Composition
          key={id}
          id={id}
          component={SceneById}
          defaultProps={{ ...DEFAULT_PROPS, id }}
          durationInFrames={frames}
          fps={30}
          width={1920}
          height={1080}
        />
      ))}
    </Folder>
    <Composition
      id="PlayerShowcase"
      component={PlayerShowcase}
      defaultProps={DEFAULT_PROPS}
      durationInFrames={TOTAL_FRAMES}
      fps={30}
      width={1920}
      height={1080}
    />
  </>
);
