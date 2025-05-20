# VoiceChatMod - Minecraft Voice Chat with AWS API Gateway WebSocket

A Minecraft mod that enables real-time voice chat using AWS API Gateway WebSocket for communication.

## Current Status

The mod is in development with the following features implemented:

- ✅ Microphone input capture and management
- ✅ Audio playback system
- ✅ WebSocket connection to AWS API Gateway
- ✅ Configuration system
- ✅ Basic audio transmission and reception

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