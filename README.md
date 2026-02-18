# ZenithProxy Auto Chatbot Plugin

A [ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that automatically responds to keyword triggers in server chat.

## Features

- **Keyword Triggers** — Define keywords that the bot listens for in public chat messages
- **Random Responses** — Each keyword maps to a list of responses; one is picked at random
- **Cooldown** — Configurable cooldown (default 3 seconds) to prevent spam
- **Multi-Bot Support** — Configure a list of accounts to ignore (useful when multiple bots are on the same server)
- **Case-Insensitive** — Keyword matching is case-insensitive and works as substring matching

## Default Keywords

| Keyword | Responses |
|---------|-----------|
| `type shi` | `shi` |
| `6 or 7` | `67` |

You can add more keywords by editing the plugin's JSON config file (`auto-chatbot.json`) in your ZenithProxy directory.

## Commands

| Command | Description |
|---------|-------------|
| `autoChatbot` | Show current status and keyword list |
| `autoChatbot on/off` | Toggle the chatbot module |
| `autoChatbot cooldown <ms>` | Set response cooldown in milliseconds |

## Configuration

The plugin config (`auto-chatbot.json`) is auto-generated on first run:

```json
{
  "enabled": true,
  "cooldownMs": 3000,
  "ignoredAccounts": [],
  "keywords": [
    {
      "keyword": "type shi",
      "responses": ["shi"]
    },
    {
      "keyword": "6 or 7",
      "responses": ["67"]
    }
  ]
}
```

### Adding Keywords

Edit the `keywords` array in the config. Each entry has:
- `keyword` — the trigger phrase (case-insensitive substring match)
- `responses` — list of possible responses (one chosen at random)

### Ignoring Accounts

Add player names to `ignoredAccounts` to prevent the bot from responding to those accounts:

```json
{
  "ignoredAccounts": ["MyOtherBot", "AnotherBot"]
}
```

## Installation

1. Download the latest release JAR from [Releases](https://github.com/JalenHo/ZenithProxyAutoChatbot/releases)
2. Place the JAR in the `plugins` folder of your ZenithProxy installation
3. Restart ZenithProxy

## Building from Source

```bash
./gradlew build
```

The built plugin JAR will be in `build/libs/`.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.
