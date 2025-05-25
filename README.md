# Minecraft Voice Chat Mod

A real-time voice chat modification for Minecraft that enables players to communicate through voice using WebSocket technology and AWS services.

## Features

- Real-time voice communication in Minecraft
- Push-to-Talk (PTT) functionality
- Echo mode for testing audio
- Automatic reconnection handling
- Distance-based voice chat (configurable)
- Volume control
- Server-side audio broadcasting
- Uses system default microphone for input

## Planned Features

- Microphone device selection UI
- Per-player volume controls
- Visual voice activity indicators
- Mute functionality for specific players
- Audio device hot-swapping support
- Advanced audio settings (noise gate, etc.)

## Requirements

- Minecraft (version supported by the mod)
- AWS Account with the following services:
  - API Gateway (WebSocket)
  - Cognito User Pool (for authentication)
- Working microphone (currently uses system default)

## Installation

1. Download the latest release from the releases page
2. Place the .jar file in your Minecraft mods folder
3. Configure the mod settings in `config/voicechatmod-common.toml`
4. Ensure your default system microphone is properly configured

## Configuration

The mod can be configured through `voicechatmod-common.toml`:

```toml
# Enable/disable voice chat
enableVoiceChat = true

# Voice chat volume (0.0 to 1.0)
defaultVolume = 0.7

# Maximum voice distance in blocks (0 for global)
maxVoiceDistance = 64

# Reconnection settings
reconnectionAttempts = 3
reconnectionDelay = 5

# WebSocket configuration
websocketStageUrl = "your-websocket-url"
websocketApiKey = "your-api-key"

# Cognito configuration
userPoolId = "your-user-pool-id"
userPoolClientId = "your-client-id"
```

## Commands

- `/vc toggle` - Toggle voice chat on/off
- `/vc volume <0.0-1.0>` - Adjust voice chat volume
- `/vc echo` - Toggle echo mode
- `/vc ping` - Test connection to voice server
- `/vc distance <blocks>` - Set maximum voice distance

## Technical Details

### Voice Processing

- Audio Format: PCM
- Encoding: Base64
- Sample Rate: 48kHz
- Bits per Sample: 16
- Channels: 1 (Mono)
- Input Device: System default microphone

### Network Protocol

The mod uses WebSocket for real-time communication with the following message formats:

#### Sending Audio
```json
{
    "action": "sendaudio",
    "data": "base64_audio_data",
    "author": "player_name",
    "timestamp": "iso8601_timestamp",
    "format": "pcm",
    "encoding": "base64"
}
```

#### Receiving Audio
```json
{
    "status": "PROCESSED",
    "message": {
        "action": "sendaudio",
        "data": {
            "audio": "base64_audio_data",
            "author": "player_name",
            "timestamp": "iso8601_timestamp"
        }
    }
}
```

### Error Handling

The mod includes robust error handling for:
- Connection failures
- Authentication issues
- Audio processing errors
- Message parsing errors
- Audio device errors

Automatic reconnection attempts are made when connection is lost, with configurable retry attempts and delays.

## Development

### Building from Source

1. Clone the repository
2. Set up your development environment
3. Run `./gradlew build`

### Architecture

The mod is structured into several key components:

- `VoiceChatMod`: Main mod class and event handling
- `VoiceGatewayClient`: WebSocket communication
- `AudioCapture`: Microphone input handling (currently uses system default)
- `AudioPlayback`: Audio output processing
- `Config`: Configuration management

## Troubleshooting

Common issues and solutions:

1. No audio transmission
   - Check if your system default microphone is properly configured
   - Test your microphone in Windows Sound Settings or similar
   - Verify PTT key is correctly set
   - Check WebSocket connection status
   - Ensure microphone permissions are granted to Minecraft

2. Connection issues
   - Verify AWS credentials
   - Check network connectivity
   - Confirm WebSocket URL is correct

3. Audio quality issues
   - Check system default microphone settings
   - Adjust microphone boost/gain in system settings
   - Check network latency
   - Verify audio device configuration

4. Microphone not working
   - Set desired microphone as system default in your OS settings
   - Restart Minecraft after changing default audio device
   - Check microphone privacy settings in your OS

## License

[Your License Here]

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting pull requests.

## Credits

- Developer: Diego Sandoval