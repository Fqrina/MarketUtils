# MarketUtils -- Mod Direction Notes

Last updated: 3 June 2026


## What the mod actually does

MarketUtils is a small Fabric 1.21.10 client-side mod that helps you decide, at a glance, whether an item on the Hypixel Skyblock Auction House is worth buying.

When you open any screen whose title contains "Auction" or "BIN", the mod reads the tooltip of every non-player-inventory slot, pulls out two numbers -- the **listing price** (BIN price, starting bid, current bid) and the **estimated item value** (added by SkyHanni or similar mods) -- and paints a translucent background behind the item:

| Color | Meaning |
|-------|---------|
| Green tint | Estimated value > listing price. The bigger the profit, the more opaque the green. |
| Red tint   | Estimated value < listing price. The bigger the loss, the more opaque the red. |
| No tint    | Either both values are missing, or value equals price. |

A debug line is also appended to the tooltip itself ("Worth it! Profit: +X coins" or "Not worth it! Loss: -X coins") so you can sanity-check the numbers without guessing. An unconditional `[MarketUtils] Active` tag is appended to every tooltip for debugging visibility.


## How the pieces fit together

```
MarketutilsClient (entrypoint)
  -- registers ItemTooltipCallback
       -> ProfitRenderer.appendTooltipText()
          (parses already-assembled lines, no getTooltipLines call)

AbstractContainerScreenMixin (Mixin)
  -- @Inject into renderSlot() at HEAD
       -> ProfitRenderer.renderSlotBackground()
          (throttled evaluation with slot-index cache)
  -- @Inject into init() at TAIL
       -> ProfitRenderer.clearCache()
  -- @Inject into onClose() at HEAD
       -> ProfitRenderer.clearCache()

ProfitRenderer (core logic)
  -- HashMap<Integer, SlotProfitEntry> cache keyed by slot index
  -- Display-name fingerprint for staleness detection on page navigation
  -- Frame-throttled evaluation (max 3 per frame, ~18 frames to fill all slots)
  -- Boolean recursion guard (plain boolean, not ThreadLocal -- all GUI is single-threaded)
  -- appendTooltipText(): parses provided lines directly, appends debug text
  -- renderSlotBackground(): reads cache or evaluates with getTooltipLines()
  -- computeTintColor(): maps profit/loss delta to ARGB with variable alpha

PriceParser (utility)
  -- stripFormatting(): removes Minecraft section-sign color codes
  -- parsePrice(): pre-compiled regex, extracts numbers with K/M/B suffixes
```

### File locations

| File | Purpose |
|------|---------|
| [MarketutilsClient.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/MarketutilsClient.java) | Fabric client entrypoint, registers tooltip callback |
| [ProfitRenderer.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/render/ProfitRenderer.java) | Core profit evaluation, slot-index cache, tint rendering, tooltip text |
| [PriceParser.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/util/PriceParser.java) | Formatting strip and number extraction |
| [AbstractContainerScreenMixin.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/mixin/AbstractContainerScreenMixin.java) | Mixin that hooks renderSlot, init, and onClose |


## Lessons learned the hard way

1. **Tooltip recursion.** Calling `stack.getTooltipLines()` inside an `ItemTooltipCallback` triggers the callback again. The original fix was a `ThreadLocal<Boolean>` guard inside `evaluateItemProfit`, but the guard was in the wrong place -- it only protected one code path while re-entry happened through another. The correct fix: guard both public entry points (`appendTooltipText` and `renderSlotBackground`) with a single boolean, and have the tooltip callback parse the already-provided lines instead of calling `getTooltipLines()`.

2. **Identity-based caching fails on Hypixel.** Using `System.identityHashCode(ItemStack)` as cache key only works if `Slot.getItem()` returns the same reference each frame. Hypixel's custom inventory implementation (and some Fabric API layers) can return different ItemStack instances per call. Slot-index keying with a display-name fingerprint for staleness is stable regardless.

3. **Frame-throttled evaluation.** Evaluating all 54 AH slots in a single frame (each calling `getTooltipLines()`) drops FPS to ~20. Limiting to 3 evaluations per frame spreads the cost across ~18 frames (~300ms at 60fps) with no visible lag.

4. **`computeIfAbsent` is re-entrant-unsafe.** The `WeakHashMap.computeIfAbsent` (and even `ConcurrentHashMap.computeIfAbsent`) has undefined behavior when the mapping function triggers a re-entrant call for the same key. Explicit `get()` then `put()` is always safe.

5. **`meowdding-lib` hooks Component constructors.** Creating `Component.literal(...)` inside a tooltip callback while another mod intercepts `Component.<init>` can create a second layer of recursion that bypasses tooltip-level guards. Guarding at the outermost entry point (before any Component creation) prevents this.

6. **Mixin `@Shadow` fragility.** Shadowing `getTitle()` on `AbstractContainerScreen` can fail at runtime because of obfuscation differences between vanilla, Fabric Loader, and modded launchers. Casting `this` to `Screen` and calling the public method directly is safer and works everywhere.

7. **Gradle network timeouts.** The Fabric Loom toolchain downloads a lot of artifacts. On slower connections, default timeouts cause failures. We bumped `systemProp.org.gradle.internal.http.connectionTimeout` and `socketTimeout` to 120000ms in `gradle.properties`.


## Compliance

The mod is designed to be fully compliant with the Minecraft EULA and Hypixel's rules:

- **100% client-side and visual only.** No packets are sent, no server data is modified, no automation of any kind.
- **No unfair advantage.** The information it displays (listing price and estimated value) is already visible in the tooltip. The mod just adds a colored background summary so you don't have to mentally compare two numbers on every item.
- **Same category as cosmetic overlay mods.** Functionally similar to what SkyHanni, NotEnoughUpdates, and SkyblockAddons already do with price tooltips.
- **No external UI libraries.** The mod uses only vanilla Minecraft rendering (`GuiGraphics.fill()`) and Fabric API's `ItemTooltipCallback`. No UILIBS or third-party rendering frameworks.


## Where to go from here

These are ideas, not commitments. Pick whatever sounds useful in the next session.

### Short-term polish

- **Remove debug tag.** Once tooltip visibility is confirmed, remove the `[MarketUtils] Active` line from `appendTooltipText()`.
- **Config screen.** Let the user toggle the overlay on/off, adjust the alpha range, or set a minimum profit threshold before tinting. Fabric has `ModMenuApi` integration for this.
- **Cache TTL.** The slot cache is invalidated on screen open/close and by fingerprint mismatch. Consider adding a time-based TTL for long browsing sessions where bids change.
- **Edge cases in price parsing.** Some Hypixel listings use unusual formatting. Collect more real tooltip samples and expand the keyword list.

### Medium-term features

- **Profit per slot summary.** A small HUD overlay showing "Total potential profit on this page: X coins" at the bottom of the AH screen.
- **Flip finder mode.** Highlight only items where the profit margin exceeds a user-defined threshold (e.g., >500K profit). Everything else stays un-tinted.
- **Sound alert.** Play a subtle sound when a high-profit item appears on the page, so you don't have to visually scan every slot.
- **Historical price tracking.** Log prices to a local file and show a mini price-history trend in the tooltip (would require a small local SQLite or flat-file database).

### Long-term ideas

- **Multi-source value estimation.** Instead of relying solely on SkyHanni's "Estimated Value" line, query a local cache of average sale prices (pulled from the Hypixel API on a background thread) for a second opinion.
- **Bazaar support.** Extend the overlay to Bazaar buy/sell orders, showing the spread and whether instant-buying vs. creating a buy order is more profitable.
- **Web dashboard.** Export flip history to a simple local HTML page for post-session analysis.


## Build and run

```powershell
# Build the mod jar
.\gradlew build

# Output lands in
# build\libs\marketutils-1.0.0.jar

# Copy to your Fabric mods folder and launch Minecraft
```

The mod requires Fabric Loader for 1.21.10 and Fabric API. It is designed to work alongside SkyHanni (which provides the "Estimated Value" tooltip line the mod reads).


## Git history

The repo has clean incremental commits on the `master` branch. If anything breaks, `git log --oneline` shows the full progression, and any commit can be checked out to roll back.
