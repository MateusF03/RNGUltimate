package me.mateus.rngultimate.utils;

import me.mateus.rngultimate.RNGUltimate;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Objects;

public class ArmorUtil {



    public static void createRandomArmor(EntityEquipment equipment) {
        int red = RNGUltimate.RANDOM.nextInt(256);
        int green = RNGUltimate.RANDOM.nextInt(256);
        int blue = RNGUltimate.RANDOM.nextInt(256);
        Color color = Color.fromRGB(red, green, blue);
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta helmetMeta = (LeatherArmorMeta) helmet.getItemMeta();

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();

        Objects.requireNonNull(helmetMeta);
        Objects.requireNonNull(chestMeta);

        helmetMeta.setColor(color);
        chestMeta.setColor(color);

        helmet.setItemMeta(helmetMeta);
        chest.setItemMeta(chestMeta);
        equipment.setChestplate(chest);
        equipment.setHelmet(helmet);

    }
}
