import { linearTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { Extras } from "./scenes/Extras";
import { Intro } from "./scenes/Intro";
import { Library } from "./scenes/Library";
import { Lyrics } from "./scenes/Lyrics";
import { NowPlaying } from "./scenes/NowPlaying";
import { Outro } from "./scenes/Outro";
import { Terminal } from "./scenes/Terminal";
import { Themes } from "./scenes/Themes";

/**
 * Facts about the app shown in the video. `npm run render` fills them from
 * app/build.gradle.kts and the test sources; these defaults only serve Remotion Studio.
 */
export type ShowcaseProps = { version: string; tests: number };
export const DEFAULT_PROPS: ShowcaseProps = { version: "dev", tests: 0 };

export const SCENES: { id: string; frames: number; render: (p: ShowcaseProps) => React.ReactNode }[] = [
  { id: "Intro", frames: 150, render: () => <Intro /> },
  { id: "Library", frames: 180, render: () => <Library /> },
  { id: "NowPlaying", frames: 180, render: () => <NowPlaying /> },
  { id: "Lyrics", frames: 180, render: () => <Lyrics /> },
  { id: "Terminal", frames: 280, render: () => <Terminal /> },
  { id: "Themes", frames: 160, render: () => <Themes /> },
  { id: "Extras", frames: 140, render: (p) => <Extras tests={p.tests} /> },
  { id: "Outro", frames: 150, render: (p) => <Outro version={p.version} /> },
];

/** Short, dry crossfades between scenes, in line with the system's motion rules. */
export const TRANSITION_FRAMES = 12;

export const TOTAL_FRAMES = SCENES.reduce((sum, s) => sum + s.frames, 0) - TRANSITION_FRAMES * (SCENES.length - 1);

export const PlayerShowcase: React.FC<ShowcaseProps> = (props) => (
  <TransitionSeries>
    {SCENES.flatMap(({ id, frames, render }, i) => [
      <TransitionSeries.Sequence key={id} durationInFrames={frames} name={id}>
        {render(props)}
      </TransitionSeries.Sequence>,
      ...(i < SCENES.length - 1
        ? [<TransitionSeries.Transition key={`${id}-fade`} presentation={fade()} timing={linearTiming({ durationInFrames: TRANSITION_FRAMES })} />]
        : []),
    ])}
  </TransitionSeries>
);
