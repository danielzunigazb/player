# Relay del monitor web

Worker de Cloudflare que junta a un teléfono con los navegadores vinculados a él (un Durable
Object por sala) y les pasa los mensajes. Van cifrados de punta a punta: el relay no puede leerlos
y no guarda nada. El protocolo completo está en [`docs/monitor.md`](../docs/monitor.md).

- `src/room.js`: las reglas (salas, roles, límites, presencia), sin APIs de Cloudflare.
- `src/index.js`: el Worker y el Durable Object `Room`.
- `npm test`: pruebas de las reglas con `node --test`.
- `npm run dev`: el relay en local (`ws://localhost:8787`).

Cloudflare lo despliega solo desde `main` cuando cambia esta carpeta (Workers Builds), con
`npm run deploy`, que corre las pruebas antes de desplegar.
