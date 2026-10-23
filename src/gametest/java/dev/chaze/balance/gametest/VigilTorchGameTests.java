package dev.chaze.balance.gametest;

import dev.chaze.balance.block.entity.VigilTorchBlockEntity;
import dev.chaze.balance.registry.ModBlocks;
import dev.chaze.balance.registry.ModItems;
import dev.chaze.balance.ward.VigilWards;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Real server-world tests, compiled separately from the distributable mod.
 * These methods require an authorized Minecraft world run before they count as
 * executed gameplay checks. Mock players exercise item use, not network clients.
 */
public final class VigilTorchGameTests implements FabricGameTest {
    private static final String ARENA = "balance-test:ward_arena";
    private static final String BATCH = "balance-vigil-runtime";
    private static final BlockPos SOURCE = new BlockPos(16, 1, 24);
    private static final BlockPos SECOND_SOURCE = new BlockPos(28, 1, 24);

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
    public void itemPlacementSupportLossAndDrops(TestContext context) {
        BlockPos wallTorch = new BlockPos(28, 2, 24);
        BlockPos wallSupport = wallTorch.west();
        context.setBlockState(SOURCE.down(), Blocks.STONE);
        context.setBlockState(wallSupport, Blocks.STONE);
        context.setBlockState(wallTorch.down(), Blocks.AIR);

        placeWithItem(context, SOURCE.down(), Direction.UP);
        placeWithItem(context, wallSupport, Direction.EAST);
        context.expectBlock(ModBlocks.VIGIL_TORCH, SOURCE);
        context.expectBlock(ModBlocks.WALL_VIGIL_TORCH, wallTorch);
        context.expectBlockProperty(wallTorch, WallTorchBlock.FACING, Direction.EAST);
        context.assertEquals(context.getBlockState(SOURCE).getLuminance(), 14, "standing luminance");
        context.assertEquals(context.getBlockState(wallTorch).getLuminance(), 14, "wall luminance");
        // Probe opposite outer edges so one source cannot mask missing registration of the other.
        context.assertTrue(protectedAt(context, center(context, SOURCE).add(-3.0, 0.0, 0.0)),
                "Standing item placement must activate its own ward");
        context.assertTrue(protectedAt(context, center(context, wallTorch).add(3.0, 0.0, 0.0)),
                "Wall item placement must activate its own ward");

        context.removeBlock(SOURCE.down());
        context.removeBlock(wallSupport);
        context.waitAndRun(5, () -> {
            context.expectBlock(Blocks.AIR, SOURCE);
            context.expectBlock(Blocks.AIR, wallTorch);
            context.expectItemsAt(ModItems.VIGIL_TORCH, SOURCE, 2.0, 1);
            context.expectItemsAt(ModItems.VIGIL_TORCH, wallTorch, 2.0, 1);
            context.assertFalse(protectedAt(context, center(context, SOURCE)), "Support loss must remove standing protection");
            context.assertFalse(protectedAt(context, center(context, wallTorch)), "Support loss must remove wall protection");
            context.complete();
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
    public void inclusiveThreeDimensionalRadiusAndEligibleMobs(TestContext context) {
        placeStanding(context, SOURCE);
        Vec3d center = center(context, SOURCE);
        context.assertTrue(protectedAt(context, center.add(14.0, 0.0, 0.0)), "Horizontal boundary must qualify");
        context.assertTrue(protectedAt(context, center.add(0.0, 14.0, 0.0)), "Vertical boundary must qualify");
        context.assertFalse(protectedAt(context, center.add(14.01, 0.0, 0.0)), "Beyond the radius must not qualify");
        context.assertFalse(protectedAt(context, center.add(0.0, 14.01, 0.0)), "Vertical distance must count");

        ZombieEntity inside = stationary(context, EntityType.ZOMBIE, center.add(2.0, 0.0, 0.0));
        ZombieEntity boundary = stationary(context, EntityType.ZOMBIE, center.add(14.0, 0.0, 0.0));
        ZombieEntity verticalBoundary = stationary(context, EntityType.ZOMBIE, center.add(0.0, 14.0, 0.0));
        ZombieEntity outside = stationary(context, EntityType.ZOMBIE, center.add(-14.1, 0.0, 0.0));
        MobEntity husk = stationary(context, EntityType.HUSK, center.add(5.0, 0.0, 0.0));
        MobEntity cow = stationary(context, EntityType.COW, center.add(8.0, 0.0, 0.0));
        context.waitAndRun(25, () -> {
            expectSlownessOne(context, inside);
            expectSlownessOne(context, boundary);
            expectSlownessOne(context, verticalBoundary);
            context.assertFalse(outside.hasStatusEffect(StatusEffects.SLOWNESS), "An outside zombie must remain unaffected");
            context.assertFalse(husk.hasStatusEffect(StatusEffects.SLOWNESS), "A husk must not be treated as an ordinary zombie");
            context.assertFalse(cow.hasStatusEffect(StatusEffects.SLOWNESS), "Unsupported passive mobs must remain unaffected");
            context.complete();
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 220)
    public void refreshPersistsAndLeavingExpiresNaturally(TestContext context) {
        placeStanding(context, SOURCE);
        ZombieEntity zombie = stationary(context, EntityType.ZOMBIE, center(context, SOURCE).add(2.0, 0.0, 0.0));
        context.waitAndRun(100, () -> {
            expectSlownessOne(context, zombie); // Beyond the initial 60-tick duration: refresh must have run.
            Vec3d outside = context.getAbsolute(new Vec3d(45.5, 1.5, 24.5));
            zombie.refreshPositionAndAngles(outside.x, outside.y, outside.z, 0.0F, 0.0F);
            context.assertFalse(protectedAt(context, outside), "The destination must be outside all wards");
            context.waitAndRun(80, () -> {
                context.assertFalse(zombie.hasStatusEffect(StatusEffects.SLOWNESS), "Leaving must stop refresh and allow expiry");
                context.assertTrue(protectedAt(context, center(context, SOURCE)), "Departure must not deactivate the source");
                context.complete();
            });
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 140)
    public void removingTorchStopsRefreshAndLeavesNoStaleWard(TestContext context) {
        placeStanding(context, SOURCE);
        ZombieEntity zombie = stationary(context, EntityType.ZOMBIE, center(context, SOURCE).add(2.0, 0.0, 0.0));
        context.waitAndRun(25, () -> {
            expectSlownessOne(context, zombie);
            context.removeBlock(SOURCE);
            context.assertFalse(protectedAt(context, zombie.getPos()), "Source removal must immediately invalidate the ward");
            context.waitAndRun(80, () -> {
                context.assertFalse(zombie.hasStatusEffect(StatusEffects.SLOWNESS), "Removed sources must not refresh Slowness");
                context.complete();
            });
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 240)
    public void overlappingSourcesSurviveOneRemovalWithoutStacking(TestContext context) {
        placeStanding(context, SOURCE);
        placeStanding(context, SECOND_SOURCE);
        ZombieEntity zombie = stationary(context, EntityType.ZOMBIE, center(context, SOURCE).add(6.0, 0.0, 0.0));
        context.waitAndRun(25, () -> {
            expectSlownessOne(context, zombie);
            context.removeBlock(SOURCE);
            context.assertTrue(protectedAt(context, zombie.getPos()), "The remaining overlapping source must protect");
            context.waitAndRun(80, () -> {
                expectSlownessOne(context, zombie);
                context.removeBlock(SECOND_SOURCE);
                context.assertFalse(protectedAt(context, zombie.getPos()), "Removing all sources must end protection");
                context.waitAndRun(80, () -> {
                    context.assertFalse(zombie.hasStatusEffect(StatusEffects.SLOWNESS), "All-source removal must allow natural expiry");
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 140)
    public void strongerLongerAndInfiniteEffectsArePreserved(TestContext context) {
        placeStanding(context, SOURCE);
        Vec3d center = center(context, SOURCE);
        ZombieEntity stronger = stationary(context, EntityType.ZOMBIE, center.add(2.0, 0.0, 0.0));
        ZombieEntity longer = stationary(context, EntityType.ZOMBIE, center.add(5.0, 0.0, 0.0));
        ZombieEntity infinite = stationary(context, EntityType.ZOMBIE, center.add(8.0, 0.0, 0.0));
        stronger.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 240, 2));
        longer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 240, 0));
        infinite.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, -1, 0));
        context.waitAndRun(100, () -> {
            context.expectEntityHasEffect(stronger, StatusEffects.SLOWNESS, 2);
            context.assertTrue(stronger.getStatusEffect(StatusEffects.SLOWNESS).getDuration() > 100,
                    "The stronger external duration must not be replaced by a short ward duration");
            expectSlownessOne(context, longer);
            context.assertTrue(longer.getStatusEffect(StatusEffects.SLOWNESS).getDuration() > 100,
                    "A longer Slowness I must not be shortened");
            expectSlownessOne(context, infinite);
            context.assertTrue(infinite.getStatusEffect(StatusEffects.SLOWNESS).isInfinite(),
                    "An infinite unrelated effect must remain infinite");
            context.complete();
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
    public void serializedMarkerRestoresAndReplacementPreservesProtection(TestContext context) {
        placeStanding(context, SOURCE);
        ServerWorld world = context.getWorld();
        BlockPos absolute = context.getAbsolutePos(SOURCE);
        BlockState state = world.getBlockState(absolute);
        BlockEntity original = world.getBlockEntity(absolute);
        context.assertTrue(original instanceof VigilTorchBlockEntity, "Placement must create the marker block entity");
        NbtCompound saved = original.createNbtWithIdentifyingData(world.getRegistryManager());

        // Exercises real NBT codecs and world add/remove hooks, not disk/chunk reload.
        world.removeBlockEntity(absolute);
        context.assertFalse(protectedAt(context, Vec3d.ofCenter(absolute)), "Unloading the marker must remove its source");
        BlockEntity restored = BlockEntity.createFromNbt(absolute, state, saved, world.getRegistryManager());
        context.assertTrue(restored instanceof VigilTorchBlockEntity, "The serialized marker ID must deserialize correctly");
        world.addBlockEntity(restored);
        context.assertTrue(protectedAt(context, Vec3d.ofCenter(absolute)), "The real load hook must restore protection");

        BlockEntity replacement = BlockEntity.createFromNbt(absolute, state, saved, world.getRegistryManager());
        context.assertTrue(replacement instanceof VigilTorchBlockEntity, "A replacement must deserialize correctly");
        world.addBlockEntity(replacement);
        context.assertTrue(restored.isRemoved(), "World replacement must unload the old marker");
        context.assertTrue(world.getBlockEntity(absolute) == replacement, "The replacement must be the authoritative marker");
        context.assertTrue(protectedAt(context, Vec3d.ofCenter(absolute)), "The old unload must not erase the replacement's ward");
        ZombieEntity zombie = stationary(context, EntityType.ZOMBIE, Vec3d.ofCenter(absolute).add(2.0, 0.0, 0.0));
        context.waitAndRun(25, () -> {
            expectSlownessOne(context, zombie);
            context.removeBlock(SOURCE);
            context.assertFalse(protectedAt(context, zombie.getPos()), "Removing the restored source must leave no stale protection");
            context.complete();
        });
    }

    @GameTest(templateName = ARENA, batchId = BATCH)
    public void queryingAnAbsentChunkDoesNotForceLoadIt(TestContext context) {
        placeStanding(context, SOURCE);
        ServerWorld world = context.getWorld();
        BlockPos far = context.getAbsolutePos(SOURCE).add(1_048_576, 0, 1_048_576);
        int chunkX = far.getX() >> 4;
        int chunkZ = far.getZ() >> 4;
        context.assertTrue(world.getChunkManager().getWorldChunk(chunkX, chunkZ) == null,
                "The distant probe chunk must initially be absent");
        context.assertFalse(protectedAt(context, Vec3d.ofCenter(far)), "An unrelated distant point must be unprotected");
        context.assertTrue(world.getChunkManager().getWorldChunk(chunkX, chunkZ) == null,
                "A ward query must not create or await a distant chunk");
        context.complete();
    }

    private static void placeStanding(TestContext context, BlockPos pos) {
        context.assertEquals(VigilWards.radius(), 14.0, "isolated test configuration ward radius");
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.VIGIL_TORCH);
        context.assertTrue(protectedAt(context, center(context, pos)), "Placed torches must enter the real server index");
    }

    private static void placeWithItem(TestContext context, BlockPos support, Direction face) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        BlockPos absoluteSupport = context.getAbsolutePos(support);
        Vec3d hit = Vec3d.ofCenter(absoluteSupport).add(Vec3d.of(face.getVector()).multiply(0.5));
        player.refreshPositionAndAngles(absoluteSupport.getX() + 3.5, absoluteSupport.getY() + 1.0,
                absoluteSupport.getZ() + 3.5, 0.0F, 0.0F);
        ItemStack stack = new ItemStack(ModItems.VIGIL_TORCH);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        ActionResult result = stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(hit, face, absoluteSupport, false)));
        context.assertTrue(result.isAccepted(), "The Vigil Torch item must accept valid vanilla placement");
        context.assertTrue(stack.isEmpty(), "Survival placement must consume one Vigil Torch");
    }

    private static <E extends MobEntity> E stationary(TestContext context, EntityType<E> type, Vec3d position) {
        E mob = type.create(context.getWorld());
        context.assertTrue(mob != null, "The vanilla test mob must be constructible");
        mob.setPersistent();
        mob.setAiDisabled(true);
        mob.setNoGravity(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.refreshPositionAndAngles(position.x, position.y, position.z, 0.0F, 0.0F);
        context.assertTrue(context.getWorld().spawnEntity(mob), "The test mob must spawn in the real world");
        return mob;
    }

    private static Vec3d center(TestContext context, BlockPos relative) {
        return Vec3d.ofCenter(context.getAbsolutePos(relative));
    }

    private static boolean protectedAt(TestContext context, Vec3d absolute) {
        return VigilWards.isProtected(context.getWorld(), absolute.x, absolute.y, absolute.z);
    }

    private static void expectSlownessOne(TestContext context, ZombieEntity zombie) {
        context.expectEntityHasEffect(zombie, StatusEffects.SLOWNESS, 0);
    }
}
