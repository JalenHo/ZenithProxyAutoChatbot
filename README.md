# ZenithProxy Auto Chatbot Plugin

A [ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that automatically responds to keyword triggers in server chat.

## Features

- **Keyword Triggers** — Define keywords that the bot listens for in public chat messages
- **Random Responses** — Each keyword maps to a list of responses; one is picked at random
- **Typing Delay** — Simulates human typing speed before sending (enabled by default, 200 CPM ≈ 40 WPM)
- **Cooldown** — Configurable cooldown (default 3 seconds) to prevent spam
- **Multi-Bot Support** — Configure a list of accounts to ignore (useful when multiple bots are on the same server)
- **Case-Insensitive** — Keyword matching is case-insensitive and works as substring matching
- **Full Command Config** — All settings manageable via proxy commands, no need to edit JSON

## Default Keywords

| Keyword | Responses |
|---------|-----------|
| `type shi` | `shi` |
| `6 or 7` | `67` |

## Commands

| Command | Description |
|---------|-------------|
| `autoChatbot status` | Show current status, keywords, and all settings |
| `autoChatbot on/off` | Toggle the chatbot module |
| `autoChatbot cooldown <ms>` | Set response cooldown in milliseconds |

### Keyword Management

| Command | Description |
|---------|-------------|
| `autoChatbot keyword list` | List all keyword triggers and their responses |
| `autoChatbot keyword add <keyword> \| <response>` | Add a new keyword with its first response |
| `autoChatbot keyword addResponse <keyword> \| <response>` | Add another random response to an existing keyword |
| `autoChatbot keyword remove <keyword>` | Remove a keyword and all its responses |

> **Note:** Use `|` to separate the keyword from the response. E.g. `autoChatbot keyword add type shi | shi`

### Ignored Accounts

| Command | Description |
|---------|-------------|
| `autoChatbot ignore list` | List all ignored accounts |
| `autoChatbot ignore add <name>` | Add an account to the ignore list |
| `autoChatbot ignore remove <name>` | Remove an account from the ignore list |

### Typing Delay

The bot simulates human typing speed before sending a response. The delay is calculated from the message length and configured typing speed (characters per minute).

| Command | Description |
|---------|-------------|
| `autoChatbot typing on/off` | Toggle typing delay simulation |
| `autoChatbot typing speed <cpm>` | Set typing speed in characters per minute |

- Default: **200 CPM** (~40 WPM, average human typing speed)
- A 3-character response at 200 CPM ≈ 900ms delay
- A 20-character response at 200 CPM ≈ 6s delay
- Delay is clamped between 100ms and 30 seconds

## Configuration

The plugin config (`auto-chatbot.json`) is auto-generated on first run:

```json
{
  "enabled": true,
  "cooldownMs": 3000,
  "ignoredAccounts": [],
  "typingDelay": {
    "enabled": true,
    "charsPerMinute": 200
  },
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

While you can edit this JSON directly, all settings are also configurable via the `autoChatbot` command.

## Installation

1. Download the latest release JAR from [Releases](https://github.com/JalenHo/ZenithProxyAutoChatbot/releases)
2. Place the JAR in the `plugins` folder of your ZenithProxy installation
3. Restart ZenithProxy

**Compatible with:** MC 1.21.4, 1.21.10, 1.21.11 (any ZenithProxy version)

## Building from Source

```bash
./gradlew build
```

The built plugin JAR will be in `build/libs/`.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.
