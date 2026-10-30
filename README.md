# GoldenDPS

A DPS, damage and healing meter for groups and individuals.

- **Solo**: works on its own. No party is needed and nothing is sent anywhere.
- **Groups**: party members who run GoldenDPS share their numbers through RuneLite Party, so everyone
  sees the whole group. Reports go to the party chat when you click Report, and after a boss kill
  from the party leader.

## Features

- **Meter overlay** with damage, DPS, healing or damage taken per player. Right-click it to switch
  modes, toggle boss-only, switch between the current fight and the session total, or report.
- **Boss meter**: a second meter for the boss you are fighting, with DPS over the boss time only. It
  pauses when nobody hits the boss for 10 seconds and stays until the next boss fight.
- **Side panel** with kills per hour at the top, then every fight; click a heading to show or hide it: per-player details (accuracy, biggest hit, healing by source,
  overhealing, damage taken), damage per target, and a breakdown per weapon, special attack and spell.
- **Overhealing**: food, potions, Sanguinesti staff and blood spells, shown as a percentage of all
  healing.
- **Personal bests** per boss, raid and Barrows run, kept apart for solo and party kills: fastest time and
  best DPS, compared with your last one. A Barrows run only sets a PB when all six brothers died in it.
  Click "reset" next to a personal best to delete it.
- **Raids**: in CoX, ToB, ToA and the Fractured Archive one fight covers the whole raid, and every boss
  also gets a fight of its own.
- **Barrows**: every brother is a boss fight, and opening the chest adds the run: from your first hit on
  a brother until the chest, with banking and teleports in between counted. The clock stops while you are
  logged out and after 10 minutes without a brother hit. The run counts only damage on the brothers. The
  overlay shows the run time while a run is going on, a run survives a client restart, and the panel
  shows chests per hour and your fastest run.
- **Report to party**: sends the top five of the overall damage, the boss damage or the healing to the
  RuneLite party chat. After every boss kill the party leader sends the boss report by itself (setting
  **Auto boss report**, on by default).
- **Fun extras** in group fights: a crown over the MVP, a burger over the top healer of the fight or
  raid, fries over the top healer on the current boss (one player can get both), and hungry lines from
  the snack holders. When a boss dies, the fries holder says how much they healed on the boss and the
  burger holder the total of the fight or raid. These lines last 5 seconds; while one shows, that
  player's typed chat only appears in the chatbox. The damage bars also have a Max column with every
  player's biggest hit.
- **Party tab**: join a party by name, see who runs GoldenDPS, and (as leader) remove members who run it.
  In raids, everyone with GoldenDPS joins the same party automatically.

## How it works

The game only marks which hitsplats are your own. Each GoldenDPS client therefore counts its own hits
and heals and sends them to the party once per game tick. Players without GoldenDPS are estimated from
their attack animations (marked with `~`), and damage nobody reported shows as "Others (unknown)".

Party members only count while you are in the same instance or fight the same boss, so a friend
training elsewhere does not show on your meter. This can be turned off.

Fights use active time: when nobody attacks for 10 seconds the clock stops, so AFK time does not lower
DPS. A fight ends when the boss dies or after 5 minutes without hits. Raids are the exception: the raid
and its bosses run on the game clock (time between rooms counts), from the first hit to the last.

The overall meter goes on over boss kills: it adds up your fights until you stop fighting for 5 minutes.
The boss meter and the panel show each fight on its own.

### Raid party

Every member of a raid party derives the same party name from data the game shows all of them: the
member list in ToB, ToA and the Fractured Archive, or the raid party id in CoX. Once the raid party
has been stable for a few ticks, GoldenDPS joins it automatically (setting: Party > Raid party, off by default). The party
is left again when the raid party is gone. A party you joined yourself is never changed.

With the setting **Own party** (off by default), GoldenDPS also puts you in a party of your own when you
start a boss fight in no party, so the automatic boss reports and the Barrows run report ("Run damage") also work solo. A
raid party replaces it, and leaving it yourself turns it off until your next login.

Automatic reports add the kill count from the game chat and your PB ("KC 19 - PB 5:48" or "NEW PB!").
A finished raid is reported as a whole ("Chambers of Xeric complete: 31:30 - … - KC 21").
At Barrows only the whole run is reported ("Barrows run complete: 3:12 - total 400 (2.1/s) - KC 19"), not
every brother, and a solo report is one line.

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
