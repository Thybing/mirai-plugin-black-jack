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

internal class Dealer(decks : Int, debug : Boolean = false) {
    private val cards : MutableList<PokerCard> = mutableListOf()

    init {
        if(!debug) {
            repeat(decks) {
                for (suit in PokerCard.Suit.values()) {
                    for (rank in PokerCard.Rank.values()) {
                        cards.add(PokerCard(suit, rank))
                    }
                }
            }
            shuffleCards()
        } else {
            /**
             *  Debug 模式下自定义牌序，方便调式
             */
            repeat(52) {
                cards.add(PokerCard(PokerCard.Suit.Heart,PokerCard.Rank.Ace))
            }
        }
    }

    private fun shuffleCards() {
        cards.shuffle()
    }

    fun dealCard() : PokerCard {
        return cards.removeLastOrNull() ?: throw NoSuchElementException("No card left in the cards")
    }
}
