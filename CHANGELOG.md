# Changelog

## v1.3.0 (2026-07-18)

### ✨ Port to Minecraft 26.x
- **NEW:** Ported to **Minecraft 26.1.2**
- **NEW:** Ported to **Minecraft 26.2**
- Dual build system: `mc-26.1.2/` and `mc-26.2/` folders
- Fully adapted to the new 26.x rendering API (`GuiGraphicsExtractor`, `ActiveTextCollector`, `RenderPipelines`, etc.)

### 🎨 UI Improvements
- **NEW:** Smooth marquee text scrolling with proper scissor clipping (no more text overflowing bounds)
- **NEW:** Semi-transparent gradient background behind the radio menu (matches pause menu style)
- **IMPROVED:** Larger album artwork in the bottom bar (45px → 55px)
- **IMPROVED:** Station favicons now render correctly in the station list
- **FIX:** Album artwork now displays properly via `RenderPipelines.GUI_TEXTURED`
- **FIX:** Toast icons (album art, favicon, fallback) render correctly

### 🔧 Technical Changes
- Adapted `KeyMapping` registration for 26.x (no longer uses `KeyBindingHelper`)
- Switched from `ResourceLocation` to `Identifier`
- Replaced `GuiGraphics` with `GuiGraphicsExtractor` API
- Text rendering via `ActiveTextCollector.accept()` instead of `drawString()`
- Icons and textures rendered via `RenderPipelines.GUI_TEXTURED`
- Removed unused `songWidget` dead code

### 🐛 Bug Fixes
- Fix: station list text was invisible (adapted to 26.x text rendering)
- Fix: toasts had no text (adapted to 26.x toast rendering)
- Fix: scrolling text overflow (implemented scissor via `Parameters.withScissor()`)
- Fix: colored/black squares instead of icons (fixed UV coordinates for 26.x `blit()`)

---

## v1.2.0
- **NEW:** Album art fetched automatically from iTunes API — displayed in toast and main menu
- **NEW:** Song history screen (last 20 songs) — click any entry to copy to clipboard
- **NEW:** Toast size selector in settings (Small / Medium / Large)
- **NEW:** Export & Import favorites as JSON (`config/radiomod_favorites_export.json`)
- **FIX:** Station icons now support JPEG, WEBP and ICO formats (previously only PNG worked)
- **FIX:** Crash (NullPointerException) when icon download failed
- **IMPROVED:** Main menu button layout — evenly spaced and aligned

## v1.0.0
- Initial release
