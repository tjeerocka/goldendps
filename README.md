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
(GoldenDPS side panel → Party, or the Party plugin, with the same code). Each client sends its own numbers once
per game tick (`GoldenDpsUpdate` message), like RuneLite's DPS Counter does.

Solo: works without a party, only your numbers.

## Fights

- A fight starts on the first hit.
- It ends when the boss dies, or when nobody hit anything for X seconds (setting, default 5 min).
- **Active time** (like Details!): when nobody hits for 10 s (setting **Pause clock after**), the clock
  stops ("paused" in the overlay) until the next hit. AFK time does not lower DPS.
  Each gap counts at most 10 s. Fight, boss and session timers all use active time.
- Party hits that arrive up to 5 s after a boss died still go into that fight (network delay).
- The panel keeps the last fights (setting, default 50) and a **session total**.

### One fight per raid

In **CoX, ToB, ToA and The Fractured Archive** (setting **One fight per raid**, on by default):

- When you enter the raid instance, the meter starts fresh: the old fight ends and a new fight counts the
  **whole raid**. It does not end when a boss dies or when you AFK; it ends when you leave the raid
  (or log out).
- Every boss in the raid still gets its **own fight**: the boss meter shows only the boss you fight now
  and starts again at the next boss. In the panel list the bosses show as `> Maiden ...` under the raid.
- PBs come from these boss fights. The session total counts each raid once.

### Stay in your party

GoldenDPS remembers your party **per account**. After a relog or a client restart you join it again by yourself
(your other accounts are not affected).
It only forgets the party when you leave it yourself (Leave button or the Party plugin) or are kicked.

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

Shown as a % of all healing (healing + overhealing): on the Healing bars ("1,240  OH 22%"),
in the overlay header for the whole group, and in the panel per player.

Party members send their overhealing too (they need this version).

## Overlay and panel

- Overlay: colored bars, sorted, with a category row above the bars:
  Damage mode `Player · Damage · DPS · %`, DPS mode `Player · DPS · Damage · %`,
  Healing `Player · Healing · OH · %`, Damage taken `Player · Taken · %`. The boss meter uses the Damage columns.
- Overlay: Right-click: **Show Damage / DPS / Healing / Damage taken**,
  **Toggle Boss only**, **Toggle Fight / Session**, **Reset Session**.
- The main meter header says `Damage overall` (all targets), or `Damage - Session`.
- Damage bars show damage, DPS and share: `820  13.7/s  50%`.
- **Boss meter** (red header, under the main meter): only damage on the boss, and DPS over the
  boss time (first hit on the boss to its death), so trash before the boss does not count.
  After the boss dies it shows `(12s)` and hides after that time (setting **Hide boss meter after**,
  default 20 s). The next boss fight shows a new one. Settings: **Boss meter** on/off.
- The panel shows Boss time / Boss damage / Boss DPS for the fight and per player.
- Side panel (gold bars icon): pick the current fight, the session total or an old fight;
  full details per player and per target. Buttons: End fight, New session, Clear.

## Party (own tab in the side panel)

The side panel has two tabs: **Meter** (fights) and **Party**. GoldenDPS uses RuneLite's own party
(the same one as the Party plugin), so friends with only the Party plugin can join too.

- **Party name**: type any name (spaces are fine) and press **Join** (or Enter). Everyone with the
  same name is in the same party. **Random** fills in random words. **Copy** copies the name.
- **Members**: green dot = online, ★ = leader, `(you)`, `no GoldenDPS` = only the Party plugin.
- **Leader**: whoever starts the party (alone in it). New GoldenDPS members are told who leads.
  If the leader leaves, the GoldenDPS member who joined the party system first takes over.
- **Kick** (leader only): the kicked player's GoldenDPS leaves the party. RuneLite parties have no
  real kick, so this only works on players with GoldenDPS, and they could join again with the same
  name: start a new party name if needed.
- **Leave**: leave the party.

### Auto party in raids

In **Theatre of Blood**, **Tombs of Amascut** and **The Fractured Archive**, everyone with GoldenDPS joins
the same party by itself. The game shows every member the names of the raid party; GoldenDPS makes the
same party name from the raid, the world and the sorted names, so nobody has to type anything.

- Only when you are not in a party you joined yourself (it never changes your own party).
- **Random** and **Join** only work when you are not in a party. To change party, press **Leave** first.
- Waits until the names stay the same for 5 ticks (people join the lobby one by one); when someone
  joins or leaves the raid party, everyone moves to the new party name.
- After the raid you **stay** in the party, until you leave it yourself or make a raid party with
  other people (then you move to the new one).
- Setting: **Auto party in raids** (on by default). Chambers of Xeric is not supported (it does not show
  the member names the same way).

## Report to party (like Details! "Report")

- Overlay right-click **Report to party**: what the overlay shows (fight or session).
- Side panel button **Report to party**: the fight picked in the panel.
- Sends the top 5 for the mode the overlay is in (Damage / DPS / Healing / Damage taken) to your
  **RuneLite party chat**; everyone in the RuneLite party sees it (they don't need GoldenDPS):

```
GoldenDPS Damage - Vorkath 1:23 - total 27.4k (330.1/s)
1. Kurt Yoghurt 12.3k 148.2/s 45%
2. Zezima 9.1k 109.6/s 33%
```

- Not in a party: a message tells you to join one.
- The game chats (public, private, clan, friends, group) are not possible: Jagex does not allow
  plugins to send messages in the game chat.

## Personal bests per boss

Every boss kill is compared with your records for that boss:

- **Fastest kill** (boss time, from the first boss hit to the death) and **your best DPS** on the boss.
- The boss meter shows `PB 1:10` during the fight and `NEW PB!` (gold) after a faster kill.
- The panel shows for a kill: `Kill time 0:50 NEW PB! (was 1:00)`, `Your boss DPS 8.1 (best 9.0)`,
  `vs last kill +5% DPS` and the kill count.
- At the bottom of the Meter tab: **Personal bests** for all bosses (kc, fastest, best DPS).
- Saved in `<account id>.records.json`, so PBs stay when old fights are removed from the history.
  The first time, PBs are built from your saved fights.

## Breakdown per weapon / spell

Like the Details! spell breakdown. Each player card in the panel shows damage per source:

- **Weapon**: the weapon you held when your attack started (switching while an arrow flies is fine).
- **Special attacks**: own line, for example `Dragon claws (spec)` (found by spec energy going down).
- **Spells you click** (Cast on an NPC): by spell name, for example `Ice Barrage`. Autocast shows the staff.
- Hover a line for hits, misses, biggest hit and average per attack.
- Exact for you. Party members only show a breakdown when they also have GoldenDPS (this version).
  Players without GoldenDPS have no breakdown.
- If the game shows thrall hits as yours, they count for the weapon you hold.

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

### Run together with AoRC (one client)

1. Have both projects next to each other, for example `IdeaProjects\AoRC` and `IdeaProjects\goldendps`.
   (Other place: add `aorcDir=C:/path/to/AoRC` to `%USERPROFILE%\.gradle\gradle.properties`.)
2. In IntelliJ: reload Gradle (Gradle panel → Reload). AoRC now shows up in the Gradle panel too.
3. Run **GoldenDPS + AoRC** (or **GoldenDPS + AoRC (Gradle)** = `gradlew runBoth`).

AoRC is loaded straight from its project folder, so changes in AoRC are used on the next start.

The boss id list comes from RuneLite's DPS Counter plugin (BSD-2-Clause).
