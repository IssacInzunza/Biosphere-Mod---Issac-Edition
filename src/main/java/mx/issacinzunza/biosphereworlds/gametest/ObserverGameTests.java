package mx.issacinzunza.biosphereworlds.gametest;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import mx.issacinzunza.biosphereworlds.easteregg.ObserverEntity;
import mx.issacinzunza.biosphereworlds.easteregg.ObserverEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real mixin/engine checks; JUnit intentionally never bootstraps Minecraft. */
@GameTestHolder("biosphereworlds_observer")
@PrefixGameTestTemplate(false)
public final class ObserverGameTests {
    private ObserverGameTests() { }

    @GameTest(template = "solar_empty")
    public static void vanillaSignalsEqualTheLiteralPreObserverMethods(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        helper.assertTrue(!ObserverEvents.applies(level), "Fixture must be vanilla");
        var pos = helper.absolutePos(new BlockPos(0, 1, 0));
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.REDSTONE_WIRE, Blocks.REPEATER,
                Blocks.COMPARATOR, Blocks.LEVER, Blocks.OBSERVER, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_BLOCK,
                Blocks.STONE_BUTTON, Blocks.DAYLIGHT_DETECTOR, Blocks.TARGET}) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                for (var direction : Direction.values()) {
                    helper.assertTrue(state.getSignal(level, pos, direction) == literalSignal(state, level, pos, direction),
                            "Observer changed vanilla weak signal: " + state);
                    helper.assertTrue(state.getDirectSignal(level, pos, direction) == literalDirectSignal(state, level, pos, direction),
                            "Observer changed vanilla direct signal: " + state);
                }
            }
        }
        helper.succeed();
    }

    // Literal BlockBehaviour.BlockStateBase methods from the pre-change Minecraft source:
    // sourcesAndCompiledWithNeoForge_42f2db420ab373c8c4bc64d3fb9c7f08c61dc5a2_output.jar,
    // net/minecraft/world/level/block/state/BlockBehaviour.java:674-675 and 694-695.
    private static int literalSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) throws Exception {
        // The old expression delegates directly to the block. Reflection only crosses its protected access.
        var method = net.minecraft.world.level.block.state.BlockBehaviour.class.getDeclaredMethod(
                "getSignal", BlockState.class, BlockGetter.class, BlockPos.class, Direction.class);
        method.setAccessible(true);
        return (int) method.invoke(state.getBlock(), state, level, pos, direction);
    }

    private static int literalDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) throws Exception {
        var method = net.minecraft.world.level.block.state.BlockBehaviour.class.getDeclaredMethod(
                "getDirectSignal", BlockState.class, BlockGetter.class, BlockPos.class, Direction.class);
        method.setAccessible(true);
        return (int) method.invoke(state.getBlock(), state, level, pos, direction);
    }

    @GameTest(template = "solar_empty", timeoutTicks = 400)
    public static void disabledObserverIsAnExactSignalNoOpInClassicWorld(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        var root = java.nio.file.Path.of("observer-test-" + java.util.UUID.randomUUID());
        try (var fixture = ClassicPersistenceGameTests.open(helper, root, "disabled", false, true)) {
            var level = fixture.level();
            helper.assertTrue(ObserverEvents.applies(level), "Fixture must use the classic generator");
            EasterEggConfig.ENABLED.set(false);
            for (var block : new net.minecraft.world.level.block.Block[]{Blocks.REDSTONE_BLOCK, Blocks.LEVER,
                    Blocks.STONE_BUTTON, Blocks.OBSERVER, Blocks.REPEATER}) {
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    for (var direction : Direction.values()) {
                        helper.assertTrue(state.getSignal(level, BlockPos.ZERO, direction) == literalSignal(state, level, BlockPos.ZERO, direction),
                                "Disabled Observer changed weak signal");
                        helper.assertTrue(state.getDirectSignal(level, BlockPos.ZERO, direction) == literalDirectSignal(state, level, BlockPos.ZERO, direction),
                                "Disabled Observer changed direct signal");
                    }
                }
            }
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(new net.minecraft.world.phys.Vec3(128, 200, 0), 0, 91, 0, 1, 400);
            light.tick();
            helper.assertTrue(light.isRemoved(), "Disabled Observer survived in classic world");
            ObserverEvents.levelTick(new LevelTickEvent.Post(() -> true, level));
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void vanillaTicksNeverCreateObserverPlayerState(GameTestHelper helper) {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "observer-vanilla"));
        var before = player.getPersistentData().copy();
        ObserverEvents.playerTick(new PlayerTickEvent.Post(player));
        ObserverEvents.levelTick(new LevelTickEvent.Post(() -> true, helper.getLevel()));
        helper.assertTrue(before.equals(player.getPersistentData()), "Vanilla player acquired an encounter clock");
        var light = new ObserverEntity(ObserverEntity.TYPE.get(), helper.getLevel());
        light.tick();
        helper.assertTrue(light.isRemoved(), "Externally created Observer survived in vanilla");
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void interferenceReaches64BlocksWithoutLoadingMissingChunks(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        var root = java.nio.file.Path.of("observer-radius-test-" + java.util.UUID.randomUUID());
        try (var fixture = ClassicPersistenceGameTests.open(helper, root, "radius", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            level.getChunk(0, 0);
            level.getChunk(4, 0);
            level.getChunk(-4, 0);
            var anchor = new BlockPos(8, 240, 8);
            var inside = anchor.offset(64, 0, 0);
            var negative = anchor.offset(-64, 0, 0);
            var outside = anchor.offset(65, 0, 0);
            for (var pos : java.util.List.of(inside, negative, outside)) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            var machine = anchor.offset(62, 0, 2);
            level.setBlock(machine, Blocks.CHEST.defaultBlockState(), 3);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var type = Class.forName("mx.issacinzunza.biosphereworlds.easteregg.ObserverEvents$Encounter");
            var constructor = type.getDeclaredConstructor(java.util.UUID.class, BlockPos.class, java.util.List.class, int.class);
            constructor.setAccessible(true);
            Object encounter = constructor.newInstance(java.util.UUID.randomUUID(), anchor, java.util.List.of(), 5000);
            var scan = type.getDeclaredMethod("scan", net.minecraft.server.level.ServerLevel.class);
            var sources = type.getDeclaredMethod("scanSources", net.minecraft.server.level.ServerLevel.class);
            scan.setAccessible(true);
            sources.setAccessible(true);
            scan.invoke(encounter, level);
            for (int tick = 0; tick < 100; tick++) sources.invoke(encounter, level);
            var found = type.getDeclaredField("sources");
            var machines = type.getDeclaredField("machines");
            found.setAccessible(true);
            machines.setAccessible(true);
            var positions = (java.util.Set<?>) found.get(encounter);
            helper.assertTrue(positions.contains(inside) && positions.contains(negative), "64-block boundary was not reached in both directions");
            helper.assertTrue(!positions.contains(outside), "Interference escaped its 64-block radius");
            helper.assertTrue(((java.util.List<?>) machines.get(encounter)).contains(machine), "Distant loaded machine was not collected");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Interference search loaded additional chunks");
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void observerCanFlyAboveBuildHeight(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        var root = java.nio.file.Path.of("observer-altitude-test-" + java.util.UUID.randomUUID());
        try (var fixture = ClassicPersistenceGameTests.open(helper, root, "altitude", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            level.getChunk(0, 0);
            var layout = mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen.classicLayout(level.getChunkSource().getGenerator());
            var point = new net.minecraft.world.phys.Vec3(8, level.getMaxBuildHeight() + layout.params().maxRadius() + 80, 8);
            helper.assertTrue(ObserverEvents.freeSpace(level, point), "Build ceiling incorrectly blocks an airborne Observer");
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(point, 0, 81, 0, 1, 400);
            helper.assertTrue(level.addFreshEntity(light), "Engine rejected the Observer above build height");
            light.tick();
            helper.assertTrue(!light.isRemoved() && light.getY() > level.getMaxBuildHeight(), "Observer cannot remain above the build ceiling");
            light.discard();
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", templateNamespace = "biosphereworlds_observer_create", timeoutTicks = 1200)
    public static void createReceiversPulseAndRestoreCurrentNetwork(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            BiosphereWorlds.LOGGER.info("Observer Create integration test skipped: optional Create is not installed");
            helper.succeed();
            return;
        }
        boolean enabled = EasterEggConfig.ENABLED.get();
        var root = java.nio.file.Path.of("observer-create-test-" + java.util.UUID.randomUUID());
        Object network = null;
        net.minecraft.server.level.ServerLevel testLevel = null;
        try (var fixture = ClassicPersistenceGameTests.open(helper, root, "links", false, true)) {
            var level = fixture.level();
            testLevel = level;
            network = Class.forName("com.simibubi.create.Create").getField("REDSTONE_LINK_NETWORK_HANDLER").get(null);
            network.getClass().getMethod("onLoadWorld", net.minecraft.world.level.LevelAccessor.class).invoke(network, level);
            EasterEggConfig.ENABLED.set(true);
            level.getChunk(0, 0);
            var txPos = new BlockPos(1, 220, 1);
            var rxPos = new BlockPos(4, 220, 1);
            var tx = createLink(level, txPos, false);
            var rx = createLink(level, rxPos, true);
            var transmit = tx.getClass().getMethod("transmit", int.class);
            var tick = rx.getClass().getMethod("tick");
            var received = rx.getClass().getMethod("getReceivedSignal");
            transmit.invoke(tx, 11);
            tick.invoke(rx);
            helper.assertTrue((int) received.invoke(rx) == 11, "Actual Create network did not transmit its initial signal");

            var adapter = Class.forName("mx.issacinzunza.biosphereworlds.easteregg.ObserverLinks");
            var find = adapter.getDeclaredMethod("find", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
            find.setAccessible(true);
            Object target = find.invoke(null, level, rxPos);
            helper.assertTrue(target != null, "Optional adapter did not bind to the actual Create receiver");
            helper.assertTrue(find.invoke(null, level, txPos) == null, "Adapter must not treat transmitters as receivers");
            var update = target.getClass().getDeclaredMethod("update", net.minecraft.server.level.ServerLevel.class, int.class);
            update.setAccessible(true);

            update.invoke(target, level, 0);
            tick.invoke(rx);
            helper.assertTrue((int) received.invoke(rx) == 0 && !rx.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),
                    "Low pulse failed to switch off receiver signal/indicator");
            update.invoke(target, level, 15);
            tick.invoke(rx);
            helper.assertTrue((int) received.invoke(rx) == 15 && rx.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),
                    "High pulse failed to switch on receiver signal/indicator");

            transmit.invoke(tx, 7); // User changes the actual input while interference is active.
            update.invoke(target, level, 15);
            helper.assertTrue((int) received.invoke(rx) == 15, "Network update escaped an active pulse");
            EasterEggConfig.ENABLED.set(false);
            helper.assertTrue(find.invoke(null, level, rxPos) == null, "Disabled integration collected a new receiver");
            update.invoke(target, level, -1); // Cleanup must work even after the master switch is disabled.
            tick.invoke(rx);
            helper.assertTrue((int) received.invoke(rx) == 7, "Restoration reused a stale pre-interference signal");
            EasterEggConfig.ENABLED.set(true);

            var type = Class.forName("com.simibubi.create.content.redstone.link.LinkBehaviour");
            var behaviourType = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType");
            Object link = rx.getClass().getMethod("getBehaviour", behaviourType).invoke(rx, type.getField("TYPE").get(null));
            type.getMethod("setFrequency", boolean.class, net.minecraft.world.item.ItemStack.class)
                    .invoke(link, true, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            var frequency = rx.saveWithoutMetadata(level.registryAccess()).get("FrequencyFirst");
            update.invoke(target, level, 15);
            update.invoke(target, level, -1);
            tick.invoke(rx);
            helper.assertTrue((int) received.invoke(rx) == 0, "Restoration ignored a frequency changed during the encounter");
            helper.assertTrue(frequency.equals(rx.saveWithoutMetadata(level.registryAccess()).get("FrequencyFirst")),
                    "Interference changed the player's frequency item");

            update.invoke(target, level, 15);
            level.setBlock(rxPos, Blocks.STONE.defaultBlockState(), 3);
            var replacement = createLink(level, rxPos, true);
            replacement.getClass().getMethod("setSignal", int.class).invoke(replacement, 3);
            update.invoke(target, level, -1);
            helper.assertTrue((int) received.invoke(replacement) == 3, "Old encounter touched a replacement block entity");
        } finally {
            try {
                if (network != null) network.getClass().getMethod("onUnloadWorld", net.minecraft.world.level.LevelAccessor.class).invoke(network, testLevel);
            } finally { EasterEggConfig.ENABLED.set(enabled); }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void interiorLightsRespectThinGlassAndTheirSphereBoundary(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-interior-" + java.util.UUID.randomUUID()), "inside", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            var layout = mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen.classicLayout(level.getChunkSource().getGenerator());
            var sphere = layout.sphereContaining(8, 8);
            var point = new net.minecraft.world.phys.Vec3(sphere.centerX() + 2, sphere.centerY() + sphere.radius() - 18, sphere.centerZ() + 2);
            level.getChunk((int) point.x >> 4, (int) point.z >> 4);
            helper.assertTrue(ObserverEvents.freeSpace(level, point, true), "Clear interior sky rejected");
            helper.assertTrue(!ObserverEvents.freeSpace(level, point), "Exterior variant entered a sphere");
            var outside = new net.minecraft.world.phys.Vec3(point.x, sphere.centerY() + sphere.radius() + 8, point.z);
            helper.assertTrue(!ObserverEvents.freeSpace(level, outside, true), "Interior variant escaped its sphere");
            helper.assertTrue(ObserverEvents.freeSpace(level, outside), "Exterior air above shell rejected");
            var wall = BlockPos.containing(point).offset(3, 0, 0);
            for (var pos : BlockPos.betweenClosed(wall.offset(0, -3, -3), wall.offset(0, 3, 3)))
                level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(!ObserverEvents.freePath(level, point, point.add(6, 0, 0), true), "Fast light crossed a thin glass wall");
            helper.assertTrue(level.getBlockState(wall).is(Blocks.GLASS), "Glass was changed");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Swept collision loaded chunks");
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void exteriorSpawnUsesActualShellAboveTheCenter(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-center-" + java.util.UUID.randomUUID()), "center", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            var layout = mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen.classicLayout(level.getChunkSource().getGenerator());
            var sphere = layout.sphereContaining(8, 8);
            var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "observer-center"));
            player.setPos(sphere.centerX(), sphere.centerY(), sphere.centerZ());
            for (int x = -1; x <= 1; x++) for (int z = -2; z <= 5; z++)
                level.getChunk((((int) sphere.centerX() + 80) >> 4) + x, ((int) sphere.centerZ() >> 4) + z);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var random = new java.util.Random(13) { @Override public double nextDouble() { return 0; } };
            var spawn = ObserverEvents.class.getDeclaredMethod("spawn", net.minecraft.server.level.ServerPlayer.class,
                    int.class, boolean.class, java.util.Random.class, mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.class, Boolean.class);
            spawn.setAccessible(true);
            helper.assertTrue((boolean) spawn.invoke(null, player, 3, false, random,
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false), "Center observer failed to spawn above actual shell");
            var runtime = ObserverEvents.class.getDeclaredField("RUNTIME");
            runtime.setAccessible(true);
            @SuppressWarnings("unchecked")
            var attachment = (java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<ObserverEvents.Runtime>>) runtime.get(null);
            var encounters = ObserverEvents.Runtime.class.getDeclaredField("encounters");
            encounters.setAccessible(true);
            var encounter = ((java.util.List<?>) encounters.get(level.getData(attachment))).getFirst();
            var lights = encounter.getClass().getDeclaredField("lights");
            lights.setAccessible(true);
            for (var value : (java.util.List<?>) lights.get(encounter)) {
                var light = (ObserverEntity) value;
                helper.assertTrue(light.getY() > sphere.centerY() + sphere.radius(), "Light remained inside shell");
            }
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Spawn loaded chunks");
            ObserverEvents.levelTick(new LevelTickEvent.Post(() -> true, level)); // Missing witness cleans up the transient group.
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void exceptionalAttackIsSingleLimitedAndDoesNotChangeVelocity(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-attack-" + java.util.UUID.randomUUID()), "attack", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            for (int x = 0; x <= 11; x++) level.getChunk(x, 0);
            var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "observer-target")) {
                @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
            };
            // FakePlayer is normally invulnerable; this fixture exercises actual survival damage.
            var spawnProtection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(player, 0);
            player.setPos(8, 230, 8);
            level.players().add(player); // Test witness only: do not create asynchronous player chunk tickets.
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(player.getEyePosition(), 0, 91, 0, 1, 400, player.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.ATTACK, false);
            setAge(light, 200);
            light.setPos(player.getEyePosition().add(160, 40, 0));
            level.addFreshEntity(light);
            var velocity = new net.minecraft.world.phys.Vec3(0.4, 0.1, -0.3);
            player.setDeltaMovement(velocity);
            player.setHealth(20);
            Object encounter = encounter(player.getUUID(), player.blockPosition(), java.util.List.of(light));
            tickEncounter(encounter, level);
            helper.assertTrue(player.getHealth() == 20 && light.warning(), "Ranged pulse skipped its two-second warning");
            setAge(light, 240);
            tickEncounter(encounter, level);
            helper.assertTrue(player.getHealth() == 18, "Attack did not deal exactly one unarmored heart");
            helper.assertTrue(light.beam(0).length() > 128, "Pulse did not reach beyond the old sight limit");
            helper.assertTrue(player.getDeltaMovement().equals(velocity), "Pulse changed flight velocity");
            player.invulnerableTime = 0;
            tickEncounter(encounter, level);
            helper.assertTrue(player.getHealth() == 18, "Encounter attacked twice");
            player.setHealth(3);
            player.invulnerableTime = 0;
            Object lowHealth = encounter(player.getUUID(), player.blockPosition(), java.util.List.of(light));
            tickEncounter(lowHealth, level);
            setAge(light, 280);
            tickEncounter(lowHealth, level);
            helper.assertTrue(player.getHealth() == 2, "Pulse failed to preserve its health margin");
            player.setHealth(20);
            player.invulnerableTime = 0;
            Object shielded = encounter(player.getUUID(), player.blockPosition(), java.util.List.of(light));
            tickEncounter(shielded, level);
            for (var pos : BlockPos.betweenClosed(new BlockPos(88, 220, 6), new BlockPos(88, 285, 10)))
                level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            setAge(light, 320);
            tickEncounter(shielded, level);
            helper.assertTrue(player.getHealth() == 20 && light.beam(0).length() > 0 && light.beam(0).length() < 100,
                    "A wall added during the warning failed to stop the visible pulse");
            light.discard();
            level.players().remove(player);
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void flybyFollowsMovingPlayerWithoutVehicleDependencies(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-flyby-" + java.util.UUID.randomUUID()), "flyby", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            for (int x = 6; x <= 9; x++) for (int z = -3; z <= 6; z++) level.getChunk(x, z);
            var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "observer-pilot"));
            player.setPos(8, 700, 8);
            level.players().add(player); // The fixture needs UUID lookup, not a network-connected player.
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(player.getEyePosition(), 0, 92, 0, 1, 400, player.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.FLYBY, false);
            level.addFreshEntity(light);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            for (int age = 1; age <= 200; age++) {
                player.setPos(8 + age * 0.1, 700, 8);
                setAge(light, age);
                var before = light.position();
                light.tick();
                helper.assertTrue(!light.isRemoved() && before.distanceTo(light.position()) <= 2.01, "Flyby jumped or disappeared");
                helper.assertTrue(player.distanceToSqr(light) >= 80 * 80, "Flyby lost its stand-off distance");
            }
            helper.assertTrue(light.getX() > 130 && player.distanceToSqr(light) < 180 * 180, "Light did not accompany its moving witness at a distance");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Flyby forced chunk loading");
            player.setPos(light.position().add(8, 0, 0));
            light.tick();
            helper.assertTrue(light.isRemoved(), "A player reached a close view of the light");
            level.players().remove(player);
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    private static Object encounter(java.util.UUID owner, BlockPos anchor, java.util.List<ObserverEntity> lights) throws Exception {
        var type = Class.forName("mx.issacinzunza.biosphereworlds.easteregg.ObserverEvents$Encounter");
        var constructor = type.getDeclaredConstructor(java.util.UUID.class, BlockPos.class, java.util.List.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(owner, anchor, lights, 0);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> encounters(net.minecraft.server.level.ServerLevel level) throws Exception {
        var field = ObserverEvents.class.getDeclaredField("RUNTIME");
        field.setAccessible(true);
        var attachment = (java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<ObserverEvents.Runtime>>) field.get(null);
        var list = ObserverEvents.Runtime.class.getDeclaredField("encounters");
        list.setAccessible(true);
        return (java.util.List<Object>) list.get(level.getData(attachment));
    }

    private static net.neoforged.neoforge.common.util.FakePlayer vulnerablePlayer(net.minecraft.server.level.ServerLevel level, String name,
            double x, double y, double z) throws Exception {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        var protection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        protection.setAccessible(true);
        protection.setInt(player, 0);
        player.setPos(x, y, z);
        level.players().add(player);
        return player;
    }

    private static void loadBlastChunks(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center) {
        for (int x = ((int) Math.floor(center.x) - 18) >> 4; x <= ((int) Math.floor(center.x) + 18) >> 4; x++)
            for (int z = ((int) Math.floor(center.z) - 18) >> 4; z <= ((int) Math.floor(center.z) + 18) >> 4; z++) level.getChunk(x, z);
    }

    private static AutoCloseable includeInExplosionQueries(net.minecraft.server.level.ServerPlayer player) {
        // These fixtures deliberately avoid registering network players/chunk tickets.
        // Supply that missing query result through the normal explosion event; damage,
        // exposure, resistance, knockback and death still run in Minecraft itself.
        java.util.function.Consumer<net.neoforged.neoforge.event.level.ExplosionEvent.Detonate> listener = event -> {
            if (event.getLevel() == player.level() && event.getExplosion().getDirectSourceEntity() instanceof ObserverEntity
                    && !event.getAffectedEntities().contains(player)) event.getAffectedEntities().add(player);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void retaliationLocksTheAggressorAndNeverRedirectsToAWitness(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        AutoCloseable explosionQuery = null;
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-retaliation-" + java.util.UUID.randomUUID()), "hostile", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            for (int x = 0; x <= 11; x++) level.getChunk(x, 0);
            var witness = vulnerablePlayer(level, "observer-witness", 8, 230, 8);
            var aggressor = vulnerablePlayer(level, "observer-aggressor", 80, 230, 8);
            explosionQuery = includeInExplosionQueries(aggressor);
            loadBlastChunks(level, aggressor.getEyePosition());
            aggressor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(200);
            aggressor.setAbsorptionAmount(200);
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(new net.minecraft.world.phys.Vec3(168, 270, 8), 0, 21, 0, 1, 400, witness.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false);
            level.addFreshEntity(light);
            Object encounter = encounter(witness.getUUID(), witness.blockPosition(), java.util.List.of(light));
            encounters(level).add(encounter);
            helper.assertTrue(!light.hurt(level.damageSources().generic(), 10) && !light.hostile(), "Unknown damage blamed a nearby witness");
            var arrow = new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
            arrow.setOwner(aggressor);
            arrow.setPos(aggressor.getEyePosition());
            arrow.setDeltaMovement(light.position().subtract(arrow.position()));
            level.addFreshEntity(arrow);
            ObserverEvents.projectileTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(arrow));
            helper.assertTrue(light.hostile() && light.warning(), "Swept projectile attack failed to trigger a warning");
            var hostilePosition = light.positionAt(20);
            light.hostile(false);
            helper.assertTrue(hostilePosition.distanceTo(light.positionAt(20)) > 8, "A solitary hostile light kept its passive trajectory");
            light.hostile(true);
            // A second attacker must not redirect the pending pulse away from the first aggressor.
            light.hurt(level.damageSources().playerAttack(witness), 1);
            aggressor.setPos(84, 230, 8);
            setAge(light, 59);
            tickEncounter(encounter, level);
            helper.assertTrue(aggressor.getAbsorptionAmount() == 200 && light.beam(0).lengthSqr() > 0 && light.warning(),
                    "Retaliation skipped its full three-second visible charge");
            helper.assertTrue(light.position().add(light.beam(0)).distanceToSqr(aggressor.getEyePosition()) < 0.001,
                    "Charging beam failed to track the moving aggressor");
            setAge(light, 60);
            tickEncounter(encounter, level);
            helper.assertTrue(aggressor.getAbsorptionAmount() < 200 && witness.getHealth() == 20, "Retaliation hit the wrong player");
            float remainingAbsorption = aggressor.getAbsorptionAmount();
            aggressor.invulnerableTime = 0;
            light.hurt(level.damageSources().playerAttack(aggressor), 1);
            setAge(light, 100);
            tickEncounter(encounter, level);
            helper.assertTrue(aggressor.getAbsorptionAmount() == remainingAbsorption, "Repeated attacks caused an unbounded retaliation loop");
            encounters(level).clear();
            Object abandoned = encounter(witness.getUUID(), witness.blockPosition(), java.util.List.of(light));
            encounters(level).add(abandoned);
            light.hurt(level.damageSources().playerAttack(aggressor), 1);
            level.players().remove(aggressor);
            setAge(light, 160);
            tickEncounter(abandoned, level);
            helper.assertTrue(witness.getHealth() == 20, "Disconnected aggressor's retaliation was reassigned to a witness");
            arrow.discard();
            light.discard();
            level.players().remove(witness);
            encounters(level).clear();
        } finally {
            if (explosionQuery != null) explosionQuery.close();
            EasterEggConfig.ENABLED.set(enabled);
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void chargedRetaliationIsLethalDestructiveAndNeverLoadsBlastChunks(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        AutoCloseable explosionQuery = null;
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-blast-" + java.util.UUID.randomUUID()), "blast", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            for (int x = 0; x <= 11; x++) level.getChunk(x, 0);
            var player = vulnerablePlayer(level, "observer-blast", 80, 230, 8);
            explosionQuery = includeInExplosionQueries(player);
            loadBlastChunks(level, player.getEyePosition());
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(new net.minecraft.world.phys.Vec3(168, 270, 8), 0, 31, 0, 1, 400, player.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false);
            level.addFreshEntity(light);
            var glass = player.blockPosition().below();
            var resistant = player.blockPosition().offset(4, 0, 0);
            var outside = player.blockPosition().offset(17, 0, 0);
            level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
            level.setBlock(resistant, Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(outside, Blocks.GLASS.defaultBlockState(), 3);
            var detonate = ObserverEvents.class.getDeclaredMethod("detonate", net.minecraft.server.level.ServerLevel.class,
                    ObserverEntity.class, net.minecraft.world.phys.Vec3.class);
            detonate.setAccessible(true);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(!(boolean) detonate.invoke(null, level, light, new net.minecraft.world.phys.Vec3(2000, 230, 2000))
                    && level.getChunkSource().getLoadedChunksCount() == loaded, "Blast forced an unloaded chunk");
            EasterEggConfig.ENABLED.set(false);
            helper.assertTrue(!(boolean) detonate.invoke(null, level, light, player.getEyePosition()), "Disabled observer detonated");
            EasterEggConfig.ENABLED.set(true);
            helper.assertTrue(!(boolean) detonate.invoke(null, helper.getLevel(), light, player.getEyePosition()), "Observer detonated in vanilla");
            Object encounter = encounter(player.getUUID(), player.blockPosition(), java.util.List.of(light));
            encounters(level).add(encounter);
            light.hurt(level.damageSources().playerAttack(player), 1);
            for (int age = 0; age < 60; age++) {
                setAge(light, age);
                tickEncounter(encounter, level);
                helper.assertTrue(player.getHealth() == 20 && level.getBlockState(glass).is(Blocks.GLASS)
                        && light.beam(0).lengthSqr() > 0, "Charge damaged its target early or lost the beam");
            }
            setAge(light, 60);
            tickEncounter(encounter, level);
            helper.assertTrue(player.getHealth() <= 0, "Retaliation preserved the player's health instead of applying real explosion damage");
            helper.assertTrue(level.getBlockState(glass).isAir(), "Blast did not break nearby biosphere glass");
            helper.assertTrue(level.getBlockState(resistant).is(Blocks.OBSIDIAN) && level.getBlockState(outside).is(Blocks.GLASS),
                    "Blast ignored resistance or exceeded the 16-block radius");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Charged explosion loaded extra chunks");
            light.discard();
            level.players().remove(player);
            encounters(level).clear();
        } finally {
            if (explosionQuery != null) explosionQuery.close();
            EasterEggConfig.ENABLED.set(enabled);
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void chargedRetaliationExplodesOnCoverInsteadOfThroughIt(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-cover-" + java.util.UUID.randomUUID()), "cover", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            for (int x = 0; x <= 11; x++) level.getChunk(x, 0);
            var player = vulnerablePlayer(level, "observer-cover", 80, 230, 8);
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(new net.minecraft.world.phys.Vec3(168, 270, 8), 0, 32, 0, 1, 400, player.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false);
            level.addFreshEntity(light);
            Object encounter = encounter(player.getUUID(), player.blockPosition(), java.util.List.of(light));
            encounters(level).add(encounter);
            light.hurt(level.damageSources().playerAttack(player), 1);
            tickEncounter(encounter, level);
            helper.assertTrue(light.position().add(light.beam(0)).distanceToSqr(player.getEyePosition()) < 0.001,
                    "Warning beam did not initially aim at its aggressor");
            // Introduce cover after the warning starts, far enough from the player
            // that its destruction cannot reach them via the explosion radius.
            var wall = new BlockPos(120, 248, 8);
            for (var pos : BlockPos.betweenClosed(wall.offset(0, -4, -2), wall.offset(0, 5, 2)))
                level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            loadBlastChunks(level, wall.getCenter());
            setAge(light, 59);
            tickEncounter(encounter, level);
            var endpoint = light.position().add(light.beam(0));
            helper.assertTrue(endpoint.x > 120 && endpoint.x < 122 && player.getHealth() == 20,
                    "Warning beam ignored newly placed cover");
            var impact = BlockPos.containing(endpoint.add(-0.01, 0, 0));
            helper.assertTrue(level.getBlockState(impact).is(Blocks.GLASS), "Fixture missed its glass wall");
            setAge(light, 60);
            tickEncounter(encounter, level);
            helper.assertTrue(level.getBlockState(impact).isAir() && player.getHealth() == 20,
                    "Explosion failed to break cover or teleported through it onto the player");
            light.discard();
            level.players().remove(player);
            encounters(level).clear();
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void distantEncountersCoexistAndSurviveTheirInitialWitnessLeaving(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-multiplayer-" + java.util.UUID.randomUUID()), "groups", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            level.getChunk(10, 0);
            level.getChunk(74, 0);
            var first = vulnerablePlayer(level, "observer-first", 8, 650, 8);
            var second = vulnerablePlayer(level, "observer-second", 24, 650, 8);
            var distant = vulnerablePlayer(level, "observer-distant", 1032, 650, 8);
            var a = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            a.configure(new net.minecraft.world.phys.Vec3(168, 700, 8), 0, 22, 0, 1, 600, first.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false);
            var b = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            b.configure(new net.minecraft.world.phys.Vec3(1192, 700, 8), 0, 23, 0, 1, 600, distant.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false);
            level.addFreshEntity(a);
            level.addFreshEntity(b);
            Object groupA = encounter(first.getUUID(), first.blockPosition(), java.util.List.of(a));
            Object groupB = encounter(distant.getUUID(), distant.blockPosition(), java.util.List.of(b));
            encounters(level).add(groupA);
            var nearby = ObserverEvents.class.getDeclaredMethod("nearby", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.phys.Vec3.class);
            nearby.setAccessible(true);
            helper.assertTrue((boolean) nearby.invoke(null, level, first.position()) && !(boolean) nearby.invoke(null, level, distant.position()),
                    "Encounter exclusion became a world-wide lock");
            encounters(level).add(groupB);
            ObserverEvents.levelTick(new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(encounters(level).size() == 2, "Distant encounters did not coexist");
            level.players().remove(first);
            ObserverEvents.levelTick(new LevelTickEvent.Post(() -> true, level));
            var owner = groupA.getClass().getDeclaredField("owner");
            owner.setAccessible(true);
            helper.assertTrue(encounters(level).size() == 2 && owner.get(groupA).equals(second.getUUID()) && !a.isRemoved(),
                    "Encounter ended instead of selecting a remaining witness");
            level.players().remove(second);
            ObserverEvents.levelTick(new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(encounters(level).size() == 1 && a.isRemoved() && !b.isRemoved(), "Empty group cleanup affected a distant group");
            // A travelling witness can stop its own encounter even after leaving the original anchor.
            distant.setPos(2200, 650, 8);
            var stop = ObserverEvents.class.getDeclaredMethod("stop", net.minecraft.commands.CommandSourceStack.class);
            stop.setAccessible(true);
            helper.assertTrue((int) stop.invoke(null, distant.createCommandSourceStack()) == 1 && encounters(level).isEmpty(),
                    "Travelling witness could not stop its encounter");
            level.players().remove(distant);
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void clearanceProtectsSweptPathsAndGenericVehicleBounds(GameTestHelper helper) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-clearance-" + java.util.UUID.randomUUID()), "vessel", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            var witness = vulnerablePlayer(level, "observer-clearance", 8, 700, 8);
            var eye = witness.getEyePosition();
            helper.assertTrue(ObserverEvents.clearOfWitnesses(level, eye.add(64, 0, 0), eye.add(64, 0, 0), false), "Exact exterior boundary was rejected");
            helper.assertTrue(!ObserverEvents.clearOfWitnesses(level, eye.add(63.99, 0, -2), eye.add(63.99, 0, 2), false),
                    "Swept path crossed the player's exclusion zone");
            helper.assertTrue(!ObserverEvents.clearOfWitnesses(level, eye.add(31, 0, 0), eye.add(31, 0, 0), true), "Interior distance became a close flyby");
            level.players().remove(witness);
            var vessel = new net.minecraft.world.entity.vehicle.Boat(net.minecraft.world.entity.EntityType.BOAT, level);
            vessel.setBoundingBox(new net.minecraft.world.phys.AABB(80, 690, -20, 220, 750, 30));
            var pilot = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "observer-hull")) {
                @Override public net.minecraft.world.entity.Entity getRootVehicle() { return vessel; }
            };
            pilot.setPos(8, 700, 8);
            level.players().add(pilot);
            var point = new net.minecraft.world.phys.Vec3(168, 720, 8);
            helper.assertTrue(!ObserverEvents.clearOfWitnesses(level, point, point.add(2, 0, 0), false),
                    "Generic vessel hull was crossed despite distant pilot");
            level.players().remove(pilot);
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    private static void tickEncounter(Object encounter, net.minecraft.server.level.ServerLevel level) throws Exception {
        var tick = encounter.getClass().getDeclaredMethod("tick", net.minecraft.server.level.ServerLevel.class);
        tick.setAccessible(true);
        tick.invoke(encounter, level);
    }

    private static void setAge(ObserverEntity light, int age) throws Exception {
        var field = ObserverEntity.class.getDeclaredField("START");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var start = (net.minecraft.network.syncher.EntityDataAccessor<Long>) field.get(null);
        light.getEntityData().set(start, light.level().getGameTime() - age);
    }

    @GameTest(template = "solar_empty", templateNamespace = "biosphereworlds_observer_cc", timeoutTicks = 1200)
    public static void actualCcNetworkDropsOnlyLocalPulsesAndRecovers(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            BiosphereWorlds.LOGGER.info("Observer CC integration test skipped: optional CC:Tweaked is not installed");
            helper.succeed();
            return;
        }
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-cc-" + java.util.UUID.randomUUID()), "wireless", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            var center = new net.minecraft.world.phys.Vec3(8, 240, 8);
            var senderType = Class.forName("dan200.computercraft.api.network.PacketSender");
            var receiverType = Class.forName("dan200.computercraft.api.network.PacketReceiver");
            var packetType = Class.forName("dan200.computercraft.api.network.Packet");
            var networkType = Class.forName("dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork");
            Object network = networkType.getConstructor().newInstance();
            var inside = new java.util.concurrent.atomic.AtomicInteger();
            var outside = new java.util.concurrent.atomic.AtomicInteger();
            var delivered = new java.util.concurrent.atomic.AtomicReference<Object>();
            Object near = endpoint(receiverType, level, center.add(64, 0, 0), inside, delivered);
            Object far = endpoint(receiverType, level, center.add(65, 0, 0), outside, delivered);
            networkType.getMethod("addReceiver", receiverType).invoke(network, near);
            networkType.getMethod("addReceiver", receiverType).invoke(network, far);
            Object sender = endpoint(senderType, level, center.add(100, 0, 0), null, null);
            Object packet = packetType.getConstructor(int.class, int.class, Object.class, senderType).newInstance(7, 9, java.util.Map.of("test", "unaltered"), sender);
            var transmit = networkType.getMethod("transmitSameDimension", packetType, double.class);
            transmit.invoke(network, packet, 256.0);
            helper.assertTrue(inside.get() == 1 && outside.get() == 1 && delivered.get() == packet, "Baseline CC delivery or payload changed");
            mx.issacinzunza.biosphereworlds.easteregg.ObserverWireless.publish(level, java.util.List.of(center));
            transmit.invoke(network, packet, 256.0);
            helper.assertTrue(inside.get() == 1 && outside.get() == 2, "Wireless radius boundary failed");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { transmit.invoke(network, packet, 256.0); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            helper.assertTrue(inside.get() == 1 && outside.get() == 3, "Computer-thread immutable snapshot failed");
            Object localSender = endpoint(senderType, level, center, null, null);
            Object localPacket = packetType.getConstructor(int.class, int.class, Object.class, senderType).newInstance(7, 9, "outgoing", localSender);
            networkType.getMethod("transmitInterdimensional", packetType).invoke(network, localPacket);
            helper.assertTrue(inside.get() == 1 && outside.get() == 3, "Local sender escaped interference via ender transmission");
            EasterEggConfig.ENABLED.set(false);
            transmit.invoke(network, packet, 256.0);
            helper.assertTrue(inside.get() == 2 && outside.get() == 4 && delivered.get() == packet, "Disabled CC integration changed delivery");
            EasterEggConfig.ENABLED.set(true);
            mx.issacinzunza.biosphereworlds.easteregg.ObserverWireless.publish(level, java.util.List.of());
            transmit.invoke(network, packet, 256.0);
            helper.assertTrue(inside.get() == 3 && outside.get() == 5, "Wireless network failed to recover");
            Object vanillaReceiver = endpoint(receiverType, helper.getLevel(), center, inside, delivered);
            Object vanillaSender = endpoint(senderType, helper.getLevel(), center, null, null);
            Object vanillaPacket = packetType.getConstructor(int.class, int.class, Object.class, senderType).newInstance(7, 9, "vanilla", vanillaSender);
            Object vanillaNetwork = networkType.getConstructor().newInstance();
            networkType.getMethod("addReceiver", receiverType).invoke(vanillaNetwork, vanillaReceiver);
            mx.issacinzunza.biosphereworlds.easteregg.ObserverWireless.publish(helper.getLevel(), java.util.List.of(center));
            transmit.invoke(vanillaNetwork, vanillaPacket, 256.0);
            helper.assertTrue(inside.get() == 4 && delivered.get() == vanillaPacket, "Vanilla wireless delivery changed");
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", templateNamespace = "biosphereworlds_observer_cbc", timeoutTicks = 1200)
    public static void actualCbcShellTriggersThreePulsesAtItsRecordedOrigin(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("createbigcannons")) {
            BiosphereWorlds.LOGGER.info("Observer CBC integration test skipped: optional CBC is not installed");
            helper.succeed();
            return;
        }
        counterfireAtRecordedOrigin(helper, true);
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void automatedProjectilesTriggerCounterfireWithoutAnyCannonMod(GameTestHelper helper) throws Exception {
        counterfireAtRecordedOrigin(helper, false);
    }

    private static void counterfireAtRecordedOrigin(GameTestHelper helper, boolean cbc) throws Exception {
        boolean enabled = EasterEggConfig.ENABLED.get();
        try (var fixture = ClassicPersistenceGameTests.open(helper, java.nio.file.Path.of("observer-cbc-" + java.util.UUID.randomUUID()), "battery", false, true)) {
            var level = fixture.level();
            EasterEggConfig.ENABLED.set(true);
            for (int x = 0; x <= 11; x++) level.getChunk(x, 0);
            var witness = vulnerablePlayer(level, "observer-cbc", 8, 230, 8);
            var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
            light.configure(new net.minecraft.world.phys.Vec3(168, 270, 8), 0, 29, 0, 1, 400, witness.getUUID(),
                    mx.issacinzunza.biosphereworlds.easteregg.ObserverRules.Mode.WATCH, false);
            level.addFreshEntity(light);
            Object encounter = encounter(witness.getUUID(), witness.blockPosition(), java.util.List.of(light));
            encounters(level).add(encounter);
            var origin = new net.minecraft.world.phys.Vec3(32, 232, 8);
            var control = BlockPos.containing(origin).offset(0, 0, 32);
            var glass = BlockPos.containing(origin).below();
            loadBlastChunks(level, origin);
            level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
            level.getChunk(control.getX() >> 4, control.getZ() >> 4);
            level.setBlock(control, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            var type = cbc ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createbigcannons", "shot")) : net.minecraft.world.entity.EntityType.ARROW;
            var projectile = (net.minecraft.world.entity.projectile.Projectile) type.create(level);
            helper.assertTrue(projectile != null && projectile.getOwner() == null, "Automated shot has unexpected attribution");
            projectile.setPos(origin);
            projectile.setDeltaMovement(light.position().subtract(origin));
            level.addFreshEntity(projectile); // Real join event captures the firing position, with no mod whitelist.
            if (cbc) {
                var damage = (net.minecraft.world.damagesource.DamageSource) projectile.getClass()
                    .getMethod("indirectArtilleryFire", boolean.class).invoke(projectile, false);
                helper.assertTrue(damage.getEntity() == null && damage.getDirectEntity() == null, "CBC source contract changed; revalidate attribution");
                helper.assertTrue(!light.hurt(damage, 30) && !light.hostile(), "Unattributed damage guessed a player");
            }
            int loaded = level.getChunkSource().getLoadedChunksCount();
            ObserverEvents.projectileTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(projectile));
            helper.assertTrue(light.hostile() && light.warning(), "Swept automated shot did not trigger retaliation");
            var remaining = encounter.getClass().getDeclaredField("shots");
            var effectAnchor = encounter.getClass().getDeclaredField("effectAnchor");
            remaining.setAccessible(true);
            effectAnchor.setAccessible(true);
            helper.assertTrue(effectAnchor.get(encounter).equals(BlockPos.containing(origin)), "Counterfire followed a bystander instead of the cannon origin");
            for (int index = 0; index < 3; index++) {
                setAge(light, 60 + index * 8);
                tickEncounter(encounter, level);
                helper.assertTrue((int) remaining.get(encounter) == 2 - index && light.beam(0).lengthSqr() > 0,
                        "Counterfire failed to emit three separated pulses");
                helper.assertTrue(light.position().add(light.beam(0)).distanceToSqr(origin) < 16,
                        "Counterfire left the recorded firing position: " + light.position().add(light.beam(0)));
            }
            helper.assertTrue(level.getBlockState(control).getSignal(level, control, Direction.UP) == 0,
                    "Counterfire failed to interrupt the nearby cannon control");
            helper.assertTrue(witness.getHealth() == 20 && level.getBlockState(control).is(Blocks.REDSTONE_BLOCK)
                    && level.getBlockState(glass).isAir(), "Counterfire failed to destroy its target or exceeded the blast radius");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Counterfire loaded extra chunks");
            encounters(level).clear();
            var finish = encounter.getClass().getDeclaredMethod("finish", net.minecraft.server.level.ServerLevel.class);
            finish.setAccessible(true);
            finish.invoke(encounter, level);
            helper.assertTrue(level.getBlockState(control).getSignal(level, control, Direction.UP) == 15, "Cannon control did not recover");
            projectile.discard();
            level.players().remove(witness);
        } finally { EasterEggConfig.ENABLED.set(enabled); }
        helper.succeed();
    }

    private static Object endpoint(Class<?> type, net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 point,
            java.util.concurrent.atomic.AtomicInteger received, java.util.concurrent.atomic.AtomicReference<Object> packet) {
        return java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "getLevel" -> level;
            case "getPosition" -> point;
            case "getRange" -> 256.0;
            case "isInterdimensional" -> true;
            case "getSenderID" -> "observer-test";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Observer test endpoint";
            case "receiveSameDimension", "receiveDifferentDimension" -> { received.incrementAndGet(); packet.set(args[0]); yield null; }
            default -> throw new UnsupportedOperationException(method.toString());
        });
    }

    private static net.minecraft.world.level.block.entity.BlockEntity createLink(net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean receiver) throws Exception {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "redstone_link"));
        var receiving = (net.minecraft.world.level.block.state.properties.BooleanProperty) block.getClass().getField("RECEIVER").get(null);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, block.defaultBlockState().setValue(receiving, receiver)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP), 3);
        var entity = level.getBlockEntity(pos);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("Transmitter", !receiver);
        entity.loadWithComponents(tag, level.registryAccess());
        entity.getClass().getMethod("tick").invoke(entity);
        return entity;
    }
}
