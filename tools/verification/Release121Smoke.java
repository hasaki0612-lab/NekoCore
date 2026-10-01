import land.momo.nekocore.data.*;
import land.momo.nekocore.model.*;
import java.nio.file.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

class Release121Smoke {
    static final UUID ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID WORLD=UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID WORLD_NEW=UUID.fromString("33333333-3333-3333-3333-333333333333");
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10,TimeUnit.SECONDS); }
    static void check(boolean value,String why) { if(!value) throw new AssertionError(why); }
    static SqliteStore open(Path db) throws Exception {
        var store=new SqliteStore(Logger.getAnonymousLogger(),Clock.fixed(Instant.parse("2026-09-29T06:00:00Z"),ZoneOffset.UTC));
        await(store.open(db,new LevelCurve(100,50,0,10000))); return store;
    }
    static void close(SqliteStore store) throws Exception { store.closeAsync(); check(store.awaitClosed(10),"close"); }
    public static void main(String[] args) throws Exception {
        Path db=Path.of(args[0]); var store=open(db); String batch;
        try {
            try(var connection=DriverManager.getConnection("jdbc:sqlite:"+db);var statement=connection.createStatement()) {
                try(var result=statement.executeQuery("PRAGMA user_version")){result.next();check(result.getInt(1)==3,"schema must remain 3");}
            }
            var profile=await(store.join(ID,"MomoRenamed",2000));
            check(profile.equals(new Profile(ID,"MomoRenamed",13052,3,275,3600,1000,2000,true)),"profile fields retained");
            check(await(store.home(ID,WORLD,"world")).orElseThrow().equals(new Home(ID,WORLD,"world",128.5,72,-435.5,90,15)),"world Home");
            check(await(store.home(ID,WORLD_NEW,"world_new")).orElseThrow().equals(new Home(ID,WORLD_NEW,"world_new",-38.5,80,43.5,30,0)),"world_new Home");
            check(!await(store.checkin(ID,ZoneId.of("Asia/Shanghai"),100,50)).claimed(),"checkin must not duplicate");
            var titles=new TitleRepository(store); var commerce=new CommerceRepository(store);
            var owned=await(titles.load(ID)); check(owned.owned().contains("sora")&&owned.equipped().equals("sora"),"stable highest title ownership/equipment");
            var bag=await(commerce.bag(ID,18)); check(bag.capacity()==27&&Arrays.equals(bag.contents(),new byte[]{17,42,88}),"Bag retained");
            check(await(commerce.quota(ID,"stone",null)).bought()==16,"store quota retained");
            batch=await(commerce.batch()).id(); check(batch!=null&&!batch.isBlank(),"enchant batch retained");
            check(await(commerce.pending(ID)).isEmpty(),"transaction log committed");
            var reward=await(store.afkReward(ID,12,4)); check(reward.exp()==12&&reward.coins()==4,"AFK reward delta");
            check(reward.profile().coins()==13056&&reward.profile().exp()==287&&reward.profile().level()==3,"AFK reward profile");
        } finally { close(store); }
        store=open(db);
        try {
            var profile=await(store.join(ID,"MomoRenamed",3000));
            check(profile.coins()==13056&&profile.exp()==287&&profile.showIp()&&profile.playtimeSeconds()==3600,"restart persistence");
            check(await(new TitleRepository(store).load(ID)).equipped().equals("sora"),"active title after restart");
            check(await(new CommerceRepository(store).batch()).id().equals(batch),"same enchant batch after restart");
        } finally { close(store); }
        System.out.println("V120_TO_V121_SHADED_JAR_UPGRADE_SCHEMA3_AND_RESTART_OK");
    }
}
