package land.momo.nekocore.command;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.model.Home;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.*;
import java.util.logging.Level;
import java.time.format.DateTimeFormatter;

public final class CoreCommands implements CommandExecutor, TabCompleter {
    private final NekoCorePlugin plugin;
    private final Map<UUID, UUID> pendingHomes = new HashMap<>();

    public CoreCommands(NekoCorePlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        String permission = permission(name);
        if (permission == null || !plugin.permission(sender, permission) || !plugin.available(sender)) return true;
        if (name.equals("nekocore")) { admin(sender, args); return true; }
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "players-only"); return true; }
        if (name.equals("tpn")) {
            if (args.length != 1) usage(sender, "tpn"); else plugin.teleportRequests().request(player, args[0]);
            return true;
        }
        if (name.equals("home")) {
            if (args.length > 1) usage(sender, "home");
            else {
                World target = args.length == 0 ? player.getWorld() : Bukkit.getWorld(args[0]);
                if (target == null) plugin.messages().send(player, "home-world-unavailable", Map.of("world", args[0]));
                else home(player, target);
            }
            return true;
        }
        if (name.equals("check")) {
            if (args.length != 2 || !args[0].equalsIgnoreCase("home")) usage(sender, "check");
            else check(player, args[1]);
            return true;
        }
        if (args.length != 0) { usage(sender, name); return true; }
        switch (name) {
            case "menu" -> plugin.menus().open(player);
            case "tasks" -> plugin.dailyTaskMenu().open(player);
            case "coins" -> plugin.messages().send(player, "coins", plugin.variables(plugin.data().view(player.getUniqueId())));
            case "checkin" -> plugin.checkins().claim(player);
            case "sethome" -> setHome(player);
            case "store" -> plugin.storeMenu().open(player);
            case "bag" -> plugin.bagMenu().open(player);
            case "yes" -> plugin.teleportRequests().answer(player, true);
            case "no" -> plugin.teleportRequests().answer(player, false);
            default -> { }
        }
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("lookup")) { lookup(sender, args); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("tasks")) { tasks(sender, args); return; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { plugin.reload(sender); return; }
        if (args.length == 2 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("check")) {
            plugin.checkConfig(sender); return;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("doctor"))) {
            plugin.status(sender); return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cleanup") && args[1].equalsIgnoreCase("now")) {
            plugin.cleanup().cleanNow(); return;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("levelshop") && args[1].equalsIgnoreCase("open")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) plugin.messages().send(sender, "tpn-offline"); else plugin.titleMenu().open(target);
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("store") && args[1].equalsIgnoreCase("refresh-enchants")) {
            plugin.finish(sender, plugin.enchantments().refresh(true), batch -> plugin.messages().send(sender, "store-refreshed"));
            return;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("store")) {
            String operation = args[1].toLowerCase(Locale.ROOT);
            if (operation.equals("status")) {
                plugin.finish(sender, plugin.store().knownPlayer(args[2]).thenCompose(profile -> plugin.commerce().status(profile.uuid())
                        .thenApply(status -> Map.of("player", profile.name(), "status", status.isBlank() ? "—" : status))),
                        vars -> plugin.messages().send(sender, "store-status", vars)); return;
            }
            if (operation.equals("reset")) {
                plugin.finish(sender, plugin.store().knownPlayer(args[2]).thenCompose(profile -> plugin.commerce().resetQuotas(profile.uuid())
                        .thenApply(ignored -> profile.name())), player -> plugin.messages().send(sender, "store-reset", Map.of("player", player))); return;
            }
        }
        if (args.length != 4) { usage(sender, "admin"); return; }
        String category = args[0].toLowerCase(Locale.ROOT);
        String action = args[1].toLowerCase(Locale.ROOT);
        boolean coins = category.equals("coins") && List.of("add", "set", "take").contains(action);
        boolean experience = category.equals("exp") && List.of("add", "set").contains(action);
        if (!coins && !experience) { usage(sender, "admin"); return; }
        long amount;
        try {
            if (!args[3].matches("[0-9]+")) throw new NumberFormatException();
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) { plugin.messages().send(sender, "invalid-amount"); return; }
        if (coins) {
            plugin.finish(sender, plugin.store().coins(args[2], action, amount),
                    profile -> plugin.messages().send(sender, "coins-updated", plugin.variables(profile)));
        } else {
            plugin.finish(sender, plugin.store().experience(args[2], action, amount),
                    profile -> plugin.messages().send(sender, "exp-updated", plugin.variables(profile)));
        }
    }

    private void lookup(CommandSender sender, String[] args) {
        boolean entity = args.length > 1 && args[1].equalsIgnoreCase("entity");
        if (args.length == 1) {
            if (!(sender instanceof Player player)) { plugin.messages().send(sender, "lookup.console"); return; }
            var material = player.getInventory().getItemInMainHand().getType();
            if (material.isAir()) { plugin.messages().send(sender, "lookup.empty-hand"); return; }
            plugin.messages().send(sender, "lookup.held", Map.of("material", material.name()));
            return;
        }
        if (args.length != (entity ? 3 : 2)) { plugin.messages().send(sender, "lookup.usage"); return; }
        String keyword = args[entity ? 2 : 1];
        if (!keyword.matches("(?i)(?:minecraft:)?[a-z0-9_]+")) { plugin.messages().send(sender, "lookup.english-only"); return; }
        List<String> names = entity
                ? Arrays.stream(org.bukkit.entity.EntityType.values()).map(Enum::name).toList()
                : Arrays.stream(org.bukkit.Material.values()).filter(value -> !value.isLegacy() && !value.isAir() && (value.isItem() || value.isBlock())).map(Enum::name).toList();
        List<String> results = land.momo.nekocore.config.LookupNames.search(keyword, names, 10);
        if (results.isEmpty()) { plugin.messages().send(sender, "lookup.none", Map.of("keyword", keyword)); return; }
        plugin.messages().send(sender, "lookup.results", Map.of("kind", entity ? "生物" : "物品 / 方块", "count", "" + results.size()));
        for (String name : results) plugin.messages().send(sender, entity ? "lookup.entity-line" : "lookup.material-line", Map.of("name", name));
        plugin.messages().send(sender, "lookup.copy", Map.of("kind", entity ? "entities 列表" : "material 字段"));
    }

    private void tasks(CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("status")) {
            plugin.finish(sender, plugin.dailyTasks().ensureToday(), rotation -> {
                StringJoiner tasks = new StringJoiner("\n");
                rotation.entries().forEach(entry -> tasks.add(plugin.messages().plain("tasks-admin-status-line", Map.of(
                        "difficulty", plugin.messages().raw("daily-tasks.difficulty." + entry.difficulty().key()), "task_id", entry.taskId(),
                        "task", plugin.messages().raw("daily-tasks.tasks." + entry.taskId() + ".name")))));
                plugin.messages().send(sender, "tasks-admin-status", Map.of("date", rotation.date().toString(), "tasks", tasks.toString(),
                        "refresh", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(plugin.dailyTasks().nextRefresh())));
            });
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("reroll") && args[2].equalsIgnoreCase("confirm")) {
            plugin.finish(sender, plugin.dailyTasks().reroll(), rotation ->
                    plugin.messages().send(sender, "tasks-admin-rerolled", Map.of("date", rotation.date().toString())));
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
            plugin.finish(sender, plugin.store().knownPlayer(args[2]).thenCompose(profile -> plugin.dailyTasks().reset(profile.uuid())
                            .thenApply(ignored -> profile.name())),
                    player -> plugin.messages().send(sender, "tasks-admin-reset", Map.of("player", player)));
            return;
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("progress")) {
            long amount;
            try { if (!args[4].matches("[0-9]+")) throw new NumberFormatException(); amount = Long.parseLong(args[4]); }
            catch (NumberFormatException e) { plugin.messages().send(sender, "invalid-amount"); return; }
            long delta = amount;
            plugin.finish(sender, plugin.store().knownPlayer(args[2]).thenCompose(profile ->
                            plugin.dailyTasks().adminProgress(profile.uuid(), args[3], delta).thenApply(result -> Map.entry(profile, result))),
                    result -> plugin.messages().send(sender, "tasks-admin-progress", Map.of("player", result.getKey().name(),
                            "task_id", args[3], "progress", "" + result.getValue().progress().progress())));
            return;
        }
        plugin.messages().send(sender, "tasks-admin-usage");
    }

    private void setHome(Player player) {
        Location location = player.getLocation();
        World world = player.getWorld();
        Home home = new Home(player.getUniqueId(), world.getUID(), world.getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
        if (!valid(world, location)) { plugin.messages().send(player, "home-invalid"); return; }
        plugin.finish(player, plugin.store().saveHome(home), ignored -> plugin.messages().send(player, "home-set", coordinates(home)));
    }

    private void check(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        plugin.finish(player, plugin.store().home(player.getUniqueId(), world == null ? null : world.getUID(), worldName), result -> {
            if (result.isEmpty()) plugin.messages().send(player, "home-missing", Map.of("world", worldName));
            else {
                Map<String, String> vars = coordinates(result.get());
                if (world != null) vars.put("world", world.getName());
                plugin.messages().send(player, "home-check", vars);
            }
        });
    }

    private void home(Player player, World world) {
        UUID id = player.getUniqueId();
        if (pendingHomes.containsKey(id)) { plugin.messages().send(player, "home-busy"); return; }
        World source = player.getWorld();
        UUID token = UUID.randomUUID();
        pendingHomes.put(id, token);
        plugin.store().home(id, world.getUID(), world.getName()).whenComplete((result, error) -> plugin.onMain(() -> {
            if (!current(player, source, world, token)) return;
            if (error != null) { failedHome(player, token, error); return; }
            if (result.isEmpty()) {
                pendingHomes.remove(id, token);
                plugin.messages().send(player, "home-missing", Map.of("world", world.getName())); return;
            }
            Home home = result.get();
            Location destination = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
            if (!valid(world, destination)) {
                pendingHomes.remove(id, token); plugin.messages().send(player, "home-invalid"); return;
            }
            // Load without a main-thread chunk wait, then recheck player/session/world before teleporting.
            // No warmup or artificial delay is added. All Bukkit API invocations happen on the main thread.
            try {
                world.getChunkAtAsync(destination).whenComplete((chunk, chunkError) -> plugin.onMain(() -> {
                    if (!current(player, source, world, token)) return;
                    if (chunkError != null) { failedHome(player, token, chunkError); return; }
                    pendingHomes.remove(id, token);
                    if (!valid(world, destination)) { plugin.messages().send(player, "home-invalid"); return; }
                    if (!plugin.permission(player, "nekocore.home.use")) return;
                    try {
                        boolean success = !player.isDead() && player.teleport(destination, TeleportCause.PLUGIN);
                        plugin.messages().send(player, success ? "home-success" : "home-failed");
                    } catch (RuntimeException e) { failedHome(player, token, e); }
                }));
            } catch (RuntimeException e) { failedHome(player, token, e); }
        }));
    }

    private boolean current(Player player, World source, World target, UUID token) {
        UUID id = player.getUniqueId();
        if (!token.equals(pendingHomes.get(id))) return false;
        if (!NekoCorePlugin.present(player)) { pendingHomes.remove(id, token); return false; }
        if (player.getWorld() != source || Bukkit.getWorld(source.getUID()) != source) {
            pendingHomes.remove(id, token); plugin.messages().send(player, "home-moved"); return false;
        }
        if (Bukkit.getWorld(target.getUID()) != target) {
            pendingHomes.remove(id, token);
            plugin.messages().send(player, "home-world-unavailable", Map.of("world", target.getName())); return false;
        }
        return true;
    }

    private void failedHome(Player player, UUID token, Throwable error) {
        pendingHomes.remove(player.getUniqueId(), token);
        plugin.getLogger().log(Level.WARNING, "Home 操作失败", NekoCorePlugin.unwrap(error));
        if (NekoCorePlugin.present(player)) plugin.messages().send(player, "home-failed");
    }

    private boolean valid(World world, Location location) {
        return location.getY() >= world.getMinHeight() && location.getY() < world.getMaxHeight()
                && world.getWorldBorder().isInside(location);
    }
    public void cancelHome(UUID id) { pendingHomes.remove(id); }

    private Map<String, String> coordinates(Home home) {
        Map<String, String> vars = new HashMap<>();
        vars.put("world", home.worldName()); vars.put("x", decimal(home.x())); vars.put("y", decimal(home.y()));
        vars.put("z", decimal(home.z())); vars.put("yaw", decimal(home.yaw())); vars.put("pitch", decimal(home.pitch()));
        return vars;
    }
    private String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private void usage(CommandSender sender, String key) { plugin.messages().send(sender, "usage." + key); }

    private String permission(String name) {
        return switch (name) {
            case "menu" -> "nekocore.menu";
            case "tasks" -> "nekocore.tasks";
            case "coins" -> "nekocore.coins";
            case "checkin" -> "nekocore.checkin";
            case "sethome" -> "nekocore.home.set";
            case "home" -> "nekocore.home.use";
            case "check" -> "nekocore.home.check";
            case "nekocore" -> "nekocore.admin";
            case "store" -> "nekocore.store";
            case "bag" -> "nekocore.bag";
            case "tpn", "yes", "no" -> "nekocore.tpn";
            default -> null;
        };
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        String permission = permission(name);
        if (permission == null || !sender.hasPermission(permission) || args.length == 0) return List.of();
        List<String> options = new ArrayList<>();
        if (name.equals("tpn") && args.length == 1) {
            Bukkit.getOnlinePlayers().stream().filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p))
                    .forEach(p -> options.add(p.getName()));
        } else if (name.equals("home") && args.length == 1) {
            Bukkit.getWorlds().forEach(world -> options.add(world.getName()));
        } else if (name.equals("check")) {
            if (args.length == 1) options.add("home");
            if (args.length == 2 && args[0].equalsIgnoreCase("home")) Bukkit.getWorlds().forEach(world -> options.add(world.getName()));
        } else if (name.equals("nekocore")) {
            if (args.length == 1) options.addAll(List.of("coins", "exp", "cleanup", "reload", "config", "status", "doctor", "store", "levelshop", "tasks", "lookup"));
            if (args.length == 2) options.addAll(switch (args[0].toLowerCase(Locale.ROOT)) {
                case "coins" -> List.of("add", "set", "take");
                case "exp" -> List.of("add", "set");
                case "cleanup" -> List.of("now");
                case "config" -> List.of("check");
                case "lookup" -> List.of("entity");
                case "levelshop" -> List.of("open");
                case "store" -> List.of("refresh-enchants", "reset", "status");
                case "tasks" -> List.of("status", "reroll", "reset", "progress");
                default -> List.of();
            });
            if (args.length == 3 && (args[0].equalsIgnoreCase("coins") || args[0].equalsIgnoreCase("exp")
                    || args[0].equalsIgnoreCase("levelshop") && args[1].equalsIgnoreCase("open")
                    || args[0].equalsIgnoreCase("store") && List.of("reset", "status").contains(args[1].toLowerCase(Locale.ROOT))
                    || args[0].equalsIgnoreCase("tasks") && List.of("reset", "progress").contains(args[1].toLowerCase(Locale.ROOT))))
                Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            if (args.length == 3 && args[0].equalsIgnoreCase("tasks") && args[1].equalsIgnoreCase("reroll")) options.add("confirm");
            if (args.length == 4 && (args[0].equalsIgnoreCase("coins") || args[0].equalsIgnoreCase("exp"))) options.addAll(List.of("0", "10", "100"));
            if (args.length == 4 && args[0].equalsIgnoreCase("tasks") && args[1].equalsIgnoreCase("progress"))
                options.addAll(land.momo.nekocore.task.DailyTaskDefinition.ids());
            if (args.length == 5 && args[0].equalsIgnoreCase("tasks") && args[1].equalsIgnoreCase("progress")) options.addAll(List.of("1", "5", "20"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
