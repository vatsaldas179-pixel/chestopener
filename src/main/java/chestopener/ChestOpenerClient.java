package chestopener;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Opens any chest (or trapped chest) within your block reach, once per chest.
 * Client-side only. Intended for singleplayer.
 */
public class ChestOpenerClient implements ClientModInitializer {
    private static final int COOLDOWN_TICKS = 10;

    // Chests we've already opened, so they don't re-open every time you close the screen.
    private final Set<BlockPos> opened = new HashSet<>();
    private int cooldown = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> opened.clear());
    }

    private void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || client.interactionManager == null) return;

        // Don't act while any screen is open (including the chest we just opened).
        if (client.currentScreen != null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // Sneaking + holding a block would try to place instead of open.
        if (player.isSneaking()) return;

        // Forget positions that are no longer chests, so a new chest there opens again.
        opened.removeIf(pos -> !(world.getBlockState(pos).getBlock() instanceof ChestBlock));

        double range = player.getBlockInteractionRange();
        double rangeSq = range * range;
        Vec3d eye = player.getEyePos();
        int r = (int) Math.ceil(range);
        BlockPos center = player.getBlockPos();

        for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock)) continue;

            BlockPos immutable = pos.toImmutable();
            if (opened.contains(immutable)) continue;
            if (eye.squaredDistanceTo(Vec3d.ofCenter(immutable)) > rangeSq) continue;
            // Blocked chests (solid block or cat above) can't be opened anyway.
            if (ChestBlock.isChestBlocked(world, immutable)) continue;

            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(immutable), Direction.UP, immutable, false);
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);

            opened.add(immutable);
            // A double chest is one inventory; mark the other half too.
            if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                opened.add(immutable.offset(ChestBlock.getFacing(state)));
            }

            cooldown = COOLDOWN_TICKS;
            return; // one chest per attempt
        }
    }
}
