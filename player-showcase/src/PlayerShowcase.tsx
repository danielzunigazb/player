import { linearTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { Extras } from "./scenes/Extras";
import { Intro } from "./scenes/Intro";
import { Library } from "./scenes/Library";
import { Lyrics } from "./scenes/Lyrics";
import { NowPlaying } from "./scenes/NowPlaying";
import { Outro } from "./scenes/Outro";
import { Share } from "./scenes/Share";
import { Terminal } from "./scenes/Terminal";
import { Themes } from "./scenes/Themes";
import { NewsItem, WhatsNew } from "./scenes/WhatsNew";

/**
 * Facts about the app shown in the video. `npm run render` fills them from
 * app/build.gradle.kts, the test sources and docs/releases/; these defaults only serve
 * Remotion Studio.
 */
export type ShowcaseProps = { version: string; tests: number; news: NewsItem[] };
export const DEFAULT_PROPS: ShowcaseProps = {
  version: "dev",
  tests: 0,
  news: [
    { title: "Idiomas", detail: "inglés y español, también en la terminal" },
    { title: "Compartir", detail: "tarjetas 9:16 para historias" },
  ],
};

type Scene = { id: string; frames: number; render: (p: ShowcaseProps) => React.ReactNode; when?: (p: ShowcaseProps) => boolean };

export const SCENES: Scene[] = [
  { id: "Intro", frames: 150, render: () => <Intro /> },
  {
    id: "WhatsNew",
    frames: 150,
    render: (p) => <WhatsNew version={p.version} items={p.news} />,
    when: (p) => p.news.length > 0,
  },
  { id: "Library", frames: 180, render: () => <Library /> },
  { id: "NowPlaying", frames: 180, render: () => <NowPlaying /> },
  { id: "Lyrics", frames: 180, render: () => <Lyrics /> },
  { id: "Terminal", frames: 280, render: () => <Terminal /> },
  { id: "Share", frames: 170, render: () => <Share /> },
  { id: "Themes", frames: 160, render: () => <Themes /> },
  { id: "Extras", frames: 140, render: (p) => <Extras tests={p.tests} /> },
  { id: "Outro", frames: 150, render: (p) => <Outro version={p.version} /> },
];

/** Short, dry crossfades between scenes, in line with the system's motion rules. */
export const TRANSITION_FRAMES = 12;

/** The scenes this render includes: "WhatsNew" only when the version has release notes. */
export const scenesFor = (props: ShowcaseProps) => SCENES.filter((s) => !s.when || s.when(props));

export const totalFrames = (props: ShowcaseProps) => {
  const scenes = scenesFor(props);
  return scenes.reduce((sum, s) => sum + s.frames, 0) - TRANSITION_FRAMES * (scenes.length - 1);
};

export const PlayerShowcase: React.FC<ShowcaseProps> = (props) => {
  const scenes = scenesFor(props);
  return (
    <TransitionSeries>
      {scenes.flatMap(({ id, frames, render }, i) => [
        <TransitionSeries.Sequence key={id} durationInFrames={frames} name={id}>
          {render(props)}
        </TransitionSeries.Sequence>,
        ...(i < scenes.length - 1
          ? [<TransitionSeries.Transition key={`${id}-fade`} presentation={fade()} timing={linearTiming({ durationInFrames: TRANSITION_FRAMES })} />]
          : []),
      ])}
    </TransitionSeries>
  );
};
