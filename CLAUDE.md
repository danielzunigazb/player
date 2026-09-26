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
