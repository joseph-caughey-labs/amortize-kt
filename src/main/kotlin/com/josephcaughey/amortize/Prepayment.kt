package com.josephcaughey.amortize

import java.math.BigDecimal

/** Principal paid on top of the regular payment. */
public sealed interface Prepayment {
    /** [amount] extra with every payment from payment number [startingWith] on. */
    public data class Recurring(
        val amount: BigDecimal,
        val startingWith: Int = 1,
    ) : Prepayment {
        init {
            require(amount.signum() > 0) { "prepayment must be positive, was $amount" }
            require(startingWith >= 1) { "payment numbers start at 1, was $startingWith" }
        }
    }

    /** A one-off [amount] paid together with payment number [withPayment]. */
    public data class Lump(
        val amount: BigDecimal,
        val withPayment: Int,
    ) : Prepayment {
        init {
            require(amount.signum() > 0) { "prepayment must be positive, was $amount" }
            require(withPayment >= 1) { "payment numbers start at 1, was $withPayment" }
        }
    }
}

internal fun List<Prepayment>.extraFor(number: Int): BigDecimal =
    fold(BigDecimal.ZERO) { total, prepayment ->
        when (prepayment) {
            is Prepayment.Recurring -> if (number >= prepayment.startingWith) total + prepayment.amount else total
            is Prepayment.Lump -> if (number == prepayment.withPayment) total + prepayment.amount else total
        }
    }
