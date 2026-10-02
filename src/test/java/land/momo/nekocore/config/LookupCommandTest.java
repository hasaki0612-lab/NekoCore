package land.momo.nekocore.config;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.command.CoreCommands;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LookupCommandTest {
    NekoCorePlugin plugin; Messages messages; CommandSender sender; Command command; CoreCommands commands;
    @BeforeEach void setup() {
        plugin=mock(NekoCorePlugin.class); messages=mock(Messages.class); sender=mock(CommandSender.class); command=mock(Command.class);
        when(plugin.messages()).thenReturn(messages); when(command.getName()).thenReturn("nekocore");
        when(plugin.permission(any(),eq("nekocore.admin"))).thenReturn(true); when(plugin.available(any())).thenReturn(true);
        commands=new CoreCommands(plugin);
    }
    void run(CommandSender target,String... args) { commands.onCommand(target,command,"nekocore",args); }
    @Test void consoleNoArgumentExplainsKeywordUsage() {
        run(sender,"lookup"); verify(messages).send(sender,"lookup.console");
    }
    @Test void heldItemUsesActualMainHandAndEmptyHandDoesNotInventAType() {
        try(var registry=new PaperRegistryFixture()) {
        Player player=mock(Player.class); PlayerInventory inventory=mock(PlayerInventory.class); ItemStack item=mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(Material.BREAD);run(player,"lookup");
        verify(messages).send(player,"lookup.held",Map.of("material","BREAD"));
        when(item.getType()).thenReturn(Material.AIR);run(player,"lookup");verify(messages).send(player,"lookup.empty-hand");
        }
    }
    @Test void permissionAndLoadingGatePreventRegistryOrInventoryAccess() {
        Player player=mock(Player.class);when(plugin.permission(player,"nekocore.admin")).thenReturn(false);run(player,"lookup");
        verifyNoInteractions(messages);verify(player,never()).getInventory();
        when(plugin.permission(player,"nekocore.admin")).thenReturn(true);when(plugin.available(player)).thenReturn(false);run(player,"lookup","bread");
        verifyNoInteractions(messages);
    }
    @Test void materialKeywordFuzzyAndPrefixAreEnglishOnlyAndCappedAtTen() {
        try(var registry=new PaperRegistryFixture()) {
            run(sender,"lookup","bred");verify(messages).send(sender,"lookup.material-line",Map.of("name","BREAD"));
            clearInvocations(messages);run(sender,"lookup","stone");
            var values=org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(messages,atMost(10)).send(eq(sender),eq("lookup.material-line"),values.capture());
            assertFalse(values.getAllValues().isEmpty());assertEquals("STONE",values.getAllValues().getFirst().get("name"));
        }
        run(sender,"lookup","面包");verify(messages).send(sender,"lookup.english-only");
    }
    @Test void entityFuzzyLookupAndArgumentErrorsDoNotMutateAnything() {
        run(sender,"lookup","entity","phanton");verify(messages).send(sender,"lookup.entity-line",Map.of("name","PHANTOM"));
        run(sender,"lookup","entity");verify(messages).send(sender,"lookup.usage");
        run(sender,"lookup","not_a_real_type_at_all");verify(messages).send(sender,"lookup.none",Map.of("keyword","not_a_real_type_at_all"));
        verify(plugin,never()).reload(any());verify(plugin,never()).store();
    }
    @Test void completionUsesExistingAdminPermission() {
        when(sender.hasPermission("nekocore.admin")).thenReturn(true);
        assertTrue(commands.onTabComplete(sender,command,"nekocore",new String[]{"loo"}).contains("lookup"));
        assertTrue(commands.onTabComplete(sender,command,"nekocore",new String[]{"lookup","e"}).contains("entity"));
        when(sender.hasPermission("nekocore.admin")).thenReturn(false);
        assertTrue(commands.onTabComplete(sender,command,"nekocore",new String[]{"loo"}).isEmpty());
    }
}
