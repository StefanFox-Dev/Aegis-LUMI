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
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.inventory.transaction.data.UseItemOnEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFood;
import cn.nukkit.item.ItemPotion;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.SwapAction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketObserver implements Listener {

    private final AegisConfig config;
    private final AegisConnection connection;
    private final SessionRegistry sessions;
    private final TickBudget budget;
    private final Map<Long, Integer> lastSlots = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastClientTicks = new ConcurrentHashMap<>();

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
            lastClientTicks.put(session, auth.getTick());

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

            int controlExt = 0;
            if (player.isUsingItem() || auth.getInputData().contains(AuthInputAction.START_USING_ITEM)) controlExt |= InputControl.EXT_USING_ITEM;
            if (auth.getInputData().contains(AuthInputAction.START_GLIDING) || player.isGliding()) controlExt |= InputControl.EXT_GLIDING;
            if (auth.getInputData().contains(AuthInputAction.START_SWIMMING) || player.isSwimming()) controlExt |= InputControl.EXT_SWIMMING;
            if (auth.getInputData().contains(AuthInputAction.START_SPIN_ATTACK)) controlExt |= InputControl.EXT_SPIN_ATTACK;
            if (auth.getInputData().contains(AuthInputAction.UP)) controlExt |= InputControl.EXT_UP;
            if (auth.getInputData().contains(AuthInputAction.DOWN)) controlExt |= InputControl.EXT_DOWN;
            if (auth.getInputData().contains(AuthInputAction.LEFT)) controlExt |= InputControl.EXT_LEFT;
            if (auth.getInputData().contains(AuthInputAction.RIGHT)) controlExt |= InputControl.EXT_RIGHT;

            Vector3f delta = auth.getDelta();
            Vector2f analog = auth.getAnalogMoveVector();
            Vector2f raw = auth.getRawMoveVector();
            Vector2f interact = auth.getInteractRotation();
            Vector3f cam = auth.getCameraOrientation();
            int playMode = auth.getPlayMode() != null ? auth.getPlayMode().ordinal() : 0;
            int interactionModel = auth.getInteractionModel() != null ? auth.getInteractionModel().ordinal() : 0;

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
                    control,
                    controlExt,
                    delta != null ? delta.x : 0f,
                    delta != null ? delta.y : 0f,
                    delta != null ? delta.z : 0f,
                    analog != null ? analog.getX() : 0f,
                    analog != null ? analog.getY() : 0f,
                    raw != null ? raw.getX() : 0f,
                    raw != null ? raw.getY() : 0f,
                    interact != null ? interact.getX() : 0f,
                    interact != null ? interact.getY() : 0f,
                    cam != null ? cam.x : 0f,
                    cam != null ? cam.y : 0f,
                    cam != null ? cam.z : 0f,
                    playMode,
                    interactionModel
            );
            connection.send(frame);

            // Handle inline item interaction
            InventoryTransactionPacket tx = auth.getItemUseTransaction();
            if (tx != null) {
                emitTransactionTelemetry(player, session, now, auth.getTick(), tx);
            }
            if (auth.getItemStackRequest() != null) {
                emitItemStackTelemetry(session, now, auth.getTick(), auth.getItemStackRequest());
            }

        } else if (packet instanceof InventoryTransactionPacket tx) {
            long tick = lastClientTicks.getOrDefault(session, (long) Server.getInstance().getTick());
            emitTransactionTelemetry(player, session, now, tick, tx);

        } else if (packet instanceof MobEquipmentPacket eq) {
            long tick = lastClientTicks.getOrDefault(session, (long) Server.getInstance().getTick());
            int toSlot = eq.hotbarSlot;
            int fromSlot = lastSlots.getOrDefault(session, toSlot);
            if (fromSlot != toSlot) {
                lastSlots.put(session, toSlot);
                connection.send(FrameWriter.slotChange(session, now, tick, fromSlot, toSlot));
            }

        } else if (packet instanceof ItemStackRequestPacket req && req.getRequests() != null) {
            long tick = lastClientTicks.getOrDefault(session, (long) Server.getInstance().getTick());
            for (ItemStackRequest r : req.getRequests()) {
                if (r != null) {
                    emitItemStackTelemetry(session, now, tick, r);
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

    private void emitTransactionTelemetry(Player player, long session, long now, long tick, InventoryTransactionPacket tx) {
        if (tx.transactionType == InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY) {
            if (tx.transactionData instanceof UseItemOnEntityData useData
                    && useData.actionType == InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_ATTACK) {
                connection.send(FrameWriter.attack(session, now, useData.entityRuntimeId));
            }
        } else if (tx.transactionType == InventoryTransactionPacket.TYPE_USE_ITEM) {
            int slot = player.getInventory() != null ? player.getInventory().getHeldItemIndex() : 0;
            Item item = player.getInventory() != null ? player.getInventory().getItemInHandFast() : null;
            int actionType = tx.transactionData instanceof UseItemData u ? u.actionType : 0;
            sendUseItemFrame(session, now, tick, actionType, slot, item);
        } else if (tx.transactionType == InventoryTransactionPacket.TYPE_RELEASE_ITEM) {
            int slot = player.getInventory() != null ? player.getInventory().getHeldItemIndex() : 0;
            Item item = player.getInventory() != null ? player.getInventory().getItemInHandFast() : null;
            sendUseItemFrame(session, now, tick, 2, slot, item);
        }
    }

    private void sendUseItemFrame(long session, long now, long tick, int actionType, int slot, Item item) {
        int itemId = item != null ? item.getId() : 0;
        int flags = 0;
        if (item != null) {
            String ns = item.getNamespaceId();
            if (ns != null && (ns.contains("potion") || ns.contains("ender_pearl") || ns.contains("snowball")
                    || ns.contains("egg") || ns.contains("wind_charge") || ns.contains("experience_bottle"))) {
                flags |= 1; // projectile
            }
            if (item instanceof ItemFood || item instanceof ItemPotion) {
                flags |= 2; // consumable
            }
        }
        connection.send(FrameWriter.useItem(session, now, tick, actionType, slot, itemId, flags));
    }

    private void emitItemStackTelemetry(long session, long now, long tick, ItemStackRequest req) {
        if (req.getActions() == null) {
            return;
        }
        for (ItemStackRequestAction action : req.getActions()) {
            if (action instanceof PlaceAction place && place.getSource() != null && place.getDestination() != null) {
                int src = place.getSource().getSlot();
                int dst = place.getDestination().getSlot();
                if (dst >= 0 && dst <= 8 && src >= 9) {
                    connection.send(FrameWriter.inventoryAction(session, now, tick, 0, src, dst, 1));
                }
            } else if (action instanceof SwapAction swap && swap.getSource() != null && swap.getDestination() != null) {
                int src = swap.getSource().getSlot();
                int dst = swap.getDestination().getSlot();
                if (dst >= 0 && dst <= 8 && src >= 9) {
                    connection.send(FrameWriter.inventoryAction(session, now, tick, 0, src, dst, 2));
                } else if (src >= 0 && src <= 8 && dst >= 9) {
                    connection.send(FrameWriter.inventoryAction(session, now, tick, 0, dst, src, 2));
                }
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
