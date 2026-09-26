# player-showcase

Video de presentación de Player, hecho con [Remotion](https://remotion.dev) y el sistema de diseño Daniel Zúñiga
(Obsidiana, oro, JetBrains Mono + Instrument Serif, la ñ).

- `src/dz.tsx`: tokens, fuentes (las mismas `.ttf` de la app, en `public/fonts`) y piezas compartidas.
- `src/scenes/`: una escena por archivo; `src/PlayerShowcase.tsx` las une con fundidos cortos.
- `public/img`: capturas reales de la app, renderizadas con Robolectric.

```console
npm ci
npm run dev                                   # Remotion Studio
npm run render                                # video + miniatura en out/, con la versión y los tests de la app
```

El workflow `.github/workflows/showcase.yml` hace lo mismo en CI y publica el resultado en el release `showcase-v1`.

Si Remotion no puede descargar su Chrome, pásale uno local: `npm run render -- --browser-executable=/ruta/a/chrome-headless-shell`.
