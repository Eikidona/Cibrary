package org.crychicteam.cibrary.content.armorset.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.crychicteam.cibrary.Cibrary;
import org.crychicteam.cibrary.content.armorset.ArmorSet;
import org.crychicteam.cibrary.network.CibraryNetworkHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ArmorSetCapability implements INBTSerializable<CompoundTag>, ICapabilityProvider {
    public static final Capability<ArmorSetCapability> ARMOR_SET_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});
    public static final ResourceLocation ARMOR_SET_CAPABILITY_ID = new ResourceLocation(Cibrary.MOD_ID, "armor_set");

    private ArmorSet activeSet;
    private ArmorSet.State state;
    private Map<Item, ArmorSet> itemSetMap = new HashMap<>();
    private Map<Item, ArmorSet> curioSetMap = new HashMap<>();

    public ArmorSetCapability() {
        this.activeSet = ArmorSetManager.getInstance().getDefaultArmorSet();
        this.state = ArmorSet.State.NORMAL;
    }

    public ArmorSet getActiveSet() {
        return activeSet != null ? activeSet : ArmorSetManager.getInstance().getDefaultArmorSet();
    }

    public void setActiveSet(ArmorSet set) {
        this.activeSet = set;
        updateItemSetMap();
    }

    public ArmorSet.State getState(ArmorSet set) {
        return set.getState();
    }

    public boolean isItemInActiveSet(ItemStack itemStack) {
        return itemSetMap.containsKey(itemStack.getItem()) || curioSetMap.containsKey(itemStack.getItem());
    }

    public ArmorSet getSetForItem(ItemStack itemStack) {
        ArmorSet set = itemSetMap.get(itemStack.getItem());
        if (set == null) {
            set = curioSetMap.get(itemStack.getItem());
        }
        return set != null ? set : ArmorSetManager.getInstance().getDefaultArmorSet();
    }

    private void updateItemSetMap() {
        itemSetMap.clear();
        curioSetMap.clear();
        if (activeSet != null) {
            for (Map.Entry<EquipmentSlot, Item> entry : activeSet.getEquipmentItems().entrySet()) {
                    if (entry.getValue() != null) {
                        itemSetMap.put(entry.getValue(), activeSet);
                    }
            }
            for (Map.Entry<Item, Integer> entry : activeSet.getCurioItems().entrySet()) {
                curioSetMap.put(entry.getKey(), activeSet);
            }
        }
    }

    public List<Component> getAdditionalTooltip(ItemStack itemStack) {
        List<Component> tooltip = new ArrayList<>();
        if (activeSet != null && isItemInActiveSet(itemStack)) {

            if (!activeSet.getEffects().isEmpty()) {
                for (Map.Entry<MobEffect, Integer> entry : activeSet.getEffects().entrySet()) {
                    tooltip.add(Component.translatable("tooltip.armorset.effect",
                            Component.translatable(entry.getKey().getDescriptionId()),
                            entry.getValue() + 1));
                }
            }

            if (!activeSet.getAttributes().isEmpty()) {
                for (Map.Entry<Attribute, AttributeModifier> entry : activeSet.getAttributes().entries()) {
                    AttributeModifier modifier = entry.getValue();
                    double amount = modifier.getAmount();
                    String operation = modifier.getOperation() == AttributeModifier.Operation.ADDITION ? "+" : "×";
                    tooltip.add(Component.translatable("tooltip.armorset.attribute",
                            Component.translatable(entry.getKey().getDescriptionId()),
                            operation + String.format("%.2f", amount)));
                }
            }
        }
        return tooltip;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        if (activeSet != null) {
            nbt.putString("activeSet", activeSet.getIdentifier());
            nbt.putString("state", state.name());
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        String identifier = nbt.getString("activeSet");
        this.activeSet = ArmorSetManager.getInstance().getArmorSetByIdentifier(identifier);
        String stateName = nbt.getString("state");
        try {
            this.state = ArmorSet.State.valueOf(stateName);
        } catch (IllegalArgumentException e) {
            this.state = ArmorSet.State.NORMAL;
        }
        updateItemSetMap();
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ArmorSetCapability.class);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
        return ARMOR_SET_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
    }

    public void syncToClient(Player player) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer) {
            CibraryNetworkHandler.sendArmorSetSync((ServerPlayer) player, this);
        }
    }
}