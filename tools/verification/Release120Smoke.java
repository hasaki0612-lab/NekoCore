import land.momo.nekocore.data.*;
import land.momo.nekocore.model.*;
import java.nio.file.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

class Release120Smoke {
    static final UUID ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID WORLD=UUID.fromString("22222222-2222-2222-2222-222222222222");
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10,TimeUnit.SECONDS); }
    static void check(boolean value,String why) { if(!value) throw new AssertionError(why); }
    static SqliteStore open(Path db) throws Exception {
        var store=new SqliteStore(Logger.getAnonymousLogger(),Clock.fixed(Instant.parse("2026-09-29T06:00:00Z"),ZoneOffset.UTC));
        await(store.open(db,new LevelCurve(100,50,0,10000))); return store;
    }
    static void close(SqliteStore store) throws Exception { store.closeAsync(); check(store.awaitClosed(10),"close"); }
    public static void main(String[] args) throws Exception {
        Path db=Path.of(args[0]); var store=open(db); String batchId;
        try {
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
                try(var r=s.executeQuery("PRAGMA user_version")){r.next();check(r.getInt(1)==3,"schema 3");}
                try(var r=s.executeQuery("SELECT first_join,last_join FROM players")){r.next();check(r.getLong(1)==1000&&r.getLong(2)==1000,"timestamps unchanged by migration");}
            }
            var p=await(store.join(ID,"Momo",2000));
            check(p.equals(new Profile(ID,"Momo",777,3,275,3600,1000,2000,true)),"all old profile data");
            check(await(store.home(ID,WORLD,"world")).orElseThrow().equals(new Home(ID,WORLD,"world",128.5,72,-435.5,90,15)),"world Home");
            var worldNew=UUID.fromString("33333333-3333-3333-3333-333333333333");
            check(await(store.home(ID,worldNew,"world_new")).orElseThrow().equals(new Home(ID,worldNew,"world_new",-38.5,80,43.5,30,0)),"world_new Home");
            check(!await(store.checkin(ID,ZoneId.of("Asia/Shanghai"),100,50)).claimed(),"old checkin retained");
            await(store.coins("Momo","set",10000)); var titles=new TitleRepository(store); var repo=new CommerceRepository(store);
            await(titles.purchase(ID,"mame",1500)); var bag=await(repo.bag(ID,27));
            var x=new CommerceRepository.Exchange(UUID.randomUUID(),ID,WORLD,"bag",new byte[]{1},new byte[]{2},0,"","","",0,bag.contents(),new byte[]{17,42,88},bag.revision());
            await(repo.prepareBag(x)); await(repo.commit(x.id()));
            var quota=await(repo.quota(ID,"stone",null));
            x=new CommerceRepository.Exchange(UUID.randomUUID(),ID,WORLD,"store",new byte[]{1},new byte[]{2},-48,"stone",quota.period(),"buy",16,null,null,0);
            await(repo.prepareStore(x,3,64,false)); await(repo.commit(x.id()));
            List<CommerceRepository.Offer> offers=new ArrayList<>();
            for(int i=0;i<8;i++) offers.add(new CommerceRepository.Offer(i,"minecraft:unbreaking",i<2?3:1,i<2?1800:300,i<2));
            batchId=await(repo.ensureBatch(offers,false)).id();
        } finally { close(store); }
        store=open(db);
        try {
            var p=await(store.join(ID,"MomoRenamed",3000));check(p.coins()==8452&&p.exp()==275&&p.showIp()&&p.playtimeSeconds()==3600,"old and new state persists");
            var titles=new TitleRepository(store);var repo=new CommerceRepository(store);
            check(await(titles.load(ID)).equipped().equals("mame"),"title persists");
            var bag=await(repo.bag(ID,18));check(bag.capacity()==27&&Arrays.equals(bag.contents(),new byte[]{17,42,88}),"bag persists after rename no shrink");
            check(await(repo.quota(ID,"stone",null)).bought()==16,"quota persists");
            check(await(repo.batch()).id().equals(batchId),"books same batch");
            check(await(repo.pending(ID)).isEmpty(),"journal acknowledged");
        } finally { close(store); }
        System.out.println("V111_TO_V120_SHADED_JAR_UPGRADE_AND_COMMERCE_RESTART_OK");
    }
}
