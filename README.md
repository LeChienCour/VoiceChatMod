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
- ✅ Ping/Pong system for connection health monitoring
- ✅ Automatic reconnection handling

### Known Issues & Pending Tasks
- ❌ WebSocket message format issues (binary data being sent as text)
- ❌ Microphone selection not implemented (currently uses default device)
- ❌ Excessive logging needs cleanup
- ❌ Echo mode (hearing own voice) not working consistently
- ❌ Player name not being set correctly in audio transmission

### Recent Changes
- Added binary message handling for audio data
- Implemented periodic ping system (30-second intervals)
- Enhanced connection state tracking
- Improved error handling and logging structure
- Fixed duplicate ping issues

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

1. Manual configuration in `runs/client/config/voicechatmod-common.toml`:
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

- `/vc ping` - Test WebSocket connection
- `/vc micstart` - Start microphone capture
- `/vc micstop` - Stop microphone capture
- `/vc playloopback` - Play back captured audio
- `/vc echo on/off/status` - Control/check echo mode
- `/vc updateconfig` - Update configuration from SSM

### Controls

- Push-to-Talk: Default key 'V' (configurable in game settings)

## Development Roadmap

1. Fix WebSocket message format issues:
   - Properly handle binary audio data
   - Implement correct JSON message structure
   - Clean up logging system

2. Implement microphone selection:
   - Add UI for microphone device selection
   - Save/load microphone preferences
   - Handle device changes

3. Audio System Improvements:
   - Fix echo mode functionality
   - Implement proper player name handling
   - Add distance-based audio attenuation
   - Optimize audio quality and latency

4. Quality of Life:
   - Add volume controls per player
   - Implement mute functionality
   - Add visual indicators for voice activity

### WebSocket Message Structure

```json
// Outgoing message (client to server)
{
  "action": "sendaudio",
  "data": "base64_audio_data",
  "format": "pcm",
  "encoding": "base64",
  "author": "player_name",
  "timestamp": "iso8601_timestamp"
}

// Incoming message (server to client)
{
  "action": "audio",
  "data": {
    "audio": "base64_audio_data",
    "format": "pcm",
    "encoding": "base64",
    "author": "player_name",
    "timestamp": "iso8601_timestamp"
  }
}

// Ping message
{
  "action": "ping",
  "data": {
    "timestamp": "iso8601_timestamp"
  }
}
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.