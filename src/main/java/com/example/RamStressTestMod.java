package com.example.ramstress;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class RamStressTestMod implements ClientModInitializer {

    private static final long MIN_INTERVAL_MS = 1 * 60 * 1000L;   // 1 минута
    private static final long MAX_INTERVAL_MS = 10 * 60 * 1000L;  // 10 минут
    private static final long MIN_HOLD_MS = 10 * 1000L;           // 10 секунд
    private static final long MAX_HOLD_MS = 40 * 1000L;           // 40 секунд
    private static final double TARGET_FRACTION = 0.95;           // 95% от ОБЩЕЙ (max) памяти

    private static final java.util.Random RANDOM = new java.util.Random();

    private final List<byte[]> heldMemory = new ArrayList<>();
    private final Timer timer = new Timer("ram-stress-test-timer", true);

    @Override
    public void onInitializeClient() {
        scheduleNextCycle();
    }

    private void scheduleNextCycle() {
        long delay = randomBetween(MIN_INTERVAL_MS, MAX_INTERVAL_MS);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runStressCycle();
                scheduleNextCycle();
            }
        }, delay);
    }

    private long randomBetween(long min, long max) {
        return min + (long) (RANDOM.nextDouble() * (max - min));
    }

    private void runStressCycle() {
        long holdMs = randomBetween(MIN_HOLD_MS, MAX_HOLD_MS);
        MinecraftClient client = MinecraftClient.getInstance();
        

        Runtime rt = Runtime.getRuntime();
        long maxHeap = rt.maxMemory();
        long targetBytes = (long) (maxHeap * TARGET_FRACTION);

        heldMemory.clear();
        int chunkSize = 10 * 1024 * 1024;
        final int minChunkSize = 256 * 1024;
        long allocated = 0;

        while (true) {
            long currentlyUsed = rt.totalMemory() - rt.freeMemory();
            if (currentlyUsed >= targetBytes) break;

            long remaining = targetBytes - currentlyUsed;
            int size = (int) Math.min(chunkSize, remaining);
            if (size <= 0) break;

            try {
                byte[] chunk = new byte[size];
                for (int i = 0; i < chunk.length; i += 4096) {
                    chunk[i] = 1;
                }
                heldMemory.add(chunk);
                allocated += size;
            } catch (OutOfMemoryError e) {
                if (chunkSize > minChunkSize) {
                    chunkSize = Math.max(minChunkSize, chunkSize / 2);
                } else {
                    break;
                }
            }
        }

        long usedAfter = rt.totalMemory() - rt.freeMemory();
        int percentOfMax = (int) ((usedAfter * 100L) / maxHeap);
        try {
            Thread.sleep(holdMs);
        } catch (InterruptedException ignored) {
        }

    
        heldMemory.clear();
        System.gc();
    }
