# StopSpam Plugin
**StopSpam** is a Minecraft Paper 1.21.4 plugin that prevents chat spam by implementing message cooldowns, similarity detection, and escalating timeout punishments for repeat offenders.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/stopspam/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Configuration
In `config.yml`, you can configure:
```yaml
# Minimum time (in milliseconds) between messages
message-cooldown: 280

# Message similarity detection
similarity:
  enabled: true
  # Threshold (0.0-1.0) - higher means more similar messages will be blocked
  # 0.9 means 90% similar content will trigger spam detection
  threshold: 0.9
  # Number of recent messages to check for similarity
  recent-messages-to-check: 15
  # Only check messages within this time window (in seconds)
  time-window: 20
  # Number of similar messages required before triggering spam detection
  repetition-threshold: 4

# Warning messages shown to spammers (randomly selected)
warning-messages:
  - "Silence is a virtue"
  - "Even a fool who keeps silent is considered wise."
  - "Speak only if it improves upon the silence."
  - "Brevity is the soul of wit."
  - "Wise men speak because they have something to say; fools speak because they have to say something."

# Timeout durations (in seconds) for repeated violations
timeouts:
  first: 10
  second: 20
  third: 30
  fourth: 100
  fifth: 300
  sixth: 600
  seventh: 1200
  eighth: 1800
```

### Configuration Options Explained

#### Message Cooldown
- `message-cooldown`: The minimum time (in milliseconds) allowed between player messages. Default: 280ms.

#### Similarity Detection
- `similarity.enabled`: Enable/disable message similarity detection.
- `similarity.threshold`: How similar messages need to be to trigger spam detection (0.0-1.0). Higher values are more lenient.
- `similarity.recent-messages-to-check`: Number of recent messages to compare against.
- `similarity.time-window`: Only check messages within this time window (in seconds).
- `similarity.repetition-threshold`: Number of similar messages required before triggering spam detection.

#### Timeout System
Timeout durations escalate with repeated violations, from 10 seconds to 30 minutes.

## Commands
`/stopspam reload`: Reloads the plugin configuration (requires stopspam.admin permission)

## Permissions
`stopspam.admin`: Allows reloading the plugin configuration (default: op)

## Support Me
Donations will help me with the development of this project.

One-off donation: https://ko-fi.com/lolwhatyesme

Patreon: https://www.patreon.com/lolwhatyesme
