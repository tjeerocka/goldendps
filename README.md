# GoldenDPS

A damage and healing meter for groups.
Party members who run GoldenDPS share their numbers through RuneLite Party, so everyone sees the
whole group.

## Features

- **Meter overlay** with damage, DPS, healing or damage taken per player. Right-click it to switch
  modes, toggle boss-only, switch between the current fight and the session total, or report.
- **Boss meter**: a second meter for the boss you are fighting, with DPS over the boss time only.
- **Side panel** with every fight: per-player details (accuracy, biggest hit, healing by source,
  overhealing, damage taken), damage per target, and a breakdown per weapon, special attack and spell.
- **Overhealing**: food, potions, Sanguinesti staff and blood spells, shown as a percentage of all
  healing.
- **Personal bests** per boss: fastest kill and best DPS, compared with your last kill.
- **Raids**: in CoX, ToB, ToA and the Fractured Archive one fight covers the whole raid, and every boss
  also gets a fight of its own.
- **Report to party**: sends the top five to the RuneLite party chat.
- **Party tab**: join a party by name, see who runs GoldenDPS, and (as leader) remove members who run it.
  In raids, one click puts everyone with GoldenDPS in the same party.

## How it works

The game only marks which hitsplats are your own. Each GoldenDPS client therefore counts its own hits
and heals and sends them to the party once per game tick. Players without GoldenDPS are estimated from
their attack animations (marked with `~`), and damage nobody reported shows as "Others (unknown)".

Party members only count while you are in the same instance or fight the same boss, so a friend
training elsewhere does not show on your meter. This can be turned off.

Fights use active time: when nobody attacks for 10 seconds the clock stops, so AFK time does not lower
DPS. A fight ends when the boss dies or after 5 minutes without hits.

### Raid party

Every member of a raid party derives the same party name from data the game shows all of them: the
member list in ToB, ToA and the Fractured Archive, or the raid party id in CoX. Once the raid party
has been stable for a few ticks, the Party tab shows **Join raid party**; one click joins it. The party
is left again when the raid party is gone. A party you joined yourself is never changed.

### Healing

| Source | Detection | Overhealing |
|---|---|---|
| Food and potions | Eat/Drink click and the item leaving the inventory | heal from a table minus the real heal |
| Sanguinesti staff | heal graphic on your target or on you | half the damage minus the real heal |
| Blood spells | blood impact on your target with a magic weapon | 25% of the damage (more with bloodbark, ancient sceptre, Zuriel's staff) minus the real heal |
| Other lifesteal | HP gained in the tick you hit | not known |
| Heal Other / Heal Group | HP lost without a hitsplat after casting | — |

Overhealing is only counted when you end at your maximum HP, so a wrong table value cannot create waste.

## Limits

- Damage of players without GoldenDPS is an estimate, and their healing is unknown.
- Every client times fights on its own, so DPS can differ slightly between members.
- The game chats are not supported for reports: plugins may not send game chat messages.

## Data

Fight history and personal bests are stored per account in `.runelite/goldendps/`.

## Development

Requires Java 11. Run `com.goldendps.GoldenDpsPluginTest` (with `-ea`) or `gradlew run` to start a client
with the plugin, and `gradlew test` for the tests. `settings.gradle` explains how to run it together with
another plugin project in one client.

The boss id list is taken from RuneLite's DPS Counter plugin (BSD 2-Clause).
