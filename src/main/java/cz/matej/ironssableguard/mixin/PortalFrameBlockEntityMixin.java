package cz.matej.ironssableguard.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity;
import io.redspace.ironsspellbooks.capabilities.magic.PortalManager;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalData;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = PortalFrameBlockEntity.class, remap = false)
public abstract class PortalFrameBlockEntityMixin {

    private static final Map<UUID, Long> PORTAL_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 4000L;

    @Shadow
    public abstract UUID getUUID();

    @Shadow
    private PortalData getPortalData() {
        throw new AssertionError();
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void ironssableguard$teleport(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel currentLevel)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long cooldown = PORTAL_COOLDOWNS.get(player.getUUID());

        if (cooldown != null && cooldown > now) {
            ci.cancel();
            return;
        }

        PORTAL_COOLDOWNS.put(player.getUUID(), now + COOLDOWN_MS);

        UUID frameUuid = this.getUUID();

        if (frameUuid == null) {
            ci.cancel();
            return;
        }

        PortalManager.INSTANCE.processDelayCooldown(
                frameUuid,
                player.getUUID(),
                1
        );

        if (!PortalManager.INSTANCE.canUsePortal(frameUuid, player)) {
            ci.cancel();
            return;
        }

        PortalData portalData = this.getPortalData();

        if (portalData == null) {
            ci.cancel();
            return;
        }

        Optional<PortalPos> connectedOptional = portalData.getConnectedPortalPos(frameUuid);

        if (connectedOptional.isEmpty()) {
            ci.cancel();
            return;
        }

        PortalPos connectedPos = connectedOptional.get();

        ServerLevel targetLevel = currentLevel.getServer().getLevel(connectedPos.dimension());

        if (targetLevel == null) {
            ci.cancel();
            return;
        }

        Vec3 target = SableWaystoneCompat.getVisibleTeleportPos(
                targetLevel,
                connectedPos.pos()
        );

        ChunkPos targetChunk = new ChunkPos(
                ((int) Math.floor(target.x)) >> 4,
                ((int) Math.floor(target.z)) >> 4
        );

        targetLevel.getChunk(targetChunk.x, targetChunk.z);

        PortalManager.INSTANCE.addPortalCooldown(player, frameUuid);

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        PortalFrameBlockEntity self = (PortalFrameBlockEntity) (Object) this;

        currentLevel.playSound(
                null,
                self.getBlockPos(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );

        if (currentLevel.dimension().equals(targetLevel.dimension())) {
            player.teleportTo(
                    targetLevel,
                    target.x,
                    target.y,
                    target.z,
                    RelativeMovement.ROTATION,
                    connectedPos.rotation(),
                    player.getXRot()
            );
        } else {
            player.changeDimension(
                    new DimensionTransition(
                            targetLevel,
                            target,
                            Vec3.ZERO,
                            connectedPos.rotation(),
                            player.getXRot(),
                            DimensionTransition.DO_NOTHING
                    )
            );
        }

        player.connection.teleport(
                target.x,
                target.y,
                target.z,
                connectedPos.rotation(),
                player.getXRot()
        );

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        targetLevel.playSound(
                null,
                target.x,
                target.y,
                target.z,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );

        ci.cancel();
    }
}