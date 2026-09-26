// Renders the showcase with facts read from the app itself, so the video can't drift from it:
// the version from app/build.gradle.kts and the number of @Test methods in app/src/test.
import { execFileSync } from "node:child_process";
import { readdirSync, readFileSync, statSync } from "node:fs";
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

const props = JSON.stringify({ version, tests });
const extra = process.argv.slice(2);
console.log(`rendering player ${version} · ${tests} tests`);
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
