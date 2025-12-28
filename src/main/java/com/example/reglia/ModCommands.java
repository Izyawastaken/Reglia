package com.example.reglia;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Commands for Reglia Discord Bridge.
 */
public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /setwebhook <url> - MC -> Discord
        dispatcher.register(Commands.literal("setwebhook")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ModCommands::setWebhook)));

        // /setbottoken <token> - Discord -> MC
        dispatcher.register(Commands.literal("setbottoken")
                .requires(source -> source.hasPermission(4)) // Owner only
                .then(Commands.argument("token", StringArgumentType.greedyString())
                        .executes(ModCommands::setBotToken)));

        // /setchannel <id>
        dispatcher.register(Commands.literal("setchannel")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ModCommands::setChannel)));

        // /discord subcommands
        dispatcher.register(Commands.literal("discord")
                .then(Commands.literal("status").executes(ModCommands::status))
                .then(Commands.literal("test").requires(s -> s.hasPermission(2)).executes(ModCommands::testWebhook))
                .then(Commands.literal("toggle").requires(s -> s.hasPermission(2)).executes(ModCommands::toggle))
                .then(Commands.literal("reconnect").requires(s -> s.hasPermission(2))
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
        context.getSource().sendSuccess(() -> Component.literal("§aWebhook set! MC → Discord enabled."), true);
        DiscordWebhook.sendMessage("✅ Reglia connected!", "Server");
        return 1;
    }

    private static int setBotToken(CommandContext<CommandSourceStack> context) {
        String token = StringArgumentType.getString(context, "token");
        Config.setBotToken(token);
        context.getSource().sendSuccess(() -> Component.literal("§aBot token set! Connecting..."), true);
        DiscordBot.restart();
        return 1;
    }

    private static int setChannel(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Config.setChannelId(id);
        context.getSource().sendSuccess(() -> Component.literal("§aChannel set to: §e" + id), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        StringBuilder sb = new StringBuilder("§6[Reglia] §fStatus:\n");
        sb.append("§7Webhook (MC→Discord): ").append(Config.hasWebhook() ? "§aConfigured" : "§cNot set").append("\n");
        sb.append("§7Bot (Discord→MC): ").append(DiscordBot.isConnected() ? "§aConnected" : "§cDisconnected");
        if (Config.hasChannelId()) {
            sb.append("\n§7Channel: §e").append(Config.channelId);
        }
        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int testWebhook(CommandContext<CommandSourceStack> context) {
        if (!Config.hasWebhook()) {
            context.getSource().sendFailure(Component.literal("§cNo webhook set!"));
            return 0;
        }
        DiscordWebhook.sendMessage("🧪 Test message!", "Server");
        context.getSource().sendSuccess(() -> Component.literal("§aTest sent!"), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        Config.bridgeEnabled = !Config.bridgeEnabled;
        context.getSource().sendSuccess(() -> Component.literal(
                "§6[Reglia] §fBridge " + (Config.bridgeEnabled ? "§aenabled" : "§cdisabled")), true);
        return 1;
    }

    private static int reconnect(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§6[Reglia] §fReconnecting bot..."), false);
        DiscordBot.restart();
        return 1;
    }
}
