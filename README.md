# Civilization

Civilization is a turn-based strategy game built with Java 17 and Swing for an Advanced Programming course. Explore a hex map, gather resources, build settlements, train units, and expand your territory. Play locally or join a multiplayer match with friends.

## Features

- Hex maps with fog of war, resource gathering, construction, and tactical combat
- Technology, production queues, trade, diplomacy, seasons, and natural disasters
- Single-player manual saves and autosaves
- Multiplayer lobbies with map selection, synchronized turns, text chat, and reconnection
- Player-versus-player combat, trade agreements, and alliances

## Getting Started

You’ll need JDK 17 or later and Maven.

From the repository root, build the project and run its test suites.

```bash
mvn test
```

### Single-player

Launch the desktop client and start a new game from the main menu.

```bash
./scripts/run-client.sh
```

### Multiplayer

Start the server in a separate terminal.

```bash
./scripts/run-server.sh
```

Launch the client once for each player.

```bash
./scripts/run-client.sh
```

Open **Multiplayer** from the main menu and connect to `127.0.0.1:7777` when playing on the same machine. For LAN play, enter the server machine’s local IP address and TCP port `7777`. The default UDP heartbeat port is `7778`.

## Architecture

The multiplayer server owns the game state, validates player actions, and sends each client a view filtered by fog of war. The Swing client handles input and rendering. The network layer uses JSON messages over TCP for game commands and state updates, with UDP heartbeats for connection monitoring.
