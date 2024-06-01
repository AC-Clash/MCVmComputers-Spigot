package com.acclash.vmcomputers.utils;

import net.minecraft.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class CustomItems {

    public static ItemStack getPhone() {
        ItemStack phone = new ItemStack(Material.BRICK);
        ItemMeta phoneMeta = phone.getItemMeta();
        phoneMeta.setDisplayName("DynaTAC Brick Phone");
        phone.setItemMeta(phoneMeta);
        net.minecraft.world.item.ItemStack nmsPhone = CraftItemStack.asNMSCopy(phone);
        CompoundTag phoneCompound = (nmsPhone.hasTag()) ? nmsPhone.getTag() : new CompoundTag();
        phoneCompound.putUUID("UUID", UUID.randomUUID());
        nmsPhone.setTag(phoneCompound);
        phone = CraftItemStack.asBukkitCopy(nmsPhone);

        return phone;
    }
}
