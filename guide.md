# Reglia User Guide

Welcome to the official guide for **Reglia**, a seamless Discord Bridge mod for Minecraft (NeoForge 1.21.1) that brings your two worlds together with support for inline GIF previews!

## 📥 Installation

1.  **Prerequisites**: Ensure you have **Minecraft 1.21.1** installed with the latest **NeoForge** loader.
2.  **Download**: Visit the Reglia Modrinth Page and download the latest version.
3.  **Install**: Drop the `.jar` file into your Minecraft instance's `mods` folder.
4.  **Launch**: Start the game. The config file will be generated automatically, but you'll need to configure the connection using in-game commands.

## 🛠️ Configuration

Reglia is designed to be configured entirely from within the game using the `/set` commands.

### Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/setwebhook <url>` | Admin (Level 2) | Sets the Discord Webhook URL for sending Minecraft chat **to** Discord. |
| `/setbottoken <token>` | Owner (Level 4) | Sets the Discord Bot Token for receiving Discord chat **in** Minecraft. |
| `/setchannel <id>` | Admin (Level 2) | (Optional) Restricts the bot to listen to a specific Discord channel ID. |
| `/discord status` | Everyone | Checks the current connection status of the Webhook and Bot. |
| `/discord test` | Admin (Level 2) | Sends a test message to Discord via the Webhook. |
| `/discord toggle` | Admin (Level 2) | Temporarily enables or disables the bridge. |
| `/discord reconnect` | Admin (Level 2) | Forces the Discord bot to reconnect. |

> **Note**: `/setbottoken` requires operator level 4 permissions (server console or fully authorized operator) for security reasons.

---

## 🤖 Discord Integration Setup

To get Reglia working fully (sending AND receiving), you need to set up two things on Discord: a **Webhook** and a **Bot Application**.

### Part 1: Setting up the Webhook (MC -> Discord)

This allows Minecraft messages to appear in your Discord channel.

1.  Open Discord and navigate to the text channel where you want Minecraft chat to appear.
2.  Click the **Edit Channel** (gear icon) -> **Integrations** -> **Webhooks**.
3.  Click **New Webhook**.
4.  Name it "Reglia" (or whatever you prefer) and copy the **Webhook URL**.
5.  In Minecraft, run:
    ```
    /setwebhook https://discord.com/api/webhooks/YOUR_WEBHOOK_URL_HERE
    ```

### Part 2: Setting up the Bot Token (Discord -> MC)

This allows Discord messages to appear in Minecraft chat.

1.  Go to the [Discord Developer Portal](https://discord.com/developers/applications).
2.  Click **New Application** and give it a name (e.g., "Reglia Bridge").
3.  Go to the **Bot** tab on the left sidebar.
4.  **CRITICAL STEP**: Scroll down to **Privileged Gateway Intents** and enable **MESSAGE CONTENT INTENT**.
    *   *Without this, the bot cannot read messages to send them to Minecraft!*
5.  Scroll back up to the "Build-A-Bot" section and click **Reset Token**.
6.  Copy the new token setup.
7.  In Minecraft, run:
    ```
    /setbottoken YOUR_BOT_TOKEN_HERE
    ```
    *(Note: Be careful not to share this token with anyone!)*

### Part 3: Channel Filtering (Optional)

If your bot is in multiple channels or you want to be extra safe:

1.  Enable **Developer Mode** in Discord (User Settings -> Advanced -> Developer Mode).
2.  Right-click the text channel you are using and click **Copy Channel ID**.
3.  In Minecraft, run:
    ```
    /setchannel YOUR_CHANNEL_ID_HERE
    ```

## ✅ Verification

Once configured:
1.  Run `/discord status` to see if your Webhook and Bot are connected.
2.  Run `/discord test` to send a message to Discord.
3.  Type in Discord and see if it appears in-game!
