# VoiceChatMod - Minecraft Voice Chat with AWS API Gateway WebSocket

A Minecraft mod that enables real-time voice chat using AWS API Gateway WebSocket for communication.

## Current Status

The mod is in development with the following features:

### Working Features
- ✅ Microphone input capture and management
- ✅ Audio playback system
- ✅ WebSocket connection to AWS API Gateway
- ✅ Configuration system with AWS SSM Parameter Store integration
- ✅ Basic audio transmission
- ✅ Push-to-Talk functionality
- ✅ AWS credentials validation

### Known Issues
- ❌ Echo mode (hearing own voice) not working despite being enabled
- ❌ Audio reception from other players not implemented yet
- ❌ Player name not being set correctly in audio transmission (shows as "Unknown")

## Setup

### Prerequisites

- Minecraft 1.21.4
- NeoForge
- AWS Account with:
  - API Gateway WebSocket API configured
  - SSM Parameter Store parameters set up
  - Valid AWS credentials in ~/.aws/credentials

### Configuration

The mod supports two ways of configuration:

1. Manual configuration in `config/voicechatmod-common.toml` or `runs/client/config/voicechatmod-common.toml`:
```toml
# Enable/disable voice chat functionality
enableVoiceChat = true
# Default volume for voice chat (0.0 to 1.0)
defaultVolume = 0.7
# Maximum distance for voice transmission (in blocks)
maxVoiceDistance = 64
# Number of reconnection attempts
reconnectionAttempts = 3
# Delay between reconnection attempts (in seconds)
reconnectionDelay = 5
# WebSocket Gateway URL
websocketStageUrl = "wss://your-api-id.execute-api.region.amazonaws.com/stage"
# API key for authentication
websocketApiKey = "your-api-key-here"
# Cognito User Pool ID
userPoolId = "your-user-pool-id"
# Cognito User Pool Client ID
userPoolClientId = "your-user-pool-client-id"
```

2. Automatic configuration using AWS SSM Parameter Store:
   - Use the `/vc updateconfig` command in-game to fetch parameters from SSM
   - Required SSM parameters:
     - `/game-server/test/websocket/stage-url`
     - `/game-server/test/websocket/api-key`
     - `/game-server/test/cognito/user-pool-id`
     - `/game-server/test/cognito/user-pool-client-id`

### Commands

- `/vc micstart` - Start microphone capture
- `/vc micstop` - Stop microphone capture
- `/vc playloopback` - Play back captured audio
- `/vc echo on/off/status` - Control/check echo mode
- `/vc updateconfig` - Update configuration from SSM

### Controls

- Push-to-Talk: Default key 'V' (configurable in game settings)

## Development Status

The mod is currently in active development. Next steps:
1. Fix echo mode functionality
2. Implement proper audio reception from other players
3. Fix player name transmission
4. Add distance-based audio attenuation
5. Implement proper error handling for WebSocket disconnections

## Setup

### Prerequisites

- Minecraft 1.21.4
- NeoForge
- AWS Account with API Gateway WebSocket API configured
- Java Development Kit (JDK) 17 or higher

### Configuration

1. Create/edit `config/voicechatmod-common.toml`:
```toml
[voice_chat]
# Enable/disable voice chat functionality
enabled = true
# Default volume for voice chat (0.0 to 1.0)
default_volume = 0.7
# Maximum distance for voice transmission (in blocks)
max_voice_distance = 64

[aws_gateway]
# Your API Gateway WebSocket URL
voice_gateway_url = "wss://your-api-id.execute-api.region.amazonaws.com/stage"
# Optional API key for authentication
api_key = "your-api-key-here"
# Number of reconnection attempts
reconnection_attempts = 3
# Delay between reconnection attempts (in seconds)
reconnection_delay = 5
```

### AWS API Gateway WebSocket Requirements

The mod expects the following WebSocket message structure:

```json
// Outgoing message (client to server)
{
  "action": "sendaudio",
  "channel": "string",
  "format": "string",
  "encoding": "string",
  "data": "string", // Base64 encoded audio data
  "author": "string",
  "timestamp": "string",
  "context": "string"
}

// Incoming message (server to client)
{
  "action": "audio",
  "data": "string", // Base64 encoded audio data
  "format": "string",
  "encoding": "string",
  "author": "string",
  "timestamp": "string",
  "context": "string"
}
```

## Development Status

### Working Features
- Microphone detection and initialization
- Audio capture in PCM format (16kHz, 16-bit mono)
- WebSocket connection to API Gateway
- Configuration management
- In-game commands (/vc test, /vc toggle)

### Current Issues
- None reported

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.