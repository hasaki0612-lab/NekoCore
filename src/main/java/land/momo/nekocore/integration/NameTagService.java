package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import java.util.*;

/** Uses viewers' existing scoreboards. Never replaces a board or steals another plugin's team entry. */
public final class NameTagService {
    private final NekoCorePlugin plugin;
    private final Map<Scoreboard, Map<UUID, Team>> owned = new IdentityHashMap<>();
    private final Set<String> warned = new HashSet<>();
    private BukkitTask timer;
    public NameTagService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public void restart() {
        stop();
        if (!plugin.features().nameTags().enabled()) return;
        refresh();
        long ticks = plugin.features().nameTags().checkSeconds() * 20L;
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, ticks, ticks);
    }
    public void refresh() {
        if (!plugin.features().nameTags().enabled()) return;
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<>());
        Bukkit.getOnlinePlayers().forEach(p -> boards.add(p.getScoreboard()));
        for (Scoreboard stale : new ArrayList<>(owned.keySet())) if (!boards.contains(stale)) removeBoard(stale);
        Set<UUID> online = new HashSet<>(); Bukkit.getOnlinePlayers().forEach(p -> online.add(p.getUniqueId()));
        for (Scoreboard board : boards) {
            Map<UUID, Team> teams = owned.computeIfAbsent(board, ignored -> new HashMap<>());
            for (UUID id : new ArrayList<>(teams.keySet())) if (!online.contains(id)) unregister(teams.remove(id));
            for (Player player : Bukkit.getOnlinePlayers()) update(board, teams, player);
        }
    }
    public void update(Player player) {
        if (player == null || !plugin.features().nameTags().enabled()) return;
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<>());
        Bukkit.getOnlinePlayers().forEach(p -> boards.add(p.getScoreboard()));
        for (Scoreboard board : boards) update(board, owned.computeIfAbsent(board, ignored -> new HashMap<>()), player);
    }
    private void update(Scoreboard board, Map<UUID, Team> teams, Player player) {
        UUID id = player.getUniqueId();
        if (plugin.store().cached(id) == null || plugin.titles().cached(id) == null) return;
        String entry = player.getName(); Team team = teams.get(id);
        if (team != null && team.getScoreboard() != board) { teams.remove(id); team = null; }
        Team existing = board.getEntryTeam(entry);
        if (existing != null && existing != team) { warn(board, entry); return; }
        if (team == null) {
            String name = "nc" + id.toString().replace("-", "").substring(0, 14);
            if (board.getTeam(name) != null) { warn(board, entry); return; }
            team = board.registerNewTeam(name); teams.put(id, team);
            // A one-person team only changes visual prefix; preserve vanilla collisions/friendly fire.
            team.setAllowFriendlyFire(true); team.setCanSeeFriendlyInvisibles(false);
            team.addEntry(entry);
        }
        Component prefix = plugin.prefixes().primary(id);
        if (!prefix.equals(team.prefix())) team.prefix(prefix);
    }
    private void warn(Scoreboard board, String name) {
        if (warned.add(System.identityHashCode(board) + ":" + name))
            plugin.getLogger().warning("跳过 " + name + " 的头顶前缀：该 scoreboard team 已由其他插件管理；请使用 %nekocore_display_prefix% 集成。");
    }
    public void quit(UUID id) {
        for (Map<UUID, Team> teams : owned.values()) unregister(teams.remove(id));
    }
    public void stop() {
        if (timer != null) { timer.cancel(); timer = null; }
        for (Scoreboard board : new ArrayList<>(owned.keySet())) removeBoard(board);
        warned.clear();
    }
    private void removeBoard(Scoreboard board) { owned.remove(board).values().forEach(NameTagService::unregister); }
    private static void unregister(Team team) { if (team != null && team.getScoreboard() != null) team.unregister(); }
}
