package me.aver005.escape.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Глобальная статистика игроков в SQLite (stats.db). Запись — асинхронно. */
public class StatsRepository
{
    /** Разрешённые колонки-счётчики. */
    public static final Set<String> COLUMNS = Set.of(
        "wins", "loses", "kills", "deaths", "ores_mined", "quests_completed",
        "trades_completed", "games_played", "mvp_games", "last_game_kills", "best_game_kills");

    public record Row(String name, int wins, int loses, int kills, int deaths, int oresMined,
                      int questsCompleted, int tradesCompleted, int gamesPlayed, int mvpGames,
                      int lastGameKills, int bestGameKills) {}

    private final JavaPlugin plugin;
    private Connection connection;

    public StatsRepository(JavaPlugin plugin) {this.plugin = plugin;}

    public void open() throws SQLException
    {
        File db = new File(plugin.getDataFolder(), "stats.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        try (Statement st = connection.createStatement())
        {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stats (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    wins INTEGER NOT NULL DEFAULT 0,
                    loses INTEGER NOT NULL DEFAULT 0,
                    kills INTEGER NOT NULL DEFAULT 0,
                    deaths INTEGER NOT NULL DEFAULT 0,
                    ores_mined INTEGER NOT NULL DEFAULT 0,
                    quests_completed INTEGER NOT NULL DEFAULT 0,
                    trades_completed INTEGER NOT NULL DEFAULT 0,
                    games_played INTEGER NOT NULL DEFAULT 0,
                    mvp_games INTEGER NOT NULL DEFAULT 0,
                    last_game_kills INTEGER NOT NULL DEFAULT 0,
                    best_game_kills INTEGER NOT NULL DEFAULT 0
                )""");
        }
    }

    public void close()
    {
        try {if (connection != null) {connection.close();}}
        catch (SQLException ignored) {}
    }

    private synchronized void ensureRow(UUID uuid, String name) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO stats(uuid, name) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name"))
        {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    /** Увеличить счётчик на delta (асинхронно). */
    public void add(UUID uuid, String name, String column, int delta)
    {
        if (!COLUMNS.contains(column)) {throw new IllegalArgumentException("Bad column " + column);}
        async(() ->
        {
            ensureRow(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE stats SET " + column + " = " + column + " + ? WHERE uuid = ?"))
            {
                ps.setInt(1, delta);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /** Записать точное значение (асинхронно). */
    public void set(UUID uuid, String name, String column, int value)
    {
        if (!COLUMNS.contains(column)) {throw new IllegalArgumentException("Bad column " + column);}
        async(() ->
        {
            ensureRow(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE stats SET " + column + " = ? WHERE uuid = ?"))
            {
                ps.setInt(1, value);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /** best_game_kills = max(best_game_kills, value); last_game_kills = value. */
    public void recordGameKills(UUID uuid, String name, int kills)
    {
        async(() ->
        {
            ensureRow(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE stats SET last_game_kills = ?, best_game_kills = MAX(best_game_kills, ?) WHERE uuid = ?"))
            {
                ps.setInt(1, kills);
                ps.setInt(2, kills);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /** Прочитать статистику по нику (асинхронно, callback в main thread; row == null если нет). */
    public void findByName(String name, Consumer<Row> callback)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            Row row = null;
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM stats WHERE name = ? COLLATE NOCASE"))
            {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next()) {row = read(rs);}
                }
            }
            catch (SQLException e)
            {
                plugin.getLogger().severe("Stats read error: " + e.getMessage());
            }
            Row result = row;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    private Row read(ResultSet rs) throws SQLException
    {
        return new Row(
            rs.getString("name"), rs.getInt("wins"), rs.getInt("loses"),
            rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("ores_mined"),
            rs.getInt("quests_completed"), rs.getInt("trades_completed"),
            rs.getInt("games_played"), rs.getInt("mvp_games"),
            rs.getInt("last_game_kills"), rs.getInt("best_game_kills"));
    }

    private interface SqlRunnable {void run() throws SQLException;}

    private void async(SqlRunnable action)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            try {synchronized (this) {action.run();}}
            catch (SQLException e) {plugin.getLogger().severe("Stats write error: " + e.getMessage());}
        });
    }
}
