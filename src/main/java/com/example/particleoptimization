package com.example.ramstress;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * RAM Stress Test — раз в 5 минут выделяет ~95% свободной памяти кучи JVM
 * на 30 секунд, затем освобождает её. Предназначен для тестирования
 * поведения СВОЕГО устройства/системы под пиковой нагрузкой на память.
 *
 * ВНИМАНИЕ: при малом -Xmx это почти гарантированно приведёт к
 * OutOfMemoryError / краху игры — это ожидаемое поведение стресс-теста.
 */
public class RamStressTestMod implements ClientModInitializer {

    private static final long INTERVAL_MS = 5 * 60 * 1000L; // 5 минут
    private static final long HOLD_MS = 30 * 1000L;          // 30 секунд
    private static final double TARGET_FRACTION = 0.95;      // 95% доступной памяти

    // Держим ссылки, чтобы GC не собрал массивы раньше времени
    private final List<byte[]> heldMemory = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        Timer timer = new Timer("ram-stress-test-timer", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runStressCycle();
            }
        }, INTERVAL_MS, INTERVAL_MS);
    }

    private void runStressCycle() {
        MinecraftClient client = MinecraftClient.getInstance();
        notifyPlayer(client, "§c[RAM Stress Test] Начинаю нагрузку на память...");

        Runtime rt = Runtime.getRuntime();
        long maxHeap = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();
        long available = maxHeap - used;
        long targetBytes = (long) (available * TARGET_FRACTION);

        heldMemory.clear();
        int chunkSize = 10 * 1024 * 1024; // выделяем по 10 МБ, чтобы поймать OOM плавнее
        long allocated = 0;

        try {
            while (allocated < targetBytes) {
                int size = (int) Math.min(chunkSize, targetBytes - allocated);
                byte[] chunk = new byte[size];
                for (int i = 0; i < chunk.length; i += 4096) {
                    chunk[i] = 1;
                }
                heldMemory.add(chunk);
                allocated += size;
            }
        } catch (OutOfMemoryError e) {
            notifyPlayer(client, "§4[RAM Stress Test] OutOfMemoryError при выделении памяти!");
        }

        notifyPlayer(client, "§e[RAM Stress Test] Удерживаю ~" + (allocated / (1024 * 1024)) + " МБ на 30 секунд...");

        try {
            Thread.sleep(HOLD_MS);
        } catch (InterruptedException ignored) {
        }

        heldMemory.clear();
        System.gc();
        notifyPlayer(client, "§a[RAM Stress Test] Память освобождена.");
    }

    private void notifyPlayer(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.execute(() -> client.player.sendMessage(Text.literal(message), false));
        }
    }
}
