package dev.flowtail.benchplugin;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.sun.management.GarbageCollectionNotificationInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

/** Local-only benchmark probe: rolling tick duration and JVM GC pause measurements. */
public final class BenchMetricsPlugin extends JavaPlugin implements Listener {
    private final ArrayDeque<Double> ticks = new ArrayDeque<>();
    private volatile long gcCount;
    private volatile long gcTotalMs;
    private volatile long gcMaxMs;
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final com.sun.management.OperatingSystemMXBean osBean =
        ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean ? bean : null;
    private final Map<Long, Long> lastThreadCpu = new HashMap<>();
    private volatile String threadCpuJson = "{\"available\":false}";
    private long lastThreadSampleNanos;
    private volatile double processCpuPercent = -1;
    private volatile long processRssMb = -1;
    private Path output;
    private ShapeHitboxProbe hitboxProbe;

    @Override public void onEnable() {
        this.output = getDataFolder().toPath().resolve("metrics.json");
        getServer().getPluginManager().registerEvents(this, this);
        this.hitboxProbe = new ShapeHitboxProbe(this);
        getServer().getPluginManager().registerEvents(this.hitboxProbe, this);
        if (getCommand("hitboxprobe") != null) getCommand("hitboxprobe").setExecutor(this.hitboxProbe);
        if (threadBean.isThreadCpuTimeSupported()) {
            try { threadBean.setThreadCpuTimeEnabled(true); } catch (SecurityException ignored) { }
        }
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof NotificationEmitter emitter) {
                NotificationListener listener = this::onGcNotification;
                try { emitter.addNotificationListener(listener, null, null); }
                catch (Exception ex) { getLogger().warning("Could not attach GC metrics listener: " + ex.getMessage()); }
            }
        }
        getLogger().info("Local benchmark metrics enabled; output: " + output);
    }

    @Override public void onDisable() {
        if (this.hitboxProbe != null) this.hitboxProbe.cleanup();
    }

    private void onGcNotification(Notification notification, Object ignored) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
        var info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
        long duration = info.getGcInfo().getDuration();
        synchronized (this) { gcCount++; gcTotalMs += duration; gcMaxMs = Math.max(gcMaxMs, duration); }
    }

    @EventHandler public void onTickEnd(ServerTickEndEvent event) {
        if (event.getTickNumber() % 20 != 0) return;
        synchronized (ticks) {
            ticks.addLast(event.getTickDuration());
            while (ticks.size() > 6000) ticks.removeFirst();
            threadCpuJson = sampleThreadCpu();
            sampleProcessMetrics();
            try {
                Files.createDirectories(output.getParent());
                Files.writeString(output, report(ticks));
            } catch (Exception ex) { getLogger().warning("Could not write metrics file: " + ex.getMessage()); }
        }
    }

    private String report(ArrayDeque<Double> data) {
        List<Double> sorted = new ArrayList<>(data);
        sorted.sort(Double::compareTo);
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        synchronized (this) {
            return String.format(Locale.ROOT,
                "{\"time\":\"%s\",\"sample_count\":%d,\"mspt\":{\"mean\":%.3f,\"p50\":%.3f,\"p95\":%.3f,\"p99\":%.3f,\"max\":%.3f},\"gc\":{\"count\":%d,\"total_pause_ms\":%d,\"max_pause_ms\":%d},\"thread_cpu_pct\":%s,\"process_cpu_percent_one_core\":%.2f,\"rss_mb\":%d}%n",
                java.time.Instant.now(), sorted.size(), mean, pick(sorted, .50), pick(sorted, .95), pick(sorted, .99), sorted.isEmpty() ? 0 : sorted.getLast(), gcCount, gcTotalMs, gcMaxMs, threadCpuJson, processCpuPercent, processRssMb);
        }
    }

    private void sampleProcessMetrics() {
        if (osBean != null) {
            double cpu = osBean.getProcessCpuLoad();
            if (cpu >= 0) processCpuPercent = cpu * 100.0;
        }
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) { processRssMb = Math.round(Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024.0); return; }
            }
        } catch (Exception ignored) { }
    }

    private synchronized String sampleThreadCpu() {
        if (!threadBean.isThreadCpuTimeSupported() || !threadBean.isThreadCpuTimeEnabled()) return "{\"available\":false}";
        long now = System.nanoTime();
        long elapsed = lastThreadSampleNanos == 0 ? 0 : Math.max(1, now - lastThreadSampleNanos);
        lastThreadSampleNanos = now;
        List<Double> percentages = new ArrayList<>();
        for (long id : threadBean.getAllThreadIds()) {
            long cpu = threadBean.getThreadCpuTime(id);
            if (cpu < 0) { lastThreadCpu.remove(id); continue; }
            Long previous = lastThreadCpu.put(id, cpu);
            if (previous != null && elapsed > 0) percentages.add(Math.max(0, (cpu - previous) * 100.0 / elapsed));
        }
        if (percentages.isEmpty()) return "{\"available\":true,\"samples\":0}";
        percentages.sort(Double::compareTo);
        double p95 = percentages.get(Math.max(0, (int) Math.ceil(percentages.size() * .95) - 1));
        double max = percentages.getLast();
        return String.format(Locale.ROOT, "{\"available\":true,\"samples\":%d,\"p95_one_core_percent\":%.2f,\"max_one_core_percent\":%.2f}", percentages.size(), p95, max);
    }

    private static double pick(List<Double> values, double p) {
        return values.isEmpty() ? 0 : values.get(Math.max(0, (int) Math.ceil(p * values.size()) - 1));
    }
}
