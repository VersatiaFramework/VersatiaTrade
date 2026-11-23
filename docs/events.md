# Events

Every event VersatiaTrade fires, all in the package `dev.versatia.trade.event` and on the server
thread. An event fired before something happens is named in the present and may be denied; one
fired after it is named in the past and only informs.

| Event | When | A listener can |
|---|---|---|
| [`TradeStartEvent`](#tradestartevent) | before a trade opens | deny it, with a reason both players read |
| [`TradeItemOfferEvent`](#tradeitemofferevent) | a player clicks an item to offer it | deny it, with a reason the player reads |
| [`TradeItemRenderEvent`](#tradeitemrenderevent) | a window shows an item the partner offers | change the copy the viewer sees |
| [`TradeCompletedEvent`](#tradecompletedevent) | after an exchange | read what changed hands |
| [`TradeCancelledEvent`](#tradecancelledevent) | after a trade ended without an exchange | read why (`cause`) |

A trade that opened ends with exactly one `TradeCompletedEvent` or `TradeCancelledEvent`. With
VersatiaInteractions on the server, the request that opens a trade fires that plugin's events too
(action `trade`), such as `InteractionRequestSendEvent`, which stops a request before it reaches the
other player.

## Listening

A plugin listening to these events compiles against VersatiaTrade
(`compileOnly("dev.versatia:VersatiaTrade:<version>")`) and names it in `plugin.yml`: under
`depend` when it needs it, under `softdepend` when it runs without it too. A Versatia plugin of the
second kind marks the listener `@ConditionalOnPlugin("VersatiaTrade")`, so that the listener, which
names VersatiaTrade's classes, exists only on a server running it.

A deniable event takes a whole sentence from the denying plugin's messages; one cancelled without
a reason (`isCancelled = true`) shows VersatiaTrade's own sentence, `deny.unavailable` or
`trade.item-denied` of its messages.

```kotlin
@ConditionalOnPlugin("VersatiaTrade")
class TradeRules(private val combat: CombatTags, private val messages: Messages) : Listener {
    @EventHandler
    fun onStart(event: TradeStartEvent) {
        val tagged = listOf(event.first, event.second).firstOrNull(combat::isTagged) ?: return
        event.deny(messages["combat.no-trade"].get(arg("player", tagged)))
    }
}
```

## TradeStartEvent

Before a trade opens, once VersatiaTrade's own checks passed (permissions, distance, neither of the
two trading already), whether it was asked for with `/trade` or in VersatiaInteractions' menu.
Denied, the trade does not open and both players read the reason, so it names whom it concerns
(`{player} is in combat`). A request from VersatiaInteractions' menu then ends with that plugin's
`InteractionRequestCancelledEvent` (cause `UNAVAILABLE`), never `InteractionRequestAcceptedEvent`,
and the menu announces no acceptance.

| Property | |
|---|---|
| `first` | the player who asked |
| `second` | the player who accepted |

## TradeItemOfferEvent

When `player` clicks an item of their inventory, in the lower part of the trade window, to offer it
to `partner`, before it joins the offer. Denied, the item stays out of the offer and `player` reads
the reason: such as for items bound to their owner, or quest items. The event is asked when an item
joins an offer, not at the exchange, so an item that becomes untradeable while offered stays offered.

| Property | |
|---|---|
| `player` | the player offering |
| `partner` | the other player |
| `item` | a copy, one item of the kind clicked; changing it changes nothing |
| `amount` | how many the click asks for, the stack or one; fewer join when the player has no more or the offer is full |

```kotlin
@EventHandler
fun onOffer(event: TradeItemOfferEvent) {
    if (bound.isBound(event.item)) event.deny(messages["item.bound"].get(arg("item", event.item)))
}
```

## TradeItemRenderEvent

Whenever the trade window of `viewer` shows an item `owner` offers, at every redraw. `item` is a
copy for the viewer's eyes alone, which the listener may change or replace (`event.item = ...`),
such as to colour the requirements the viewer meets; what the owner offers and what the viewer gets
stay as they are. A listener must be cheap, since every redraw fires it for every item shown.

| Property | |
|---|---|
| `viewer` | the player looking at the window |
| `owner` | the partner, who offers the item |
| `item` | the copy shown, writable |

## TradeCompletedEvent

After the exchange, once both players' data are saved: such as to save at once what a plugin keeps
of its own for the two, to count trades for a quest, or to report trades elsewhere.

| Property | |
|---|---|
| `first`, `second` | the player who asked, the player who accepted |
| `firstGave`, `secondGave` | copies of the stacks each gave, as they lay in the inventory |
| `firstMoney`, `secondMoney` | the money each gave (`BigDecimal`), zero when none |

## TradeCancelledEvent

After a trade ended without an exchange, for `cause`:

| `cause` | When |
|---|---|
| `CLOSED` | one of the two closed the window, or another plugin opened an inventory over it |
| `QUIT` | one of the two left the server |
| `DEATH` | one of the two died |
| `DISTANCE` | the two moved farther apart than `trade.max-distance`, or into different worlds |
| `PERMISSION` | one of the two may no longer trade, or no longer offer the money they offer |
| `STOPPED` | VersatiaTrade stopped |

An exchange that cannot happen when the countdown ends (an item gone, no room, no money) does not
end the trade: the acceptances go, the players are told why, and the trade goes on.
