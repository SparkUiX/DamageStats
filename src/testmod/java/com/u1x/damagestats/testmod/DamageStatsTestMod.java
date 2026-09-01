package com.u1x.damagestats.testmod;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.UUID;

@Mod(DamageStatsTestMod.MOD_ID)
public final class DamageStatsTestMod {
    public static final String MOD_ID = "damagestatstest";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final RegistryObject<Item> ADD_CHARM = ITEMS.register("add_attack_charm",
            () -> new TestCurioItem(6.0D, AttributeModifier.Operation.ADDITION));
    private static final RegistryObject<Item> MULTIPLY_CHARM = ITEMS.register("multiply_attack_charm",
            () -> new TestCurioItem(3.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));

    public DamageStatsTestMod() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("damagestatstest")
                .then(Commands.literal("equip_add").executes(context -> equip(context.getSource(), ADD_CHARM.get())))
                .then(Commands.literal("equip_multiply").executes(context -> equip(context.getSource(), MULTIPLY_CHARM.get())))
                .then(Commands.literal("clear").executes(context -> equip(context.getSource(), ItemStack.EMPTY))));
    }

    private static int equip(CommandSourceStack source, Item item) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return equip(source, new ItemStack(item));
    }

    private static int equip(CommandSourceStack source, ItemStack stack) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty() || optional.get().getStacksHandler("charm").isEmpty()) {
            source.sendFailure(Component.literal("Curios charm 槽不存在。"));
            return 0;
        }
        optional.get().setEquippedCurio("charm", 0, stack);
        source.sendSuccess(() -> Component.literal("测试饰品已设置为：" +
                (stack.isEmpty() ? "空" : stack.getHoverName().getString())), false);
        return 1;
    }

    private static final class TestCurioItem extends Item implements ICurioItem {
        private final double amount;
        private final AttributeModifier.Operation operation;

        private TestCurioItem(double amount, AttributeModifier.Operation operation) {
            super(new Item.Properties().stacksTo(1));
            this.amount = amount;
            this.operation = operation;
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
                SlotContext slotContext, UUID uuid, ItemStack stack) {
            return ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(uuid, "Damage Stats test curio", amount, operation));
        }

        @Override
        public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
            return true;
        }
    }
}
