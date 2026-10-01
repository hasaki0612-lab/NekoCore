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

class Schema4UpgradeTest {
    @TempDir Path directory;
    Path database; SqliteStore store; UUID player=UUID.randomUUID();

    @BeforeEach void seedSchemaThree() throws Exception {
        database=directory.resolve("121.db");
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+database); Statement statement=connection.createStatement()) {
            for(String resource:List.of("V1__initial.sql","V2__daily_checkins.sql","V3__shops_bags_titles.sql")) {
                try(var input=getClass().getResourceAsStream("/db/migration/"+resource)) {
                    assertNotNull(input,resource);
                    for(String sql:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";")) if(!sql.isBlank()) statement.execute(sql);
                }
            }
            String id=player.toString();
            // Level 19 is the value derived from 9,876 EXP by this test curve, so
            // the store's normal startup consistency pass has nothing to repair.
            statement.execute("INSERT INTO players VALUES('"+id+"','Momo',12345,19,9876,6543,1,2,1)");
            statement.execute("INSERT INTO daily_checkins VALUES('"+id+"','2026-09-29',1,'Asia/Shanghai',100,50)");
            statement.execute("INSERT INTO owned_titles VALUES('"+id+"','sora',2,9000)");
            statement.execute("INSERT INTO equipped_titles VALUES('"+id+"','sora')");
            statement.execute("INSERT INTO bags VALUES('"+id+"',36,7,X'010203')");
            statement.execute("INSERT INTO store_quotas VALUES('"+id+"','2026-09-30','diamond_sword','buy',2)");
            statement.execute("INSERT INTO store_periods VALUES('daily','2026-09-30')");
            statement.execute("INSERT INTO enchant_batches VALUES('batch','2026-09-30',3)");
            statement.execute("INSERT INTO enchant_offers VALUES('batch',0,'minecraft:sharpness',5,3600,1)");
            statement.execute("INSERT INTO inventory_exchanges VALUES('exchange','"+id+"','00000000-0000-0000-0000-000000000001','store','committed',4,X'01',X'02',-1600,'diamond_sword','2026-09-30','buy',1,NULL,NULL,0)");
            statement.execute("PRAGMA user_version=3");
        }
        store=new SqliteStore(Logger.getAnonymousLogger());
    }

    @AfterEach void close() throws Exception {
        if(store!=null){store.closeAsync();assertTrue(store.awaitClosed(10));}
    }

    @Test void schemaThreeUpgradeAddsDailyTasksAndWeeklyLeaderboardWithoutChangingExistingRows() throws Exception {
        Map<String,List<String>> before=snapshot();
        store.open(database,new LevelCurve(100,50,0,10000)).get(10,TimeUnit.SECONDS);
        assertEquals(before,snapshot());
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+database); Statement statement=connection.createStatement()) {
            try(ResultSet version=statement.executeQuery("PRAGMA user_version")){assertTrue(version.next());assertEquals(5,version.getInt(1));}
            assertEquals(0,count(statement,"daily_task_rotations"));
            assertEquals(0,count(statement,"player_daily_task_progress"));
            assertEquals(0,count(statement,"weekly_coin_earnings"));
        }
        assertEquals(12345,store.join(player,"Momo",10).get(10,TimeUnit.SECONDS).coins());
    }

    private Map<String,List<String>> snapshot() throws Exception {
        List<String> tables=List.of("players","homes","daily_checkins","owned_titles","equipped_titles","bags",
                "store_quotas","store_periods","enchant_batches","enchant_offers","inventory_exchanges");
        Map<String,List<String>> result=new LinkedHashMap<>();
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+database); Statement statement=connection.createStatement()) {
            for(String table:tables) {
                List<String> rows=new ArrayList<>();
                try(ResultSet set=statement.executeQuery("SELECT * FROM "+table)) {
                    while(set.next()) {
                        List<String> row=new ArrayList<>();
                        for(int index=1;index<=set.getMetaData().getColumnCount();index++) {
                            byte[] bytes=set.getBytes(index); row.add(bytes==null?"null":HexFormat.of().formatHex(bytes));
                        }
                        rows.add(row.toString());
                    }
                }
                result.put(table,rows);
            }
        }
        return result;
    }

    private static int count(Statement statement,String table) throws SQLException {
        try(ResultSet set=statement.executeQuery("SELECT COUNT(*) FROM "+table)){set.next();return set.getInt(1);}
    }
}
