package org.inventivetalent.onewayblocks;

import org.bstats.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.pluginannotations.PluginAnnotations;
import org.inventivetalent.pluginannotations.command.Command;
import org.inventivetalent.pluginannotations.command.OptionalArg;
import org.inventivetalent.pluginannotations.command.Permission;
import org.inventivetalent.pluginannotations.config.ConfigValue;
import org.inventivetalent.vectors.d3.Vector3DDouble;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OneWayBlocks extends JavaPlugin implements Listener {

	@ConfigValue(path = "radius.x") double radiusX;
	@ConfigValue(path = "radius.y") double radiusY;
	@ConfigValue(path = "radius.z") double radiusZ;

	@ConfigValue(path = "allowInteract") boolean allowInteract = false;

	ItemStack wandItem;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		PluginAnnotations.loadAll(this, this);
		Bukkit.getPluginManager().registerEvents(this, this);

		wandItem = new ItemStack(Material.NAME_TAG);
		ItemMeta wandMeta = wandItem.getItemMeta();
		wandMeta.setDisplayName("§eOneWayBlock§6Wand");
		wandItem.setItemMeta(wandMeta);

		new Metrics(this);
	}

	@Command(name = "onewayblockwand",
			 aliases = {
					 "owbw",
					 "blockwand",
					 "onewaywand",
					 "oww"
			 },
			 usage = "[material] [inverted]",
			 description = "Give yourself the one-way-block-wand",
			 min = 0,
			 max = 2,
			 fallbackPrefix = "onewayblocks")
	@Permission("onewayblocks.wand")
	public void oneWayWand(Player sender, @OptionalArg(def = "NULL") String material, @OptionalArg(def = "not inverted") String inverted) {
		ItemStack itemStack = wandItem.clone();
		setLoreIndex(itemStack, 0, "inverted".equalsIgnoreCase(inverted) ? "inverted" : "not inverted");
		setLoreIndex(itemStack, 1, material.toUpperCase());
		sender.getInventory().addItem(itemStack);
	}

	@EventHandler
	public void on(PlayerMoveEvent event) {
		if (event.getFrom().distanceSquared(event.getTo()) < 0.004) { return; }

		Vector3DDouble playerVector = new Vector3DDouble(event.getPlayer().getEyeLocation());
		for (OneWayBlock block : getNearbyOneWayBlocks(event.getPlayer())) {
			refreshBlock(event.getPlayer(), playerVector, block);
		}
	}

	void refreshBlock(Player player, Vector3DDouble playerVector, OneWayBlock block) {
		Block bukkitBlock = block.getBlock(player.getWorld());

		Location location = bukkitBlock.getLocation();
		boolean visible = !block.faceVisibleFrom(playerVector) && block.getDirectionMarker().hasLineOfSight(player);
		if (block.isInverted()) { visible = !visible; }
		if (visible) {
			player.sendBlockChange(location, block.getMaterial(), block.getData());
		} else {
			player.sendBlockChange(location, bukkitBlock.getType(), bukkitBlock.getData());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(BlockBreakEvent event) {
		if (event.isCancelled()) { return; }
		killBlock(event.getPlayer(), event.getBlock());
	}

	void killBlock(Player player, Block block) {
		for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
			if (entity.getType() == EntityType.ARMOR_STAND) {
				if (entity.getCustomName() != null && entity.getCustomName().startsWith("OneWayBlock-")) {
					if (entity.getLocation().getBlock().equals(block)) {
						OneWayBlock oneWayBlock = OneWayBlock.of(entity);
						entity.remove();

						ArmorStand directionMarker = getArmorStandInBlock(block.getRelative(oneWayBlock.getDirection()));
						if (directionMarker != null) {
							directionMarker.remove();
						}
					}
				}
			}
		}
	}

	Set<ArmorStand> getArmorStandsInBlock(Block block) {
		Set<ArmorStand> set = new HashSet<>();
		for (ArmorStand armorStand : block.getWorld().getEntitiesByClass(ArmorStand.class)) {
			if (armorStand.getCustomName() != null && (armorStand.getCustomName().startsWith("OneWayBlock-") || armorStand.getCustomName().equals("OneWayBlock:Direction"))) {
				if (armorStand.getLocation().getBlock().equals(block)) {
					set.add(armorStand);
				}
			}
		}
		return set;
	}

	ArmorStand getArmorStandInBlock(Block block) {
		for (ArmorStand armorStand : block.getWorld().getEntitiesByClass(ArmorStand.class)) {
			if (armorStand.getCustomName() != null && (armorStand.getCustomName().startsWith("OneWayBlock-") || armorStand.getCustomName().equals("OneWayBlock:Direction"))) {
				if (armorStand.getLocation().getBlock().equals(block)) {
					return armorStand;
				}
			}
		}
		return null;
	}

	@EventHandler(priority = EventPriority.MONITOR,
				  ignoreCancelled = true)
	public void onRefreshInteract(final PlayerInteractEvent event) {
		if (allowInteract) { return; }
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
			Bukkit.getScheduler().runTask(this, new Runnable() {
				@Override
				public void run() {
					Vector3DDouble playerVector = new Vector3DDouble(event.getPlayer().getEyeLocation());
					for (OneWayBlock block : getNearbyOneWayBlocks(event.getPlayer())) {
						refreshBlock(event.getPlayer(), playerVector, block);
					}
				}
			});
		}
	}

	@EventHandler
	public void onWandInteract(PlayerInteractEvent event) {
		if (event.isCancelled()) { return; }
		if (!event.getPlayer().hasPermission("onewayblocks.create")) { return; }
		if (event.getItem() == null) { return; }
		if (event.getItem().getType() != wandItem.getType()) { return; }
		if (!event.getItem().hasItemMeta()) { return; }
		if (!wandItem.getItemMeta().getDisplayName().equals(event.getItem().getItemMeta().getDisplayName())) { return; }
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			boolean inverted = "inverted".equals(getLoreIndex(event.getItem(), 0));

			Material material = null;
			byte data = 0;
			try {
				String materialString = getLoreIndex(event.getItem(), 1);
				if (materialString.contains(":")) {
					String[] materialSplit = materialString.split(":");
					if (materialSplit.length != 2) {
						return;
					}
					material = Material.valueOf(materialSplit[0]);
					data = Byte.parseByte(materialSplit[1]);
				} else {
					material = Material.valueOf(materialString);
				}
			} catch (Exception ignored) {
			}

			if (material == null) {
				event.getPlayer().sendMessage("§cPlease left-click to select another material first");
				return;
			}
			event.setCancelled(true);

			// Kill old ArmorStands
			killBlock(event.getPlayer(), event.getClickedBlock());

			BlockFace face = event.getBlockFace();
			//			if (inverted) { face = face.getOppositeFace(); }
			Location location = event.getClickedBlock().getLocation().add(.5, .5, .5);
			ArmorStand blockMarker = location.getWorld().spawn(location, ArmorStand.class);
			blockMarker.setMarker(true);
			blockMarker.setVisible(false);
			blockMarker.setGravity(false);
			blockMarker.setSmall(true);
			blockMarker.setBasePlate(false);
			blockMarker.setCustomName("OneWayBlock-" + face.name() + "-" + material + ":" + data + "-" + (inverted ? "inverted" : ""));

			location = event.getClickedBlock().getRelative(face).getLocation().add(.5, .5, .5);
			ArmorStand directionMarker = location.getWorld().spawn(location, ArmorStand.class);
			directionMarker.setMarker(true);
			directionMarker.setVisible(false);
			directionMarker.setGravity(false);
			directionMarker.setSmall(true);
			directionMarker.setBasePlate(false);
			directionMarker.setCustomName("OneWayBlock:Direction");

			event.getPlayer().sendMessage("§aBlock converted");
		} else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
			Block clicked = event.getClickedBlock();
			String materialString = clicked.getType() + ":" + clicked.getData();
			setLoreIndex(event.getItem(), 1, materialString);
			event.getPlayer().sendMessage("§aMaterial changed to §b" + materialString);
			event.setCancelled(true);
		}
	}

	ItemStack setLoreIndex(ItemStack itemStack, int index, String text) {
		ItemMeta meta = itemStack.getItemMeta();
		List<String> lore = !meta.hasLore() ? new ArrayList<String>() : new ArrayList<>(meta.getLore());
		if (index >= lore.size()) {
			lore.add(index, text);
		} else {
			lore.set(index, text);
		}
		meta.setLore(lore);
		itemStack.setItemMeta(meta);
		return itemStack;
	}

	String getLoreIndex(ItemStack itemStack, int index) {
		if (index >= itemStack.getItemMeta().getLore().size()) { return ""; }
		return itemStack.getItemMeta().getLore().get(index);
	}

	public Set<OneWayBlock> getNearbyOneWayBlocks(Player player) {
		Set<OneWayBlock> blocks = new HashSet<>();
		for (Entity entity : player.getNearbyEntities(radiusX, radiusY, radiusZ)) {
			if (entity.getType() == EntityType.ARMOR_STAND) {
				if (entity.getCustomName() != null && entity.getCustomName().startsWith("OneWayBlock-")) {
					OneWayBlock oneWayBlock = OneWayBlock.of(entity);
					oneWayBlock.setEntity((ArmorStand) entity);

					ArmorStand directionMarker = getArmorStandInBlock(oneWayBlock.getBlock(player.getWorld()).getRelative(oneWayBlock.getDirection()));
					if (directionMarker == null) { continue; }
					oneWayBlock.setDirectionMarker(directionMarker);

					blocks.add(oneWayBlock);
				}
			}
		}

		return blocks;
	}

}
