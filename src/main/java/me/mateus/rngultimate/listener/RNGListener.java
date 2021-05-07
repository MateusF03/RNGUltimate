package me.mateus.rngultimate.listener;

import me.mateus.rngultimate.RNGUltimate;
import me.mateus.rngultimate.utils.ArmorUtil;
import net.sothatsit.blockstore.BlockStoreApi;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class RNGListener implements Listener{

    private final Plugin plugin;
    private final Set<Player> players = new HashSet<>();
    private final Set<GameMode> allowedGameModes = EnumSet.of(GameMode.CREATIVE, GameMode.SPECTATOR);
    private final Set<Material> logTypes = EnumSet.of(Material.ACACIA_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG, Material.JUNGLE_LOG, Material.SPRUCE_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM, Material.OAK_LOG);
    private final TreeMap<Integer, ChatColor> appropriatedChatColors = new TreeMap<>();
    private final TreeMap<Integer, BiConsumer<PlayerMoveEvent, Integer>> moveEvents = new TreeMap<>();
    private final TreeMap<Integer, BiConsumer<BlockBreakEvent, Integer>> breakEvents = new TreeMap<>();

    public RNGListener(Plugin plugin) {
        this.plugin = plugin;
        appropriatedChatColors.put(20, ChatColor.LIGHT_PURPLE);
        appropriatedChatColors.put(15, ChatColor.GOLD);
        appropriatedChatColors.put(10, ChatColor.GREEN);
        appropriatedChatColors.put(5, ChatColor.RED);
        appropriatedChatColors.put(1, ChatColor.DARK_RED);

        initEvents();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (players.contains(player)) {
            event.setCancelled(true);
            return;
        }
        double walkChance = RNGUltimate.RANDOM.nextDouble();
        if (walkChance <= RNGUltimate.walkChance) {
            int result = getResult(player);
            if (result == -1) {
                return;
            }
            roll(player, result, () -> {
                int key = moveEvents.floorKey(result);
                moveEvents.get(key).accept(event, result);
            });
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        double eatChance = RNGUltimate.RANDOM.nextDouble();
        if (eatChance <= RNGUltimate.eatChance) {
            Player player = event.getPlayer();
            int result = getResult(player);
            if (result == -1) {
                return;
            }
            roll(player, result, () -> giveEffect(player, result));
        }
    }

    @EventHandler
    public void onWake(PlayerBedLeaveEvent e) {
        if (RNGUltimate.RANDOM.nextBoolean()) {
            Player player = e.getPlayer();
            int result = getResult(player);
            if (result == -1) {
                return;
            }
            roll(player, result, () -> giveEffect(player, result));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getType() == EntityType.ZOMBIE && entity.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            double attackChance = RNGUltimate.RANDOM.nextDouble();
            if (attackChance <= RNGUltimate.attackChance) {
                Player player = entity.getKiller();
                if (player == null)
                    return;
                int result = getResult(player);
                if (result == -1)
                    return;
                if (result <= 10) {
                    Zombie zombie = (Zombie) player.getWorld().spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
                    player.getWorld().playSound(entity.getLocation(), Sound.ENTITY_TNT_PRIMED, 100,0.5f);
                    Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(5.0);
                    Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(1.2);
                    Objects.requireNonNull(zombie.getEquipment()).setHelmet(new ItemStack(Material.LEATHER_HELMET));
                    zombie.setCustomName("Robert");
                    zombie.setCustomNameVisible(true);
                }
            }
        }
    }

    @EventHandler
    public void onResult(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        if (event.getSlotType() == InventoryType.SlotType.RESULT) {
            ItemStack item = event.getCurrentItem();
            if (item == null) {
                return;
            }
            short max = item.getType().getMaxDurability();
            if (max <= 0) {
                return;
            }
            Damageable itemMeta = (Damageable) item.getItemMeta();
            if (itemMeta == null) {
                return;
            }
            int result = getResult(player);
            if (result == -1) {
                return;
            }
            int damage = (result * max) / 20;
            itemMeta.setDamage(max - damage);
            item.setItemMeta((ItemMeta) itemMeta);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 100,1.0f);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event){
        Player player = event.getPlayer();
        Block block = event.getBlock();

        World world = block.getWorld();

        BlockData blockData = block.getBlockData();
        Material material = blockData.getMaterial();
        if (logTypes.contains(material)) {
            if (BlockStoreApi.isPlaced(block))
                return;
            int roll = getResult(player);
            if (roll != -1) {
                event.setDropItems(false);
                ItemStack logs = new ItemStack(material);
                roll(player, roll, () -> {
                    if (roll > 20) {
                        ItemStack goldIngots = new ItemStack(Material.GOLD_INGOT, 2);
                        logs.setAmount(roll - 10);
                        world.dropItemNaturally(block.getLocation(), goldIngots);
                        world.dropItemNaturally(block.getLocation(), logs);
                    } else if (roll >= 10) {
                        logs.setAmount(roll - 10);
                        world.dropItemNaturally(block.getLocation(), logs);
                    } else {
                        spawnBees(player, roll);
                    }
                });
            }
        } else if (material == Material.STONE) {
            double stoneChance = RNGUltimate.RANDOM.nextDouble();
            if (stoneChance <= RNGUltimate.mineStoneChance){
                int roll = getResult(player);
                if (roll == -1)
                    return;
                List<Block> blocks = getSurroundingBlocks(block.getLocation(), block.getWorld());
                Stream<Block> stones = blocks.stream().filter(b -> b.getBlockData().getMaterial() == Material.STONE);
                if (stones.count() >= 4) {
                    roll(player, roll, () -> {
                        int key = breakEvents.floorKey(roll);
                        breakEvents.get(key).accept(event, roll);
                    });
                }
            }
        }
    }

    private void roll(Player player, int result, Runnable runLater) {
        player.getNearbyEntities(32,32,32).forEach(e -> {
            if (e instanceof LivingEntity) {
                ((LivingEntity) e).setAI(false);
            }
        });
        player.sendTitle(ChatColor.GOLD + "Rolling...", ChatColor.MAGIC + "H" ,0, 80, 0);
        player.setAllowFlight(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 100));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 80, 100));
        players.add(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                showResult(player, result);
                runLater.run();
            }
        }.runTaskLater(plugin, 80);
    }

    private void showResult(Player player, int result) {
        Integer chatColor = appropriatedChatColors.floorKey(result);
        player.sendTitle(ChatColor.GOLD + "Result:", appropriatedChatColors.get(chatColor) + String.valueOf(result), 0, 40, 4);
        player.getNearbyEntities(32,32,32).forEach(e -> {
            if (e instanceof LivingEntity) {
                ((LivingEntity) e).setAI(true);
            }
        });
        if (result >= 19) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 100, 1.0f);
        } else if (result <= 2) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 100, 0.5f);
        }
        if (!allowedGameModes.contains(player.getGameMode())) {
            player.setAllowFlight(false);
        }
        players.remove(player);
    }

    private int getResult(Player player) {
        if (!canRoll(player)) {
            return -1;
        }
        return RNGUltimate.RANDOM.nextInt(20) + 1;
    }

    private void spawnMonsters(Player player, int result) {
        Location location = player.getLocation();
        World world = location.getWorld();
        
        Objects.requireNonNull(world);
        
        for (int i = 0; i < 11 - result; i++) {
            Location randomLoc = getRandomLocation(location, 5);
            Zombie entity = (Zombie) world.spawnEntity(randomLoc, EntityType.ZOMBIE);
            EntityEquipment equipment = entity.getEquipment();

            Objects.requireNonNull(equipment);

            ArmorUtil.createRandomArmor(equipment);
            Material swordMaterial;
            if (RNGUltimate.RANDOM.nextBoolean()) {
                swordMaterial = Material.WOODEN_SWORD;
            } else {
                swordMaterial = Material.STONE_SWORD;
            }
            ItemStack sword = new ItemStack(swordMaterial);
            equipment.setItemInMainHand(sword);
        }
    }

    private void spawnBees(Player player, int result) {
        Location location = player.getLocation();
        World world = location.getWorld();

        Objects.requireNonNull(world);

        for (int i = 0; i < (11 - result) * 2; i++) {
            Location randomLoc = getRandomLocation(location, 10);
            Bee bee = (Bee) world.spawnEntity(randomLoc, EntityType.BEE);

            bee.setTarget(player);
            bee.setAnger(960);

        }
    }

    private Location getRandomLocation(Location center, int distance) {
        double r1 = RNGUltimate.RANDOM.nextDouble();
        double r2 = RNGUltimate.RANDOM.nextDouble();

        double a = r1 * 2.0 * Math.PI;
        double r = distance * Math.sqrt(r2);

        double x = r * Math.cos(a);
        double z = r * Math.sin(a);

        World world = center.getWorld();

        Objects.requireNonNull(world);

        Location newLocation = center.clone();
        newLocation.add(x, 0, z);

        int y = world.getHighestBlockYAt(newLocation);
        newLocation.setY(y);
        return newLocation;
    }



    private boolean canRoll(Player player) {
        GameMode gamemode = player.getGameMode();
        return gamemode == GameMode.SURVIVAL && !players.contains(player);
    }

    private void giveEffect(Player player, int result) {
        if (result >= 20) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 5));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, 5));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 2400, 5));
        } else if (result >= 15) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 600, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 1200, 1));
        } else if (result >= 10) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 1));
        } else if (result >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1600, 1));
        }
    }

    private void initEvents() {
        moveEvents.put(20, (e, r) -> {
            Player player = e.getPlayer();
            Location location = player.getLocation();
            World world = location.getWorld();
            Objects.requireNonNull(world);

            world.dropItemNaturally(getRandomLocation(location, 4), new ItemStack(Material.GOLD_INGOT, 18));
        });
        moveEvents.put(15, (e, r) -> {
            Player player = e.getPlayer();
            Location location = player.getLocation();
            World world = location.getWorld();
            Objects.requireNonNull(world);

            world.dropItemNaturally(getRandomLocation(location, 4), new ItemStack(Material.GOLD_NUGGET, (r - 7) * 2));
        });
        moveEvents.put(10, (e, r) -> {
            Player player = e.getPlayer();
            Location location = player.getLocation();
            World world = location.getWorld();
            Objects.requireNonNull(world);

            world.dropItemNaturally(getRandomLocation(location, 4), new ItemStack(Material.EMERALD, r - 9));
        });
        moveEvents.put(1, (e, r) -> spawnMonsters(e.getPlayer(), r));

        breakEvents.put(20, (e, r) -> {
            Block block = e.getBlock();
            List<Block> blocks = getSurroundingBlocks(block.getLocation(), block.getWorld());
            for (Block b: blocks) {
                if (b.getBlockData().getMaterial() != Material.STONE)
                    continue;
                b.setType(Material.DIAMOND_ORE);
            }
        });
        breakEvents.put(15, (e, r) -> {
            Block block = e.getBlock();

            List<Block> blocks = getSurroundingBlocks(block.getLocation(), block.getWorld());
            for (Block b: blocks) {
                if (b.getBlockData().getMaterial() != Material.STONE)
                    continue;
                b.setType(Material.IRON_ORE);
            }

        });
        breakEvents.put(10, (e, r) -> {
            Block block = e.getBlock();

            List<Block> blocks = getSurroundingBlocks(block.getLocation(), block.getWorld());
            for (Block b: blocks) {
                if (b.getBlockData().getMaterial() != Material.STONE)
                    continue;
                b.setType(Material.COAL_ORE);
            }

        });
        breakEvents.put(5, (e, r) -> {
            Block block = e.getBlock();
            List<Block> blocks = getSurroundingBlocks(block.getLocation(), block.getWorld());
            for (Block b: blocks) {
                if (b.getBlockData().getMaterial() != Material.STONE)
                    continue;
                b.setType(Material.SANDSTONE);
            }
        });
        breakEvents.put(1, (e, r) -> {
            Block block = e.getBlock();

            List<Block> blocks = getSurroundingBlocks(block.getLocation(), block.getWorld());

            for (Block b: blocks) {
                if (b.getBlockData().getMaterial() != Material.STONE)
                    continue;
                b.setType(Material.TNT);
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    Block loc = blocks.get(0);
                    loc.getWorld().createExplosion(loc.getLocation(),4.0f);
                }
            }.runTaskLater(plugin, 20 * 3);
        });
    }
    
    private List<Block> getSurroundingBlocks(Location location, World world) {
        List<Block> blocks = new ArrayList<>();
        for (int x = location.getBlockX(); x < location.getBlockX() + 2; x++) {
            for (int y = location.getBlockY(); y < location.getBlockY() + 2; y++) {
                for (int z = location.getBlockZ(); z < location.getBlockZ() + 2; z++) {
                    blocks.add(world.getBlockAt(x,y,z));
                }
            }
        }
        return blocks;
    }
}
