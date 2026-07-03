# ThrottleGuard

A Minecraft Fabric mod that provides real-time CPU monitoring HUD and automatic Vault opening functionality.

> **Note:** This mod is generated and written by **DeepSeek AI**.

---

## Features

### 1. CPU Monitoring HUD
- Displays real-time CPU frequency (GHz)
- Shows current CPU load percentage
- Visual frequency bar with color indication
- **Throttling detection** – turns red when CPU frequency drops below 85% of max
- HUD visibility automatically follows F3 debug menu:
  - F3 menu closed → HUD visible
  - F3 menu open → HUD hidden

### 2. Auto Vault Opener
- Automatically scans for **Ominous Vaults** within a 4-block radius
- Opens vaults when they display a **Heavy Core**
- Smart delay – waits 12 ticks (~0.6 seconds) before opening
- Built-in cooldown to prevent spam
- Works with any trial key in your main hand

---

## Requirements

- **Minecraft:** 1.21.11+
- **Fabric Loader:** 0.15.0+
- **Fabric API:** Latest version

---

## Installation

1. Download the latest `throttleguard-*.jar` from the Releases page
2. Place the `.jar` file into your `mods` folder
3. Launch Minecraft with Fabric

---

## Usage

### HUD Controls
- **F3** – Opens/closes debug menu, automatically toggles HUD visibility

### Auto Vault Opening
- Simply hold a **Trial Key** (or any key) in your main hand
- Stand within 4 blocks of an Ominous Vault
- The mod will automatically open it when a Heavy Core is displayed

---

## How It Works

### CPU Monitor
- Uses **OSHI** to read hardware data directly from the system
- Updates every **1 second** via a scheduled background task
- Smoothing window averages CPU load over 5 samples for stable readings
- Throttling threshold: 85% of max frequency
- The mod continuously monitors CPU performance in real-time to detect throttling events

### Vault Opener
- Scans for vaults **every tick** (20 times per second)
- Reads vault NBT data to detect:
  - Ominous status
  - Displayed item (Heavy Core detection)
- Sends a block interaction packet without requiring line-of-sight

---

## Configuration

No configuration file is required. All settings are hardcoded for simplicity:

| Setting | Default Value | How to Change |
|---------|---------------|---------------|
| **CPU Update Interval** | 1 second | In `onInitialize()`: `SCHEDULER.scheduleAtFixedRate(..., 0, X, TimeUnit.SECONDS)` |
| **Vault Scan Frequency** | Every tick | In `registerVaultHelper()`: add `if (scanCounter % X != 0) return;` |
| **Heavy Core Display Delay** | 12 ticks (~0.6s) | In `autoOpenVault()`: change `if (displayDuration >= X)` |
| **Vault Cooldown** | 10 ticks (0.5s) | Change `COOLDOWN_TICKS = X` at class top |
| **Scan Radius** | 4 blocks | Change `SCAN_RADIUS = X` at class top |

### Detailed Modification Guide

**1. CPU Monitoring Frequency**

In `onInitialize()` method:
```java
SCHEDULER.scheduleAtFixedRate(() -> { ... }, 0, 1, TimeUnit.SECONDS);
```
Change the `1` to your desired seconds. Use `TimeUnit.MILLISECONDS` for sub-second intervals.

**2. Vault Scan Frequency**

In `registerVaultHelper()` method:
```java
scanCounter++;
if (scanCounter % 2 != 0) return; // Runs every 2 ticks (0.1s)
// Change 2 to any number:
// - 1 = every tick (default)
// - 5 = every 5 ticks (0.25s)
// - 10 = every 10 ticks (0.5s)
// - 20 = every 20 ticks (1s)
```

**3. Heavy Core Display Delay**

In `autoOpenVault()` method:
```java
if (displayDuration >= 12) { // 12 ticks = 0.6 seconds
    openVault(client, pos);
}
```
Change `12` to your desired tick count:
- `5` = 0.25 seconds (faster)
- `20` = 1 second
- `30` = 1.5 seconds

**4. Vault Cooldown**

At class top:
```java
private static final int COOLDOWN_TICKS = 10; // Change this value
```
- `5` = 0.25 seconds
- `10` = 0.5 seconds (default)
- `20` = 1 second
- `0` = no cooldown

**5. Scan Radius**

At class top:
```java
private static final int SCAN_RADIUS = 4; // Change this value
```
Range: 1-16 blocks (higher values = more server load)

---

## Commands

This mod has no commands – everything runs automatically.

---

## License

MIT License

---

## Credits
  
- **Code Generation:** DeepSeek AI
- **Integration & Testing:** [brSucfkevin]

---

## Disclaimer

This mod is provided "as is" without any warranties. Use at your own risk.
