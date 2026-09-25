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

export const SCENES = [
  { id: "Intro", component: Intro, frames: 150 },
  { id: "Library", component: Library, frames: 180 },
  { id: "NowPlaying", component: NowPlaying, frames: 180 },
  { id: "Lyrics", component: Lyrics, frames: 180 },
  { id: "Terminal", component: Terminal, frames: 250 },
  { id: "Themes", component: Themes, frames: 160 },
  { id: "Extras", component: Extras, frames: 140 },
  { id: "Outro", component: Outro, frames: 150 },
] as const;

/** Short, dry crossfades between scenes, in line with the system's motion rules. */
export const TRANSITION_FRAMES = 12;

export const TOTAL_FRAMES = SCENES.reduce((sum, s) => sum + s.frames, 0) - TRANSITION_FRAMES * (SCENES.length - 1);

export const PlayerShowcase: React.FC = () => (
  <TransitionSeries>
    {SCENES.flatMap(({ id, component: Scene, frames }, i) => [
      <TransitionSeries.Sequence key={id} durationInFrames={frames} name={id}>
        <Scene />
      </TransitionSeries.Sequence>,
      ...(i < SCENES.length - 1
        ? [<TransitionSeries.Transition key={`${id}-fade`} presentation={fade()} timing={linearTiming({ durationInFrames: TRANSITION_FRAMES })} />]
        : []),
    ])}
  </TransitionSeries>
);
