package dev.versatia.trade.window

import dev.versatia.di.LocalBean
import dev.versatia.text.arg
import dev.versatia.trade.config.TradeSettings
import dev.versatia.trade.config.WindowIcon
import dev.versatia.trade.event.TradeItemRenderEvent
import dev.versatia.trade.money.Money
import dev.versatia.trade.offer.OfferedItem
import dev.versatia.trade.offer.Side
import dev.versatia.trade.session.ActiveTrade
import dev.versatia.trade.session.TradeSessions
import dev.versatia.trade.session.TradeStatus
import dev.versatia.trade.session.TradeTexts
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.TooltipDisplay
import java.math.BigDecimal
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.invui.window.Window

/**
 * Opens the trade window of one player: in the upper part their offer and the partner's, the money
 * buttons, the accept button and the status; the lower part shows a copy of their inventory with the
 * offered items taken out, so a click there changes the offer and never the inventory. Every part
 * asks for its state whenever it is drawn. See docs/trade.md.
 */
@LocalBean
internal class TradeWindows(private val settings: TradeSettings, private val texts: TradeTexts, private val money: Money) {
    fun open(trade: ActiveTrade, viewer: Player, sessions: TradeSessions): TradeWindow {
        val view = View(trade, viewer)
        val upper = Gui.empty(9, 6)
        OWN_SLOTS.forEachIndexed { index, slot -> upper.setItem(slot, item({ ownStack(view, index) }) { click -> withdraw(view, index, click, sessions) }) }
        PARTNER_SLOTS.forEachIndexed { index, slot -> upper.setItem(slot, item({ partnerStack(view, index) })) }
        val divider = icon(WindowIcon.DIVIDER, Component.empty()).apply { setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build()) }
        DIVIDER_SLOTS.forEach { upper.setItem(it, Item.simple(divider)) }
        MONEY_SLOTS.forEachIndexed { index, slot -> upper.setItem(slot, item({ moneyButton(view, index) }) { click -> changeMoney(view, index, click, sessions) }) }
        upper.setItem(PARTNER_MONEY_SLOT, item({ partnerMoney(view) }))
        upper.setItem(ACCEPT_SLOT, item({ acceptButton(view) }) { sessions.toggleAcceptance(trade, viewer) })
        upper.setItem(STATUS_SLOT, item({ status(view) }))
        upper.setItem(PARTNER_ACCEPTANCE_SLOT, item({ partnerAcceptance(view) }))
        val lower = Gui.empty(9, 4)
        for (index in 0 until LOWER_SLOTS) lower.setItem(index, item({ inventoryStack(view, index) }) { click -> offer(view, index, click, sessions) })
        val window = Window.builder()
            .setUpperGui(upper)
            .setLowerGui(lower)
            .setTitle(texts.get("window.title", arg("partner", view.partner)))
            .setViewer(viewer)
            .build()
        window.addCloseHandler { sessions.closed(trade, viewer) }
        window.open()
        return TradeWindow(window, listOf(upper, lower), view)
    }

    /** An item drawn by [draw] whenever its window redraws, null drawing an empty slot; [click] gets the click's type. */
    private fun item(draw: () -> ItemStack?, click: (ClickType) -> Unit = {}) = Item.builder()
        .setItemProvider { _: Player -> draw()?.let(::ItemWrapper) ?: ItemProvider.EMPTY }
        .addClickHandler { _, event -> click(event.clickType()) }
        .build()

    private fun ownStack(view: View, index: Int) = view.ownStacks.getOrNull(index)?.second?.let { withHints(it, texts.lines("window.your-item.lore")) }

    private fun partnerStack(view: View, index: Int) =
        view.partnerStacks.getOrNull(index)?.second?.let { TradeItemRenderEvent(view.viewer, view.partner, it.clone()).apply { callEvent() }.item }

    private fun inventoryStack(view: View, index: Int) = view.remaining[storageSlot(index)]?.let { withHints(it, texts.lines("window.inventory-item.lore")) }

    private fun moneyButton(view: View, index: Int): ItemStack? {
        val step = settings.moneySteps.getOrNull(index) ?: return null
        if (!view.canOfferMoney) return null
        val arguments = arrayOf(arg("step", view.format(step)), arg("money", view.format(view.trade.session.money(view.side))))
        return icon(WindowIcon.MONEY, texts.get("window.money.name", *arguments), texts.lines("window.money.lore", *arguments))
    }

    private fun partnerMoney(view: View): ItemStack? {
        if (!view.showsMoney) return null
        val arguments = arrayOf(arg("partner", view.partner), arg("money", view.format(view.trade.session.money(view.side.other))))
        return icon(WindowIcon.PARTNER_MONEY, texts.get("window.partner-money.name", *arguments))
    }

    private fun acceptButton(view: View): ItemStack {
        val partner = arg("partner", view.partner)
        return when {
            view.trade.session.acceptance.isAccepted(view.side) -> icon(WindowIcon.ACCEPTED, texts.get("window.accepted.name", partner), texts.lines("window.accepted.lore", partner))
            view.trade.status == TradeStatus.Ready -> icon(WindowIcon.ACCEPT, texts.get("window.accept.name", partner), texts.lines("window.accept.lore", partner))
            else -> icon(WindowIcon.BLOCKED, texts.get("window.blocked.name", partner), texts.lines("window.blocked.lore", partner, arg("reason", view.statusSentence)))
        }
    }

    private fun status(view: View) = icon(if (view.secondsLeft != null) WindowIcon.COUNTDOWN else WindowIcon.STATUS, view.statusSentence)

    private fun partnerAcceptance(view: View): ItemStack {
        val partner = arg("partner", view.partner)
        return if (view.trade.session.acceptance.isAccepted(view.side.other)) icon(WindowIcon.PARTNER_ACCEPTED, texts.get("window.partner-accepted.name", partner))
        else icon(WindowIcon.PARTNER_WAITING, texts.get("window.partner-waiting.name", partner))
    }

    private fun withdraw(view: View, index: Int, click: ClickType, sessions: TradeSessions) {
        val (offered, stack) = view.ownStacks.getOrNull(index) ?: return
        sessions.withdrawItem(view.trade, view.viewer, offered.item, amountOf(click, stack) ?: return)
    }

    private fun offer(view: View, index: Int, click: ClickType, sessions: TradeSessions) {
        val stack = view.remaining[storageSlot(index)] ?: return
        sessions.offerItem(view.trade, view.viewer, stack, amountOf(click, stack) ?: return)
    }

    private fun changeMoney(view: View, index: Int, click: ClickType, sessions: TradeSessions) {
        val step = settings.moneySteps.getOrNull(index) ?: return
        val amount = if (click.isShiftClick) step * BigDecimal.TEN else step
        when {
            click.isLeftClick -> sessions.changeMoney(view.trade, view.viewer, amount)
            click.isRightClick -> sessions.changeMoney(view.trade, view.viewer, amount.negate())
        }
    }

    /** A left click moves the whole stack, a right click one item; other clicks, number keys and drops say, nothing. */
    private fun amountOf(click: ClickType, stack: ItemStack) = when {
        click.isLeftClick -> stack.amount
        click.isRightClick -> 1
        else -> null
    }

    private fun icon(kind: WindowIcon, name: Component, lore: List<Component> = emptyList()) = ItemStack.of(Material.PAPER).apply {
        setData(DataComponentTypes.ITEM_MODEL, settings.icons.getValue(kind))
        setData(DataComponentTypes.ITEM_NAME, name)
        if (lore.isNotEmpty()) setData(DataComponentTypes.LORE, ItemLore.lore(lore.map(::upright)))
    }

    /** A copy of [stack] for display, its own lore followed by [hints]. */
    private fun withHints(stack: ItemStack, hints: List<Component>) = stack.clone().apply {
        if (hints.isNotEmpty()) setData(DataComponentTypes.LORE, ItemLore.lore(getData(DataComponentTypes.LORE)?.lines().orEmpty() + hints.map(::upright)))
    }

    private fun upright(line: Component) = line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    /** What one window shows of the trade, worked out once per redraw. */
    inner class View(val trade: ActiveTrade, val viewer: Player) {
        val side = trade.sideOf(viewer)
        val partner = trade.partnerOf(viewer)

        lateinit var ownStacks: List<Pair<OfferedItem, ItemStack>>
        lateinit var partnerStacks: List<Pair<OfferedItem, ItemStack>>
        lateinit var remaining: Array<ItemStack?>
        lateinit var statusSentence: Component
        var secondsLeft: Int? = null

        /** Whether this player may offer money now: an economy is there and the permission held. */
        var canOfferMoney = false

        /** Whether the partner's money shows: an economy is there, or the partner offers some. */
        var showsMoney = false

        init {
            update()
        }

        fun update() {
            ownStacks = stacks(side)
            partnerStacks = stacks(side.other)
            remaining = trade.session.remainingIn(side, viewer.inventory.storageContents)
            secondsLeft = trade.session.acceptance.secondsLeft()
            statusSentence = texts.status(trade.status, side, partner, secondsLeft)
            val economy = money.funds() != null
            canOfferMoney = economy && viewer.hasPermission(MONEY_PERMISSION)
            showsMoney = economy || trade.session.money(side.other).signum() > 0
        }

        fun format(amount: BigDecimal) = money.funds()?.format(amount) ?: amount.toPlainString()

        private fun stacks(of: Side) = trade.session.items(of).flatMap { offered -> offered.stacks().map { offered to it } }
    }

    companion object {
        /** How many stacks each side's offer shows, and so may hold. */
        const val OFFER_SLOTS = 16
        private const val LOWER_SLOTS = 36
        private const val MONEY_PERMISSION = "versatia.trade.money"
        private val OWN_SLOTS = (0 until 4).flatMap { row -> (0 until 4).map { row * 9 + it } }
        private val PARTNER_SLOTS = (0 until 4).flatMap { row -> (5 until 9).map { row * 9 + it } }
        private val DIVIDER_SLOTS = (0 until 6).map { it * 9 + 4 }
        private val MONEY_SLOTS = (36 until 40).toList()
        private const val PARTNER_MONEY_SLOT = 41
        private const val ACCEPT_SLOT = 45
        private const val STATUS_SLOT = 47
        private const val PARTNER_ACCEPTANCE_SLOT = 53

        /** The storage slot a slot of the lower part shows: its three upper rows the inventory, its last row the hotbar. */
        private fun storageSlot(index: Int) = if (index < 27) index + 9 else index - 27
    }
}

/** One player's trade window. */
internal class TradeWindow(private val window: Window, private val guis: List<Gui>, private val view: TradeWindows.View) {
    fun redraw() {
        view.update()
        guis.forEach(Gui::notifyWindows)
    }

    fun close() = window.close()
}
