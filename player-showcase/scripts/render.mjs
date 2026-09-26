// Renders the showcase with facts read from the repo itself, so the video can't drift from it:
// the version from app/build.gradle.kts, the number of @Test methods in app/src/test and the
// version's news from docs/releases/v<version>.md.
import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const app = new URL("../../app/", import.meta.url).pathname;

const gradle = readFileSync(join(app, "build.gradle.kts"), "utf8");
const version = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
if (!version) throw new Error("versionName not found in app/build.gradle.kts");

const countTests = (dir) =>
  readdirSync(dir).reduce((n, name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return n + countTests(path);
    return name.endsWith(".kt") ? n + (readFileSync(path, "utf8").match(/@Test\b/g) ?? []).length : n;
  }, 0);
const tests = countTests(join(app, "src/test"));

// One item per "###" section of the release notes (install instructions aside): its title and
// the first sentence of its first bullet, without markdown.
const plain = (md) =>
  md
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/[*`_]/g, "")
    .trim();
const firstSentence = (text) => {
  const sentence = text.split(/(?<=\.)\s/)[0].replace(/\.$/, "");
  if (sentence.length <= 90) return sentence;
  return sentence.slice(0, 88).replace(/\s+\S*$/, "") + "…";
};
const parseNews = (md) => {
  const sections = [];
  let current = null;
  for (const line of md.split("\n")) {
    const heading = line.match(/^###\s+(.+)/);
    if (heading) {
      current = /instalar|install/i.test(heading[1]) ? null : { title: plain(heading[1]), bullets: [] };
      if (current) sections.push(current);
      continue;
    }
    const bullet = line.match(/^\s*-\s+(.+)/);
    if (current && bullet) current.bullets.push(bullet[1]);
  }
  return sections
    .filter((s) => s.bullets.length > 0)
    .slice(0, 5)
    .map(({ title, bullets }) => {
      // Bullets led by a bold label ("**Búsqueda**: …") read best as the list of labels.
      const labels = bullets.map((b) => b.match(/^\*\*([^*]+)\*\*\s*[:—-]/)?.[1]).filter(Boolean);
      const detail = labels.length >= 2 ? firstSentence(labels.join(" · ")) : firstSentence(plain(bullets[0]));
      return { title, detail };
    });
};
const notes = new URL(`../../docs/releases/v${version}.md`, import.meta.url).pathname;
const news = existsSync(notes) ? parseNews(readFileSync(notes, "utf8")) : [];

const props = JSON.stringify({ version, tests, news });
const extra = process.argv.slice(2);
console.log(`rendering player ${version} · ${tests} tests · ${news.length} news items`);
for (const item of news) console.log(`  · ${item.title}: ${item.detail}`);
execFileSync(
  "npx",
  ["remotion", "render", "PlayerShowcase", "out/PlayerShowcase.mp4", "--codec=h264", "--crf=18", `--props=${props}`, ...extra],
  { stdio: "inherit" },
);
execFileSync(
  "npx",
  ["remotion", "still", "PlayerShowcase", "out/showcase-thumb.jpg", "--frame=100", "--image-format=jpeg", "--jpeg-quality=90", `--props=${props}`, ...extra],
  { stdio: "inherit" },
);
