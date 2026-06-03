# MarketUtils -- Mod Direction Notes

Last updated: 3 June 2026 (v1.0.0-rc3)


## What the mod actually does

MarketUtils is a small Fabric 1.21.10 client-side mod that helps you decide, at a glance, whether an item on the Hypixel Skyblock Auction House is worth buying.

When you open any screen whose title contains "Auction" or "BIN", the mod reads the tooltip of every non-player-inventory slot, pulls out two numbers -- the **listing price** (BIN price, starting bid, current bid) and the **estimated item value** (added by SkyHanni or similar mods) -- and draws a colored **2px border** around the item slot:

| Border Color | Meaning |
|-------------|---------|
| Deep green | BIN is far below estimated value (50%+ profit margin, great deal) |
| Yellow-green | BIN is moderately below estimated value |
| Yellow | BIN is within 3% of estimated value (neutral, fair price) |
| Orange | BIN is moderately above estimated value |
| Deep red | BIN is far above estimated value (50%+ overpay, bad deal) |

The border is drawn at TAIL of renderSlot (after the item icon and SkyHanni's rarity backgrounds), so it sits on top at the thin edges only. The center of the slot remains fully visible for the rarity color (pink mythic, yellow legendary, etc.) and the item icon.

A debug line is appended to the tooltip ("Worth it! 25.3% below value (+1.5M coins profit)") plus an unconditional `[MarketUtils] Active` tag for debugging.


## How the pieces fit together

```
MarketutilsClient (entrypoint)
  -- registers ItemTooltipCallback
       -> ProfitRenderer.appendTooltipText()
          (parses already-assembled lines, no getTooltipLines call)

AbstractContainerScreenMixin (Mixin)
  -- @Inject into renderSlot() at TAIL
       -> ProfitRenderer.renderSlotBackground()
          (draws 2px border on top of item + rarity backgrounds)
  -- @Inject into init() at TAIL
       -> ProfitRenderer.clearCache()
  -- @Inject into onClose() at HEAD
       -> ProfitRenderer.clearCache()

ProfitRenderer (core logic)
  -- HashMap<Integer, SlotProfitEntry> cache keyed by slot index
  -- Display-name fingerprint for staleness detection on page navigation
  -- Frame-throttled evaluation (max 3 per frame)
  -- Boolean recursion guard (plain boolean, single-threaded GUI)
  -- Percentage-based color: green <-> yellow <-> red gradient
  -- 2px border rendering (top/bottom/left/right fill calls)

PriceParser (utility)
  -- stripFormatting(): removes Minecraft section-sign color codes
  -- parsePrice(): pre-compiled regex, extracts numbers with K/M/B suffixes
```

### File locations

| File | Purpose |
|------|---------|
| [MarketutilsClient.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/MarketutilsClient.java) | Fabric client entrypoint, registers tooltip callback |
| [ProfitRenderer.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/render/ProfitRenderer.java) | Core profit evaluation, slot-index cache, border rendering, tooltip text |
| [PriceParser.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/util/PriceParser.java) | Formatting strip and number extraction |
| [AbstractContainerScreenMixin.java](file:///C:/Users/2020d/AI/marketutils-1.21.10/src/client/java/com/marketutils/client/mixin/AbstractContainerScreenMixin.java) | Mixin that hooks renderSlot (TAIL), init (TAIL), onClose (HEAD) |


## Lessons learned the hard way

1. **Tooltip recursion.** Calling `stack.getTooltipLines()` inside an `ItemTooltipCallback` triggers the callback again. The original fix was a `ThreadLocal<Boolean>` guard inside `evaluateItemProfit`, but the guard was in the wrong place -- it only protected one code path while re-entry happened through another. The correct fix: guard both public entry points and have the tooltip callback parse the already-provided lines instead of calling `getTooltipLines()`.

2. **Identity-based caching fails on Hypixel.** Using `System.identityHashCode(ItemStack)` as cache key only works if `Slot.getItem()` returns the same reference each frame. Hypixel's custom inventory implementation can return different ItemStack instances per call. Slot-index keying with a display-name fingerprint for staleness is stable regardless.

3. **Frame-throttled evaluation.** Evaluating all 54 AH slots in a single frame (each calling `getTooltipLines()`) drops FPS to ~20. Limiting to 3 evaluations per frame spreads the cost across ~18 frames (~300ms at 60fps) with no visible lag.

4. **`computeIfAbsent` is re-entrant-unsafe.** `WeakHashMap.computeIfAbsent` and `ConcurrentHashMap.computeIfAbsent` have undefined behavior when the mapping function triggers a re-entrant call for the same key. Explicit `get()` then `put()` is safe.

5. **`meowdding-lib` hooks Component constructors.** Creating `Component.literal(...)` inside a tooltip callback while another mod intercepts `Component.<init>` can create a second layer of recursion. Guarding at the outermost entry point (before any Component creation) prevents this.

6. **Mixin injection point matters for visual layering.** Injecting at HEAD of `renderSlot()` draws BEFORE the item icon and before other mods' rarity backgrounds. Injecting at TAIL draws AFTER everything, so a border sits visibly on top. For compatibility with SkyHanni rarity backgrounds, TAIL is correct.

7. **Absolute vs. percentage-based color.** A 2M coin difference means very different things on a 5M item vs a 100M item. Percentage-based gradation (profit as fraction of estimated value) gives consistent visual meaning regardless of item price tier.

8. **Border vs. full-slot fill.** A full translucent fill overlaps with SkyHanni's rarity backgrounds (pink, yellow, blue), making both hard to read. A thin 2px border at the slot edges avoids the overlap entirely.

9. **Callback ordering between mods.** Fabric's `ItemTooltipCallback` fires callbacks in registration order. If SkyHanni registers after MarketUtils, its "Estimated Item Value:" line is not present in the `lines` list when our callback runs. The fix: have the tooltip callback fall back to the slot cache (populated by `renderSlotBackground`, which calls `getTooltipLines()` independently and sees all mods' lines).

10. **Overly broad label matching.** Using `lowerLine.contains("price:")` to find the BIN price also matches unrelated tooltip lines like "Upgrade Price:" or item lore containing the word "price". This caused garbage values to be parsed as the listing price, producing wrong colors (green when it should be red). Each label pattern must include enough prefix context to avoid false matches.


## Compliance

The mod is designed to be fully compliant with the Minecraft EULA and Hypixel's rules:

- **100% client-side and visual only.** No packets are sent, no server data is modified, no automation of any kind.
- **No unfair advantage.** The information it displays (listing price and estimated value) is already visible in the tooltip. The mod just adds a colored border so you don't have to mentally compare two numbers on every item.
- **Same category as cosmetic overlay mods.** Functionally similar to what SkyHanni, NotEnoughUpdates, and SkyblockAddons already do with price tooltips.
- **No external UI libraries.** Uses only vanilla Minecraft rendering (`GuiGraphics.fill()`) and Fabric API's `ItemTooltipCallback`. No UILIBS or third-party rendering frameworks.


## Where to go from here

These are ideas, not commitments. Pick whatever sounds useful in the next session.

### Short-term polish

- **Remove debug tag.** Once tooltip visibility is confirmed working, remove the `[MarketUtils] Active` line from `appendTooltipText()`.
- **Config screen.** Toggle overlay on/off, adjust border thickness, set custom neutral band width, minimum profit threshold. Fabric `ModMenuApi`.
- **Cache TTL.** Invalidation on screen open/close + fingerprint mismatch handles most cases. A time-based TTL would catch bids that change while the screen stays open.
- **Fine-tune color palette.** The current gradient (green-yellow-red) is functional but could benefit from user testing. Consider making the colors configurable.

### Medium-term features

- **Profit per slot summary.** HUD overlay at the bottom of the AH screen: "Total potential profit on this page: X coins".
- **Flip finder mode.** Only show borders on items exceeding a profit threshold. Everything else gets no border.
- **Sound alert.** Subtle notification when a high-profit item appears.
- **Historical price tracking.** Log prices to a local file, show mini price-history in the tooltip.

### Long-term ideas

- **Multi-source value estimation.** Query local cache of average sale prices from Hypixel API for a second opinion beyond SkyHanni's estimate.
- **Bazaar support.** Extend to Bazaar buy/sell orders and show the spread.
- **Web dashboard.** Export flip history to a local HTML page for post-session analysis.


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
