import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.data.TitleRepository;
import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.model.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

class Upgrade120Seed {
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10,TimeUnit.SECONDS); }
    public static void main(String[] args) throws Exception {
        Path db=Path.of(args[0]); if(Files.exists(db)) throw new AssertionError("seed requires fresh path");
        UUID id=UUID.fromString("11111111-1111-1111-1111-111111111111");
        var store=new SqliteStore(Logger.getAnonymousLogger(),Clock.fixed(Instant.parse("2026-09-29T06:00:00Z"),ZoneOffset.UTC));
        try {
            await(store.open(db,new LevelCurve(100,50,0,10000))); await(store.join(id,"Momo",1000));
            await(store.coins("Momo","set",25000)); await(store.experience("Momo","set",225));
            await(store.addPlaytime(Map.of(id,3600L))); await(store.togglePrivacy(id));
            await(store.saveHome(new Home(id,UUID.fromString("22222222-2222-2222-2222-222222222222"),"world",128.5,72,-435.5,90,15)));
            await(store.saveHome(new Home(id,UUID.fromString("33333333-3333-3333-3333-333333333333"),"world_new",-38.5,80,43.5,30,0)));
            if(!await(store.checkin(id,ZoneId.of("Asia/Shanghai"),100,50)).claimed()) throw new AssertionError("initial claim");
            var titles=new TitleRepository(store); var commerce=new CommerceRepository(store);
            await(titles.purchase(id,"sora",12000));
            var bag=await(commerce.bag(id,27));
            var exchange=new CommerceRepository.Exchange(UUID.randomUUID(),id,
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),"bag",new byte[]{1},new byte[]{2},0,
                    "","","",0,bag.contents(),new byte[]{17,42,88},bag.revision());
            await(commerce.prepareBag(exchange)); await(commerce.commit(exchange.id()));
            var quota=await(commerce.quota(id,"stone",null));
            exchange=new CommerceRepository.Exchange(UUID.randomUUID(),id,
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),"store",new byte[]{1},new byte[]{2},-48,
                    "stone",quota.period(),"buy",16,null,null,0);
            await(commerce.prepareStore(exchange,3,64,false)); await(commerce.commit(exchange.id()));
            List<CommerceRepository.Offer> offers=new ArrayList<>();
            for(int i=0;i<8;i++) offers.add(new CommerceRepository.Offer(i,"minecraft:unbreaking",i<2?3:1,i<2?1800:300,i<2));
            await(commerce.ensureBatch(offers,false));
        } finally { store.closeAsync(); if(!store.awaitClosed(10)) throw new AssertionError("close"); }
        System.out.println("V120_SEEDED_SCHEMA3_WITH_PROFILE_HOMES_CHECKIN_TITLE_BAG_STORE");
    }
}
