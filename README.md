# ZenithProxy Auto Chatbot Plugin

A [ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that automatically responds to keyword triggers and AI-powered conversations in Minecraft chat.

## Features

### Keyword Auto-Chat
- **Keyword Triggers** — Define keywords that the bot listens for in public chat messages
- **Random Responses** — Each keyword maps to a list of responses; one is picked at random
- **Cooldown** — Configurable cooldown (default 3s) to prevent spam
- **Case-Insensitive** — Keyword matching is case-insensitive substring matching

### AI Chatbot
- **AI DM Responses** — Bot responds to `/msg` whispers automatically via AI
- **AI Server Chat** — Bot reads public chat and responds when trigger keywords match (e.g. its name)
- **Player Memory** — Remembers conversations with individual players
- **Context Awareness** — Includes recent server chat as context for better responses
- **Customizable Personality** — Configurable system prompt for the bot's behavior

### General
- **Typing Delay** — Simulates human typing speed before sending (default 200 CPM ≈ 40 WPM)
- **Multi-Bot Support** — Ignore list for other bot accounts on the same server
- **Independent Toggles** — Keyword auto-chat and AI work independently — use one, both, or neither
- **Codex CLI Config** — Load model, API key, and reasoning effort from `config.toml` + `auth.json`
- **Full Command Config** — All settings manageable via proxy commands

## Commands

### General

| Command | Description |
|---------|-------------|
| `autoChatbot status` | Show current status and all settings |
| `autoChatbot on/off` | Toggle keyword auto-chat |
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

### AI Commands

| Command | Description |
|---------|-------------|
| `autoChatbot ai on/off` | Toggle AI chatbot (independent from keyword auto-chat) |
| `autoChatbot ai serverChat on/off` | Toggle AI reading/responding to public chat |
| `autoChatbot ai apiKey <key>` | Set OpenAI API key |
| `autoChatbot ai model <model>` | Set AI model (e.g. `gpt-4o-mini`, `gpt-5.5`) |
| `autoChatbot ai prompt <system-prompt>` | Set the AI's personality/behavior |
| `autoChatbot ai contextLength <count>` | Number of recent chat messages for context (0-100) |
| `autoChatbot ai reasoningEffort <level>` | Set reasoning effort: `low`, `medium`, `high`, `xhigh` |
| `autoChatbot ai loadConfig` | Load settings from Codex config files |
| `autoChatbot ai triggerKeyword add <keyword>` | Add a keyword that triggers AI in public chat |
| `autoChatbot ai triggerKeyword remove <keyword>` | Remove a trigger keyword |
| `autoChatbot ai triggerKeyword list` | List all trigger keywords |
| `autoChatbot ai memory clear <player>` | Clear conversation memory for a player |

## Codex CLI Config Support

You can configure the bot by placing Codex CLI config files in the plugin data folder:

```
plugins/auto-chatbot/codex/config.toml
plugins/auto-chatbot/codex/auth.json
```

**config.toml:**
```toml
model_provider = "OpenAI"
model = "gpt-5.5"
model_reasoning_effort = "xhigh"

[model_providers.OpenAI]
name = "OpenAI"
base_url = "[https://api.openai.com]"
wire_api = "responses"
requires_openai_auth = true
```

**auth.json:**
```json
{
  "OPENAI_API_KEY": "sk-your-api-key-here"
}
```

The plugin auto-loads these files on startup, or use `/autoChatbot ai loadConfig` to reload manually.

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
  "keywords": [],
  "aiEnabled": false,
  "aiServerChatEnabled": false,
  "openaiApiKey": "",
  "openaiModel": "gpt-4o-mini",
  "openaiBaseUrl": "https://api.openai.com",
  "openaiReasoningEffort": "",
  "aiSystemPrompt": "You are a helpful assistant responding in Minecraft chat. Keep responses brief and conversational. You have memory of previous conversations.",
  "aiChatContextLength": 10,
  "aiTriggerKeywords": [],
  "aiMemoryPath": "plugins/auto-chatbot/memory/"
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
