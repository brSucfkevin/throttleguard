# ThrottleGuard

A Minecraft client mod for real-time CPU frequency and load monitoring with in-game HUD display.

## Features

- 📊 **Real-time CPU Monitoring**: Display current CPU frequency (GHz) and maximum frequency
- 📈 **CPU Load Display**: Show current CPU usage as percentage
- 🚨 **Throttling Detection**: Automatically display "THROTTLING!" warning when CPU frequency drops below 85% of maximum
- 📉 **Visual Progress Bar**: Intuitive display of current frequency ratio to maximum frequency
- ⌨️ **F3 Menu Integration**: Automatically hides HUD when F3 menu is open to avoid cluttering debug information
- 🔐 **Vault Auto-Open**: Automatically detects and opens ominous vaults displaying Heavy Core

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.15.0 or higher
- Fabric API 0.141.4+1.21.11 or higher
- Java 17 or higher

## Installation

1. Download the mod JAR file
2. Place it in your Minecraft `mods` folder
3. Launch the game with Fabric Loader

## Usage

Once installed, the CPU HUD will appear in the top-right corner of your screen by default.

### Controls

- **F3**: Toggle HUD visibility (hides when F3 debug menu is open)

### HUD Information

The HUD displays the following information:
- **Frequency**: Current and maximum CPU frequency in GHz
- **Load**: Current CPU usage percentage
- **Status Bar**: Visual representation of frequency ratio
  - Green: Normal operation
  - Red: Throttling detected

## ⚠️ Important Note

**If you are using any F3-enhancing mods (such as BetterF3, etc.), the CPU HUD may not display correctly when the F3 menu is open.** This is due to how these mods modify the debug screen rendering.

If you encounter this issue, you can:
1. Close the F3 menu to see the HUD
2. Temporarily disable your F3 mod if you need to use both simultaneously
3. Configure your F3 mod to not override the debug HUD behavior

## Building from Source

1. Clone the repository
2. Run `./gradlew build`
3. The compiled JAR will be in `build/libs/`

## License

This project is licensed under the MIT License.

## Credits

- Built with Fabric Mod Development Kit
- CPU monitoring powered by OSHI (Operating System and Hardware Information)
