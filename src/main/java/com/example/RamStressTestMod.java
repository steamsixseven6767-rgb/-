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
 * RAM Stress Test — раз в 3 минуты выделяет память так, чтобы суммарно
 * занятая куча JVM достигла ~95% от общего максимума (-Xmx), держит 30 секунд,
 * затем освобождает по кусочку с задержкой (растянутый, а не мгновенный фриз).
 *
 * ВНИМАНИЕ: это почти гарантированно приведёт к OutOfMemoryError / краху игры —
 * это ожидаемое поведение стресс-теста.
 */
public class RamStressTestMod implements ClientModInitializer {

    private static final long INTERVAL_MS = 3 * 60 * 1000L; // 3 минуты
    private static final long HOLD_MS = 30 * 1000L;          // 30 секунд
    private static final double TARGET_FRACTION = 0.95;      // 95% от ОБЩЕЙ (max) памяти, не от свободной
    private static final long RELEASE_STEP_DELAY_MS = 150L;  // задержка между освобождением кусков — растягивает "просадку" при очистке

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
        long targetBytes = (long) (maxHeap * TARGET_FRACTION);

        heldMemory.clear();
        int chunkSize = 10 * 1024 * 1024;
        long allocated = 0;

        try {
            while (true) {
                long currentlyUsed = rt.totalMemory() - rt.freeMemory();
                if (currentlyUsed >= targetBytes) break;

                int size = (int) Math.min(chunkSize, targetBytes - currentlyUsed);
                if (size <= 0) break;

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

        long usedAfter = rt.totalMemory() - rt.freeMemory();
        int percentOfMax = (int) ((usedAfter * 100L) / maxHeap);
        notifyPlayer(client, "§e[RAM Stress Test] Занято ~" + percentOfMax + "% от общей памяти (~"
                + (usedAfter / (1024 * 1024)) + " МБ из " + (maxHeap / (1024 * 1024)) + " МБ). Держу 30 секунд...");

        try {
            Thread.sleep(HOLD_MS);
        } catch (InterruptedException ignored) {
        }

        notifyPlayer(client, "§6[RAM Stress Test] Начинаю освобождение (с задержкой)...");
        while (!heldMemory.isEmpty()) {
            heldMemory.remove(heldMemory.size() - 1);
            try {
                Thread.sleep(RELEASE_STEP_DELAY_MS);
            } catch (InterruptedException ignored) {
            }
        }
        System.gc();
        notifyPlayer(client, "§a[RAM Stress Test] Память освобождена.");
    }

    private void notifyPlayer(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.execute(() -> client.player.sendMessage(Text.literal(message), false));
        }
    }
}
