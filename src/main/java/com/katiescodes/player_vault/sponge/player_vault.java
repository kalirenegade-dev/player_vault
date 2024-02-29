package com.katiescodes.player_vault.sponge;

import com.google.common.collect.Lists;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryArchetypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.property.InventoryDimension;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import com.google.inject.Inject;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent.Close;
import org.spongepowered.api.event.network.ClientConnectionEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Plugin(
        id = "player_vault",
        name = "player_vault",
        version = player_vault.VERSION,
        description = "Official player_vault, by katiescodes.",
        authors = {
                "katiescodes"
        }
)
public class player_vault {

    @Inject
    private org.slf4j.Logger logger;
    static player_vault instance = null;

    public static final String VERSION = "0.0.1";

    private static final Path DATA_FOLDER = Paths.get("config", "player_vault");

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        instance = this;
        logger.info("player_vault started !");

        // Register the /vault command
        Sponge.getCommandManager().register(this,
                CommandSpec.builder()
                        .description(Text.of("Opens the vault for the player."))
                        .permission("player_vault.command.vault")
                        .executor(this::openVault)
                        .build(),
                "vault");

        // Register inventory interaction event listener
        Sponge.getEventManager().registerListeners(this, this);
    }

    @Listener
    public void onServerStop(GameStoppingServerEvent event) {
        logger.info("player_vault has stopped.");
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        loadPlayerInventory(event.getTargetEntity());
    }

    @Listener
    public void onPlayerQuit(ClientConnectionEvent.Disconnect event) {
        savePlayerInventory(event.getTargetEntity());
    }

    private CommandResult openVault(CommandSource src, CommandContext args) {
        if (!(src instanceof Player)) {
            src.sendMessage(Text.of("Only players can use this command."));
            return CommandResult.empty();
        }

        Player player = (Player) src;

        // Load player's inventory before opening the vault
        loadPlayerInventory(player);

        // Create a custom-sized inventory (6 rows, each row has 9 slots)
        Inventory inventory = Inventory.builder()
                .of(InventoryArchetypes.CHEST)
                .property(InventoryTitle.PROPERTY_NAME, InventoryTitle.of(Text.of("Vault")))
                .property(InventoryDimension.of(9, 6)) // 9 slots per row, 6 rows
                .build(this);

        player.openInventory(inventory);

        return CommandResult.success();
    }

    // Method to save player's inventory to file
    private void savePlayerInventory(Player player) {
        try {
            if (!Files.exists(DATA_FOLDER))
                Files.createDirectories(DATA_FOLDER);
            Path playerDataFile = DATA_FOLDER.resolve(player.getUniqueId().toString() + ".txt");
            List<String> inventoryContents = Lists.newArrayList();
            for (Inventory slot : player.getInventory().slots()) {
                slot.peek().ifPresent(itemStack -> inventoryContents.add(itemStack.toContainer().toString()));
            }
            Files.write(playerDataFile, inventoryContents);
            logger.info("Saved inventory for player: " + player.getName());
        } catch (IOException e) {
            logger.error("Error saving inventory for player: " + player.getName(), e);
        }
    }

    // Method to load player's inventory from file
    private void loadPlayerInventory(Player player) {
        try {
            if (!Files.exists(DATA_FOLDER))
                Files.createDirectories(DATA_FOLDER);
            Path playerDataFile = DATA_FOLDER.resolve(player.getUniqueId().toString() + ".txt");
            if (Files.exists(playerDataFile)) {
                List<String> inventoryContents = Files.readAllLines(playerDataFile);
                for (String itemString : inventoryContents) {
                    if (!itemString.isEmpty()) {
                        player.getInventory().offer(ItemStack.builder().build());
                    }
                }
                logger.info("Loaded inventory for player: " + player.getName());
            }
        } catch (IOException e) {
            logger.error("Error loading inventory for player: " + player.getName(), e);
        }
    }

    // Listener for inventory interaction events
    @Listener
    public void onInventoryInteract(InteractInventoryEvent event) {
        Player player = event.getCause().first(Player.class).orElse(null);
        if (player != null && event.getTargetInventory().getName().equals(Text.of("Vault"))) {
            savePlayerInventory(player);
        }
    }

    // Listener for inventory close events
    @Listener
    public void onInventoryClose(Close event) {
        Optional<Player> player = event.getCause().first(Player.class);
        if (player.isPresent()) {
            Inventory inventory = event.getTargetInventory();
            if (inventory.getName().equals(Text.of("Vault"))) {
                savePlayerInventory(player.get());
            }
        }
    }
}
