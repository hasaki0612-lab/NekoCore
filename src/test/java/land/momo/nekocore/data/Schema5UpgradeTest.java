package land.momo.nekocore.data;

import land.momo.nekocore.model.LevelCurve;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class Schema5UpgradeTest {
    @TempDir Path directory; Path database; SqliteStore store; UUID player=UUID.randomUUID();
    @BeforeEach void seedSchemaFour() throws Exception {
        database=directory.resolve("schema4.db");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+database); Statement s=c.createStatement()) {
            for(String resource:List.of("V1__initial.sql","V2__daily_checkins.sql","V3__shops_bags_titles.sql","V4__daily_tasks.sql"))
                try(var in=getClass().getResourceAsStream("/db/migration/"+resource)) {
                    for(String sql:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split(";")) if(!sql.isBlank()) s.execute(sql);
                }
            String id=player.toString(),rotation=UUID.randomUUID().toString();
            s.execute("INSERT INTO players VALUES('"+id+"','Momo',321,1,0,456,1,2,1)");
            s.execute("INSERT INTO daily_task_rotations VALUES('2026-09-30','"+rotation+"','easy',0,'simple_healthy',3)");
            s.execute("INSERT INTO player_daily_task_progress VALUES('"+id+"','2026-09-30','"+rotation+"','simple_healthy',1,1,1,'',4)");
            s.execute("INSERT INTO owned_titles VALUES('"+id+"','sora',5,20000)");
            s.execute("INSERT INTO equipped_titles VALUES('"+id+"','sora')"); s.execute("PRAGMA user_version=4");
        }
        store=new SqliteStore(Logger.getAnonymousLogger());
    }
    @AfterEach void close() throws Exception { if(store!=null){store.closeAsync();assertTrue(store.awaitClosed(10));} }

    @Test void v5OnlyAddsWeeklyTableAndIndexAndPreservesSchemaFourRows() throws Exception {
        Map<String,List<String>> before=snapshot(List.of("players","daily_task_rotations","player_daily_task_progress","owned_titles","equipped_titles"));
        store.open(database,new LevelCurve(100,50,0,10000)).get(10,TimeUnit.SECONDS);
        assertEquals(before,snapshot(before.keySet()));
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+database); Statement s=c.createStatement()) {
            try(ResultSet version=s.executeQuery("PRAGMA user_version")){version.next();assertEquals(5,version.getInt(1));}
            try(ResultSet count=s.executeQuery("SELECT COUNT(*) FROM weekly_coin_earnings")){count.next();assertEquals(0,count.getInt(1));}
            try(ResultSet index=s.executeQuery("SELECT sql FROM sqlite_master WHERE type='index' AND name='weekly_coin_earnings_ranking'")){
                assertTrue(index.next()); assertTrue(index.getString(1).contains("period_start, earned_coins DESC"));
            }
        }
    }
    private Map<String,List<String>> snapshot(Collection<String> tables) throws Exception {
        Map<String,List<String>> result=new LinkedHashMap<>();
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+database); Statement s=c.createStatement()) {
            for(String table:tables){List<String> rows=new ArrayList<>();try(ResultSet rs=s.executeQuery("SELECT * FROM "+table)){
                while(rs.next()){List<String> row=new ArrayList<>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.add(Objects.toString(rs.getObject(i),"null"));rows.add(row.toString());}}
                result.put(table,rows);}
        }
        return result;
    }
}
