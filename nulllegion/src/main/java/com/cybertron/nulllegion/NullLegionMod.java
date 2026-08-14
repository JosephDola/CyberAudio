package com.cybertron.nulllegion;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NullLegionMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("nulllegion");
    private static final String LEGION = "nulllegion";
    private static final String OWNER = "nl_owner_";
    private static final String INFANTRY = "nl_role_infantry";
    private static final String RANGER = "nl_role_ranger";
    private static final String MODE = "nl_mode_";
    private static final String FOLLOW = "nl_mode_follow";
    private static final String HOLD = "nl_mode_hold";
    private static final String ATTACK = "nl_mode_attack";
    private static final String TARGET = "nl_target_";
    private static int ticks;

    @Override public void onInitialize() {
        registerCommands();
        ServerTickEvents.END_SERVER_TICK.register(NullLegionMod::tick);
        LOGGER.info("NULL Legion online");
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((d, r, e) -> d.register(
            Commands.literal("legion")
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("summon")
                    .then(Commands.literal("infantry")
                        .executes(c -> summon(c.getSource(), false, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                            .executes(c -> summon(c.getSource(), false, IntegerArgumentType.getInteger(c, "count")))))
                    .then(Commands.literal("ranger")
                        .executes(c -> summon(c.getSource(), true, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                            .executes(c -> summon(c.getSource(), true, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("follow").executes(c -> order(c.getSource(), FOLLOW)))
                .then(Commands.literal("hold").executes(c -> order(c.getSource(), HOLD)))
                .then(Commands.literal("regroup").executes(c -> regroup(c.getSource())))
                .then(Commands.literal("dismiss").executes(c -> dismiss(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("attack")
                    .then(Commands.literal("nearest").executes(c -> attackNearest(c.getSource())))
                    .then(Commands.argument("target", EntityArgument.entity())
                        .executes(c -> attack(c.getSource(), EntityArgument.getEntity(c, "target")))))
        ));
    }

    private static int help(CommandSourceStack s) {
        s.sendSuccess(() -> Component.literal("§5§lNULL LEGION §8// §fCommander interface"), false);
        s.sendSuccess(() -> Component.literal("§d/legion summon infantry [1-24] §7- melee unit"), false);
        s.sendSuccess(() -> Component.literal("§d/legion summon ranger [1-24] §7- ranged unit"), false);
        s.sendSuccess(() -> Component.literal("§d/legion follow §7- follow you"), false);
        s.sendSuccess(() -> Component.literal("§d/legion hold §7- hold position"), false);
        s.sendSuccess(() -> Component.literal("§d/legion attack nearest §7- focus nearby target"), false);
        s.sendSuccess(() -> Component.literal("§d/legion attack <selector> §7- focus selected target"), false);
        s.sendSuccess(() -> Component.literal("§d/legion regroup §7- teleport army back to you"), false);
        s.sendSuccess(() -> Component.literal("§d/legion dismiss §7- remove your army"), false);
        return 1;
    }

    private static int summon(CommandSourceStack s, boolean ranger, int requested) {
        ServerPlayer p = player(s); if (p == null) return 0;
        MinecraftServer server = p.level().getServer();
        int allowed = Math.max(0, 40 - soldiers(server, p.getUUID()).size());
        int count = Math.min(requested, allowed);
        if (count == 0) { s.sendFailure(Component.literal("NULL Legion cap reached (40).")); return 0; }
        ensureTeam(p);
        ServerLevel level = p.level();
        int made = 0;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / Math.max(1, count);
            double radius = 2.5 + i % 3;
            Mob mob = create(level, p, ranger, p.getX() + Math.cos(a) * radius, p.getY(), p.getZ() + Math.sin(a) * radius);
            if (mob != null && level.addFreshEntity(mob)) { addToTeam(p, mob); made++; }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY()+1, p.getZ(), 90, 2.2, .8, 2.2, .08);
        level.playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1f, .75f);
        int finalMade = made;
        s.sendSuccess(() -> Component.literal("§5NULL §8// §fDeployed §d" + finalMade + (ranger ? " §franger(s)." : " §finfantry.")), false);
        return made > 0 ? 1 : 0;
    }

    private static Mob create(ServerLevel level, ServerPlayer owner, boolean ranger, double x, double y, double z) {
        Mob mob;
        if (ranger) {
            Stray m = EntityType.STRAY.create(level, EntitySpawnReason.COMMAND); if (m == null) return null;
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            m.setCustomName(Component.literal("§5NULL Ranger")); m.addTag(RANGER); mob = m;
        } else {
            Husk m = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND); if (m == null) return null;
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            m.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            m.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            m.setCustomName(Component.literal("§5NULL Infantry")); m.addTag(INFANTRY); mob = m;
        }
        mob.setCustomNameVisible(false); mob.setPersistenceRequired(); mob.setPos(x,y,z); mob.setYRot(owner.getYRot());
        mob.addTag(LEGION); mob.addTag(OWNER + owner.getUUID()); mob.addTag(FOLLOW); return mob;
    }

    private static int order(CommandSourceStack s, String mode) {
        ServerPlayer p = player(s); if (p == null) return 0;
        List<Mob> list = soldiers(p.level().getServer(), p.getUUID());
        for (Mob m : list) { clearMode(m); clearTarget(m); m.addTag(mode); m.setTarget(null); if (HOLD.equals(mode)) m.getNavigation().stop(); }
        s.sendSuccess(() -> Component.literal("§5NULL §8// §fOrder §d" + (HOLD.equals(mode) ? "HOLD" : "FOLLOW") + " §8(" + list.size() + ")"), false);
        return 1;
    }

    private static int attackNearest(CommandSourceStack s) {
        ServerPlayer p = player(s); if (p == null) return 0;
        LivingEntity target = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32),
            e -> e.isAlive() && e != p && !isSoldier(e, p.getUUID()) && !sameTeam(p,e))
            .stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
        if (target == null) { s.sendFailure(Component.literal("No valid target within 32 blocks.")); return 0; }
        return attack(s, target);
    }

    private static int attack(CommandSourceStack s, Entity entity) {
        ServerPlayer p = player(s); if (p == null) return 0;
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) { s.sendFailure(Component.literal("Target must be alive.")); return 0; }
        if (target == p || isSoldier(target, p.getUUID()) || sameTeam(p,target)) { s.sendFailure(Component.literal("Your legion will not attack allies.")); return 0; }
        List<Mob> list = soldiers(p.level().getServer(), p.getUUID());
        for (Mob m : list) { clearMode(m); clearTarget(m); m.addTag(ATTACK); m.addTag(TARGET + target.getUUID()); if (m.level() == target.level()) m.setTarget(target); }
        s.sendSuccess(() -> Component.literal("§5NULL §8// §fFocus target: §d" + target.getName().getString() + " §8(" + list.size() + ")"), false);
        return 1;
    }

    private static int regroup(CommandSourceStack s) {
        ServerPlayer p = player(s); if (p == null) return 0;
        List<Mob> list = soldiers(p.level().getServer(), p.getUUID()); ServerLevel dest = p.level(); int moved = 0; int i = 0;
        for (Mob m : list) {
            double a = Math.PI * 2 * i++ / Math.max(1, list.size()), r = 3 + i % 3;
            if (m.teleportTo(dest, p.getX()+Math.cos(a)*r, p.getY(), p.getZ()+Math.sin(a)*r, Set.<Relative>of(), p.getYRot(), 0f, false)) {
                clearMode(m); clearTarget(m); m.addTag(FOLLOW); m.setTarget(null); moved++;
            }
        }
        dest.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY()+1, p.getZ(), Math.max(50,moved*10),2.5,1,2.5,.1);
        dest.playSound(null,p.blockPosition(),SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,1f,.75f);
        int finalMoved=moved; s.sendSuccess(() -> Component.literal("§5NULL §8// §fRegrouped §d"+finalMoved+" §fsoldiers."),false); return 1;
    }

    private static int dismiss(CommandSourceStack s) {
        ServerPlayer p=player(s); if(p==null)return 0; List<Mob> list=soldiers(p.level().getServer(),p.getUUID()); int count=list.size();
        for(Mob m:list){ if(m.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.REVERSE_PORTAL,m.getX(),m.getY()+1,m.getZ(),20,.35,.6,.35,.07); m.discard(); }
        s.sendSuccess(() -> Component.literal("§5NULL §8// §fLegion dismissed: §d"+count),false); return 1;
    }

    private static int status(CommandSourceStack s) {
        ServerPlayer p=player(s); if(p==null)return 0; List<Mob> a=soldiers(p.level().getServer(),p.getUUID());
        long inf=a.stream().filter(m->m.getTags().contains(INFANTRY)).count(), rng=a.stream().filter(m->m.getTags().contains(RANGER)).count();
        long f=a.stream().filter(m->m.getTags().contains(FOLLOW)).count(), h=a.stream().filter(m->m.getTags().contains(HOLD)).count(), atk=a.stream().filter(m->m.getTags().contains(ATTACK)).count();
        s.sendSuccess(() -> Component.literal("§5NULL LEGION §8// §fTotal §d"+a.size()+" §8| §fInfantry §d"+inf+" §8| §fRangers §d"+rng),false);
        s.sendSuccess(() -> Component.literal("§7Orders: follow "+f+" | hold "+h+" | attack "+atk),false); return 1;
    }

    private static void tick(MinecraftServer server) {
        if(++ticks%5!=0)return;
        for(ServerLevel level:server.getAllLevels()) for(Entity e:level.getAllEntities()) {
            if(!(e instanceof Mob m)||!m.isAlive()||!m.getTags().contains(LEGION))continue;
            UUID ownerId=ownerId(m); if(ownerId==null)continue; ServerPlayer owner=server.getPlayerList().getPlayer(ownerId); if(owner==null)continue;
            addToTeam(owner,m); LivingEntity cur=m.getTarget(); if(cur!=null&&(cur==owner||isSoldier(cur,ownerId)||sameTeam(owner,cur)))m.setTarget(null);
            if(m.getTags().contains(ATTACK)) {
                UUID tid=targetId(m); Entity te=tid==null?null:find(server,tid);
                if(te instanceof LivingEntity t&&t.isAlive()&&t.level()==m.level()&&!sameTeam(owner,t))m.setTarget(t); else m.setTarget(null); continue;
            }
            if(m.getTags().contains(HOLD)){m.setTarget(null);m.getNavigation().stop();continue;}
            if(m.getTags().contains(FOLLOW)&&m.level()==owner.level()) {
                m.setTarget(null); double d=m.distanceToSqr(owner);
                if(d>144)m.getNavigation().moveTo(owner,1.35); else if(d>36)m.getNavigation().moveTo(owner,1.15); else if(d<9)m.getNavigation().stop();
            }
        }
    }

    private static List<Mob> soldiers(MinecraftServer server,UUID owner){ List<Mob> out=new ArrayList<>(); for(ServerLevel l:server.getAllLevels())for(Entity e:l.getAllEntities())if(e instanceof Mob m&&m.isAlive()&&isSoldier(m,owner))out.add(m); return out; }
    private static Entity find(MinecraftServer s,UUID id){ for(ServerLevel l:s.getAllLevels()){Entity e=l.getEntity(id);if(e!=null)return e;}return null; }
    private static boolean isSoldier(Entity e,UUID owner){return e.getTags().contains(LEGION)&&e.getTags().contains(OWNER+owner);}
    private static UUID ownerId(Entity e){return taggedUuid(e,OWNER);}
    private static UUID targetId(Entity e){return taggedUuid(e,TARGET);}
    private static UUID taggedUuid(Entity e,String prefix){for(String t:e.getTags())if(t.startsWith(prefix))try{return UUID.fromString(t.substring(prefix.length()));}catch(Exception ignored){return null;}return null;}
    private static void clearMode(Entity e){e.getTags().stream().filter(t->t.startsWith(MODE)).toList().forEach(e::removeTag);}
    private static void clearTarget(Entity e){e.getTags().stream().filter(t->t.startsWith(TARGET)).toList().forEach(e::removeTag);}
    private static ServerPlayer player(CommandSourceStack s){try{return s.getPlayerOrException();}catch(Exception e){s.sendFailure(Component.literal("This command must be used by a player."));return null;}}
    private static String teamName(UUID id){return "nl_"+id.toString().replace("-","").substring(0,12);}
    private static void ensureTeam(ServerPlayer p){Scoreboard sb=p.level().getServer().getScoreboard();String n=teamName(p.getUUID());PlayerTeam t=sb.getPlayerTeam(n);if(t==null){t=sb.addPlayerTeam(n);t.setAllowFriendlyFire(false);t.setSeeFriendlyInvisibles(true);}sb.addPlayerToTeam(p.getScoreboardName(),t);}
    private static void addToTeam(ServerPlayer p,Entity e){Scoreboard sb=p.level().getServer().getScoreboard();String n=teamName(p.getUUID());PlayerTeam t=sb.getPlayerTeam(n);if(t==null){t=sb.addPlayerTeam(n);t.setAllowFriendlyFire(false);t.setSeeFriendlyInvisibles(true);}sb.addPlayerToTeam(p.getScoreboardName(),t);sb.addPlayerToTeam(e.getScoreboardName(),t);}
    private static boolean sameTeam(Entity a,Entity b){return a.getTeam()!=null&&a.getTeam()==b.getTeam();}
}
