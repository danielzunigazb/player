# Player

## Commits y PRs

- Todos los commits van con la identidad del dueño: `danielzunigazb <danielzunigazb@gmail.com>`. Nunca como Claude ni `noreply@anthropic.com`.
- Nada de atribución a la IA en commits: sin `Co-Authored-By: Claude…`, sin `Claude-Session: …`, sin "Generated with Claude Code", sin enlaces de sesión. Tampoco en títulos ni descripciones de PR.
- Lo aplican solos `.claude/settings.json` (hook `SessionStart`: fija `user.name`, `user.email` y `core.hooksPath`) y `.githooks/commit-msg` (borra esas líneas). Si el hook no corrió, antes del primer commit:

  ```sh
  git config user.name danielzunigazb
  git config user.email danielzunigazb@gmail.com
  git config core.hooksPath .githooks
  ```

## Versiones

Todo lo publica CI al mergear a `main`: no hay pasos a mano.

- Para una versión estable: sube `versionName` y `versionCode` en `app/build.gradle.kts` y escribe `docs/releases/vX.Y.Z.md` con secciones `###`. De ahí sale la escena "Novedades" del video: el título de cada sección y su primera oración, o las etiquetas en negrita. Al mergear se publican, en cadena, el release, el video y la web.
- Cualquier otro push a `main` publica el prerelease `dev`.
- Las pantallas del video salen de `ShowcaseShotsTest`. Si cambia una pantalla o se agrega una función visible, actualiza ese test y las escenas de `player-showcase/src/scenes/`.

