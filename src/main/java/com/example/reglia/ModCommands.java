package com.example.reglia;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers mod commands for Reglia Discord Bridge.
 */
public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /setwebhook <url> - Set webhook for Minecraft -> Discord
        dispatcher.register(Commands.literal("setwebhook")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ModCommands::setWebhook)));

        // /setbottoken <token> - Set bot token for Discord -> Minecraft
        dispatcher.register(Commands.literal("setbottoken")
                .requires(source -> source.hasPermission(4)) // Requires OP level 4 (owner only!)
                .then(Commands.argument("token", StringArgumentType.greedyString())
                        .executes(ModCommands::setBotToken)));

        // /setchannel <id> - Set channel ID to listen to
        dispatcher.register(Commands.literal("setchannel")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ModCommands::setChannel)));

        // /discord status|test|toggle
        dispatcher.register(Commands.literal("discord")
                .then(Commands.literal("status")
                        .executes(ModCommands::status))
                .then(Commands.literal("test")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::testWebhook))
                .then(Commands.literal("toggle")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::toggle))
                .then(Commands.literal("reconnect")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::reconnect)));
    }

    private static int setWebhook(CommandContext<CommandSourceStack> context) {
        String url = StringArgumentType.getString(context, "url");

        if (!url.startsWith("https://discord.com/api/webhooks/") &&
                !url.startsWith("https://discordapp.com/api/webhooks/")) {
            context.getSource().sendFailure(Component.literal("§cInvalid webhook URL!"));
            return 0;
        }

        Config.setWebhookUrl(url);
        context.getSource().sendSuccess(() -> Component.literal("§aWebhook set! Minecraft → Discord enabled."), true);
        DiscordWebhook.sendMessage("✅ Reglia connected!", "Server");

        return 1;
    }

    private static int setBotToken(CommandContext<CommandSourceStack> context) {
        String token = StringArgumentType.getString(context, "token");

        Config.setBotToken(token);
        context.getSource().sendSuccess(() -> Component.literal("§aBot token set! Restarting bot..."), true);

        // Restart the bot with new token
        DiscordBot.restart();

        return 1;
    }

    private static int setChannel(CommandContext<CommandSourceStack> context) {
        String channelId = StringArgumentType.getString(context, "id");

        Config.setChannelId(channelId);
        context.getSource().sendSuccess(() -> Component.literal("§aChannel ID set to: §e" + channelId), true);

        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        StringBuilder status = new StringBuilder("§6[Reglia] §fDiscord Bridge Status:\n");

        // Webhook status
        if (Config.hasWebhook()) {
            status.append("§7Webhook (MC→Discord): §aConfigured\n");
        } else {
            status.append("§7Webhook (MC→Discord): §cNot configured\n");
        }

        // Bot status
        if (Config.hasBotToken()) {
            if (DiscordBot.isConnected()) {
                status.append("§7Bot (Discord→MC): §aConnected\n");
            } else {
                status.append("§7Bot (Discord→MC): §eToken set but not connected\n");
            }
        } else {
            status.append("§7Bot (Discord→MC): §cNo token configured\n");
        }

        // Channel status
        if (Config.hasChannelId()) {
            status.append("§7Channel ID: §e").append(Config.channelId);
        } else {
            status.append("§7Channel ID: §cNot set (listening to all channels)");
        }

        context.getSource().sendSuccess(() -> Component.literal(status.toString()), false);
        return 1;
    }

    private static int testWebhook(CommandContext<CommandSourceStack> context) {
        if (!Config.hasWebhook()) {
            context.getSource().sendFailure(Component.literal("§cNo webhook configured!"));
            return 0;
        }

        boolean success = DiscordWebhook.sendMessage("🧪 Test message from Reglia!", "Server");

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("§aTest message sent!"), false);
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to send message."));
        }

        return success ? 1 : 0;
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        Config.bridgeEnabled = !Config.bridgeEnabled;
        String status = Config.bridgeEnabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendSuccess(() -> Component.literal("§6[Reglia] §fBridge " + status), true);
        return 1;
    }

    private static int reconnect(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§6[Reglia] §fReconnecting bot..."), false);
        DiscordBot.restart();

        if (DiscordBot.isConnected()) {
            context.getSource().sendSuccess(() -> Component.literal("§aBot reconnected!"), false);
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to reconnect. Check token."));
        }

        return 1;
    }
}
