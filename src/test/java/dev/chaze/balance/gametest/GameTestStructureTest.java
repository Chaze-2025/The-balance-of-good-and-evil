package dev.chaze.balance.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

/** Actual Minecraft SNBT conversion only: no registries, server, world, or GameTest execution. */
class GameTestStructureTest {
    @Test
    void arenaSurvivesTheProviderConversionUsedByFabric() throws Exception {
        String source = Files.readString(Path.of(
                "src/gametest/resources/data/balance-test/gametest/structure/ward_arena.snbt"));
        NbtCompound converted = NbtHelper.fromNbtProviderString(source);
        assertEquals(3955, converted.getInt("DataVersion"));
        NbtList size = converted.getList("size", NbtElement.INT_TYPE);
        assertEquals(3, size.size());
        assertEquals(48, size.getInt(0));
        assertEquals(24, size.getInt(1));
        assertEquals(48, size.getInt(2));
        NbtList palette = converted.getList("palette", NbtElement.COMPOUND_TYPE);
        assertEquals(1, palette.size());
        assertEquals("minecraft:stone", palette.getCompound(0).getString("Name"));
        NbtList blocks = converted.getList("blocks", NbtElement.COMPOUND_TYPE);
        assertEquals(48 * 48, blocks.size());
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompound(i);
            assertEquals(0, block.getInt("state"));
            NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
            assertEquals(3, pos.size());
            assertEquals(0, pos.getInt(1));
            int x = pos.getInt(0);
            int z = pos.getInt(2);
            assertTrue(x >= 0 && x < 48 && z >= 0 && z < 48);
            assertTrue(positions.add(x * 48 + z), "Floor coordinates must be unique");
        }
        assertEquals(48 * 48, positions.size());
        assertTrue(converted.getList("entities", NbtElement.COMPOUND_TYPE).isEmpty());
        assertFalse(converted.contains("data"), "Provider data must convert into real block entries");
    }

    @Test
    void rawVanillaNbtIsNotAnEquivalentFabricProviderTemplate() throws Exception {
        NbtCompound converted = NbtHelper.fromNbtProviderString("""
                {size:[1,1,1], palette:[{Name:"minecraft:stone"}],
                 blocks:[{pos:[0,0,0],state:0}], entities:[]}
                """);
        // Fabric's loader always performs provider conversion. A raw compound palette
        // becomes empty, leaving an invalid index rather than a usable stone block.
        assertTrue(converted.getList("palette", NbtElement.COMPOUND_TYPE).isEmpty());
        assertFalse(converted.getList("blocks", NbtElement.COMPOUND_TYPE).isEmpty());
    }
}
