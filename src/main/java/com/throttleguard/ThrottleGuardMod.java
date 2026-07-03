package com.throttleguard;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ThrottleGuardMod implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "throttleguard";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();
    private static final CentralProcessor PROCESSOR = HARDWARE.getProcessor();

    private static final AtomicReference<Double> CACHED_FREQ_GHZ = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> CACHED_MAX_FREQ_GHZ = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> CACHED_LOAD = new AtomicReference<>(0.0);
    private static volatile boolean isThrottling = false;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private static final int SMOOTHING_WINDOW = 5;
    private static final List<Double> loadHistory = Collections.synchronizedList(new ArrayList<>());

    private static boolean hudVisible = true;

    private static final int SCAN_RADIUS = 4;
    private static boolean vaultHelperEnabled = true;
    private static int vaultCooldown = 0;
    private static final int COOLDOWN_TICKS = 10;

    private static int scanCounter = 0;
    private static final Map<BlockPos, Integer> heavyCoreDisplayStartTick = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("========================================");
        LOGGER.info("[ThrottleGuard] ThrottleGuard CPU HUD initialized!");
        LOGGER.info("[ThrottleGuard] F3 menu toggles HUD visibility");
        LOGGER.info("========================================");

        try {
            PROCESSOR.getCurrentFreq();
            PROCESSOR.getMaxFreq();
            LOGGER.info("[ThrottleGuard] CPU monitoring initialized successfully");
        } catch (Exception e) {
            LOGGER.warn("[ThrottleGuard] OSHI warmup failed: {}", e.getMessage());
        }

        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                long[] freqs = PROCESSOR.getCurrentFreq();
                long currentFreqHz = freqs.length > 0 ? freqs[0] : 0;
                double freqGHz = currentFreqHz / 1_000_000_000.0;

                long maxFreqHz = PROCESSOR.getMaxFreq();
                double maxFreqGHz = maxFreqHz / 1_000_000_000.0;

                double load = getSystemCpuLoad();

                CACHED_FREQ_GHZ.set(freqGHz);
                CACHED_MAX_FREQ_GHZ.set(maxFreqGHz);
                CACHED_LOAD.set(load);
                isThrottling = maxFreqHz > 0 && (currentFreqHz / (double) maxFreqHz) < 0.85;
            } catch (Exception e) {
            }
        }, 0, 1, TimeUnit.SECONDS);

        LOGGER.info("[ThrottleGuard] Loading vault auto-open feature...");
        try {
            registerVaultHelper();
            LOGGER.info("[ThrottleGuard] Vault auto-open feature loaded successfully");
        } catch (NoClassDefFoundError e) {
            LOGGER.info("[ThrottleGuard] Vault auto-open feature is client-side only");
        } catch (Exception e) {
            LOGGER.error("[ThrottleGuard] Failed to load vault auto-open feature: {}", e.getMessage(), e);
        }
        LOGGER.info("========================================");
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ThrottleGuard] ThrottleGuard client initializing...");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;

            try {
                hudVisible = !client.getDebugHud().shouldShowDebugHud();
            } catch (Exception e) {
                hudVisible = true;
            }
        });

        HudRenderCallback.EVENT.register(this::renderHud);
        LOGGER.info("[ThrottleGuard] HUD renderer registered");
    }

    private static double getSystemCpuLoad() {
        try {
            double load = PROCESSOR.getSystemCpuLoad(1000);
            if (load < 0) {
                return loadHistory.isEmpty() ? 0.0 :
                       loadHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            }
            double loadPercent = load * 100;
            loadHistory.add(loadPercent);
            while (loadHistory.size() > SMOOTHING_WINDOW) {
                loadHistory.remove(0);
            }
            return loadHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null || client.options.hudHidden || !hudVisible) return;

        double freq = CACHED_FREQ_GHZ.get();
        double maxFreq = CACHED_MAX_FREQ_GHZ.get();
        double load = CACHED_LOAD.get();
        boolean throttling = isThrottling;

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int y = 10;

        int greenColor = 0xFF00FF00;

        String freqText = String.format("Freq: %.2f / %.2f GHz", freq, maxFreq);
        context.drawText(textRenderer, freqText, screenWidth - textRenderer.getWidth(freqText) - 5, y, greenColor, true);
        y += 12;

        String loadText = String.format("Load: %.1f%%", load);
        if (throttling) {
            loadText += " THROTTLING!";
        }
        context.drawText(textRenderer, loadText, screenWidth - textRenderer.getWidth(loadText) - 5, y, greenColor, true);
        y += 12;

        int barMaxWidth = 140;
        int barWidth = (int)((freq / maxFreq) * barMaxWidth);
        int barX = screenWidth - barMaxWidth - 5;

        context.fill(barX, y, barX + barMaxWidth, y + 5, 0x44000000);

        int barColor = throttling ? 0xFFFF0000 : 0xFF00FF00;
        context.fill(barX, y, barX + Math.min(barWidth, barMaxWidth), y + 5, barColor);
        y += 10;

        String percentText = String.format("%d%%", (int)(freq / maxFreq * 100));
        context.drawText(textRenderer, percentText, screenWidth - textRenderer.getWidth(percentText) - 5, y, greenColor, true);
    }

    private void registerVaultHelper() {
        LOGGER.info("[ThrottleGuard] Registering vault scan task...");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;

            scanCounter++;

            try {
                client.execute(() -> {
                    try {
                        if (client.player == null || client.world == null) return;
                        if (!vaultHelperEnabled) return;

                        if (vaultCooldown > 0) {
                            vaultCooldown--;
                            return;
                        }

                        autoOpenVault(client);
                    } catch (Exception e) {
                    }
                });
            } catch (Exception e) {
            }
        });

        LOGGER.info("[ThrottleGuard] Vault scan task registered (every tick)");
    }

    private void autoOpenVault(MinecraftClient client) {
        PlayerEntity player = client.player;
        World world = client.world;

        if (player == null || world == null) return;

        BlockPos playerPos = player.getBlockPos();

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);

                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() != Blocks.VAULT) continue;

                    if (!isOminousVault(state, world, pos)) continue;

                    ItemStack displayItem = getDisplayItem(world, pos);
                    if (displayItem == null || displayItem.isEmpty()) {
                        if (heavyCoreDisplayStartTick.containsKey(pos)) {
                            heavyCoreDisplayStartTick.remove(pos);
                        }
                        continue;
                    }

                    if (displayItem.getItem() == Items.HEAVY_CORE) {
                        if (!heavyCoreDisplayStartTick.containsKey(pos)) {
                            heavyCoreDisplayStartTick.put(pos, scanCounter);
                        }

                        int startTick = heavyCoreDisplayStartTick.get(pos);
                        int displayDuration = scanCounter - startTick;

                        if (displayDuration >= 6) {
                            openVault(client, pos);
                            vaultCooldown = COOLDOWN_TICKS;
                            heavyCoreDisplayStartTick.remove(pos);
                            return;
                        }
                    } else {
                        if (heavyCoreDisplayStartTick.containsKey(pos)) {
                            heavyCoreDisplayStartTick.remove(pos);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean isOminousVault(BlockState state, World world, BlockPos pos) {
        try {
            for (var property : state.getProperties()) {
                if (property.getName().equals("ominous")) {
                    Object value = state.get((net.minecraft.state.property.Property) property);
                    if (value instanceof Boolean) {
                        return (Boolean) value;
                    }
                }
            }
        } catch (Exception e) {
        }

        try {
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null) return false;

            NbtCompound nbt = be.createNbt(world.getRegistryManager());
            if (nbt != null && nbt.contains("ominous")) {
                try {
                    Optional<Byte> byteOpt = nbt.getByte("ominous");
                    if (byteOpt.isPresent()) {
                        return byteOpt.get() == 1;
                    }
                } catch (Exception e) {
                }
                try {
                    Optional<String> strOpt = nbt.getString("ominous");
                    if (strOpt.isPresent()) {
                        String str = strOpt.get();
                        return str.equals("1") || str.equalsIgnoreCase("true");
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception ex) {
        }

        return false;
    }

    private ItemStack getDisplayItem(World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return ItemStack.EMPTY;

        try {
            NbtCompound nbt = be.createNbt(world.getRegistryManager());
            if (nbt == null) return ItemStack.EMPTY;

            if (nbt.contains("shared_data")) {
                Optional<NbtCompound> sharedDataOpt = nbt.getCompound("shared_data");
                if (sharedDataOpt.isPresent()) {
                    NbtCompound sharedData = sharedDataOpt.get();
                    if (sharedData.contains("display_item")) {
                        Optional<NbtCompound> displayItemOpt = sharedData.getCompound("display_item");
                        if (displayItemOpt.isPresent()) {
                            NbtCompound displayItemNbt = displayItemOpt.get();
                            ItemStack stack = nbtToItemStack(displayItemNbt);
                            if (stack != null && !stack.isEmpty()) {
                                return stack;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        return ItemStack.EMPTY;
    }

    private ItemStack nbtToItemStack(NbtCompound nbt) {
        try {
            if (!nbt.contains("id")) return ItemStack.EMPTY;

            Optional<String> idOpt = nbt.getString("id");
            if (!idOpt.isPresent()) return ItemStack.EMPTY;

            String id = idOpt.get();

            int count = 1;
            if (nbt.contains("count")) {
                Optional<Byte> countOpt = nbt.getByte("count");
                if (countOpt.isPresent()) {
                    count = countOpt.get();
                } else {
                    Optional<Integer> intCountOpt = nbt.getInt("count");
                    if (intCountOpt.isPresent()) {
                        count = intCountOpt.get();
                    }
                }
            }

            var item = Registries.ITEM.get(Identifier.of(id));
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;

            return new ItemStack(item, count);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private void openVault(MinecraftClient client, BlockPos pos) {
        if (client.player == null) return;

        Vec3d centerPos = Vec3d.ofCenter(pos);
        BlockHitResult hitResult = new BlockHitResult(
                centerPos,
                Direction.UP,
                pos,
                false
        );

        if (client.interactionManager == null) return;

        try {
            ActionResult result = client.interactionManager.interactBlock(
                    client.player,
                    net.minecraft.util.Hand.MAIN_HAND,
                    hitResult
            );

            if (result.isAccepted()) {
                client.player.sendMessage(Text.literal("§a✅ Vault opened!"), true);
            } else {
                client.player.sendMessage(Text.literal("§c❌ Failed to open vault!"), true);
            }
        } catch (Exception e) {
        }
    }
}