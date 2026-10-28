# GoldenDPS

A RuneLite plugin for Old School RuneScape: a WoW-style damage and healing meter
for groups, like Details! / Recount.

## What it shows

Per player (you and party members who run GoldenDPS):

- Damage, DPS, % of group damage
- Biggest hit, accuracy (hits / misses)
- Healing: food/potions, lifesteal, heal on others (and regen, optional)
- Damage taken

Extra rows:

- **Others (unknown)**: damage on your targets that nobody reported
  (players without the plugin). = all damage you saw on the target − what the party reported.
- **Poison / venom / burn**: the game does not say who did these.

Per target: damage split by NPC name, and a **Boss only** mode.

### When other people count

- **In a RuneLite party, or in an instance** (raids, boss rooms, Inferno...): you, your party,
  "Others (unknown)", guessed players and their damage taken.
- **Everywhere else** (open world): only **your** numbers. Other players' hits are ignored.

### Players without GoldenDPS

- **Damage (guess, marked `~`)**: when a player near you starts an attack animation on an NPC,
  the next "other" hitsplat on that NPC (within 6 ticks) is given to them. Oldest attack first.
  Good with 2–3 players; can be wrong when many hit at the same time. Hits that match nobody
  stay in "Others (unknown)". Setting: **Guess other players**.
- **Damage taken**: the hitsplats you see on them. Setting: **Damage taken of others**.
- **Healing**: not possible (you can't see their HP numbers).
- When a player turns out to run GoldenDPS (their party data arrives), their own numbers replace the guess.

## How it works

The game only tells your client which hitsplats are **yours** (`Hitsplat.isMine()`).
So every group member must run GoldenDPS and be in the same **RuneLite Party**
(Party plugin → join with the same passphrase). Each client sends its own numbers once
per game tick (`GoldenDpsUpdate` message), like RuneLite's DPS Counter does.

Solo: works without a party, only your numbers.

## Fights

- A fight starts on the first hit.
- It ends when the boss dies, or when nobody hit anything for X seconds (setting, default 10).
- Party hits that arrive up to 5 s after a boss died still go into that fight (network delay).
- The panel keeps the last fights (setting, default 50) and a **session total**.

## Healing and overhealing

Effective healing = how much your HP really went up. Overhealing = what was wasted.

| Source | How it is found | Overhealing |
|---|---|---|
| Food / potions | you click Eat/Drink **and** the item leaves your inventory | full heal from a table − effective |
| Sanguinesti staff | heal graphic on the NPC you hit (or on you) | half your damage − effective |
| Blood spells | blood impact graphic on the NPC you hit, with a magic weapon | 25% of damage (+2%/bloodbark piece, x1.1 ancient sceptre, x1.5 Zuriel's staff) − effective |
| Soulreaper axe, others | HP went up the tick you hit | not known (only effective) |
| +1 / +2 with no reason | regen | only counted if turned on |
| Heal Other / Heal Group | HP you lose (no hitsplat) in the 5 ticks after you cast it | — |

Food table (full heal): lobster 12, swordfish 14, monkfish 16, karambwan 18, curry 19, shark 20,
sea turtle 21, manta ray 22, dark crab 22, tuna potato 22,
anglerfish ⌊hp/10⌋ + 2⌊hp/25⌋ + 5⌊hp/93⌋ + 2, Saradomin brew ⌊hp×15/100⌋ + 2 per dose.
Brews and anglerfish can go above your max HP (by their heal amount); the Blood ancient sceptre lets
blood spells go 10% above.

Overhealing is only counted when your HP ended at the cap, so a wrong table value can't invent waste.
Example: shark at 95/99 = 4 healing + 16 overhealing. Shark at 99/99 = 0 + 20.

Party members send their overhealing too (they need this version).

## Overlay and panel

- Overlay: colored bars, sorted. Right-click: **Show Damage / DPS / Healing / Overhealing / Damage taken**,
  **Toggle Boss only**, **Toggle Fight / Session**, **Reset Session**.
- Side panel (gold bars icon): pick the current fight, the session total or an old fight;
  full details per player and per target. Buttons: End fight, New session, Clear.

## Saving

Fight history is saved per account in
`%USERPROFILE%\.runelite\goldendps\<account id>.json`:
when a fight ends, every 30 s during a fight, on logout / world hop and when the client closes.

## Limits

- Players without the plugin: damage is a guess and only for targets near you; no healing.
- Healing sources are a guess from timing.
- Each client has its own fight timer, so DPS can differ a little between members.
- A party member's hit only counts on your target when it is the same NPC (same world, NPC number
  and place). Party members in their own instance (for example their own Vorkath) get separate targets.

## Build and run

Needs Java 11 or newer (JDK 17 recommended). Never commit `credentials.properties`.

- IntelliJ: run **GoldenDPS** (main class `com.goldendps.GoldenDpsPluginTest`, VM option `-ea`),
  or **GoldenDPS (Gradle)** which runs `gradlew run` (assertions on).
- Tests: `gradlew test`

The boss id list comes from RuneLite's DPS Counter plugin (BSD-2-Clause).
