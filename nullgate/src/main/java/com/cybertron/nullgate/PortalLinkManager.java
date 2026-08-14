package com.cybertron.nullgate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class PortalLinkManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORAGE_TYPE = new TypeToken<List<StoredLink>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nullgate-links.json");
    private static final Map<String, PortalTarget> LINKS = new HashMap<>();

    private PortalLinkManager() {}

    public static synchronized void load() {
        LINKS.clear();
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            List<StoredLink> stored = GSON.fromJson(reader, STORAGE_TYPE);
            if (stored == null) return;
            for (StoredLink link : stored) {
                LINKS.put(key(link.sourceDimension, link.sx, link.sy, link.sz),
                        new PortalTarget(link.targetDimension, link.x, link.y, link.z));
            }
        } catch (Exception e) {
            NullGateMod.LOGGER.error("Failed to load NULLGate links", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            List<StoredLink> stored = new ArrayList<>();
            for (Map.Entry<String, PortalTarget> entry : LINKS.entrySet()) {
                String[] p = entry.getKey().split("\\|");
                String[] xyz = p[1].split(",");
                PortalTarget target = entry.getValue();
                stored.add(new StoredLink(
                        p[0], Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]),
                        target.dimension(), target.x(), target.y(), target.z()
                ));
            }
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(stored, STORAGE_TYPE, writer);
            }
        } catch (IOException e) {
            NullGateMod.LOGGER.error("Failed to save NULLGate links", e);
        }
    }

    public static synchronized int bindPortal(ServerLevel level, BlockPos portalBlock, PortalTarget target) {
        Set<BlockPos> connected = collectConnectedPortal(level, portalBlock);
        String dimension = NullGateMod.dimensionId(level);
        for (BlockPos pos : connected) {
            LINKS.put(key(dimension, pos), target);
        }
        save();
        return connected.size();
    }

    public static synchronized int unbindPortal(ServerLevel level, BlockPos portalBlock) {
        Set<BlockPos> connected = collectConnectedPortal(level, portalBlock);
        String dimension = NullGateMod.dimensionId(level);
        int removed = 0;
        for (BlockPos pos : connected) {
            if (LINKS.remove(key(dimension, pos)) != null) removed++;
        }
        if (removed > 0) save();
        return removed;
    }

    public static synchronized PortalTarget get(ServerLevel level, BlockPos pos) {
        return LINKS.get(key(NullGateMod.dimensionId(level), pos));
    }

    public static synchronized int count() {
        return LINKS.size();
    }

    private static Set<BlockPos> collectConnectedPortal(ServerLevel level, BlockPos start) {
        Set<BlockPos> result = new HashSet<>();
        if (!level.getBlockState(start).is(Blocks.NETHER_PORTAL)) return result;

        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        result.add(start.immutable());

        while (!queue.isEmpty() && result.size() < 128) {
            BlockPos current = queue.remove();
            for (BlockPos next : List.of(
                    current.above(), current.below(), current.north(), current.south(), current.east(), current.west())) {
                BlockPos immutable = next.immutable();
                if (!result.contains(immutable) && level.getBlockState(immutable).is(Blocks.NETHER_PORTAL)) {
                    result.add(immutable);
                    queue.add(immutable);
                }
            }
        }
        return result;
    }

    private static String key(String dimension, BlockPos pos) {
        return key(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    private static String key(String dimension, int x, int y, int z) {
        return dimension + "|" + x + "," + y + "," + z;
    }

    private record StoredLink(
            String sourceDimension, int sx, int sy, int sz,
            String targetDimension, double x, double y, double z) {
    }
}
