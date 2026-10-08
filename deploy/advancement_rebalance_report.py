#!/usr/bin/env python3
"""Report excess advancement dabloons from the 2026-10-05 reward reduction.

Run from the deployment directory:
    python3 advancement_rebalance_report.py data/api/app.sqlite

The script reads SQLite and, when present, data/minecraft/logs. It changes nothing.
Use --before with an offset-aware ISO timestamp if deployment happened later than
the commit, for example --before 2026-10-05T12:00:00+01:00.
"""

import argparse
import gzip
import re
import sqlite3
import sys
from collections import defaultdict
from contextlib import closing
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo


COMMIT = "5998a67d0a42b844e6e23f83daa0ae9bdc43508f"
DEFAULT_BEFORE = "2026-10-05T08:37:45+01:00"
HEART = "blazeandcave:challenges/hearts_of_the_stack"
REWARD_ROWS = """blazeandcave:adventure/a_chiptune_relic 25 6
blazeandcave:adventure/a_suspicious_advancement 20 5
blazeandcave:adventure/beethoven 3 0
blazeandcave:adventure/breeze_suppressor 26 6
blazeandcave:adventure/chromatic_armory 56 14
blazeandcave:adventure/core_keeper 199 49
blazeandcave:adventure/dry_spell 8 2
blazeandcave:adventure/filthy_rich 8 2
blazeandcave:adventure/filthy_stinkin_rich 64 16
blazeandcave:adventure/genesis_of_the_end 24 6
blazeandcave:adventure/good_lookin_treasure 11 2
blazeandcave:adventure/guardian_destroyer 35 8
blazeandcave:adventure/keys_to_success 68 17
blazeandcave:adventure/mapmaker 2 0
blazeandcave:adventure/mapmakers_table 1 0
blazeandcave:adventure/omniscient 65 16
blazeandcave:adventure/overpowered 25 6
blazeandcave:adventure/pottery_exhibition 85 21
blazeandcave:adventure/prime_specimen 4 1
blazeandcave:adventure/raiders_of_the_lost_trim 25 6
blazeandcave:adventure/sponge_miner 32 8
blazeandcave:adventure/stacked_smither 420 105
blazeandcave:adventure/superhero_of_the_village 14 3
blazeandcave:adventure/sus 64 16
blazeandcave:adventure/the_sound_of_silence 10 2
blazeandcave:adventure/tricky_treasures 22 5
blazeandcave:adventure/truffle_worm 5 1
blazeandcave:adventure/vexingly_hard_to_find 4 1
blazeandcave:adventure/workaholic 42 10
blazeandcave:animal/a_horse_in_shining_armor 9 2
blazeandcave:animal/chicken_cooper 20 5
blazeandcave:animal/colorful_cavalry 20 5
blazeandcave:animal/flamboyant_range 15 3
blazeandcave:animal/fractal 16 4
blazeandcave:animal/fuzzy_feet 15 3
blazeandcave:animal/honey_im_home 23 5
blazeandcave:animal/horse_armorer 3 0
blazeandcave:animal/in_a_hole_there_lived_a_rabbit 13 3
blazeandcave:animal/lucky_charm 2 0
blazeandcave:animal/master_angler 16 4
blazeandcave:animal/nest_quick 26 6
blazeandcave:animal/omelette 9 2
blazeandcave:animal/pig_slaughterer 3 0
blazeandcave:animal/rainbow_collection 16 4
blazeandcave:animal/ram 8 2
blazeandcave:animal/save_the_turtles 18 4
blazeandcave:animal/scuttler 12 3
blazeandcave:animal/sheariously 15 3
blazeandcave:animal/shoe_shed 16 4
blazeandcave:animal/true_cow_tipper 20 5
blazeandcave:animal/turtle_army 38 9
blazeandcave:animal/turtle_soldier 17 4
blazeandcave:animal/when_pigs_used_to_fly 8 2
blazeandcave:biomes/everybody_loves_ice 24 6
blazeandcave:biomes/eyeball_forest 8 2
blazeandcave:biomes/fake_copper 25 6
blazeandcave:biomes/flower_power 45 11
blazeandcave:biomes/for_you_my_sweet 1 0
blazeandcave:biomes/just_a_sample 3 0
blazeandcave:biomes/no_reefing 15 3
blazeandcave:biomes/powder_full 2 0
blazeandcave:biomes/resinance 3 0
blazeandcave:biomes/spike_ice 3 0
blazeandcave:biomes/thick_ice 5 1
blazeandcave:biomes/thin_ice 3 0
blazeandcave:building/a_good_start 4 1
blazeandcave:building/ah_my_old_enemy 45 11
blazeandcave:building/bamboozled 25 6
blazeandcave:building/barking_mad 4 1
blazeandcave:building/blackstonehenge 20 5
blazeandcave:building/built_on_sand_set_in_stone 17 4
blazeandcave:building/cherry_bomb 25 6
blazeandcave:building/cinnabar_island 25 6
blazeandcave:building/concrete_evidence 5 1
blazeandcave:building/crazy_walls 35 8
blazeandcave:building/creepers_and_withers 8 2
blazeandcave:building/deepslate_conspiracy 17 4
blazeandcave:building/elmer_mudd 16 4
blazeandcave:building/fake_fortress 20 5
blazeandcave:building/fake_monument 25 6
blazeandcave:building/fake_stronghold 12 3
blazeandcave:building/fruit_of_the_looms 35 8
blazeandcave:building/grass_type 25 6
blazeandcave:building/greek_art_decor 15 3
blazeandcave:building/let_there_be_light 53 13
blazeandcave:building/mangrove_master 25 6
blazeandcave:building/master_logger 75 18
blazeandcave:building/no_banner_only_color 16 4
blazeandcave:building/pale_gardener 25 6
blazeandcave:building/pane_in_the_glass 32 8
blazeandcave:building/professor_birch 25 6
blazeandcave:building/professor_dark_oak 25 6
blazeandcave:building/professor_oak 25 6
blazeandcave:building/rainbow_dreams 16 4
blazeandcave:building/rock_collection 17 4
blazeandcave:building/rock_polish 18 4
blazeandcave:building/slabs_for_days 45 11
blazeandcave:building/spruce_lee 25 6
blazeandcave:building/the_acacia_king 25 6
blazeandcave:building/the_glazed_terracotta_army 24 6
blazeandcave:building/the_jungler 25 6
blazeandcave:building/the_rainbow_you_always_wanted 24 6
blazeandcave:building/the_ritual_begins 11 2
blazeandcave:building/the_smell_of_rotten_eggs 30 7
blazeandcave:building/the_terracotta_army 16 4
blazeandcave:building/translucence 16 4
blazeandcave:building/tuff_stuff 29 7
blazeandcave:building/yay_i_got_my_wood 29 7
blazeandcave:challenges/abyss_lord 85 21
blazeandcave:challenges/all_the_blocks 223 55
blazeandcave:challenges/all_the_items 222 55
blazeandcave:challenges/constellation 142 35
blazeandcave:challenges/creepergeddon 29 7
blazeandcave:challenges/dragon_army 69 17
blazeandcave:challenges/hearts_of_the_stack 170 42
blazeandcave:challenges/lets_take_it_up_a_notch 167 41
blazeandcave:challenges/poglin 29 7
blazeandcave:challenges/rise_of_the_skeletons 29 7
blazeandcave:challenges/stack_all_the_blocks 667 166
blazeandcave:challenges/stack_all_the_items 666 166
blazeandcave:challenges/ultimate_enchanter 201 50
blazeandcave:challenges/warlock 75 18
blazeandcave:challenges/zombie_apocalypse 29 7
blazeandcave:enchanting/a_rather_pointy_fence_post 35 8
blazeandcave:enchanting/armor_for_the_masses 69 17
blazeandcave:enchanting/bane_of_one_shotting_spiders 25 6
blazeandcave:enchanting/baron_of_blacksmiths 68 17
blazeandcave:enchanting/boomerang 14 3
blazeandcave:enchanting/bow_down_to_me 21 5
blazeandcave:enchanting/complete_enchanter 89 22
blazeandcave:enchanting/curses 15 3
blazeandcave:enchanting/echidna 12 3
blazeandcave:enchanting/fiery 24 6
blazeandcave:enchanting/fortunate_son 36 9
blazeandcave:enchanting/god_of_thunder 22 5
blazeandcave:enchanting/gotta_go_fast 10 2
blazeandcave:enchanting/knocking_your_socks_off 21 5
blazeandcave:enchanting/like_a_cat 5 1
blazeandcave:enchanting/like_a_ninja 31 7
blazeandcave:enchanting/mace_windu 35 8
blazeandcave:enchanting/magical_stockpile 32 8
blazeandcave:enchanting/master_arbalist 42 10
blazeandcave:enchanting/master_armorer 64 16
blazeandcave:enchanting/master_axeman 35 8
blazeandcave:enchanting/master_digger 35 8
blazeandcave:enchanting/master_enchanter 42 10
blazeandcave:enchanting/master_farmer 42 10
blazeandcave:enchanting/master_fisher 38 9
blazeandcave:enchanting/master_jouster 55 13
blazeandcave:enchanting/master_knight 29 7
blazeandcave:enchanting/master_macerator 35 8
blazeandcave:enchanting/master_miner 29 7
blazeandcave:enchanting/master_sniper 35 8
blazeandcave:enchanting/master_tridenteer 42 10
blazeandcave:enchanting/needle_sharp 29 7
blazeandcave:enchanting/newtons_flaming_laser_sword 20 5
blazeandcave:enchanting/pick_like_silk 23 5
blazeandcave:enchanting/scuba_gear 19 4
blazeandcave:enchanting/super_efficient 15 3
blazeandcave:enchanting/to_infinity_and_beyond 5 1
blazeandcave:enchanting/unbreakable 17 4
blazeandcave:enchanting/undead_slayer 21 5
blazeandcave:enchanting/zeus 18 4
blazeandcave:end/end_bricks 2 0
blazeandcave:end/getting_chorus 3 0
blazeandcave:end/off_with_his_head 12 3
blazeandcave:end/portable_storage 5 1
blazeandcave:end/purpurfect 5 1
blazeandcave:end/shulker_breaker 32 8
blazeandcave:end/somewhere_over_the_rainbow 16 4
blazeandcave:end/the_start_of_something 10 2
blazeandcave:end/what_for 64 16
blazeandcave:farming/a_gluttonous_diet 64 16
blazeandcave:farming/hay_there 20 5
blazeandcave:farming/mega_mushroom 2 0
blazeandcave:farming/pumpa_kungen 14 3
blazeandcave:farming/squid_game 25 6
blazeandcave:farming/the_meloncholy_dane 25 6
blazeandcave:farming/undersea_gardener 16 4
blazeandcave:farming/you_cant_take_the_sky_from_me 2 0
blazeandcave:mining/9_to_5 29 7
blazeandcave:mining/amethyst_miner 35 8
blazeandcave:mining/bouncy_beats 82 20
blazeandcave:mining/chain_gang 70 17
blazeandcave:mining/coal_miner 15 3
blazeandcave:mining/conductive_conglomerate 70 17
blazeandcave:mining/copper_computer 70 17
blazeandcave:mining/copper_miner 15 3
blazeandcave:mining/d_b_copper 70 17
blazeandcave:mining/death_pointer 25 6
blazeandcave:mining/diam_oh_wait_no 1 0
blazeandcave:mining/diamond_miner 50 12
blazeandcave:mining/echonomy 76 19
blazeandcave:mining/emerald_miner 84 21
blazeandcave:mining/even_moar_tools 16 4
blazeandcave:mining/flint_miner 18 4
blazeandcave:mining/gi_geode 5 1
blazeandcave:mining/gold_miner 42 10
blazeandcave:mining/gratest_of_all_time 67 16
blazeandcave:mining/green_lanterns 67 16
blazeandcave:mining/iconic_merchandising_prop 12 3
blazeandcave:mining/iggy 1 0
blazeandcave:mining/iron_miner 29 7
blazeandcave:mining/lapis_lazuli_miner 29 7
blazeandcave:mining/life_sentence 50 12
blazeandcave:mining/lush_hour 50 12
blazeandcave:mining/master_coal_miner 35 8
blazeandcave:mining/master_copper_miner 35 8
blazeandcave:mining/master_diamond_miner 142 35
blazeandcave:mining/master_gold_miner 50 12
blazeandcave:mining/master_iron_miner 35 8
blazeandcave:mining/mineral_collection 20 5
blazeandcave:mining/mr_bean 29 7
blazeandcave:mining/obsidian_miner 29 7
blazeandcave:mining/oresome 141 35
blazeandcave:mining/redstone_miner 20 5
blazeandcave:mining/school_of_hard_knocks 67 16
blazeandcave:mining/sly_copper_the_copper_heist 25 6
blazeandcave:mining/statue_of_limitations 42 10
blazeandcave:mining/the_mistake 13 3
blazeandcave:mining/the_statue_of_liberty 10 2
blazeandcave:mining/the_way_to_spawn 5 1
blazeandcave:mining/this_dirt_is_dirtier 3 0
blazeandcave:mining/whats_the_time_mr_wolf 5 1
blazeandcave:monsters/because_im_aquaman 42 10
blazeandcave:monsters/creeper_killer 17 4
blazeandcave:monsters/creepy_heads 5 1
blazeandcave:monsters/enderman_destroyer 10 2
blazeandcave:monsters/oh_no_guys_im_oozing 25 6
blazeandcave:monsters/paleontologist 13 3
blazeandcave:monsters/phantom_slayer 20 5
blazeandcave:monsters/skeleton_smiter 10 2
blazeandcave:monsters/slime_squisher 12 3
blazeandcave:monsters/spider_smasher 10 2
blazeandcave:monsters/zombie_slayer 3 0
blazeandcave:nether/blaze_extinguisher 25 6
blazeandcave:nether/decaying_beauty 25 6
blazeandcave:nether/diamonds_are_not_forever 20 5
blazeandcave:nether/ghast_slayer 42 10
blazeandcave:nether/gilded_miner 33 8
blazeandcave:nether/glowstone_miner 12 3
blazeandcave:nether/hell_botanist 42 10
blazeandcave:nether/holy_scrap 5 1
blazeandcave:nether/magma_cube_squisher 15 3
blazeandcave:nether/master_netherite_miner 400 100
blazeandcave:nether/moar_upgraded_tools 33 8
blazeandcave:nether/nether_rock_collection 17 4
blazeandcave:nether/netherite_mad_lad 500 125
blazeandcave:nether/netherite_miner 100 25
blazeandcave:nether/piglin_peril 7 1
blazeandcave:nether/time_to_fire_back 25 6
blazeandcave:nether/time_to_upgrade 22 5
blazeandcave:nether/welcome_to_warp_zone 12 3
blazeandcave:nether/what_about_corruption_planks 17 4
blazeandcave:nether/wub_wub_wub 15 3
blazeandcave:potion/furious_ammunition 42 10
blazeandcave:potion/noxious_fumes 10 2
blazeandcave:redstone/engineer 15 3
blazeandcave:redstone/master_engineer 100 25
blazeandcave:redstone/quartz_miner 25 6
blazeandcave:redstone/railwayman 25 6
blazeandcave:redstone/sculker_box 30 7
blazeandcave:redstone/the_block_of_eternal_screaming 10 2
blazeandcave:redstone/the_incredible_sculk 45 11
blazeandcave:redstone/vibe_check 5 1
blazeandcave:weaponry/explosive_fire 3 0
blazeandcave:weaponry/fireworks_display 5 1
blazeandcave:weaponry/flying_colors 7 1
blazeandcave:weaponry/pyrotechnic 33 8
blazeandcave:weaponry/time_to_hunt 3 0
minecraft:adventure/salvage_sherd 15 3
minecraft:end/dragon_breath 15 3
minecraft:end/dragon_egg 25 6
minecraft:end/elytra 30 7
minecraft:husbandry/obtain_sniffer_egg 20 5
minecraft:nether/brew_potion 13 3
minecraft:nether/get_wither_skull 17 4
minecraft:nether/netherite_armor 68 17
minecraft:nether/obtain_ancient_debris 33 8
minecraft:nether/obtain_blaze_rod 5 1
minecraft:nether/obtain_crying_obsidian 3 0
minecraft:story/enchant_item 12 3
minecraft:story/form_obsidian 5 1
minecraft:story/iron_tools 5 1
minecraft:story/mine_diamond 18 4
minecraft:story/mine_stone 25 6
minecraft:story/smelt_iron 3 0"""
REWARDS = {
    advancement: (int(old), 0 if advancement == HEART else int(new))
    for advancement, old, new in (line.split() for line in REWARD_ROWS.splitlines())
}
DATABASE_IDS = {advancement.replace("/", "_"): advancement for advancement in REWARDS}
assert len(REWARDS) == 287 and REWARDS[HEART] == (170, 0)
assert len(DATABASE_IDS) == len(REWARDS)

LOG_EVENT = re.compile(
    r"advancement_completed at_unix_ms=(\d+) player_uuid=([^ ]+) "
    r"username=([^ ]+) advancement_id=([^ ]+) reward_dabloons=(\d+)"
)
LONDON = ZoneInfo("Europe/London")


def cutoff_ms(value):
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise argparse.ArgumentTypeError("--before needs a timezone offset")
    return int(parsed.timestamp() * 1000)


def reward(base, sunday, member):
    return (base * (6 if sunday else 5) * (6 if member else 5) + 24) // 25


def expected_amount(advancement, paid, at_ms):
    if advancement == HEART:
        return 0
    old, new = REWARDS[advancement]
    sunday = (datetime.fromtimestamp(at_ms / 1000, timezone.utc)
              .astimezone(LONDON) - timedelta(hours=4)).weekday() == 6
    possible = {reward(new, sunday, member)
                for member in (False, True)
                if reward(old, sunday, member) == paid}
    return next(iter(possible)) if len(possible) == 1 else None


def read_database(path):
    users = {}
    events = []
    with closing(sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)) as db:
        for user_id, uuid, name in db.execute(
            "SELECT id, minecraft_uuid, minecraft_username FROM users"
        ):
            users[user_id] = (uuid.lower() if uuid else "", name)
        for event_id, user_id, at_ms, paid in db.execute(
            "SELECT id, user_id, created_at_unix_ms, amount_dabloons "
            "FROM player_money_events "
            "WHERE source = 'advancement' AND direction = 'earned' "
            "AND amount_dabloons > 0"
        ):
            prefix = f"advancement:{user_id}:"
            if not event_id.startswith(prefix):
                continue
            advancement = DATABASE_IDS.get(event_id[len(prefix):])
            if advancement:
                events.append((f"user:{user_id}", advancement, at_ms, paid, "db"))
    return users, events


def read_logs(log_dir, users):
    by_uuid = {uuid: user_id for user_id, (uuid, _) in users.items() if uuid}
    by_name = {name.casefold(): user_id for user_id, (_, name) in users.items()}
    names = {}
    events = []
    seen = set()
    files = sorted(path for path in log_dir.glob("*.log*")
                   if path.name.endswith((".log", ".log.gz")))
    for path in files:
        opener = gzip.open if path.suffix == ".gz" else open
        with opener(path, "rt", encoding="utf-8", errors="replace") as stream:
            for line in stream:
                match = LOG_EVENT.search(line)
                if not match:
                    continue
                at, uuid, name, advancement, paid = match.groups()
                if advancement not in REWARDS or int(paid) <= 0:
                    continue
                user_id = by_uuid.get(uuid.lower(), by_name.get(name.casefold()))
                key = f"user:{user_id}" if user_id is not None else f"uuid:{uuid.lower()}"
                event = (key, advancement, int(at), int(paid), "log")
                signature = event[:4]
                if signature not in seen:
                    events.append(event)
                    seen.add(signature)
                names[key] = name
    return events, names, len(files)


def merge_events(database_events, log_events):
    """Keep recorded payouts and add log payouts that have no database match."""
    unmatched = defaultdict(list)
    for index, event in enumerate(database_events):
        unmatched[(event[0], event[1], event[3])].append(index)
    result = list(database_events)
    matched = set()
    for event in log_events:
        candidates = unmatched[(event[0], event[1], event[3])]
        closest = min(candidates, key=lambda i: abs(database_events[i][2] - event[2]),
                      default=None)
        if closest is not None and abs(database_events[closest][2] - event[2]) <= 60_000:
            candidates.remove(closest)
            matched.add(closest)
        else:
            result.append(event)
    return result, matched


def self_check():
    sample = "minecraft:story/mine_stone"
    sunday = int(datetime(2026, 10, 4, 12, tzinfo=timezone.utc).timestamp() * 1000)
    assert REWARDS[sample] == (25, 6)
    assert DATABASE_IDS["minecraft:story_mine_stone"] == sample
    assert expected_amount(sample, 36, sunday) == 9
    assert expected_amount(HEART, 245, sunday + 5 * 86_400_000) == 0
    first = ("user:1", sample, sunday, 36, "db")
    duplicate = ("user:1", sample, sunday + 100, 36, "log")
    repeated = ("user:1", sample, sunday + 120_000, 36, "log")
    assert merge_events([first], [duplicate, repeated]) == ([first, repeated], {0})
    print("Self-check passed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("database", type=Path, nargs="?", default=Path("data/api/app.sqlite"))
    parser.add_argument("--logs", type=Path, help="Minecraft logs directory; defaults to the sibling data/minecraft/logs")
    parser.add_argument("--before", type=cutoff_ms, default=cutoff_ms(DEFAULT_BEFORE),
                        metavar="ISO_TIMESTAMP", help="exclusive cutoff with timezone offset")
    parser.add_argument("--self-test", action="store_true", help="check payout math and log/database matching")
    args = parser.parse_args()
    if args.self_test:
        self_check()
        return 0
    log_dir = args.logs or args.database.resolve().parent.parent / "minecraft" / "logs"
    users, db_events = read_database(args.database)
    if log_dir.is_dir():
        log_events, log_names, file_count = read_logs(log_dir, users)
    else:
        log_events, log_names, file_count = [], {}, 0
    if file_count == 0:
        print(f"Warning: no Minecraft logs found in {log_dir}; repeated or unrecorded payouts may be missing.", file=sys.stderr)

    rows = defaultdict(lambda: [0, 0, 0, 0])
    unresolved = []
    events, matched_db = merge_events(db_events, log_events)
    unmatched_db = sum(1 for index, event in enumerate(db_events)
                       if index not in matched_db and (event[1] == HEART or event[2] < args.before))
    if unmatched_db and file_count:
        print(f"Warning: {unmatched_db} relevant SQLite payouts have no matching log line; historical logs may be incomplete.", file=sys.stderr)
    for key, advancement, at_ms, paid, source in events:
        if advancement != HEART and at_ms >= args.before:
            continue
        expected = expected_amount(advancement, paid, at_ms)
        if expected is None or expected > paid:
            unresolved.append((key, advancement, at_ms, paid, source))
            continue
        row = rows[(key, advancement)]
        row[0] += 1
        row[1] += paid
        row[2] += expected
        row[3] += paid - expected

    by_player = defaultdict(list)
    for (key, advancement), values in rows.items():
        if values[3] > 0:
            by_player[key].append((advancement, values))
    totals = {key: sum(values[3] for _, values in items)
              for key, items in by_player.items()}

    print(f"Commit: {COMMIT}")
    print(f"Other advancements: payouts before {datetime.fromtimestamp(args.before / 1000, LONDON).isoformat()}")
    print("Hearts of the Stack: every paid event, expected reward 0")
    print(f"Sources: {len(db_events)} relevant SQLite events, {len(log_events)} relevant log events in {file_count} files")
    if not totals:
        print("No excess payouts found.")
    for key in sorted(totals, key=lambda k: (-totals[k], k)):
        if key.startswith("user:"):
            uuid, name = users[int(key[5:])]
        else:
            uuid, name = key[5:], log_names.get(key, key)
        print(f"\n{name} ({uuid or key})")
        for advancement, (count, paid, expected, excess) in sorted(
            by_player[key], key=lambda item: (-item[1][3], item[0])
        ):
            print(f"  {advancement}: paid {paid}, should {expected}, excess {excess} ({count} payout{'s' if count != 1 else ''})")
        print(f"  OWED TOTAL: {totals[key]} dabloons")
    complete = file_count > 0 and not unresolved and unmatched_db == 0
    label = "ALL PLAYERS TOTAL" if complete else "RECORDED TOTAL (INCOMPLETE)"
    print(f"\n{label}: {sum(totals.values())} dabloons")

    if unresolved:
        print(f"\nINCOMPLETE: {len(unresolved)} paid events could not be reconciled. Do not use the totals yet.", file=sys.stderr)
        for key, advancement, at_ms, paid, source in unresolved:
            print(f"  {key} {advancement} at {at_ms}: paid {paid} ({source})", file=sys.stderr)
    return 0 if complete else 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, sqlite3.Error, ValueError) as error:
        print(f"Report failed: {error}", file=sys.stderr)
        sys.exit(2)
