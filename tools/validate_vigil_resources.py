#!/usr/bin/env python3
"""Check the Phase 1 torch's linked resources; no Minecraft renderer is simulated."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import struct
from typing import Callable

RESOURCE_ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+\Z")
REQUIRED_RESOURCES = (
    "assets/balance/blockstates/vigil_torch.json",
    "assets/balance/blockstates/wall_vigil_torch.json",
    "assets/balance/models/block/vigil_torch.json",
    "assets/balance/models/block/wall_vigil_torch.json",
    "assets/balance/models/item/vigil_torch.json",
    "assets/balance/textures/block/vigil_torch.png",
    "assets/balance/lang/en_us.json",
    "data/balance/recipe/vigil_torch.json",
    "data/balance/advancement/recipes/decorations/vigil_torch.json",
    "data/balance/loot_table/blocks/vigil_torch.json",
    "data/balance/loot_table/blocks/wall_vigil_torch.json",
)


def validate(read: Callable[[str], bytes]) -> dict:
    """Validate exported files read from a resource directory or distributable JAR.

    Built-in Minecraft model parents are the pinned 1.21.1 torch templates and
    generated-item model; all mod-local references must resolve. The generic
    asset checker separately checks PNG CRCs and JSON duplicate keys.
    """
    checked: set[str] = set()

    def require(condition, message):
        if not condition:
            raise ValueError(message)

    def raw(path):
        try:
            data = read(path)
        except (OSError, KeyError) as exc:
            raise ValueError(f"Missing Vigil Torch resource: {path}") from exc
        checked.add(path)
        return data

    def document(path):
        try:
            result = json.loads(raw(path))
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise ValueError(f"Malformed JSON: {path}") from exc
        require(isinstance(result, dict), f"Expected JSON object: {path}")
        return result

    def local_path(identifier, kind, suffix):
        require(isinstance(identifier, str) and ID.fullmatch(identifier),
                f"Invalid {kind} resource identifier: {identifier}")
        namespace, value = identifier.split(":", 1)
        require(namespace == "balance" and not any(p in ("", ".", "..") for p in value.split("/")),
                f"Expected a mod-local {kind} reference: {identifier}")
        return f"assets/{namespace}/{kind}/{value}{suffix}"

    png = raw("assets/balance/textures/block/vigil_torch.png")
    require(len(png) >= 33 and png[:8] == b"\x89PNG\r\n\x1a\n" and png[12:16] == b"IHDR",
            "Vigil Torch texture must be a PNG with an IHDR header")
    require(struct.unpack(">IIBB", png[16:26]) == (16, 16, 8, 6),
            "Vigil Torch template requires a 16x16, 8-bit RGBA texture")

    expected_models = {
        "balance:block/vigil_torch": ("minecraft:block/template_torch", "torch"),
        "balance:block/wall_vigil_torch": ("minecraft:block/template_torch_wall", "torch"),
        "balance:item/vigil_torch": ("minecraft:item/generated", "layer0"),
    }
    for model, (parent, texture_key) in expected_models.items():
        model_path = local_path(model, "models", ".json")
        value = document(model_path)
        require(value.get("parent") == parent, f"Wrong 1.21.1 torch model parent: {model_path}")
        texture = value.get("textures", {}).get(texture_key)
        require(texture == "balance:block/vigil_torch", f"Missing torch texture binding: {model_path}")
        raw(local_path(texture, "textures", ".png"))

    standing = document("assets/balance/blockstates/vigil_torch.json").get("variants", {})
    require(set(standing) == {""}, "Standing torch must have a default blockstate variant")
    require(standing[""].get("model") == "balance:block/vigil_torch", "Standing blockstate model mismatch")
    raw(local_path(standing[""].get("model"), "models", ".json"))
    wall = document("assets/balance/blockstates/wall_vigil_torch.json").get("variants", {})
    rotations = {"east": 0, "north": 270, "south": 90, "west": 180}
    require(set(wall) == {f"facing={f}" for f in rotations}, "Wall torch must cover all four horizontal facings")
    for facing, angle in rotations.items():
        variant = wall[f"facing={facing}"]
        require(variant.get("model") == "balance:block/wall_vigil_torch" and variant.get("y", 0) == angle,
                f"Wall torch has incorrect model/rotation: {facing}")
        raw(local_path(variant.get("model"), "models", ".json"))

    recipe = document("data/balance/recipe/vigil_torch.json")
    require(recipe.get("type") == "minecraft:crafting_shapeless" and recipe.get("category") == "misc",
            "Vigil Torch must use the 1.21.1 shapeless crafting recipe")
    ingredients = recipe.get("ingredients", [])
    require(isinstance(ingredients, list) and all(isinstance(i, dict) and set(i) == {"item"} for i in ingredients),
            "Recipe ingredients must use 1.21.1 item objects")
    require(Counter(i["item"] for i in ingredients) == Counter({"minecraft:torch": 1, "minecraft:gold_nugget": 1,
                                                              "minecraft:amethyst_shard": 1}),
            "Recipe must consume one torch, gold nugget, and amethyst shard")
    require(recipe.get("result") == {"id": "balance:vigil_torch", "count": 1},
            "Recipe result must use the 1.21.1 id schema and produce one Vigil Torch")

    advancement = document("data/balance/advancement/recipes/decorations/vigil_torch.json")
    require(advancement.get("parent") == "minecraft:recipes/root",
            "Recipe unlock must belong to the vanilla recipe advancement root")
    criteria = advancement.get("criteria", {})
    require(set(criteria) == {"has_amethyst_shard", "has_the_recipe"},
            "Recipe unlock must define ingredient and recipe-unlocked criteria")
    require(criteria["has_amethyst_shard"] == {
        "conditions": {"items": [{"items": "minecraft:amethyst_shard"}]},
        "trigger": "minecraft:inventory_changed"},
        "Recipe unlock must discover the Vigil Torch when an amethyst shard is obtained")
    require(criteria["has_the_recipe"] == {
        "conditions": {"recipe": "balance:vigil_torch"}, "trigger": "minecraft:recipe_unlocked"},
        "Recipe unlock criterion must refer to the Vigil Torch recipe")
    requirements = advancement.get("requirements", [])
    require(len(requirements) == 1 and set(requirements[0]) == set(criteria),
            "Recipe unlock must OR the ingredient and recipe-unlocked criteria")
    require(advancement.get("rewards") == {"recipes": ["balance:vigil_torch"]},
            "Recipe unlock reward must grant the Vigil Torch recipe")

    for block in ("vigil_torch", "wall_vigil_torch"):
        path = f"data/balance/loot_table/blocks/{block}.json"
        loot = document(path)
        pools = loot.get("pools", [])
        require(loot.get("type") == "minecraft:block" and len(pools) == 1, f"Expected one block loot pool: {path}")
        pool = pools[0]
        require(pool.get("rolls") == 1 and pool.get("bonus_rolls", 0) == 0,
                f"Torch must drop exactly one item: {path}")
        require(pool.get("entries") == [{"type": "minecraft:item", "name": "balance:vigil_torch"}],
                f"Both variants must drop the placeable Vigil Torch item: {path}")
        require(pool.get("conditions") == [{"condition": "minecraft:survives_explosion"}],
                f"Torch loot must respect explosion survival: {path}")

    lang = document("assets/balance/lang/en_us.json")
    require(lang.get("block.balance.vigil_torch") == "Vigil Torch", "Missing Vigil Torch block translation")
    require(lang.get("block.balance.wall_vigil_torch") == "Vigil Torch", "Missing wall torch translation")
    message = lang.get("message.balance.vigil_ward_active", "")
    require(isinstance(message, str) and message.count("%s") == 1,
            "Ward status translation must contain one radius placeholder")
    require(set(REQUIRED_RESOURCES) <= checked, "Not all expected Vigil resources were validated")
    return {"resources_checked": len(checked), "blocks": ["balance:vigil_torch", "balance:wall_vigil_torch"],
            "item": "balance:vigil_torch", "texture": "16x16 RGBA", "gameplay_tested": False}


def validate_directory(root: Path = RESOURCE_ROOT) -> dict:
    return validate(lambda path: (root / path).read_bytes())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=RESOURCE_ROOT)
    args = parser.parse_args()
    try:
        print(json.dumps(validate_directory(args.root), indent=2))
    except (OSError, ValueError, KeyError, TypeError, struct.error) as exc:
        parser.exit(1, f"Vigil Torch resource validation failed: {exc}\n")
