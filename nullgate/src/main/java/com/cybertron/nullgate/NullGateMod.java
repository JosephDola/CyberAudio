package com.cybertron.nullgate;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NullGateMod implements ModInitializer {
    public static final String MOD_ID = "nullgate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

    @Override
    public void onInitialize() {
        PortalLinkManager.load();
        registerCommands();
        ServerTickEvents.END_SERVER_TICK.register(NullGateMod::tickServer);
        LOGGER.info("NULLGate loaded: precise portal override online.");
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("null")
                        .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                        .then(Commands.literal("bind")
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("overworld");
                                            builder.suggest("nether");
                                            builder.suggest("end");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> bind(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "dimension"),
                                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                                        DoubleArgumentType.getDouble(ctx, "z")
                                                                )))))))
                        .then(Commands.literal("unbind").executes(ctx -> unbind(ctx.getSource())))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("count").executes(ctx -> count(ctx.getSource())))
        ));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§5§lNULLGate §8// §fPrecise Nether portal routing"), false);
        source.sendSuccess(() -> Component.literal("§d/null bind <overworld|nether|end> <x> <y> <z> §7- bind nearest portal"), false);
        source.sendSuccess(() -> Component.literal("§d/null unbind §7- remove nearest portal override"), false);
        source.sendSuccess(() -> Component.literal("§d/null info §7- inspect nearest portal"), false);
        return 1;
    }

    private static int bind(CommandSourceStack source, String dimension, double x, double y, double z) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ResourceKey<Level> targetKey = parseDimension(dimension);
        if (targetKey == null) {
            source.sendFailure(Component.literal("Dimension must be overworld, nether, or end."));
            return 0;
        }

        BlockPos portal = findNearestPortal(player.level(), player.blockPosition(), 6);
        if (portal == null) {
            source.sendFailure(Component.literal("No Nether portal found within 6 blocks."));
            return 0;
        }

        String targetDimension = dimensionName(targetKey);
        int blocks = PortalLinkManager.bindPortal(player.level(), portal, new PortalTarget(targetDimension, x, y, z));
        source.sendSuccess(() -> Component.literal("§5NULL §8// §fPortal bound to §d" + targetDimension + " §f@ §d" + clean(x) + " " + clean(y) + " " + clean(z) + " §8(" + blocks + " portal blocks)"), false);
        player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, portal.getX() + 0.5, portal.getY() + 1.0, portal.getZ() + 0.5, 80, 0.7, 1.0, 0.7, 0.08);
        return 1;
    }

    private static int unbind(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        BlockPos portal = findNearestPortal(player.level(), player.blockPosition(), 6);
        if (portal == null) {
            source.sendFailure(Component.literal("No Nether portal found within 6 blocks."));
            return 0;
        }
        int removed = PortalLinkManager.unbindPortal(player.level(), portal);
        if (removed == 0) {
            source.sendFailure(Component.literal("That portal is not bound."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§5NULL §8// §fPortal override removed."), false);
        return 1;
    }

    private static int info(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        BlockPos portal = findNearestPortal(player.level(), player.blockPosition(), 6);
        if (portal == null) {
            source.sendFailure(Component.literal("No Nether portal found within 6 blocks."));
            return 0;
        }
        PortalTarget target = PortalLinkManager.get(player.level(), portal);
        if (target == null) {
            source.sendSuccess(() -> Component.literal("§7Portal found, but it has no NULL route."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§5NULL §8// §fTarget: §d" + target.dimension() + " §f@ §d" + clean(target.x()) + " " + clean(target.y()) + " " + clean(target.z())), false);
        }
        return 1;
    }

    private static int count(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§5NULL §8// §fStored portal blocks: §d" + PortalLinkManager.count()), false);
        return 1;
    }

    private static void tickServer(MinecraftServer server) {
        COOLDOWNS.replaceAll((uuid, ticks) -> ticks - 1);
        COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= 0);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (COOLDOWNS.containsKey(player.getUUID())) continue;

            PortalTarget target = portalTargetAtPlayer(player);
            if (target == null) continue;

            teleport(player, target);
            COOLDOWNS.put(player.getUUID(), 60);
        }
    }

    private static PortalTarget portalTargetAtPlayer(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos base = player.blockPosition();
        PortalTarget target = PortalLinkManager.get(level, base);
        if (target == null) target = PortalLinkManager.get(level, base.above());
        if (target == null) target = PortalLinkManager.get(level, base.below());
        return target;
    }

    private static void teleport(ServerPlayer player, PortalTarget target) {
        ResourceKey<Level> key = parseDimension(target.dimension());
        if (key == null) {
            player.sendSystemMessage(Component.literal("§cNULL route failed: invalid target dimension."));
            return;
        }
        ServerLevel level = player.level().getServer().getLevel(key);
        if (level == null) {
            player.sendSystemMessage(Component.literal("§cNULL route failed: target dimension unavailable."));
            return;
        }

        BlockPos requested = BlockPos.containing(target.x(), target.y(), target.z());
        BlockPos safe = findSafe(level, requested);
        if (safe == null) {
            safe = makeEmergencyPad(level, requested);
        }

        ServerLevel old = player.level();
        old.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.8, 0.5, 0.08);
        old.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.7f, 1.2f);

        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.sendSystemMessage(Component.literal("§5NULL §8// §fRoute complete → §d" + target.dimension() + " §f@ §d" + safe.getX() + " " + safe.getY() + " " + safe.getZ()), true);

        level.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 100, 0.7, 1.0, 0.7, 0.12);
        level.playSound(null, safe, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    private static BlockPos findSafe(ServerLevel level, BlockPos requested) {
        int minY = level.getMinY() + 2;
        int maxY = level.getMaxY() - 3;
        int baseY = Math.max(minY, Math.min(maxY, requested.getY()));

        for (int radius = 0; radius <= 8; radius++) {
            for (int dy = -4; dy <= 8; dy++) {
                int y = baseY + dy;
                if (y < minY || y > maxY) continue;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPos feet = new BlockPos(requested.getX() + dx, y, requested.getZ() + dz);
                        if (isSafe(level, feet)) return feet;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).blocksMotion()
                && !level.getBlockState(feet).blocksMotion()
                && !level.getBlockState(feet.above()).blocksMotion();
    }

    private static BlockPos makeEmergencyPad(ServerLevel level, BlockPos requested) {
        int y = Math.max(level.getMinY() + 2, Math.min(level.getMaxY() - 3, requested.getY()));
        BlockPos feet = new BlockPos(requested.getX(), y, requested.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(feet.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState());
                level.setBlockAndUpdate(feet.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(feet.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
            }
        }
        return feet;
    }

    private static BlockPos findNearestPortal(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) continue;
                    double distance = pos.distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    public static String dimensionId(ServerLevel level) {
        if (level.dimension() == Level.NETHER) return "nether";
        if (level.dimension() == Level.END) return "end";
        return "overworld";
    }

    private static ResourceKey<Level> parseDimension(String dimension) {
        return switch (dimension.toLowerCase()) {
            case "overworld", "minecraft:overworld" -> Level.OVERWORLD;
            case "nether", "minecraft:the_nether" -> Level.NETHER;
            case "end", "minecraft:the_end" -> Level.END;
            default -> null;
        };
    }

    private static String dimensionName(ResourceKey<Level> key) {
        if (key == Level.NETHER) return "nether";
        if (key == Level.END) return "end";
        return "overworld";
    }

    private static String clean(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format("%.2f", value);
    }
}
