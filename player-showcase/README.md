# player-showcase

Video de presentación de Player, hecho con [Remotion](https://remotion.dev) y el sistema de diseño Daniel Zúñiga
(Obsidiana, oro, JetBrains Mono + Instrument Serif, la ñ).

- `src/dz.tsx`: tokens, fuentes (las mismas `.ttf` de la app, en `public/fonts`) y piezas compartidas.
- `src/scenes/`: una escena por archivo; `src/PlayerShowcase.tsx` las une con fundidos cortos.
- `public/img`: capturas reales de la app, renderizadas con Robolectric.

```console
npm ci
npm run dev                                   # Remotion Studio
npx remotion render PlayerShowcase out/PlayerShowcase.mp4 --codec=h264 --crf=18
```

Si Remotion no puede descargar su Chrome, pásale uno local con `--browser-executable=/ruta/a/chrome-headless-shell`.
