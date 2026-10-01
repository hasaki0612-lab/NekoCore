package land.momo.nekocore.service;

import io.papermc.paper.event.player.PlayerTradeEvent;
import land.momo.nekocore.*;
import land.momo.nekocore.config.*;
import land.momo.nekocore.data.*;
import land.momo.nekocore.task.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DailyTaskServiceEventTest {
    NekoCorePlugin plugin; DailyTaskRepository repository; DailyTaskSettings settings; DailyTaskService service; Player player; UUID id;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        plugin=mock(NekoCorePlugin.class); repository=mock(DailyTaskRepository.class); settings=DailyTaskSettings.load(TestDefaults.yaml());
        when(plugin.dailyTaskSettings()).thenReturn(settings); id=UUID.randomUUID(); player=mock(Player.class); when(player.getUniqueId()).thenReturn(id);
        List<DailyTaskRotation.Entry> entries=new ArrayList<>(); Map<DailyTaskDifficulty,Integer> indices=new EnumMap<>(DailyTaskDifficulty.class);
        for(var definition:DailyTaskDefinition.values()) entries.add(new DailyTaskRotation.Entry(definition.difficulty(),indices.merge(definition.difficulty(),1,Integer::sum)-1,definition.id()));
        var rotation=new DailyTaskRotation(LocalDate.of(2026,9,30),UUID.randomUUID(),entries);
        when(repository.ensure(any(),anyList())).thenReturn(CompletableFuture.completedFuture(rotation));
        when(repository.advance(any(),any(),anyString(),anyLong(),nullable(String.class),anyLong(),any())).thenAnswer(call -> {
            String task=call.getArgument(2); long amount=call.getArgument(3);
            return CompletableFuture.completedFuture(new DailyTaskRepository.Advance(new DailyTaskProgress(task,amount,false,false,""),true,false,0,0));
        });
        doAnswer(call -> { ((Consumer<Object>)call.getArgument(2)).accept(((CompletableFuture<?>)call.getArgument(1)).join()); return null; })
                .when(plugin).finish(any(),any(),any());
        service=new DailyTaskService(plugin,repository,Clock.fixed(Instant.parse("2026-09-30T08:00:00Z"),ZoneOffset.UTC),
                new Random(1),System::nanoTime, material -> material == Material.APPLE || material == Material.COOKED_BEEF,
                item -> item.getType() == Material.IRON_PICKAXE);
        service.ensureToday().join();
    }
    private void progressed(String task,long amount){ verify(repository).advance(eq(id),any(),eq(task),eq(amount),nullable(String.class),anyLong(),any()); }

    @Test void gardenerRequiresShearsOrSilkAndInventoryMovementHasNoListenerPath() {
        BlockBreakEvent event=mock(BlockBreakEvent.class); Block block=mock(Block.class); PlayerInventory inventory=mock(PlayerInventory.class);
        ItemStack tool=mock(ItemStack.class); when(event.getPlayer()).thenReturn(player); when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_LEAVES); when(player.getInventory()).thenReturn(inventory); when(inventory.getItemInMainHand()).thenReturn(tool);
        when(event.isDropItems()).thenReturn(true); when(tool.getType()).thenReturn(Material.IRON_AXE);
        service.breakBlock(event); verify(repository,never()).advance(eq(id),any(),eq("simple_gardener"),anyLong(),any(),anyLong(),any());
        when(tool.getType()).thenReturn(Material.SHEARS); service.breakBlock(event); progressed("simple_gardener",1);
        clearInvocations(repository); when(tool.getType()).thenReturn(Material.IRON_PICKAXE);
        service.breakBlock(event); progressed("simple_gardener",1);
    }

    @Test void appleAndVillagerTradeAdvanceBothOverlappingTasks() {
        PlayerItemConsumeEvent consume=mock(PlayerItemConsumeEvent.class); ItemStack apple=mock(ItemStack.class);
        when(consume.getPlayer()).thenReturn(player); when(consume.getItem()).thenReturn(apple); when(apple.getType()).thenReturn(Material.APPLE);
        service.consume(consume); progressed("simple_healthy",1); progressed("simple_eat_food",1);
        clearInvocations(repository);
        PlayerTradeEvent trade=mock(PlayerTradeEvent.class); when(trade.getPlayer()).thenReturn(player); service.trade(trade);
        progressed("normal_tax_collector",1); progressed("hard_tycoon",1);
    }

    @Test void shearPlantMatureHarvestDeepslateFurnaceFishAndEnchantUseSuccessfulEvents() {
        PlayerShearEntityEvent shear=mock(PlayerShearEntityEvent.class); Entity sheep=mock(Entity.class);
        when(shear.getPlayer()).thenReturn(player); when(shear.getEntity()).thenReturn(sheep); when(sheep.getType()).thenReturn(EntityType.SHEEP);
        service.shear(shear); progressed("simple_shear_sheep",1); clearInvocations(repository);
        BlockPlaceEvent place=mock(BlockPlaceEvent.class); ItemStack seeds=mock(ItemStack.class); when(place.getPlayer()).thenReturn(player);
        when(place.getItemInHand()).thenReturn(seeds); when(seeds.getType()).thenReturn(Material.CARROT); service.place(place); progressed("simple_pastoral",1);
        clearInvocations(repository);
        BlockBreakEvent harvest=mock(BlockBreakEvent.class); Block crop=mock(Block.class); Ageable age=mock(Ageable.class); PlayerInventory inv=mock(PlayerInventory.class);
        when(harvest.getPlayer()).thenReturn(player); when(harvest.getBlock()).thenReturn(crop); when(crop.getType()).thenReturn(Material.WHEAT);
        when(crop.getBlockData()).thenReturn(age); when(age.getAge()).thenReturn(7); when(age.getMaximumAge()).thenReturn(7);
        when(player.getInventory()).thenReturn(inv); when(inv.getItemInMainHand()).thenReturn(mock(ItemStack.class)); service.breakBlock(harvest); progressed("normal_harvest",1);
        clearInvocations(repository); when(crop.getType()).thenReturn(Material.DEEPSLATE_DIAMOND_ORE); service.breakBlock(harvest); progressed("normal_deepslate_worker",1);
        clearInvocations(repository);
        FurnaceExtractEvent furnace=mock(FurnaceExtractEvent.class); when(furnace.getPlayer()).thenReturn(player); when(furnace.getItemAmount()).thenReturn(16);
        service.furnace(furnace); progressed("normal_smelt",16); clearInvocations(repository);
        PlayerFishEvent fish=mock(PlayerFishEvent.class); Item caught=mock(Item.class); ItemStack cod=mock(ItemStack.class);
        when(fish.getPlayer()).thenReturn(player); when(fish.getState()).thenReturn(PlayerFishEvent.State.CAUGHT_FISH); when(fish.getCaught()).thenReturn(caught);
        when(caught.getItemStack()).thenReturn(cod); when(cod.getType()).thenReturn(Material.COD); service.fish(fish); progressed("normal_fishing",1);
        clearInvocations(repository);
        EnchantItemEvent enchant=mock(EnchantItemEvent.class); when(enchant.getEnchanter()).thenReturn(player); service.enchant(enchant); progressed("hard_enchant",1);
    }

    @Test void workbenchDistinctCraftDessertAndIronToolCountButTwoByTwoDoesNot() {
        CraftItemEvent event=mock(CraftItemEvent.class); Recipe recipe=mock(Recipe.class);
        ItemStack result=mock(ItemStack.class); CraftingInventory crafting=mock(CraftingInventory.class);
        when(event.getWhoClicked()).thenReturn(player); when(event.getInventory()).thenReturn(crafting);
        when(crafting.getMatrix()).thenReturn(new ItemStack[4]);
        when(event.getRecipe()).thenReturn(recipe); when(recipe.getResult()).thenReturn(result); when(result.getType()).thenReturn(Material.COOKIE);
        when(result.getAmount()).thenReturn(8); when(event.isShiftClick()).thenReturn(false);
        service.craft(event); verify(repository,never()).advance(eq(id),any(),eq("simple_crafting"),anyLong(),any(),anyLong(),any());
        when(crafting.getMatrix()).thenReturn(new ItemStack[9]); service.craft(event);
        verify(repository).advance(eq(id),any(),eq("simple_crafting"),eq(1L),eq("COOKIE"),anyLong(),any()); progressed("normal_dessert",8);
        clearInvocations(repository); when(result.getType()).thenReturn(Material.IRON_PICKAXE); when(result.getAmount()).thenReturn(1);
        service.craft(event); progressed("normal_blacksmith",1);
    }

    @Test void hostileAndIronGolemFinalKillsCountAndHappyGhastRegressionNeverCounts() {
        EntityDeathEvent hostile=mock(EntityDeathEvent.class); Monster monster=mock(Monster.class); when(hostile.getEntity()).thenReturn(monster);
        when(monster.getKiller()).thenReturn(player); when(hostile.getEntityType()).thenReturn(EntityType.ZOMBIE); service.death(hostile); progressed("normal_cleanup",1);
        clearInvocations(repository); LivingEntity golem=mock(IronGolem.class); when(golem.getKiller()).thenReturn(player);
        when(hostile.getEntity()).thenReturn(golem); when(hostile.getEntityType()).thenReturn(EntityType.IRON_GOLEM); service.death(hostile); progressed("hard_iron_golem",1);
        clearInvocations(repository); LivingEntity happy=mock(HappyGhast.class); when(happy.getKiller()).thenReturn(player);
        when(hostile.getEntity()).thenReturn(happy); when(hostile.getEntityType()).thenReturn(EntityType.HAPPY_GHAST); service.death(hostile);
        verify(repository,never()).advance(eq(id),any(),eq("hard_marksman"),anyLong(),any(),anyLong(),any());
    }

    @Test void ordinaryGhastShotByPlayerArrowCountsForMarksman() {
        EntityDeathEvent death=mock(EntityDeathEvent.class); Ghast ghast=mock(Ghast.class); EntityDamageByEntityEvent damage=mock(EntityDamageByEntityEvent.class);
        AbstractArrow arrow=mock(AbstractArrow.class); when(death.getEntity()).thenReturn(ghast); when(death.getEntityType()).thenReturn(EntityType.GHAST);
        when(ghast.getKiller()).thenReturn(player); when(ghast.getLastDamageCause()).thenReturn(damage); when(damage.getDamager()).thenReturn(arrow);
        when(arrow.getType()).thenReturn(EntityType.ARROW); when(arrow.getShooter()).thenReturn(player);
        service.death(death); progressed("hard_marksman",1);
    }

    @Test void cakeAndPumpkinPieCompleteDessertWhileOrdinaryFoodDoesNot() {
        CraftItemEvent event=mock(CraftItemEvent.class); Recipe recipe=mock(Recipe.class);
        ItemStack result=mock(ItemStack.class); CraftingInventory crafting=mock(CraftingInventory.class);
        when(event.getWhoClicked()).thenReturn(player); when(event.getInventory()).thenReturn(crafting);
        when(crafting.getMatrix()).thenReturn(new ItemStack[9]); when(event.getRecipe()).thenReturn(recipe);
        when(recipe.getResult()).thenReturn(result); when(result.getAmount()).thenReturn(1); when(event.isShiftClick()).thenReturn(false);

        when(result.getType()).thenReturn(Material.CAKE); service.craft(event);
        verify(repository).advance(eq(id),any(),eq("normal_dessert"),eq((long)settings.target("normal_dessert")),nullable(String.class),anyLong(),any());
        clearInvocations(repository);
        when(result.getType()).thenReturn(Material.PUMPKIN_PIE); service.craft(event);
        verify(repository).advance(eq(id),any(),eq("normal_dessert"),eq((long)settings.target("normal_dessert")),nullable(String.class),anyLong(),any());
        clearInvocations(repository);
        when(result.getType()).thenReturn(Material.BREAD); service.craft(event);
        verify(repository,never()).advance(eq(id),any(),eq("normal_dessert"),anyLong(),any(),anyLong(),any());
    }

    @Test void cancellableProgressHandlersTellPaperToIgnoreCancelledEvents() throws Exception {
        Map<String,Class<?>> handlers=Map.ofEntries(
                Map.entry("breakBlock",BlockBreakEvent.class), Map.entry("consume",PlayerItemConsumeEvent.class),
                Map.entry("shear",PlayerShearEntityEvent.class), Map.entry("place",BlockPlaceEvent.class),
                Map.entry("craft",CraftItemEvent.class), Map.entry("trade",PlayerTradeEvent.class),
                Map.entry("fish",PlayerFishEvent.class), Map.entry("enchant",EnchantItemEvent.class),
                Map.entry("bucket",PlayerBucketEmptyEvent.class), Map.entry("move",PlayerMoveEvent.class));
        for (var entry : handlers.entrySet()) {
            EventHandler annotation=DailyTaskService.class.getMethod(entry.getKey(),entry.getValue()).getAnnotation(EventHandler.class);
            assertNotNull(annotation,entry.getKey());
            assertTrue(annotation.ignoreCancelled(),entry.getKey()+" must not count a cancelled action");
            assertEquals(EventPriority.MONITOR,annotation.priority(),entry.getKey());
        }
    }
}
