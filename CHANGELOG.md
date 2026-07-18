# Changelog

## v1.3.1 (2026-07-18)

### 🐛 Bug Fixes
- **FIX:** Action bar messages now properly display on the HUD overlay instead of chat
  - 26.1.2: `Gui.setOverlayMessage()`
  - 26.2: `Gui.hud.setOverlayMessage()`
- **FIX:** Toast block button (⛔) now works correctly — fixed toast coordinate detection (was hardcoded to 0,0, now reads from `Matrix3x2fStack.m20/m21`)
- **FIX:** Icon cache no longer permanently blocks retries after transient network errors — failed URLs are retried with cooldown (3 attempts, 60s between retries, 5min cooldown after max)
- **FIX:** After all reconnect attempts fail, mod now cleans up station state and shows "Lost connection" message on action bar (no more phantom "playing" state)
- **FIX:** Search results no longer have race conditions — `AtomicInteger` generation token ensures stale responses don't overwrite newer searches

### 🔄 Changes
- **CHANGE:** Toast song text rendering changed from marquee scrolling to static clipped text with ellipsis — marquee (`TextAlignment` + `withScissor`) doesn't work reliably in Toast `extractRenderState` on 26.x due to batched rendering architecture
- **CHANGE:** All toast sizes widened by +40px to show more text — Small 160→200, Medium 190→230, Large 220→260, XL 260→290 (new 4th option)
- **FIX:** Settings screen button overlap — Export/Import Favorites moved to separate row (cy+70), Save & Return to cy+95; 4 size buttons now evenly spaced at 72px each (previously 3×96px)

### ✨ New Features
- **NEW:** Auto-reconnect — when the stream disconnects, the mod automatically retries (3 attempts: 2s, 5s, 15s backoff). Toggle in settings (`Auto reconnect: ON/OFF`)

### 🔧 Technical Changes
- **NEW:** Thread pool (`RADIO_EXECUTOR`) — replaces `new Thread()` and `CompletableFuture.runAsync()` with a shared `Executors.newCachedThreadPool()`
- **NEW:** Shared `HttpClient` pool (`HTTP_CLIENT`) — single `HttpClient` instance reused across all requests (faster searches, keep-alive connections)

---

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
