package com.example.reglia;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * In-game commands for configuring the Discord bridge.
 */
public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /setwebhook <url> - MC → Discord
        dispatcher.register(Commands.literal("setwebhook")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ModCommands::setWebhook)));

        // /setbottoken <token> - Discord → MC (owner only)
        dispatcher.register(Commands.literal("setbottoken")
                .requires(source -> source.hasPermission(4))
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

        // /reglia - opens config panel
        dispatcher.register(Commands.literal("reglia")
                .requires(source -> source.hasPermission(2))
                .executes(ModCommands::openConfig)
                .then(Commands.literal("config").executes(ModCommands::openConfig)));
    }

    private static int openConfig(CommandContext<CommandSourceStack> ctx) {
        ConfigServer.startAndOpenBrowser();
        String url = ConfigServer.getUrl();

        // Send clickable URL in chat
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Reglia] §fConfig panel: ")
                .append(Component.literal("§b§n" + url)
                        .withStyle(style -> style
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url)))),
                false);
        return 1;
    }

    private static int setWebhook(CommandContext<CommandSourceStack> ctx) {
        String url = StringArgumentType.getString(ctx, "url");

        if (!url.startsWith("https://discord.com/api/webhooks/") &&
                !url.startsWith("https://discordapp.com/api/webhooks/")) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid webhook URL!"));
            return 0;
        }

        Config.setWebhookUrl(url);
        ctx.getSource().sendSuccess(() -> Component.literal("§aWebhook configured! MC → Discord enabled."), true);
        DiscordWebhook.sendMessage("✅ Reglia connected!", "Server");
        return 1;
    }

    private static int setBotToken(CommandContext<CommandSourceStack> ctx) {
        String token = StringArgumentType.getString(ctx, "token");
        Config.setBotToken(token);
        ctx.getSource().sendSuccess(() -> Component.literal("§aBot token set! Connecting..."), true);
        DiscordBot.restart();
        return 1;
    }

    private static int setChannel(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        Config.setChannelId(id);
        ctx.getSource().sendSuccess(() -> Component.literal("§aChannel filter set: §e" + id), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("§6[Reglia] §fStatus:\n");
        sb.append("§7Webhook: ").append(Config.hasWebhook() ? "§aConfigured" : "§cNot set").append("\n");
        sb.append("§7Bot: ").append(DiscordBot.isConnected() ? "§aConnected" : "§cDisconnected");
        if (Config.hasChannelId()) {
            sb.append("\n§7Channel: §e").append(Config.channelId);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int testWebhook(CommandContext<CommandSourceStack> ctx) {
        if (!Config.hasWebhook()) {
            ctx.getSource().sendFailure(Component.literal("§cNo webhook configured!"));
            return 0;
        }
        DiscordWebhook.sendMessage("🧪 Test message from Reglia!", "Server");
        ctx.getSource().sendSuccess(() -> Component.literal("§aTest message sent!"), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        Config.bridgeEnabled = !Config.bridgeEnabled;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6[Reglia] §fBridge " + (Config.bridgeEnabled ? "§aenabled" : "§cdisabled")), true);
        return 1;
    }

    private static int reconnect(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Reglia] §fReconnecting..."), false);
        DiscordBot.restart();
        return 1;
    }
}
