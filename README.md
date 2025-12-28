# Reglia

A Minecraft mod for Forge 1.20.1.

## Description

Reglia is a powerful Minecraft mod that allows a discord <-> minecraft chat bridge.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0 or higher
- Java 17

## Setup

1. Clone the repository
2. Open the project in your IDE (IntelliJ IDEA or Eclipse recommended)
3. Import as a Gradle project
4. Run `gradlew genIntellijRuns` (IntelliJ) or `gradlew genEclipseRuns` (Eclipse)

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/`.

## Running

```bash
# Run Minecraft client with the mod
./gradlew runClient

# Run Minecraft server with the mod
./gradlew runServer
```

## Project Structure

```
Reglia/
├── build.gradle                 # Main build script
├── settings.gradle              # Gradle settings
├── gradle.properties            # Mod properties and versions
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/reglia/
│       │       ├── Reglia.java  # Main mod class
│       │       └── Config.java  # Configuration handler
│       └── resources/
│           ├── META-INF/
│           │   └── mods.toml    # Mod metadata
│           └── pack.mcmeta      # Resource pack info
└── gradle/
    └── wrapper/                 # Gradle wrapper files
```

## Configuration

Configuration options can be found in the game's config folder after first run.

## License

All Rights Reserved

## Credits

- Forge Team
- Minecraft Modding Community
