package org.example.mirai.plugin.blackjack

data class PokerCard(val suit: Suit, val rank: Rank) {
    enum class Suit {Heart, Spade, Club, Diamond}
    enum class Rank(val num: Int) {
        Ace(1),Tow(2), Three(3), Four(4), Five(5),
        Six(6), Seven(7), Eight(8), Nine(9), Ten(10),
        Jack(11), Queen(12), King(13);
    }

    override fun toString() = "${suit.toString()} ${rank.num.toString()}"
}

internal class Dealer(decks : Int) {
    private val cards : MutableList<PokerCard> = mutableListOf()

    init {
        repeat(decks) {
            for (suit in PokerCard.Suit.values()) {
                for (rank in PokerCard.Rank.values()) {
                    cards.add(PokerCard(suit, rank))
                }
            }
        }
        shuffleCards()
    }

    private fun shuffleCards() {
        cards.shuffle()
    }

    fun dealCard() : PokerCard {
        return cards.removeLastOrNull() ?: throw NoSuchElementException("No card left in the cards")
    }
}
