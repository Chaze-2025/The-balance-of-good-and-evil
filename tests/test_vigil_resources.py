"""Check linked Phase 1 resources and common authoring regressions, not gameplay."""
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "validate_vigil_resources", Path(__file__).parents[1] / "tools/validate_vigil_resources.py")
vigil_resources = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(vigil_resources)


class VigilResourceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "resources"
        shutil.copytree(vigil_resources.RESOURCE_ROOT, self.root)

    def edit(self, path, change):
        target = self.root / path
        value = json.loads(target.read_text())
        change(value)
        target.write_text(json.dumps(value))

    def validate(self):
        return vigil_resources.validate_directory(self.root)

    def test_exported_resources_resolve_and_match_recipe_contract(self):
        report = self.validate()
        self.assertEqual(report["item"], "balance:vigil_torch")
        self.assertEqual(report["resources_checked"], len(vigil_resources.REQUIRED_RESOURCES))
        self.assertFalse(report["gameplay_tested"])

    def test_missing_block_model_is_reported(self):
        (self.root / "assets/balance/models/block/wall_vigil_torch.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing Vigil Torch resource.*wall_vigil_torch"):
            self.validate()

    def test_missing_texture_is_reported(self):
        (self.root / "assets/balance/textures/block/vigil_torch.png").unlink()
        with self.assertRaisesRegex(ValueError, "Missing Vigil Torch resource.*vigil_torch.png"):
            self.validate()

    def test_wrong_texture_reference_is_rejected(self):
        self.edit("assets/balance/models/item/vigil_torch.json",
                  lambda data: data["textures"].update(layer0="balance:block/../missing"))
        with self.assertRaisesRegex(ValueError, "texture binding"):
            self.validate()

    def test_wall_orientation_must_match_vanilla_template(self):
        self.edit("assets/balance/blockstates/wall_vigil_torch.json",
                  lambda data: data["variants"]["facing=north"].update(y=90))
        with self.assertRaisesRegex(ValueError, "rotation: north"):
            self.validate()

    def test_legacy_recipe_result_item_key_is_rejected(self):
        def legacy(data):
            data["result"]["item"] = data["result"].pop("id")
        self.edit("data/balance/recipe/vigil_torch.json", legacy)
        with self.assertRaisesRegex(ValueError, "1.21.1 id schema"):
            self.validate()

    def test_duplicate_ingredient_cannot_replace_amethyst(self):
        self.edit("data/balance/recipe/vigil_torch.json",
                  lambda data: data["ingredients"][2].update(item="minecraft:torch"))
        with self.assertRaisesRegex(ValueError, "one torch, gold nugget, and amethyst"):
            self.validate()

    def test_recipe_book_unlock_links_and_or_requirements_are_enforced(self):
        path = self.root / "data/balance/advancement/recipes/decorations/vigil_torch.json"
        original = path.read_text()
        mutations = (
            (lambda data: data["rewards"].update(recipes=["minecraft:torch"]), "reward must grant"),
            (lambda data: data["criteria"]["has_the_recipe"]["conditions"].update(recipe="minecraft:torch"),
             "criterion must refer"),
            (lambda data: data.update(requirements=[["has_the_recipe"], ["has_amethyst_shard"]]),
             "must OR"),
        )
        for change, message in mutations:
            with self.subTest(message=message):
                value = json.loads(original)
                change(value)
                path.write_text(json.dumps(value))
                with self.assertRaisesRegex(ValueError, message):
                    self.validate()

    def test_wall_loot_must_drop_the_standing_item(self):
        self.edit("data/balance/loot_table/blocks/wall_vigil_torch.json",
                  lambda data: data["pools"][0]["entries"][0].update(name="balance:wall_vigil_torch"))
        with self.assertRaisesRegex(ValueError, "Both variants must drop"):
            self.validate()

    def test_explosion_loot_condition_is_required(self):
        self.edit("data/balance/loot_table/blocks/vigil_torch.json",
                  lambda data: data["pools"][0].pop("conditions"))
        with self.assertRaisesRegex(ValueError, "explosion survival"):
            self.validate()

    def test_template_texture_dimensions_are_enforced(self):
        path = self.root / "assets/balance/textures/block/vigil_torch.png"
        data = bytearray(path.read_bytes())
        data[16:20] = struct.pack(">I", 32)
        path.write_bytes(data)
        with self.assertRaisesRegex(ValueError, "16x16, 8-bit RGBA"):
            self.validate()

    def test_status_translation_needs_one_radius_placeholder(self):
        self.edit("assets/balance/lang/en_us.json",
                  lambda data: data.update({"message.balance.vigil_ward_active": "Vigil Ward Active"}))
        with self.assertRaisesRegex(ValueError, "one radius placeholder"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
