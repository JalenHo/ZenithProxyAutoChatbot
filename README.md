# ZenithProxy Chatbot Plugin

A [ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that automatically responds to keyword triggers in server chat.

## Features

- **Keyword Triggers** — Define keywords that the bot listens for in public chat messages
- **Random Responses** — Each keyword maps to a list of responses; one is picked at random
- **Typing Delay** — Simulates human typing speed before sending (default 200 CPM ≈ 40 WPM)
- **Cooldown** — Configurable cooldown (default 3s) to prevent spam
- **Multi-Bot Support** — Ignore list for other bot accounts on the same server
- **Case-Insensitive** — Keyword matching is case-insensitive substring matching
- **Full Command Config** — All settings manageable via proxy commands

## Commands

| Command | Description |
|---------|-------------|
| `autoChatbot status` | Show current status and all settings |
| `autoChatbot on/off` | Toggle the chatbot module |
| `autoChatbot cooldown <ms>` | Set response cooldown in milliseconds |

### Keyword Management

| Command | Description |
|---------|-------------|
| `autoChatbot keyword list` | List all keywords and responses |
| `autoChatbot keyword add <keyword> \| <response>` | Add a new keyword with a response |
| `autoChatbot keyword addResponse <keyword> \| <response>` | Add another response to an existing keyword |
| `autoChatbot keyword remove <keyword>` | Remove a keyword |

> **Note:** Use `|` to separate keyword from response, e.g. `autoChatbot keyword add type shi | shi`

### Ignored Accounts

| Command | Description |
|---------|-------------|
| `autoChatbot ignore list` | List ignored accounts |
| `autoChatbot ignore add <name>` | Add an account to ignore |
| `autoChatbot ignore remove <name>` | Remove an account from ignore |

### Typing Delay

Simulates human typing speed — delay is calculated from message length and configured CPM.

| Command | Description |
|---------|-------------|
| `autoChatbot typing on/off` | Toggle typing delay |
| `autoChatbot typing speed <cpm>` | Set typing speed (characters per minute) |

- Default: **200 CPM** (~40 WPM)
- Delay clamped between 100ms and 30s

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
  "keywords": []
}
```

All settings are also configurable via the `autoChatbot` commands above.

## Installation

1. Download the latest JAR from [Releases](https://github.com/JalenHo/ZenithProxyAutoChatbot/releases)
2. Place the JAR in the `plugins` folder of your ZenithProxy installation
3. Restart ZenithProxy

**Compatible with:** Any ZenithProxy MC version

## Building from Source

```bash
./gradlew build
```

Output JAR: `build/libs/ZenithProxyAutoChatbot-<version>.jar`

## License

Licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
