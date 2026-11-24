# VersatiaTrade

A secure two-player trade window for Paper 26.3, built on the [Versatia framework](https://versatia.dev/):
items and money, every change takes both players' acceptance back, a short countdown runs before
the exchange, and the exchange moves everything or nothing. The offered items never leave their
owner's inventory before the exchange, so a player leaving, dying or walking away, the server
stopping or crashing loses nothing. Every completed trade goes into a daily log.

> In development: complete, not yet tried on a server.

Documentation (experimental): [for plugin developers](https://versatia.dev/experimental/plugins/trade)
and [for server admins](https://versatia.dev/admin/experimental/trade).

`/trade <player>` asks a player to trade; with VersatiaInteractions on the server the request goes
through its menu (Shift + right click on a player), with its waiting button. In the trade window
each player clicks the items of their inventory, shown in the lower part, to offer them, sets the
money with the buttons, and accepts; the window says at once when one of them lacks room or money.
How a trade stays safe: [docs/trade.md](docs/trade.md).

| Permission | Default | Lets the player |
|---|---|---|
| `versatia.trade.use` | everyone | trade; both players need it |
| `versatia.trade.money` | everyone | offer money |
| `versatia.trade.command.trade` | everyone | use `/trade` |

There is no game mode rule: to keep creative items out of trades, deny `versatia.trade.use` in
creative mode (LuckPerms: `/lp group default permission set versatia.trade.use false gamemode=creative`).

## Requirements

- Required: VersatiaCore.
- Optional: [VersatiaInteractions](https://github.com/VersatiaFramework/VersatiaInteractions), for a
  trade button in its menu (Shift + right click on a player) with the waiting animation; without it
  `/trade <player>` sends a request answered from the chat.
- Optional: Vault ([VaultUnlocked](https://modrinth.com/plugin/vaultunlocked) or the original) and an
  economy plugin, for money in trades; without them trades are items only.
- Optional: [CommandAPI](https://commandapi.dev/), for `/trade`.

## Events

Other plugins deny a trade or an offered item (`TradeStartEvent`, `TradeItemOfferEvent`), change how
offered items look to the partner (`TradeItemRenderEvent`) and follow how a trade ends
(`TradeCompletedEvent`, `TradeCancelledEvent`): every event, with its properties, in
[docs/events.md](docs/events.md).

## Building

Requires JDK 25 and the Versatia artifacts in the local Maven repository, VersatiaInteractions
included (see the [workspace scripts](https://github.com/VersatiaFramework)):

```bash
./gradlew build -x deployPlugin
```

The plugin JAR is `build/libs/VersatiaTrade-<version>.jar`; it carries InvUI, relocated (see
[docs/build.md](docs/build.md)). How a trade stays safe: [docs/trade.md](docs/trade.md).

## License

Apache License 2.0; see [LICENSE](LICENSE).
