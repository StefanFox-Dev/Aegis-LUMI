package aegis.lumi.telemetry;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.network.AegisConnection;
import aegis.lumi.network.FrameWriter;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.inventory.transaction.data.UseItemOnEntityData;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.AuthInputAction;

public final class PacketObserver implements Listener {

    private final AegisConfig config;
    private final AegisConnection connection;
    private final SessionRegistry sessions;
    private final TickBudget budget;

    public PacketObserver(AegisConfig config, AegisConnection connection, SessionRegistry sessions) {
        this.config = config;
        this.connection = connection;
        this.sessions = sessions;
        this.budget = new TickBudget(config.getInputBudgetNanos(), 512);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onReceive(DataPacketReceiveEvent event) {
        if (!connection.isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        long tick = Server.getInstance().getTick();
        if (!budget.canClaim(tick)) {
            return;
        }

        long start = System.nanoTime();
        try {
            processReceivedPacket(player, event.getPacket());
        } finally {
            budget.record(System.nanoTime() - start);
        }
    }

    private void processReceivedPacket(Player player, DataPacket packet) {
        long session = player.getId();
        long now = System.currentTimeMillis();

        if (packet instanceof PlayerAuthInputPacket auth) {
            Vector3f pos = auth.getPosition();
            if (pos == null) {
                return;
            }

            int mode = auth.getInputMode() != null ? auth.getInputMode().ordinal() : 0;
            boolean missed = auth.getInputData().contains(AuthInputAction.MISSED_SWING);

            int control = InputControl.KNOWN;
            if (auth.getInputData().contains(AuthInputAction.JUMPING)) control |= InputControl.JUMP;
            if (auth.getInputData().contains(AuthInputAction.START_JUMPING)) control |= InputControl.JUMP_START;
            if (auth.getInputData().contains(AuthInputAction.SPRINTING)) control |= InputControl.SPRINT;
            if (auth.getInputData().contains(AuthInputAction.HORIZONTAL_COLLISION)) control |= InputControl.HORIZONTAL_COLLISION;
            if (auth.getInputData().contains(AuthInputAction.VERTICAL_COLLISION)) control |= InputControl.VERTICAL_COLLISION;
            if (auth.getInputData().contains(AuthInputAction.JUMP_DOWN)) control |= InputControl.JUMP_PRESSED;
            if (auth.getInputData().contains(AuthInputAction.SNEAKING)) control |= InputControl.SNEAK;

            byte[] frame = FrameWriter.input(
                    session,
                    now,
                    auth.getTick(),
                    pos.x,
                    pos.y,
                    pos.z,
                    auth.getPitch(),
                    auth.getYaw(),
                    auth.getHeadYaw(),
                    mode,
                    missed,
                    control
            );
            connection.send(frame);

            // Handle inline item interaction attack
            InventoryTransactionPacket tx = auth.getItemUseTransaction();
            if (tx != null && tx.transactionType == InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY) {
                if (tx.transactionData instanceof UseItemOnEntityData useData
                        && useData.actionType == InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_ATTACK) {
                    connection.send(FrameWriter.attack(session, now, useData.entityRuntimeId));
                }
            }

        } else if (packet instanceof InventoryTransactionPacket tx) {
            if (tx.transactionType == InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY) {
                if (tx.transactionData instanceof UseItemOnEntityData useData
                        && useData.actionType == InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_ATTACK) {
                    connection.send(FrameWriter.attack(session, now, useData.entityRuntimeId));
                }
            }

        } else if (packet instanceof AnimatePacket animate) {
            if (animate.action == AnimatePacket.Action.SWING_ARM) {
                connection.send(FrameWriter.animation(session, now));
            }

        } else if (packet instanceof LevelSoundEventPacket sound) {
            if (sound.sound == LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE
                    || sound.sound == LevelSoundEventPacket.SOUND_ATTACK_STRONG) {
                connection.send(FrameWriter.swing(session, now));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSend(DataPacketSendEvent event) {
        if (!connection.isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        DataPacket packet = event.getPacket();
        long session = player.getId();
        long now = System.currentTimeMillis();

        if (packet instanceof MovePlayerPacket move) {
            boolean isSelf = move.eid == session;
            int kind = isSelf && (move.mode == MovePlayerPacket.MODE_RESET || move.mode == MovePlayerPacket.MODE_TELEPORT)
                    ? ObservationType.TELEPORT
                    : ObservationType.POSITION;

            float[] box = new float[]{
                    move.x - 0.3f, move.y, move.z - 0.3f,
                    move.x + 0.3f, move.y + 1.8f, move.z + 0.3f
            };

            connection.send(FrameWriter.observation(session, now, move.eid, kind, box));

        } else if (packet instanceof MoveEntityAbsolutePacket moveAbs) {
            int kind = moveAbs.teleport ? ObservationType.TELEPORT : ObservationType.POSITION;
            float[] box = new float[]{
                    (float) moveAbs.x - 0.3f, (float) moveAbs.y, (float) moveAbs.z - 0.3f,
                    (float) moveAbs.x + 0.3f, (float) moveAbs.y + 1.8f, (float) moveAbs.z + 0.3f
            };
            connection.send(FrameWriter.observation(session, now, moveAbs.eid, kind, box));

        } else if (packet instanceof MoveEntityDeltaPacket moveDelta) {
            if (moveDelta.eid != session) {
                PlayerSession ps = sessions.get(session);
                if (ps != null) {
                    float[] prev = ps.getView().getVisible().get(moveDelta.eid);
                    if (prev != null) {
                        float dx = ((moveDelta.flags & MoveEntityDeltaPacket.FLAG_HAS_X) != 0) ? moveDelta.x : 0.0f;
                        float dy = ((moveDelta.flags & MoveEntityDeltaPacket.FLAG_HAS_Y) != 0) ? moveDelta.y : 0.0f;
                        float dz = ((moveDelta.flags & MoveEntityDeltaPacket.FLAG_HAS_Z) != 0) ? moveDelta.z : 0.0f;
                        float cx = (prev[0] + prev[3]) * 0.5f + dx;
                        float cy = prev[1] + dy;
                        float cz = (prev[2] + prev[5]) * 0.5f + dz;

                        float[] box = new float[]{
                                cx - 0.3f, cy, cz - 0.3f,
                                cx + 0.3f, cy + 1.8f, cz + 0.3f
                        };
                        ps.getView().getVisible().put(moveDelta.eid, box);
                        connection.send(FrameWriter.observation(session, now, moveDelta.eid, ObservationType.POSITION, box));
                    }
                }
            }

        } else if (packet instanceof SetEntityMotionPacket motion) {
            if (motion.eid == session) {
                float[] box = new float[]{motion.motionX, motion.motionY, motion.motionZ, 0f, 0f, 0f};
                connection.send(FrameWriter.observation(session, now, session, ObservationType.MOTION, box));
            }

        } else if (packet instanceof RemoveEntityPacket remove) {
            connection.send(FrameWriter.observation(session, now, remove.eid, ObservationType.REMOVE, null));
            PlayerSession ps = sessions.get(session);
            if (ps != null) {
                ps.getView().getVisible().remove(remove.eid);
            }

        } else if (packet instanceof UpdateBlockPacket block) {
            double dx = player.getX() - block.x;
            double dy = player.getY() - block.y;
            double dz = player.getZ() - block.z;
            if (dx * dx + dy * dy + dz * dz <= 64.0) {
                float[] box = new float[]{block.x, block.y, block.z, block.x + 1, block.y + 1, block.z + 1};
                connection.send(FrameWriter.observation(session, now, 0, ObservationType.BLOCK, box));
            }
        }
    }
}
