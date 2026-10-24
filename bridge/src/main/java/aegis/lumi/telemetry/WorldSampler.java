package aegis.lumi.telemetry;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.network.AegisConnection;
import aegis.lumi.network.FrameWriter;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.effect.Effect;
import cn.nukkit.entity.effect.EffectType;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

public final class WorldSampler {

    private static final double EDGE_MARGIN = 0.12;

    private final AegisConfig config;
    private final AegisConnection connection;
    private final SessionRegistry sessions;
    private final TickBudget budget;

    private int roundRobinIndex = 0;

    public WorldSampler(AegisConfig config, AegisConnection connection, SessionRegistry sessions) {
        this.config = config;
        this.connection = connection;
        this.sessions = sessions;
        this.budget = new TickBudget(config.getSampleBudgetNanos(), config.getMaxSamplesPerTick());
    }

    public void tick(long currentTick) {
        if (!connection.isConnected()) {
            return;
        }

        List<PlayerSession> all = new ArrayList<>(sessions.all());
        if (all.isEmpty()) {
            return;
        }

        int count = Math.min(all.size(), config.getMaxSamplesPerTick());
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            if (!budget.canClaim(currentTick)) {
                break;
            }

            int index = Math.abs(roundRobinIndex++ % all.size());
            PlayerSession session = all.get(index);
            long start = System.nanoTime();
            try {
                samplePlayer(session.getPlayer(), now);
            } finally {
                budget.record(System.nanoTime() - start);
            }
        }
    }

    public void samplePlayer(Player player, long time) {
        if (player == null || !player.isOnline()) {
            return;
        }

        int flags = StateFlag.PHYSICS;

        boolean flying = player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT)
                || player.getAdventureSettings().get(AdventureSettings.Type.FLYING)
                || player.isCreative()
                || player.isSpectator();
        if (flying) {
            flags |= StateFlag.FLYING;
        }

        boolean special = player.isGliding()
                || player.isSwimming()
                || player.isInsideOfWater()
                || player.riding != null
                || player.isOnLadder()
                || player.isSleeping()
                || player.getEffects().containsKey(EffectType.LEVITATION)
                || player.getEffects().containsKey(EffectType.SLOW_FALLING);

        if (Server.getInstance().getTicksPerSecond() < 18.0f || player.getPing() > 350) {
            flags |= StateFlag.LAGGING;
        }

        if (!player.isAlive()) {
            flags |= StateFlag.DEAD;
        }

        if (player.isImmobile()) {
            flags |= StateFlag.FROZEN;
        }

        Level level = player.getLevel();
        AxisAlignedBB box = player.getBoundingBox();

        if (level != null && box != null) {
            int minX = (int) Math.floor(box.getMinX() - EDGE_MARGIN);
            int maxX = (int) Math.floor(box.getMaxX() + EDGE_MARGIN);
            int minZ = (int) Math.floor(box.getMinZ() - EDGE_MARGIN);
            int maxZ = (int) Math.floor(box.getMaxZ() + EDGE_MARGIN);

            int minY = (int) Math.floor(box.getMinY() - 0.2);
            int maxY = (int) Math.floor(box.getMaxY() + 0.2);

            boolean hasGround = false;
            boolean hasCeiling = false;
            boolean obstructed = false;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cn.nukkit.level.format.FullChunk chunk = level.getChunk(x >> 4, z >> 4);
                    if (chunk == null) {
                        special = true;
                        continue;
                    }

                    for (int y = minY; y <= maxY; y++) {
                        Block block = level.getBlock(chunk, x, y, z, false);
                        if (block == null || block.getId() == Block.AIR) {
                            continue;
                        }

                        if (block instanceof BlockLiquid
                                || block instanceof BlockLadder
                                || block instanceof BlockVine
                                || block instanceof BlockCobweb
                                || block instanceof BlockSlime
                                || block instanceof BlockScaffolding
                                || block instanceof BlockHoneyBlock
                                || block instanceof BlockBubbleColumn
                                || block instanceof BlockPowderSnow
                                || block.getFrictionFactor() > 0.65f) {
                            special = true;
                        }

                        AxisAlignedBB blockBox = block.getCollisionBoundingBox();
                        if (blockBox == null) {
                            continue;
                        }

                        // Ground contact
                        if (Math.abs(blockBox.getMaxY() - box.getMinY()) <= 0.15) {
                            hasGround = true;
                        }

                        // Ceiling contact
                        if (Math.abs(blockBox.getMinY() - box.getMaxY()) <= 0.20) {
                            hasCeiling = true;
                        }

                        // Horizontal collision obstruction
                        if (blockBox.getMaxY() > box.getMinY() + 0.2
                                && blockBox.getMinY() < box.getMaxY() - 0.1
                                && (blockBox.getMaxX() > box.getMinX() && blockBox.getMinX() < box.getMaxX())
                                && (blockBox.getMaxZ() > box.getMinZ() && blockBox.getMinZ() < box.getMaxZ())) {
                            obstructed = true;
                        }
                    }
                }
            }

            if (hasGround) {
                flags |= StateFlag.GROUND;
            }
            if (hasCeiling) {
                flags |= StateFlag.CEILING;
            }
            if (obstructed) {
                flags |= StateFlag.OBSTRUCTED;
            }
        }

        if (special) {
            flags |= StateFlag.SPECIAL;
        }

        float speed = player.getMovementSpeed();

        float jump = 0.42f;
        Effect jumpEffect = player.getEffects().get(EffectType.JUMP_BOOST);
        if (jumpEffect != null) {
            jump += (jumpEffect.getAmplifier() + 1) * 0.1f;
        }

        float offset = player.getEyeHeight();

        byte[] frame = FrameWriter.state(
                player.getId(),
                time,
                flags,
                speed,
                jump,
                offset,
                (float) player.getX(),
                (float) player.getY(),
                (float) player.getZ()
        );

        connection.send(frame);
    }
}
