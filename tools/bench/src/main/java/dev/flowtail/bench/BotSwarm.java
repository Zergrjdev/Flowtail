package dev.flowtail.bench;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandSuggestionsPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundCommandSuggestionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Offline-mode protocol bots for isolated, operator-owned benchmark servers. */
public final class BotSwarm {
    private BotSwarm() { }

    private static final class Bot {
        final String name;
        final ClientSession session;
        final long startedNanos = System.nanoTime();
        final AtomicBoolean loggedIn = new AtomicBoolean();
        final AtomicBoolean disconnected = new AtomicBoolean();
        final AtomicLong bytesIn = new AtomicLong();
        final AtomicLong bytesOut = new AtomicLong();
        final AtomicLong xBits = new AtomicLong(Double.doubleToRawLongBits(0));
        final AtomicLong yBits = new AtomicLong(Double.doubleToRawLongBits(80));
        final AtomicLong zBits = new AtomicLong(Double.doubleToRawLongBits(0));
        volatile float yaw;
        volatile long loginMillis = -1;
        volatile long firstChunkMillis = -1;
        volatile long loginAtNanos;
        volatile boolean packLoaded;
        final AtomicBoolean creativeEchoed = new AtomicBoolean();

        Bot(String name, ClientSession session) { this.name = name; this.session = session; }
        double x() { return Double.longBitsToDouble(xBits.get()); }
        double y() { return Double.longBitsToDouble(yBits.get()); }
        double z() { return Double.longBitsToDouble(zBits.get()); }
        void position(Vector3d p) { xBits.set(Double.doubleToRawLongBits(p.getX())); yBits.set(Double.doubleToRawLongBits(p.getY())); zBits.set(Double.doubleToRawLongBits(p.getZ())); }
    }

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1", prefix = "FTBench", packResponse = "accept";
        String command = null, tabComplete = null;
        boolean creativeEcho = false;
        boolean dropItem = false;
        int port = 25565, count = 10, durationSeconds = 60;
        long serverPid = -1;
        boolean move = true;
        Path metricsFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--bots" -> count = Integer.parseInt(args[++i]);
                case "--duration-seconds" -> durationSeconds = Integer.parseInt(args[++i]);
                case "--server-pid" -> serverPid = Long.parseLong(args[++i]);
                case "--prefix" -> prefix = args[++i];
                case "--pack-response" -> packResponse = args[++i];
                case "--command" -> command = args[++i];
                case "--tab-complete" -> tabComplete = args[++i];
                case "--creative-echo" -> creativeEcho = true;
                case "--drop-item" -> dropItem = true;
                case "--server-metrics-file" -> metricsFile = Path.of(args[++i]);
                case "--idle" -> move = false;
                case "--help" -> { help(); return; }
                default -> { System.err.println("Unknown option: " + args[i]); help(); System.exit(2); }
            }
        }
        if (count < 1 || count > 500 || durationSeconds < 1 || durationSeconds > 3600 || port < 1 || port > 65535
                || (!packResponse.equals("accept") && !packResponse.equals("decline"))) {
            System.err.println("Use 1–500 bots, 1–3600 seconds, and a valid port."); System.exit(2);
        }

        var bots = new CopyOnWriteArrayList<Bot>();
        var loginTimes = Collections.synchronizedList(new ArrayList<Long>());
        var loggedIn = new CountDownLatch(count);
        var movementPackets = new LongAdder();
        var chunkPackets = new LongAdder();
        var packsLoaded = new LongAdder();
        var packsDeclined = new LongAdder();
        final boolean movingBots = move;
        final String packMode = packResponse;
        final String commandToSend = command;
        final String tabText = tabComplete;
        final boolean creativeEchoEnabled = creativeEcho;
        final boolean dropItemAfterGive = dropItem;
        var workerCount = Math.min(count, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ScheduledExecutorService ticks = Executors.newScheduledThreadPool(workerCount);
        log("INFO", "bench", "Connecting offline-mode bots; the target must be an isolated server with online-mode=false", "bots", count, "host", host, "port", port);

        for (int i = 0; i < count; i++) {
            String name = safeName(prefix, i);
            var protocol = new MinecraftProtocol(name);
            ClientSession session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(host, port))
                .setProtocol(protocol)
                .create();
            Bot bot = new Bot(name, session);
            bots.add(bot);
            session.addListener(new SessionAdapter() {
                @Override public void connected(ConnectedEvent event) {
                    Channel channel = session.getChannel();
                    if (channel == null) return;
                    channel.pipeline().addFirst("flowtail-bench-counter-" + name, new ChannelDuplexHandler() {
                        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof ByteBuf buffer) bot.bytesIn.addAndGet(buffer.readableBytes());
                            super.channelRead(ctx, msg);
                        }
                        @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof ByteBuf buffer) bot.bytesOut.addAndGet(buffer.readableBytes());
                            super.write(ctx, msg, promise);
                        }
                    });
                }
                @Override public void packetReceived(org.geysermc.mcprotocollib.network.Session ignored, Packet packet) {
                    if (packet instanceof ClientboundResourcePackPushPacket packRequest) {
                        respondToPack(bot, packRequest, packsLoaded, packsDeclined, packMode);
                    } else if (packet instanceof ClientboundLoginPacket && bot.loggedIn.compareAndSet(false, true)) {
                        bot.loginMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - bot.startedNanos);
                        bot.loginAtNanos = System.nanoTime();
                        loginTimes.add(bot.loginMillis);
                        loggedIn.countDown();
                        if (tabText != null) session.send(new ServerboundCommandSuggestionPacket(1, tabText));
                        if (commandToSend != null) Thread.ofVirtual().start(() -> {
                            for (String line : commandToSend.split(";")) {
                                session.send(new ServerboundChatCommandPacket(line.trim()));
                                try { Thread.sleep(150); } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt(); return;
                                }
                            }
                            if (dropItemAfterGive) {
                                try { Thread.sleep(500); } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt(); return;
                                }
                                session.send(new ServerboundPlayerActionPacket(PlayerAction.DROP_ITEM,
                                        Vector3i.from(0, 0, 0), Direction.DOWN, 0));
                            }
                        });
                        ticks.scheduleAtFixedRate(() -> tick(bot, movingBots, movementPackets), 100, 100, TimeUnit.MILLISECONDS);
                    } else if (packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
                        log("INFO", "bench", "Command suggestions received", "matches", List.of(suggestions.getMatches()));
                    } else if (creativeEchoEnabled && packet instanceof ClientboundContainerSetSlotPacket slot
                            && slot.getContainerId() == 0 && slot.getItem() != null
                            && bot.creativeEchoed.compareAndSet(false, true)) {
                        log("INFO", "bench", "Echoing returned creative inventory stack", "slot", slot.getSlot() + 1,
                                "stack", slot.getItem().toString());
                        session.send(new ServerboundSetCreativeModeSlotPacket((short) (slot.getSlot() + 1), slot.getItem()));
                    } else if (creativeEchoEnabled && packet instanceof ClientboundSetPlayerInventoryPacket inventory
                            && inventory.getContents() != null && bot.creativeEchoed.compareAndSet(false, true)) {
                        log("INFO", "bench", "Echoing returned creative inventory stack", "slot", inventory.getSlot() + 1,
                                "stack", inventory.getContents().toString());
                        session.send(new ServerboundSetCreativeModeSlotPacket((short) (inventory.getSlot() + 1), inventory.getContents()));
                    } else if (packet instanceof ClientboundPlayerPositionPacket teleport) {
                        bot.position(teleport.getPosition());
                        bot.yaw = teleport.getYRot();
                        session.send(new ServerboundAcceptTeleportationPacket(teleport.getId(), bot.x(), bot.y(), bot.z(), teleport.getYRot(), teleport.getXRot()));
                        session.send(ServerboundClientTickEndPacket.INSTANCE);
                    } else if (bot.loggedIn.get() && packet.getClass().getSimpleName().contains("Chunk")) {
                        chunkPackets.increment();
                        if (bot.firstChunkMillis < 0) bot.firstChunkMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - bot.loginAtNanos);
                    }
                }
                @Override public void disconnected(DisconnectedEvent event) { bot.disconnected.set(true); }
            });
            session.connect();
            Thread.sleep(25);
        }

        boolean allConnected = loggedIn.await(Math.min(30, durationSeconds), TimeUnit.SECONDS);
        log(allConnected ? "INFO" : "WARN", "bench", "Bot login phase ended", "connected", loginTimes.size(), "requested", count);
        List<ProcessSample> processSamples = new ArrayList<>();
        if (serverPid > 0) processSamples.add(processSample(serverPid));
        long endAt = System.nanoTime() + Duration.ofSeconds(durationSeconds).toNanos();
        while (System.nanoTime() < endAt) {
            Thread.sleep(Math.min(1000, Math.max(1, TimeUnit.NANOSECONDS.toMillis(endAt - System.nanoTime()))));
            if (serverPid > 0) processSamples.add(processSample(serverPid));
        }
        for (Bot bot : bots) bot.session.disconnect(Component.text("FlowTail benchmark finished"));
        ticks.shutdown();
        ticks.awaitTermination(5, TimeUnit.SECONDS);
        Object serverMetrics = null;
        if (metricsFile != null && Files.isRegularFile(metricsFile)) {
            try { serverMetrics = JsonParser.parseString(Files.readString(metricsFile)); }
            catch (Exception ex) { log("WARN", "bench", "Could not read server metrics file", "error", ex.getMessage()); }
        }
        long connectedCount = loginTimes.size();
        long receivedBytes = bots.stream().mapToLong(bot -> bot.bytesIn.get()).sum();
        long sentBytes = bots.stream().mapToLong(bot -> bot.bytesOut.get()).sum();
        List<Long> firstChunkTimes = bots.stream().map(bot -> bot.firstChunkMillis).filter(ms -> ms >= 0).toList();
        Object processMetrics = processSummary(processSamples);
        log("INFO", "bench", "Completed bot scenario", "connected", loginTimes.size(), "requested", count,
            "login_ms", percentiles(loginTimes), "movement_packets", movementPackets.sum(), "duration_seconds", durationSeconds,
            "disconnects", bots.stream().filter(bot -> bot.disconnected.get()).count(), "chunk_packets", chunkPackets.sum(),
            "resource_packs_loaded", packsLoaded.sum(),
            "resource_packs_declined", packsDeclined.sum(),
            "first_chunk_ms", percentiles(firstChunkTimes), "client_bytes_in_per_player", connectedCount == 0 ? 0 : receivedBytes / connectedCount,
            "client_bytes_out_per_player", connectedCount == 0 ? 0 : sentBytes / connectedCount,
            "client_bytes_per_player_per_second", connectedCount == 0 ? 0 : (receivedBytes + sentBytes) / connectedCount / durationSeconds,
            "server_process", processMetrics, "server_metrics", serverMetrics);
    }

    private static void respondToPack(Bot bot, ClientboundResourcePackPushPacket pack,
                                      LongAdder packsLoaded, LongAdder packsDeclined, String response) {
        if (response.equals("decline")) {
            bot.session.send(new ServerboundResourcePackPacket(pack.getId(), ResourcePackStatus.DECLINED));
            packsDeclined.increment();
            return;
        }
        bot.session.send(new ServerboundResourcePackPacket(pack.getId(), ResourcePackStatus.ACCEPTED));
        Thread.ofVirtual().name("flowtail-bot-pack-" + bot.name).start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(pack.getUrl())).GET().build();
                HttpResponse<byte[]> packResponse = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (packResponse.statusCode() != 200) throw new IllegalStateException("HTTP " + packResponse.statusCode());
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(packResponse.body()));
                if (pack.getHash() != null && !pack.getHash().isBlank() && !pack.getHash().equalsIgnoreCase(actual)) {
                    throw new IllegalStateException("SHA-1 mismatch");
                }
                bot.session.send(new ServerboundResourcePackPacket(pack.getId(), ResourcePackStatus.DOWNLOADED));
                bot.session.send(new ServerboundResourcePackPacket(pack.getId(), ResourcePackStatus.SUCCESSFULLY_LOADED));
                bot.packLoaded = true;
                packsLoaded.increment();
            } catch (Exception error) {
                bot.session.send(new ServerboundResourcePackPacket(pack.getId(), ResourcePackStatus.FAILED_DOWNLOAD));
                log("ERROR", "bench", "Bot could not download resource pack", "bot", bot.name, "error", error.getMessage());
            }
        });
    }

    private static void tick(Bot bot, boolean move, LongAdder movementPackets) {
        if (!bot.session.isConnected()) return;
        if (move) {
            double x = bot.x() + 0.03;
            float yaw = (bot.yaw + 1.5f) % 360f;
            bot.xBits.set(Double.doubleToRawLongBits(x));
            bot.yaw = yaw;
            bot.session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, bot.y(), bot.z(), yaw, 0));
            movementPackets.increment();
        }
        bot.session.send(ServerboundClientTickEndPacket.INSTANCE);
    }

    private static String safeName(String prefix, int index) {
        String cleaned = prefix.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.length() > 12) cleaned = cleaned.substring(0, 12);
        String suffix = Integer.toString(index + 1);
        return (cleaned.isBlank() ? "FTBench" : cleaned) + suffix;
    }

    private static Object percentiles(List<Long> input) {
        if (input.isEmpty()) return java.util.Map.of("count", 0);
        long[] data;
        synchronized (input) { data = input.stream().mapToLong(Long::longValue).sorted().toArray(); }
        return java.util.Map.of("count", data.length, "p50", pick(data, .50), "p95", pick(data, .95), "p99", pick(data, .99), "max", data[data.length - 1]);
    }

    private static long pick(long[] data, double p) { return data[Math.max(0, (int) Math.ceil(p * data.length) - 1)]; }

    private record ProcessSample(long nanoTime, long cpuNanos, long rssBytes, int threadCount) { }
    private static ProcessSample processSample(long pid) {
        long cpuNanos = ProcessHandle.of(pid).flatMap(handle -> handle.info().totalCpuDuration()).map(Duration::toNanos).orElse(-1L);
        long rssBytes = -1; int threads = -1;
        try {
            for (String line : Files.readAllLines(Path.of("/proc", Long.toString(pid), "status"))) {
                if (line.startsWith("VmRSS:")) rssBytes = Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024;
                else if (line.startsWith("Threads:")) threads = Integer.parseInt(line.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) { }
        return new ProcessSample(System.nanoTime(), cpuNanos, rssBytes, threads);
    }
    private static Object processSummary(List<ProcessSample> samples) {
        if (samples.isEmpty()) return null;
        long maxRss = samples.stream().mapToLong(ProcessSample::rssBytes).max().orElse(-1);
        var first = samples.getFirst(); var last = samples.getLast();
        double cpuPercent = first.cpuNanos() < 0 || last.cpuNanos() < 0 ? -1 :
            (last.cpuNanos() - first.cpuNanos()) * 100.0 / Math.max(1, last.nanoTime() - first.nanoTime());
        return java.util.Map.of("cpu_percent_one_core", Math.round(cpuPercent * 100) / 100.0,
            "rss_max_mb", maxRss < 0 ? -1 : Math.round(maxRss / 1048576.0 * 100) / 100.0,
            "threads_last_sample", last.threadCount(), "samples", samples.size());
    }
    private static void log(String level, String source, String message, Object... values) {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("time", java.time.Instant.now().toString()); fields.put("level", level); fields.put("source", source);
        fields.put("plugin_id", null); fields.put("content_id", null); fields.put("message", message);
        for (int i = 0; i + 1 < values.length; i += 2) fields.put(String.valueOf(values[i]), values[i + 1]);
        StringBuilder out = new StringBuilder("{");
        fields.forEach((key, value) -> {
            if (out.length() > 1) out.append(',');
            out.append(quote(key)).append(':').append(jsonValue(value));
        });
        System.out.println(out.append('}'));
    }
    private static String jsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof JsonElement element) return element.toString();
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof java.util.Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            map.forEach((key, item) -> { if (out.length() > 1) out.append(','); out.append(quote(String.valueOf(key))).append(':').append(jsonValue(item)); });
            return out.append('}').toString();
        }
        return quote(String.valueOf(value));
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t"); default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
    private static void help() {
        System.out.println("Usage: flowtail-bench --host HOST --port PORT --bots N --duration-seconds N [--prefix NAME] [--command COMMAND] [--tab-complete PREFIX] [--creative-echo] [--drop-item] [--server-metrics-file FILE] [--server-pid PID] [--pack-response accept|decline] [--idle]");
        System.out.println("Target must be an isolated server configured with online-mode=false. Default scenario walks bots slowly to request nearby chunks.");
    }
}
