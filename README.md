# StopSpam Plugin
**StopSpam** is a Minecraft Paper 1.21.4 plugin that prevents chat spam by implementing message cooldowns and escalating timeout punishments for repeat offenders.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/stopspam/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Configuration
In `config.yml`, you can configure:
```yaml
# Minimum time (in milliseconds) between messages
message-cooldown: 250

# Warning messages shown to spammers (randomly selected)
warning-messages:
  - "Shut up."
  - "Message throttled. Shut up."
  - "Stop spamming."

# Timeout durations (in seconds) for repeated violations
timeouts:
  first: 10
  second: 30
  third: 60
  fourth: 300  # 5 minutes
  fifth: 1800  # 30 minutes
```

## Commands
`/stopspam reload`: Reloads the plugin configuration (requires stopspam.admin permission)

## Permissions
`stopspam.admin`: Allows reloading the plugin configuration (default: op)

## Support Me
Donations will help me with the development of this project.

One-off donation: https://ko-fi.com/lolwhatyesme

Patreon: https://www.patreon.com/lolwhatyesme
