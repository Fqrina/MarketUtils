# MarketUtils

A Fabric 1.21.10 client-side mod for Hypixel Skyblock that evaluates Auction House listings at a glance.

Last updated: 3 June 2026

## What it does

When you open an Auction House or BIN screen, MarketUtils compares each item's **listing price** against **SkyHanni's estimated item value** and paints a translucent background behind the slot:

- **Green** -- estimated value exceeds listing price (profit). Stronger green = bigger margin.
- **Red** -- listing price exceeds estimated value (loss). Stronger red = bigger loss.
- **No tint** -- price/value data unavailable or break-even.

A debug tooltip line is also appended when hovering: "Worth it! Profit: +X coins" or "Not worth it! Loss: -X coins".

## Requirements

- Fabric Loader >= 0.19.2
- Fabric API 0.138.4+1.21.10
- Minecraft 1.21.10
- Java 21
- SkyHanni (provides the "Estimated Value" tooltip line this mod reads)

## Build

```powershell
.\gradlew build
```

Output: `build\libs\marketutils-1.0.0.jar`

Copy to your Fabric `mods` folder and launch Minecraft.

## Latest fix (3 June 2026)

- Fixed `StackOverflowError` crash caused by tooltip callback recursion.
- Fixed severe FPS drops when opening AH (was evaluating all 54 slots every frame). Now throttled to 3 evaluations per frame with slot-index caching.
- Fixed colored background only appearing on one item (was using identity-based cache keys that broke when `Slot.getItem()` returned different references).
- Tooltip callback now parses already-assembled lines directly instead of re-calling `getTooltipLines()`, eliminating recursion and reducing overhead to near zero.
- Added unconditional `[MarketUtils] Active` tag in tooltips for debugging visibility.

## License

CC0-1.0
