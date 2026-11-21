package dev.flowtail.content;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Join-time fallback: holds a player in place until the required pack reports successful reload. */
final class PackDelivery implements Listener {
    private record SavedState(GameMode gameMode, int viewDistance, int simulationDistance,
                              boolean invulnerable, boolean collidable, Location location) {}

    private final FlowTailContentPlugin plugin;
    private final PackHost host;
    private final ContentSettings settings;
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();
    private final Map<UUID, SavedState> limbo = new ConcurrentHashMap<>();
    private final Set<UUID> optionalRequests = ConcurrentHashMap.newKeySet();

    PackDelivery(FlowTailContentPlugin plugin, PackHost host, ContentSettings settings) {
        this.plugin = plugin; this.host = host; this.settings = settings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID request = UUID.randomUUID();
        if (settings.packRequired()) pending.put(player.getUniqueId(), request);
        else optionalRequests.add(player.getUniqueId());
        player.setResourcePack(request, host.url(), host.sha1(),
                Component.text("This server requires its content pack."), settings.packRequired());
        if (settings.packRequired()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (pending.containsKey(player.getUniqueId())) {
                    player.sendMessage(Component.text("Loading the required server pack. Movement is paused until it finishes."));
                }
            });
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        UUID expected = pending.get(event.getPlayer().getUniqueId());
        if (expected == null || !expected.equals(event.getID())) return;
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                pending.remove(event.getPlayer().getUniqueId(), expected);
                leaveLimbo(event.getPlayer());
                plugin.getLogger().info("Resource pack loaded player=" + event.getPlayer().getUniqueId());
                event.getPlayer().sendMessage(Component.text("Server pack loaded. You can play now."));
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                if (settings.declineAction().equals("kick")) {
                    plugin.getLogger().warning("Resource pack rejected; kicking player=" + event.getPlayer().getUniqueId()
                            + " status=" + event.getStatus());
                    event.getPlayer().kick(Component.text("this server is unplayable without the pack"));
                } else {
                    enterLimbo(event.getPlayer());
                    plugin.getLogger().warning("Resource pack rejected; moved player into low-chunk limbo player="
                            + event.getPlayer().getUniqueId() + " status=" + event.getStatus());
                    event.getPlayer().sendMessage(Component.text(
                            "this server is unplayable without the pack; you are held in a low-chunk limbo"));
                }
            }
            case ACCEPTED, DOWNLOADED -> { }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        boolean positionChanged = event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ();
        if (pending.containsKey(event.getPlayer().getUniqueId())) {
            if (positionChanged) {
            event.setTo(event.getFrom());
            }
        } else if (positionChanged && optionalRequests.remove(event.getPlayer().getUniqueId())) {
            plugin.getLogger().info("Optional-pack player movement allowed player=" + event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        optionalRequests.remove(event.getPlayer().getUniqueId());
        leaveLimbo(event.getPlayer());
    }

    @EventHandler
    public void onNewPlayer(PlayerJoinEvent event) {
        for (UUID playerId : limbo.keySet()) {
            Player limboPlayer = Bukkit.getPlayer(playerId);
            if (limboPlayer != null) event.getPlayer().hidePlayer(plugin, limboPlayer);
        }
    }

    private void enterLimbo(Player player) {
        limbo.computeIfAbsent(player.getUniqueId(), ignored -> new SavedState(player.getGameMode(),
                player.getViewDistance(), player.getSimulationDistance(), player.isInvulnerable(),
                player.isCollidable(), player.getLocation().clone()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setViewDistance(2);
        player.setSimulationDistance(2);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) other.hidePlayer(plugin, player);
        }
    }

    private void leaveLimbo(Player player) {
        SavedState previous = limbo.remove(player.getUniqueId());
        if (previous == null) return;
        player.setGameMode(previous.gameMode());
        player.setViewDistance(previous.viewDistance());
        player.setSimulationDistance(previous.simulationDistance());
        player.setInvulnerable(previous.invulnerable());
        player.setCollidable(previous.collidable());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) other.showPlayer(plugin, player);
        }
    }
}
