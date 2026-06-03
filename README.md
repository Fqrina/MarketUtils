# MarketUtils

A Fabric 1.21.10 client-side mod for Hypixel Skyblock that evaluates Auction House listings at a glance.

Last updated: 3 June 2026

## What it does

When you open an Auction House or BIN screen, MarketUtils compares each item's **listing price** against **SkyHanni's estimated item value** and draws a colored **border** around each slot:

| Border Color | Meaning |
|-------------|---------|
| Deep green | BIN price is far below estimated value (great deal, 50%+ margin) |
| Light green | BIN price is moderately below estimated value |
| Yellow | BIN price is within 3% of estimated value (fair price) |
| Orange | BIN price is moderately above estimated value |
| Deep red | BIN price is far above estimated value (bad deal, 50%+ overpay) |

The border is drawn ON TOP of the slot, so SkyHanni's rarity backgrounds (pink for mythic, yellow for legendary, blue for rare, etc.) remain fully visible in the center. No overlap, no confusion.

A tooltip line is also appended when hovering, showing the exact percentage and coin difference.

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

## Changelog

### v1.0.0-rc2 (3 June 2026)
- Changed from full-slot fill to 2px border rendering (no longer hides SkyHanni rarity backgrounds)
- Changed to percentage-based color gradation (green -> yellow -> red based on price vs estimated value ratio)
- Border draws at TAIL of renderSlot (on top of rarity backgrounds, at the edges only)
- Tooltip now shows percentage deviation and coin amount

### v1.0.0-rc1 (3 June 2026)
- Fixed `StackOverflowError` crash caused by tooltip callback recursion
- Fixed severe FPS drops (was evaluating all 54 slots every frame)
- Fixed colored background only appearing on one item
- Slot-index caching with frame-throttled evaluation (max 3/frame)
- Added `[MarketUtils] Active` debug tag in tooltips

## License

CC0-1.0
