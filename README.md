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

## Healing (best guess, from your HP)

| HP went up and... | Counted as |
|---|---|
| you ate / drank in the last 2 ticks | Food/potions |
| you hit an NPC this or last tick (Sanguinesti staff, blood spells, Soulreaper axe) | Lifesteal |
| +1 / +2 with no reason | Regen (only counted if turned on) |
| other reason (for example someone's Heal Other on you) | not counted for you |

**Heal Other / Heal Group**: the HP you lose (without a hitsplat) in the 5 ticks after you
cast it is counted as "healing on others".

## Overlay and panel

- Overlay: colored bars, sorted. Right-click: **Show Damage / DPS / Healing / Damage taken**,
  **Toggle Boss only**, **Toggle Fight / Session**, **Reset Session**.
- Side panel (gold bars icon): pick the current fight, the session total or an old fight;
  full details per player and per target. Buttons: End fight, New session, Clear.

## Saving

Fight history is saved per account in
`%USERPROFILE%\.runelite\goldendps\<account id>.json`:
when a fight ends, every 30 s during a fight, on logout / world hop and when the client closes.

## Limits

- Players without the plugin are one row ("Others (unknown)"), and only for targets near you.
- Healing sources are a guess from timing.
- Each client has its own fight timer, so DPS can differ a little between members.
- Party members must be on the same world for "Others (unknown)" to match targets.

## Build and run

Needs Java 11 or newer (JDK 17 recommended). Never commit `credentials.properties`.

- IntelliJ: run **GoldenDPS** (main class `com.goldendps.GoldenDpsPluginTest`, VM option `-ea`),
  or **GoldenDPS (Gradle)** which runs `gradlew run` (assertions on).
- Tests: `gradlew test`

The boss id list comes from RuneLite's DPS Counter plugin (BSD-2-Clause).
