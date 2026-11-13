package com.josephcaughey.amortize

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A fixed-rate loan repaid in level payments.
 *
 * [annualRate] is the nominal yearly rate as a fraction (`0.0525` for 5.25%),
 * compounded as [compounding] says. [termMonths] is the amortization period:
 * how long the loan takes to pay off at the regular payment.
 *
 * Money is held to [scale] decimal places. The regular payment and each
 * period's interest are rounded with [rounding]; the final payment absorbs
 * whatever rounding left over, so the balance always ends at exactly zero.
 */
public data class Loan(
    val principal: BigDecimal,
    val annualRate: BigDecimal,
    val termMonths: Int,
    val firstPayment: LocalDate,
    val frequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val compounding: Compounding = Compounding.MONTHLY,
    val rounding: RoundingMode = RoundingMode.HALF_UP,
    val scale: Int = 2,
) {
    init {
        require(scale >= 0) { "scale must not be negative, was $scale" }
        require(principal.signum() > 0) { "principal must be positive, was $principal" }
        require(principal.stripTrailingZeros().scale() <= scale) {
            "principal $principal has more than $scale decimal places"
        }
        require(annualRate.signum() >= 0) { "rate must not be negative, was $annualRate" }
        require(termMonths > 0) { "term must be at least one month, was $termMonths" }
        require(termMonths * frequency.periodsPerYear % 12 == 0) {
            "a $termMonths-month term isn't a whole number of ${frequency.name.lowercase()} payments"
        }
    }

    /** Interest rate per payment period, to 34 significant digits. */
    public val periodicRate: BigDecimal =
        periodicRate(annualRate, compounding, frequency.periodsPerYear)

    /**
     * Payments in the full term. Accelerated and prepaid schedules finish
     * sooner.
     */
    public val numberOfPayments: Int = termMonths * frequency.periodsPerYear / 12

    /** The regular payment, before any prepayment. */
    public val payment: BigDecimal =
        when (val divisor = frequency.monthlyDivisor) {
            null -> levelPayment(principal, periodicRate, numberOfPayments)
            else -> {
                val monthly = copy(frequency = PaymentFrequency.MONTHLY).payment
                monthly.divide(BigDecimal(divisor), scale, rounding)
            }
        }

    /** The payment-by-payment schedule, with optional [prepayments]. */
    public fun schedule(prepayments: List<Prepayment> = emptyList()): Schedule = buildSchedule(this, prepayments)

    public fun schedule(vararg prepayments: Prepayment): Schedule = schedule(prepayments.toList())

    private fun levelPayment(
        principal: BigDecimal,
        rate: BigDecimal,
        n: Int,
    ): BigDecimal {
        if (rate.signum() == 0) return principal.divide(BigDecimal(n), scale, rounding)
        val discount = (BigDecimal.ONE + rate).pow(-n, RATE_CONTEXT)
        return (principal * rate)
            .divide(BigDecimal.ONE - discount, RATE_CONTEXT)
            .setScale(scale, rounding)
    }
}
