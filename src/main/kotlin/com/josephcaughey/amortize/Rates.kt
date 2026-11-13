package com.josephcaughey.amortize

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Precision for rates and intermediate results. Money is rounded separately. */
internal val RATE_CONTEXT = MathContext(34, RoundingMode.HALF_EVEN)

/** Extra digits for Newton's method so the result is right to [RATE_CONTEXT]. */
private val WORKING_CONTEXT = MathContext(50, RoundingMode.HALF_EVEN)

/**
 * How often interest compounds in a year. Most US loans compound monthly.
 * Canadian fixed-rate mortgages are quoted with semi-annual compounding, so the
 * same 5% costs less there than on a US loan.
 */
public enum class Compounding(
    public val periodsPerYear: Int,
) {
    MONTHLY(12),
    SEMI_ANNUAL(2),
    ANNUAL(1),
}

/**
 * How often payments are made.
 *
 * The accelerated options pay half (biweekly) or a quarter (weekly) of the
 * monthly payment. That adds up to one extra monthly payment a year, which
 * goes straight to principal and shortens the loan.
 */
public enum class PaymentFrequency(
    public val periodsPerYear: Int,
    internal val monthlyDivisor: Int? = null,
) {
    MONTHLY(12),
    BIWEEKLY(26),
    WEEKLY(52),
    ACCELERATED_BIWEEKLY(26, monthlyDivisor = 2),
    ACCELERATED_WEEKLY(52, monthlyDivisor = 4),
    ;

    public val isAccelerated: Boolean get() = monthlyDivisor != null

    /**
     * Due date of the payment at zero-based [index]. Monthly dates are counted
     * from the first payment rather than the previous one, so a loan that
     * starts on the 31st goes back to the 31st after February.
     */
    internal fun dueDate(
        first: LocalDate,
        index: Int,
    ): LocalDate =
        when (periodsPerYear) {
            12 -> first.plusMonths(index.toLong())
            26 -> first.plusWeeks(2L * index)
            else -> first.plusWeeks(index.toLong())
        }
}

/**
 * The rate per payment period equivalent to [annualRate] compounded
 * [compounding] times a year: `(1 + r/m)^(m/p) - 1`.
 */
internal fun periodicRate(
    annualRate: BigDecimal,
    compounding: Compounding,
    paymentsPerYear: Int,
): BigDecimal {
    val m = compounding.periodsPerYear
    if (annualRate.signum() == 0) return BigDecimal.ZERO
    if (m == paymentsPerYear) return annualRate.divide(BigDecimal(m), RATE_CONTEXT)

    val g = gcd(m, paymentsPerYear)
    val base = BigDecimal.ONE + annualRate.divide(BigDecimal(m), WORKING_CONTEXT)
    val factor = nthRoot(base.pow(m / g, WORKING_CONTEXT), paymentsPerYear / g)
    return (factor - BigDecimal.ONE).round(RATE_CONTEXT)
}

/** The positive [n]th root of a positive [x] by Newton's method. */
internal fun nthRoot(
    x: BigDecimal,
    n: Int,
): BigDecimal {
    require(x.signum() > 0 && n > 0)
    if (n == 1) return x
    val bigN = BigDecimal(n)
    val nMinusOne = BigDecimal(n - 1)
    var y = BigDecimal(Math.pow(x.toDouble(), 1.0 / n), WORKING_CONTEXT)
    repeat(200) {
        val next =
            (y * nMinusOne + x.divide(y.pow(n - 1, WORKING_CONTEXT), WORKING_CONTEXT))
                .divide(bigN, WORKING_CONTEXT)
        if (next.compareTo(y) == 0) return next
        y = next
    }
    return y
}

private tailrec fun gcd(
    a: Int,
    b: Int,
): Int = if (b == 0) a else gcd(b, a % b)
