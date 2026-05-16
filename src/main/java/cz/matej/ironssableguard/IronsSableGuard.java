package cz.matej.ironssableguard;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellTeleportEvent;
import io.redspace.ironsspellbooks.capabilities.magic.PortalManager;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalData;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ironssableguard")
public class IronsSableGuard {

    private static final int PORTAL_REWRITE_INTERVAL_TICKS = 5;
    private static final double MIN_REWRITE_DISTANCE_SQR = 0.0001D;

    private static final IdentityHashMap<PortalData, OriginalPortalPositions> ORIGINAL_PORTAL_POSITIONS = new IdentityHashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (level.getGameTime() % PORTAL_REWRITE_INTERVAL_TICKS != 0) {
            return;
        }

        rewriteAllPortalData(level);
    }

    private static void rewriteAllPortalData(ServerLevel contextLevel) {
        HashMap<UUID, PortalData> portalLookup = getPortalLookupReflective();

        if (portalLookup == null || portalLookup.isEmpty()) {
            return;
        }

        IdentityHashMap<PortalData, Boolean> processed = new IdentityHashMap<>();

        for (Map.Entry<UUID, PortalData> entry : portalLookup.entrySet()) {
            PortalData data = entry.getValue();

            if (data == null || processed.containsKey(data)) {
                continue;
            }

            processed.put(data, true);

            if (data.globalPos1 == null || data.globalPos2 == null) {
                continue;
            }

            OriginalPortalPositions original = ORIGINAL_PORTAL_POSITIONS.get(data);

            if (original == null) {
                original = new OriginalPortalPositions(data.globalPos1, data.globalPos2);
                ORIGINAL_PORTAL_POSITIONS.put(data, original);
            }

            data.globalPos1 = rewritePortalPos(contextLevel, original.pos1);
            data.globalPos2 = rewritePortalPos(contextLevel, original.pos2);
        }
    }

    @SuppressWarnings("unchecked")
    private static HashMap<UUID, PortalData> getPortalLookupReflective() {
        try {
            Field field = PortalManager.class.getDeclaredField("portalLookup");
            field.setAccessible(true);

            Object value = field.get(PortalManager.INSTANCE);

            if (value instanceof HashMap<?, ?> map) {
                return (HashMap<UUID, PortalData>) map;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static PortalPos rewritePortalPos(ServerLevel contextLevel, PortalPos originalPortalPos) {
        if (originalPortalPos == null) {
            return null;
        }

        ServerLevel correctLevel = contextLevel.getServer().getLevel(originalPortalPos.dimension());

        if (correctLevel == null) {
            correctLevel = contextLevel;
        }

        Vec3 original = originalPortalPos.pos();

        Vec3 projected = SableWaystoneCompat.getVisibleTeleportPos(
                correctLevel,
                original
        );

        if (original.distanceToSqr(projected) < MIN_REWRITE_DISTANCE_SQR) {
            return originalPortalPos;
        }

        return PortalPos.of(
                originalPortalPos.dimension(),
                projected,
                originalPortalPos.rotation()
        );
    }

    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        String spell = event.getSpellId().toString().toLowerCase();

        boolean blockSpell =
                spell.equals("irons_spellbooks:shadow_slash") ||
                spell.contains("shadow_slash");

        if (blockSpell) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSpellTeleport(SpellTeleportEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation spellId = event.getSpell().getSpellResource();
        String spell = spellId.toString().toLowerCase();

        boolean isRecall =
                spell.equals("irons_spellbooks:recall") ||
                spell.contains("recall");

        boolean isSableMovementOrTeleport =
                spell.equals("irons_spellbooks:teleport") ||
                spell.equals("irons_spellbooks:blood_step") ||
                spell.equals("irons_spellbooks:frost_step") ||
                spell.equals("irons_spellbooks:evasion") ||
                spell.equals("irons_spellbooks:burning_dash") ||
                spell.equals("irons_spellbooks:volt_strike") ||
                spell.equals("irons_spellbooks:ascension") ||

                spell.contains("teleport") ||
                spell.contains("blood_step") ||
                spell.contains("frost_step") ||
                spell.contains("evasion") ||
                spell.contains("burning_dash") ||
                spell.contains("volt_strike") ||
                spell.contains("ascension");

        if (isRecall) {
            return;
        }

        if (!isSableMovementOrTeleport) {
            return;
        }

        Vec3 originalTarget = new Vec3(
                event.getTargetX(),
                event.getTargetY(),
                event.getTargetZ()
        );

        Vec3 projectedTarget = SableWaystoneCompat.getVisibleTeleportPos(
                player.level(),
                originalTarget
        );

        event.setTargetX(projectedTarget.x);
        event.setTargetY(projectedTarget.y);
        event.setTargetZ(projectedTarget.z);
    }

    private record OriginalPortalPositions(PortalPos pos1, PortalPos pos2) {
    }
}