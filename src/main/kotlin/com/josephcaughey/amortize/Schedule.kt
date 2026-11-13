package com.josephcaughey.amortize

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One payment. [payment] is [interest] plus [principal]; [extra] is the part
 * of [principal] that came from a prepayment. [balance] is what is owed
 * afterwards.
 */
public data class Installment(
    val number: Int,
    val date: LocalDate,
    val payment: BigDecimal,
    val interest: BigDecimal,
    val principal: BigDecimal,
    val extra: BigDecimal,
    val balance: BigDecimal,
)

public class Schedule internal constructor(
    public val loan: Loan,
    public val installments: List<Installment>,
) {
    public val totalInterest: BigDecimal = installments.sumOf { it.interest }

    public val totalPaid: BigDecimal = installments.sumOf { it.payment }

    public val payoffDate: LocalDate = installments.last().date

    /**
     * The balance after every payment due on or before [date]. A Canadian
     * mortgage renewing after five years owes `balanceAfter(renewalDate)`.
     */
    public fun balanceAfter(date: LocalDate): BigDecimal = installments.lastOrNull { !it.date.isAfter(date) }?.balance ?: loan.principal

    /** Interest this schedule saves compared with [baseline]. */
    public fun interestSavedVersus(baseline: Schedule): BigDecimal = baseline.totalInterest - totalInterest

    public fun toCsv(): String =
        buildString {
            appendLine("number,date,payment,interest,principal,extra,balance")
            for (row in installments) {
                val amounts = listOf(row.payment, row.interest, row.principal, row.extra, row.balance)
                append(row.number).append(',').append(row.date)
                amounts.forEach { append(',').append(it.toPlainString()) }
                appendLine()
            }
        }
}

internal fun buildSchedule(
    loan: Loan,
    prepayments: List<Prepayment>,
): Schedule {
    val zero = BigDecimal.ZERO.setScale(loan.scale)
    val rows = ArrayList<Installment>()
    var balance = loan.principal.setScale(loan.scale)
    var number = 0

    while (balance.signum() > 0) {
        number++
        val interest = (balance * loan.periodicRate).setScale(loan.scale, loan.rounding)
        val scheduledExtra = prepayments.extraFor(number).setScale(loan.scale, loan.rounding)
        val due = loan.payment + scheduledExtra
        val owed = balance + interest

        // The last scheduled payment clears whatever rounding left behind.
        val amount = if (owed <= due || number == loan.numberOfPayments) owed else due
        val principal = amount - interest
        check(principal.signum() > 0) {
            "payment $number of $amount doesn't cover interest of $interest"
        }
        balance -= principal
        val extra = scheduledExtra.min((amount - loan.payment).max(zero))

        rows +=
            Installment(
                number = number,
                date = loan.frequency.dueDate(loan.firstPayment, number - 1),
                payment = amount,
                interest = interest,
                principal = principal,
                extra = extra,
                balance = balance,
            )
    }
    return Schedule(loan, rows)
}
