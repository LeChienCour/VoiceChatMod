# VoiceChatMod - Minecraft Voice Chat with AWS AppSync

A Minecraft mod that enables real-time voice chat using AWS AppSync for communication.

## Current Status

The mod is in development with the following features implemented:

- ✅ Microphone input capture and management
- ✅ Audio playback system
- ✅ Basic WebSocket connection to AWS AppSync
- ✅ Configuration system
- ❌ Working AppSync mutations/subscriptions (In Progress)

## Setup

### Prerequisites

- Minecraft 1.21.4
- NeoForge
- AWS Account with AppSync API configured
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

[aws_appsync]
# Your AppSync API URL
voice_server_url = "https://your-appsync-endpoint.appsync-api.region.amazonaws.com/graphql"
# Your AppSync API Key
api_key = "your-api-key-here"
# AWS Region (e.g., us-east-1)
api_region = "your-region"
```

### AWS AppSync Schema Requirements

The mod expects the following GraphQL schema:

```graphql
type Audio {
  format: String!
  encoding: String!
  data: String!
  author: String!
}

type Mutation {
  sendAudio(
    channel: String!
    format: String!
    encoding: String!
    data: String!
    author: String!
  ): Audio
}

type Subscription {
  onReceiveAudio(channel: String!): Audio
    @aws_subscribe(mutations: ["sendAudio"])
}
```

## Development Status

### Working Features
- Microphone detection and initialization
- Audio capture in PCM format (16kHz, 16-bit mono)
- Basic WebSocket connection to AppSync
- Configuration management
- In-game commands (/vc test, /vc toggle)

### Current Issues
1. AppSync GraphQL Schema Mismatch:
   - The current mutation format doesn't match the server schema
   - Receiving "Unknown field argument method" error
   - Subscription errors with "unknown not supported through the realtime channel"

2. WebSocket Connection:
   - Connection establishes but encounters protocol errors
   - Need to verify subscription format

### Next Steps

1. **AppSync Schema Updates**:
   - Remove the `method` field from mutations
   - Verify subscription protocol compatibility
   - Update mutation format to match server expectations

2. **Error Handling**:
   - Implement better recovery for WebSocket disconnections
   - Add user feedback for connection issues
   - Improve error logging and diagnostics

3. **Audio Processing**:
   - Implement audio compression (currently using PassthroughEncoder)
   - Add voice activity detection
   - Implement spatial audio based on player positions

4. **Testing**:
   - Create comprehensive test suite
   - Add integration tests for AppSync communication
   - Test with multiple players

## Debugging

### Common Issues

1. **Configuration Issues**:
   ```log
   [ERROR] Invalid region extracted from URL: amazonaws
   ```
   Solution: Ensure `api_region` in config matches the region in your AppSync URL

2. **AppSync Connection**:
   ```log
   [ERROR] Unknown field argument method @ 'sendAudio'
   ```
   Solution: Update mutation format to match server schema

### Debug Commands

- `/vc test` - Test AppSync connectivity
- `/vc toggle` - Toggle voice chat on/off
- `/vc debug` - Show debug information

## For AI Assistants

When working on this project, focus on:

1. GraphQL Schema Alignment:
   - Current schema mismatch in mutations and subscriptions
   - Need to remove `method` field and verify field arguments

2. WebSocket Protocol:
   - Check subscription message format
   - Verify real-time channel support

3. Audio Processing:
   - Currently using uncompressed PCM
   - Need to implement proper codec

Key files:
- `AppSyncClientService.java`: WebSocket and GraphQL handling
- `MicrophoneManager.java`: Audio capture
- `AudioManager.java`: Audio playback
- `Config.java`: Configuration management

Current Priority: Fix AppSync schema mismatch and subscription issues.