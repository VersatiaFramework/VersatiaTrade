# Trade

## Nothing leaves an inventory before the exchange

An offer names kinds of items and how many of each, with an amount of money: "10 diamonds", not
"slot 3". A kind is every stack similar to one item (same type and components: durability,
enchantments, name, contents of a bundle or a shulker box). The items stay in the inventory, the
trade window shows the player a copy of it with the offered items taken out, so while it is open
nothing can be moved, dropped or used, and the exchange takes the items only at the end, from
wherever they lie then. So there is nothing to give back when a trade ends otherwise: a player
leaving, dying or walking away, the server stopping, even a crash.

An offer never holds more of a kind than the player has, nor more stacks than its part of the
window shows (`Offer.add`).

## Which stacks are the same item

By default two stacks are the same item when Bukkit finds them similar. A plugin whose items change
while they lie in an inventory, a lore rendered for the holder say, contributes an `ItemIdentity`
with `@TradeItemIdentity` (the same pattern as `@InteractionAction`: the annotation inherits
`@ConditionalOnPlugin("VersatiaTrade")` and `@LocalBean`, its processor lives here):

```kotlin
@TradeItemIdentity
class RpgItemIdentity(private val items: Items) : ItemIdentity {
    override fun of(item: ItemStack) = items.instanceIdOf(item)   // null: not an item of this plugin
}
```

`ItemIdentities` asks the identities in the order their plugins started: the first that knows either
of two stacks decides by comparing the two values, a stack it knows and one it does not being
different items; when none knows them, Bukkit's similarity decides. `fitTo` shows each offered kind
as its stacks look now. The exchange gives the stacks it took, as they lay in the inventory, never
copies of the first one offered, so an item keeps its data whatever an identity counts as the same;
received stacks still join others only when Bukkit finds them similar, as the inventory requires.

## Where a trade stands

`TradeSession` holds the two offers and the acceptance, and works out the status from the players'
storages and balances whenever an offer changes and once per second:

| Status | Meaning | Shown as |
|---|---|---|
| `Empty` | nothing offered | accepting is not possible |
| `Ready` | the exchange can happen as it stands | accepting is possible |
| `NoRoom(side, items)` | that side has no room for that many of the other side's items | both windows say who needs room for how many items |
| `NoMoney(side)` | that side offers more money than it has | both windows say so |
| `NoEconomy` | money is offered, but the economy is gone | both windows say so |
| `Short(side)` | an offer names items the player no longer has | until `fitTo` brings the offer in line |

Accepting is possible only while the status is `Ready`. Any change of either offer, any status
other than `Ready` and any shortfall take both acceptances and the countdown back, so a player who
filled their inventory, spent their money or lost an offered item after accepting must accept
again. `fitTo` caps every offered kind at what the player still holds and reports what went missing,
so the player is told.

## Acceptance and countdown

When both accepted, a countdown starts (`trade.countdown`, 3 s); when it ends, the exchange runs. A
player who clicks accept again takes theirs back, which stops the countdown.

## The exchange

`Exchange.run` does everything in one go on the server thread:

1. `ExchangePlan.of` works on copies of both storages. Each side's offered items leave its copy,
   taken from the last slot backwards so the hotbar keeps its stacks the longest, as the window
   shows; a kind that is no longer there in full gives `Short`. Then each copy receives the other
   side's items as `Inventory.addItem` would place them: similar stacks first, up to their stack
   size, then the first empty slots. The room a side's own offer frees counts. Items that do not
   fit give `NoRoom`.
2. Money: each side must have what it offers; the money moves in four steps, both withdrawals
   first. A step the economy refuses, or one that throws, undoes the steps before it in reverse
   order (`NoMoney` for a refused withdrawal, `EconomyFailed` for a refused deposit); an undo that
   fails is logged as severe, naming the players.
3. Only then both storages are set to the planned contents. Nothing else runs in between, so either
   both sides get everything or nothing moves.

## Threats

| What can happen | How it is handled | Where |
|---|---|---|
| A player moves offered items to other slots | offers name kinds, not slots; taken wherever they lie | `Offer`, `ExchangePlan` (tested) |
| A player loses offered items before the exchange (a command, another plugin, a creative-mode slot change) | `fitTo` shrinks the offer, takes the acceptances back, the player is told; the exchange checks again (`Short`) | `TradeSession`, `ExchangePlan` (tested) |
| An offered item changes (durability, lore rewritten by another plugin) | it is another kind now: treated as lost, never duplicated; a plugin rendering its items gives them an `ItemIdentity`, and they stay offered | `Offer`, `ItemIdentities` (tested) |
| Stack sizes change, or the receiver has partial stacks | room is planned with the stack sizes of the moment, partial stacks filled first | `ExchangePlan` (tested) |
| Too little room on either side | `NoRoom` shown during the trade, accepting blocked; after accepting, the acceptances go | `TradeSession` (tested) |
| A player spends money elsewhere during the trade | `NoMoney` shown, accepting blocked, checked again at the exchange | `TradeSession`, `Exchange` (tested) |
| The economy fails midway, or throws | earlier steps undone, no item moves | `Exchange` (tested) |
| The economy plugin registers late or goes away | looked up at every use; `NoEconomy` | `Money`, `TradeSession` (tested) |
| A player dies, quits, is kicked, teleports or walks away | the trade ends; nothing to give back | `TradeSessions` (events) |
| A player closes the window, or another plugin opens an inventory for them | the trade ends | `TradeWindows`, `TradeSessions` |
| A player picks up items during the trade | pickups are blocked while trading; the status is worked out again anyway | `TradeSessions` (events) |
| Shift, number-key, drag, double-click, off-hand or bundle clicks in the window | the lower part is a copy of the inventory: clicks only change the offer; only left and right clicks do anything | `TradeWindows` |
| A player loses `versatia.trade.use` or `versatia.trade.money` during the trade | checked when accepting and before the exchange: the trade ends | `TradeSessions` |
| A crash right after the exchange, before the server saved the players | both players' data are saved at once after the exchange; then `TradeCompletedEvent` lets a plugin keeping data of its own save it | `TradeSessions` |
| The countdown ends while a window closes in the same tick | one thread, and a trade that ended runs no exchange | `TradeSessions` |
| Items another plugin forbids to trade (bound to a player, quest items) | `TradeItemOfferEvent`, denied: the item stays out of the offer | `TradeSessions` |
| A trade another plugin forbids (combat, an arena) | `TradeStartEvent`, denied: the trade does not open | `TradeSessions` |

## Driven by events

`TradeSessions` acts on events and polls nothing: the windows' clicks; `PlayerQuitEvent`,
`PlayerDeathEvent`, `PlayerMoveEvent` when a trader enters another block, `PlayerTeleportEvent`
(checked a tick later, where the player landed) and `PlayerChangedWorldEvent`, which end the trade
when the two are out of reach; `PlayerAttemptPickupItemEvent`, cancelled for traders; and
`PlayerInventorySlotChangeEvent`, after which the trade is worked out again at the next tick,
once however many slots changed. Money and permissions have no events: they are checked when an
offer changes, when a player accepts and before the exchange. While both accepted, the countdown
runs as one coroutine of the trade, which redraws the windows as the seconds go and then
exchanges; any change cancels it. After an exchange both players' data are saved at once
(`Player.saveData`), so a crash before the next autosave cannot give the items back while the
economy kept a payment. The events VersatiaTrade fires for other plugins: [events.md](events.md).

## The window

Each player's window shows, in its upper part, their offer and the partner's (16 stacks each; the
partner's items are copies, passed through `TradeItemRenderEvent` for the viewer), the money
buttons (`money.steps`; a left click adds, a right click takes back, Shift ten times as much, never
beyond the balance), the partner's money, the accept button and the status. Its lower part is a copy
of the inventory with the offered items taken out the way the exchange takes them: a left click
offers the stack, a right click one item. Closing the window ends the trade.

## Requests

`/trade <player>` asks for a trade. With VersatiaInteractions on the server it sends the request of
its menu (`TradeInteraction`, `@InteractionAction("trade")`), with its waiting button and its
answers; the trade's engagements reach the menu through `Interactions.refresh`. Without it,
`ChatTradeRequests` sends a chat line whose buttons run `/trade accept <player>` and
`/trade decline <player>`, waits `request.timeout`, and two requests to each other accept each
other. Either way the trade is checked again when it opens.

## Money

`Money` finds the economy whenever a trade needs it, since an economy plugin may register after
this plugin started:

- without the plugin `Vault` (VaultUnlocked keeps that name) the bean is `NoMoney`: trades are items
  only;
- with it, `VaultMoney` uses VaultUnlocked's API (`net.milkbowl.vault2.economy`, `BigDecimal`) when
  its classes exist and an economy provides it, else the older Vault API (`double`), which
  VaultUnlocked deprecates but most economy plugins still provide. Each API is touched only by its
  own class (`UnlockedFunds`, `LegacyFunds`), loaded once it is known to exist, so the original
  Vault, without VaultUnlocked's classes, works too.

## Permissions

`versatia.trade.use` (both players), `versatia.trade.money` (to offer money) and
`versatia.trade.command.trade`. There is no game mode rule of its own: a server keeping creative
items out of trades denies `versatia.trade.use` in creative mode, with LuckPerms
`/lp group default permission set versatia.trade.use false gamemode=creative`.

## The log

`TradeLog` writes every completed trade as one line in `logs/trades-<date>.log` of the data folder,
on a thread of its own in the order of the trades; the lines still waiting are written when the
plugin stops.

```
2026-11-22T00:45:12+01:00 Alice (uuid) gave [10 × minecraft:diamond] and 30 | Bob (uuid) gave [1 × minecraft:iron_sword "Fang"] and 50
```
