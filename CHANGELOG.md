# Changelog

[SemVer](https://semver.org). Betas are `X.Y.Z-beta.N` (GitHub pre-releases).

## 0.24.0 (2026-11-15)
- Coins outside shafts: what you mine in the Dwarven Mines between shafts counts in session, day, Stats and HUD coins and Coins / h.
- Coins outside shafts HUD line with Session, Per hour and All-time ranges.
- Outside shafts row at the bottom of a session's shafts, and in the coins tooltips.
- A session lasts until your last mining, not the end of its last shaft.
- Per hour rates stop counting after 2 minutes without mining.
- Hover a session's length to see how much was a break.
- Time shafting on the Stats tab leaves breaks out.
- A session says how many items went to the material stash while you mined.
- Prices stay up to date during long shafts.
- Session Coins tooltip shows where the coins came from, then Earned, Spent and Net.
- Coins tooltips show how much Bazaar tax was taken off.
- Coins breakdown tooltips list the most valuable items first.
- Glossy Gemstones get their own row in the coins breakdown.
- Keys and campfires setting: insta-buy, crafted from your own Enchanted Tungsten or Umber, or not counted.
- A key saved by Resourceful costs nothing and the shaft says so.
- Shaft table's Gems column is now Mined: Fine for gem shafts, metal in your Metal tier for metal shafts.
- Mined amounts in the colour of the gem or metal, without the ≈.
- Hover a Mined value to see the whole amount, f.e. 303.3 Enchanted Tungsten.
- Sort the shafts in a session by clicking a column header.
- Make your own personal bests on the Stats tab: pick a value, per shaft or per session, highest or lowest, and a shaft type.
- Edit personal bests: drag to reorder, remove, rename, or reset to the default list.
- Metal / shaft and Drystreak personal bests, f.e. Jasper dry 9 (longest 18).
- Fastest spawn, Fastest session and Lapis / shaft are no longer in the default list.
- Click anywhere on a personal best to open it.
- Open sessions show your other sessions on the left, click one to switch. On small screens it folds to a strip.
- Back button moved to the top of that list.
- Arrows and chart bars in the journal light up when you hover them.
- Stats chart types (bars or line) are saved.
- Personal best HUD line: show any personal best from the Stats tab, f.e. `{pb:drystreak_jasp}`.
- Typos in `{pb:...}` tags show an error with a suggestion.
- Drystreak HUD line: shafts since your last shaft of a type you pick, f.e. `{dry:jasper}`.
- Corpses looted HUD line per corpse type, f.e. `{looted:umber}`, with Shaft, Session, Per hour and All-time ranges.
- Mined metal HUD line with Shaft, Session, Per hour and All-time ranges, in your Metal tier.
- Session length, Time since last shaft and Shafts today HUD lines.
- Times over an hour show hours, f.e. 1:16:00 or 1h 16m.
- `/sv hud` opens the HUD editor.
- New players see `(/sv hud)` under the default HUD boxes until they open the editor.
- Recordings also save what Surveyor detected live.
- Each HUD line has an Easy and an Advanced mode.
- Advanced: type the whole line with colours, values and maths, f.e. `{coins / shafts}`.
- Advanced lines can show several values.
- Colour rules are typed, one a line, f.e. `{cold} <= -80 &c`, with maths on both sides.
- Colour rules can use > >= < <= = and !=.
- Value list beside the line page: click a value, option, colour or sign to type it.
- Short value names in text: `{spawn}`, `{avg_spawn}`, `{shafts}`, `{cold}` and more. Old names still work.
- + Rule types a rule for the first value in the text.
- Mistakes in the text or rules say what's wrong.
- Numbers in rules can be typed as 1,5M, 150k, 90s or 1m 30s.
- Turning Advanced on starts with that line's ready-made rules, like Cold yellow at -50 and red at -80.
- Old lines with colour rules show as Advanced and look the same.
- Shaft lines show the last shaft while you're outside one: shaft type, time inside, corpses, Cold, campfires.
- "Outside" choice on shaft lines: last shaft or -.
- New HUD lines get their own value colour: coins gold, powder and Cold aqua, corpse Fine blue, campfires gold.
- Corpse types and the shaft type show in their own colours on the HUD.
- New lines and presets have no colour rules on.
- Money preset has white boxes with gold coin lines.
- Box titles can have their own colour and bold.
- Lines with their own colour show a colour square in the box's line list.
- "All lines" next to the box's Labels or Values colour puts it on every line.
- Colour menu can set a line back to its value's default colour.
- A line's label follows its range unless you typed your own, f.e. "All-time coins".
- Clearer HUD labels: Avg coins per shaft, Corpse Fine, Time inside, Clock.
- All-time Avg coins per shaft says "All-time avg coins/shaft:".
- Powder is now "Glacite Powder from shafts", labelled "Shaft powder".
- "Session" in Add line is now "Totals".
- Average spawn has Shaft, Session and All-time ranges.
- No format choice for FPS, Ping and Campfires.
- Negative times show as -0:13.
- Onyx shows in gray instead of dark gray.
- Hovering a value in Add line explains what it counts.
- Help card behind the ? in the Edit HUD toolbar.
- Edit HUD is lighter while a line page is open.
- Tungsten mined in Tungsten shafts and Umber in Umber shafts count as Mined metal.
- Metal tier setting: Enchanted, Tungsten or Umber, or Refined.
- Metal line in the shaft summary and Metal mined in the shaft view.
- Shaft summary as a toast. Chat, both or off in Settings.
- Toast style, time and position, top right or anywhere in Edit HUD.
- Ironman setting: coins from NPC sell prices.
- Cold at each campfire use in the shaft view, Session table and Stats.
- Mod icon with a midnight blue background.
- Even gaps between the journal's top bar buttons.
- Edit HUD button has a move icon.
- Settings title is white with the settings icon.
- Settings top bar is the same height as the journal's.
- Buttons use the resource pack's button texture.
- Buttons light up when picked with the keyboard.
- Fixed campfires missing from the shaft view and Session table when some weren't used.
- Fixed a broken character in the journal file hiding every shaft.
- Fixed a journal line with missing fields breaking the HUD and journal on every start.
- Fixed a cut off journal line losing the next shaft.
- Fixed one broken setting in config.json resetting the settings after it.

## 0.21.3 (2026-11-13)
- Fixed the HUD frame taking the colour of the last hovered item with Iconographic ([#1](https://github.com/forenick/Surveyor/issues/1), reported by @Raaf404).

## 0.21.2 (2026-11-13)
- Bars / Line and range arrows keep the Stats tab's scroll.
- Closing the game inside a shaft saves that shaft.
- Settings changed while prices load are kept when you quit.
- Price downloads can't get stuck.
- GitHub links in Mod Menu.
- Recordings leave out player chat.
- Small Stats tooltip fixes.
- An old ShaftTools jar can't run next to Surveyor.
- Square mod icon with a plain green background.

## 0.21.1 (2026-11-13)
- Open source under the LGPL-3.0.

## 0.21.0 (2026-11-13)
- Renamed to Surveyor, new icon. Remove the old `shaftjournal` jar.
- Data moves from `.minecraft/shaftjournal/` to `.minecraft/skysurveyor/` on first start.
- New commands `/surveyor` and `/sv`. `/sj` and `/shaftjournal` still work.
- Journal hotkey stays bound.

## 0.20.0 (2026-11-13)
- Stats charts can switch between bars and a line.
- Hover an average line for median, best, worst and more.
- Clearer range picker.
- Coins per shaft type bars show each type's share of all coins.

## 0.19.0 (2026-11-13)
- Stats range: All time, Last 7 days, Last 5 sessions.
- Average coins per shaft type.
- Drop chances in corpse tooltips.
- Coins / h per session chart.

## 0.18.4 (2026-11-13)
- Stats tooltips: gaps between groups, white part names, Shift icon.

## 0.18.3 (2026-11-13)
- Coins tooltip shows a full breakdown. Hold Shift for the short version.
- Corpse rows show only the average, total in the tooltip.
- Reordered All time totals.

## 0.18.2 (2026-11-13)
- Settings choices are radio options.
- Reset all button per category.

## 0.18.1 (2026-11-13)
- Settings page no longer undoes hand edits to `config.json`.
- Tax box applies after you stop typing.
- Footer turns red when settings can't be saved.
- Small settings page fixes.

## 0.18.0 (2026-11-13)
- Settings page (`/sj settings` or the journal's Settings button) with search.
- Money settings, price ages and Group by day are on it.

## 0.17.3 (2026-11-13)
- Sessions only count time in the Mines and shafts.
- New prices no longer scroll the journal to the top.
- Fixed a hitch on shaft entry with big journals.
- Small layout fixes.

## 0.17.2 (2026-11-13)
- Shaft view: net coins in the Coins heading.
- Back button fits at GUI scale 4.

## 0.17.1 (2026-11-13)
- `hud.json` is backed up before converting an old format.
- Builds now test that old saves still load.

## 0.17.0 (2026-11-12)
- Session tab shows only the current session. Older ones open from Overview with Back and < >.
- By day toggle.
- Tab icons.

## 0.16.0 (2026-11-12)
- Overview tab groups sessions by day with day totals.
- Journal opens on Overview, or Session while one is going.
- All-time Coins tooltip shows where coins came from.

## 0.15.0 (2026-11-12)
- Coins in the journal tables, Session tab and Stats.
- Money HUD values and a Money preset.

## 0.14.2 (2026-11-12)
- Fixed crystals counted when you already had them.
- Fixed bad price data wiping saved prices.
- Fixed Citrine and Peridot on Turkish systems.
- Smaller `journal.jsonl`.

## 0.14.1 (2026-11-12)
- Fixed Inside times wrapping at GUI scale 3.

## 0.14.0 (2026-11-12)
- Every shaft gets a coin value (gems, drops, corpse loot, scrap, crystals, minus keys and campfires).
- Coins line in the chat summary and a Coins section in the shaft view.
- Prices from the Bazaar and lowest BIN, cached for offline use.
- Each shaft saves the prices it was valued at.

## 0.13.2 (2026-11-12)
- Journal tables and charts use the full width at GUI scale 2.
- Edit HUD panel grows on wide screens and is opaque.

## 0.13.1 (2026-11-12)
- Ping works on Hypixel.
- Format tooltips show the unit.
- Colour rules only accept values that fit.

## 0.13.0 (2026-11-12)
- HUD values: FPS, Ping, TPS, Clock, mod and Minecraft version.
- + Preset button in Edit HUD.
- Number formats shown as format codes.

## 0.12.2 (2026-11-12)
- Small text tweaks in Edit HUD.

## 0.12.1 (2026-11-12)
- Small text tweak in Edit HUD.

## 0.12.0 (2026-11-12)
- Colour rules can compare against another value, like `{average_spawn}`.
- Breadcrumbs in the Edit HUD panel.

## 0.11.2 (2026-11-12)
- Per hour values show waiting dots until they're ready.
- Empty part of the side panel no longer blocks clicks.

## 0.11.1 (2026-11-12)
- Edit HUD panel is full height, no folding sections.
- Small layout tweaks.

## 0.11.0 (2026-11-12)
- Edit HUD on one page per box.
- Bigger buttons, colour picker grid, tooltips that fit the screen.
- Drag lines past the edge to scroll.

## 0.10.1 (2026-11-12)
- Fixed Enter or Space removing extra lines.
- Edit HUD keeps its scroll position.
- Many small Edit HUD fixes.

## 0.10.0 (2026-11-12)
- Edit HUD lines show how they look. Each line has its own page.
- Label, range, format and colour per line.
- Value and colour menus for custom text.

## 0.9.3 (2026-11-12)
- No changes in game.

## 0.9.2 (2026-11-12)
- No changes in game.

## 0.9.1 (2026-11-12)
- Fixed colour rules on custom text lines.
- Fixed Ctrl+Z while typing in Edit HUD.

## 0.9.0 (2026-11-12)
- Custom text lines with `&` colour codes and `{value}` tags.
- Colour rules: ready-made ones and your own.

## 0.8.0 (2026-11-12)
- Add your own HUD boxes and any value to any box.
- Time ranges: this shaft, session, per hour, all-time.

## 0.7.0 (2026-11-12)
- Fixed box titles not saving with Caxton.
- Edit HUD saves by itself.
- Drag lines to reorder.
- Show in: Shafts, Mines, Elsewhere.
- Undo and redo.

## 0.6.1 (2026-11-11)
- Boxes stay lined up at any window size or GUI scale.
- Broken `hud.json` is backed up instead of overwritten.
- Many small HUD fixes.

## 0.6.0 (2026-11-11)
- Edit HUD snaps boxes to the screen and to each other. Hold Ctrl to place freely.

## 0.5.3 (2026-11-11)
- Edit HUD toolbar moves out of the way.
- Arrow keys nudge the selected box.

## 0.5.2 (2026-11-11)
- Scroll on a box to resize it.
- Reset asks first.

## 0.5.1 (2026-11-11)
- Fixed an unentered portal's spawn time going to the next shaft.
- Fixed old sessions showing after a break.
- Small HUD fixes.

## 0.5.0 (2026-11-11)
- HUD with Session, Spawn and Current shaft boxes.
- Edit HUD to move and style boxes.

## 0.4.1 (2026-11-11)
- Fixed a crash when hovering next to journal text.

## 0.4.0 (2026-11-11)
- Renamed to ShaftJournal. Commands `/shaftjournal` and `/sj`.
- Corpse loot icons are real SkyBlock items (with texture packs).

## 0.3.6 (2026-11-11)
- Shafts / h best needs at least 5 shafts.

## 0.3.5 (2026-11-11)
- Journal re-lays itself out on resize.
- Small journal fixes.

## 0.3.4 (2026-11-11)
- Shorter personal best labels.

## 0.3.3 (2026-11-11)
- Reworked personal bests with arrows to open them.
- Spawn chart average line and Last 5 sessions average.

## 0.3.2 (2026-11-11)
- Shaft type bars in the gem's colour.

## 0.3.1 (2026-11-11)
- Shaft types seen and spawn chart axis on the Stats tab.

## 0.3.0 (2026-11-11)
- Journal screen with Session, Sessions and Stats tabs.

## 0.2.1 (2026-11-11)
- Spawn timer counts pickaxe abilities and caps breaks at 40 s.

## 0.2.0 (2026-11-11)
- Shaft journal, chat summary after each shaft, spawn timer and sessions.

## 0.1.0 (2026-11-08)
- First version: shaft detection.
