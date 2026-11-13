package com.josephcaughey.amortize

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rules every schedule must follow, checked over many random loans. */
class InvariantTest {
    private val random = Random(20260412)

    private fun randomLoan(): Loan {
        val frequency = PaymentFrequency.entries.random(random)
        val step = if (frequency.periodsPerYear == 12) 1 else 6
        return Loan(
            principal = BigDecimal.valueOf(random.nextLong(1, 200_000_000), 2),
            annualRate = BigDecimal.valueOf(random.nextLong(0, 2_500), 4),
            termMonths = step * random.nextInt(1, 360 / step + 1),
            firstPayment = LocalDate.of(2026, 1, 1).plusDays(random.nextLong(0, 365)),
            frequency = frequency,
            compounding = Compounding.entries.random(random),
        )
    }

    private fun randomPrepayments(loan: Loan): List<Prepayment> =
        buildList {
            if (random.nextBoolean()) {
                add(Prepayment.Recurring(BigDecimal.valueOf(random.nextLong(1, 50_000), 2), random.nextInt(1, 40)))
            }
            repeat(random.nextInt(0, 3)) {
                val amount = loan.principal.divide(BigDecimal(random.nextInt(2, 20)), 2, java.math.RoundingMode.DOWN)
                if (amount.signum() > 0) add(Prepayment.Lump(amount, random.nextInt(1, 60)))
            }
        }

    @Test
    fun `schedules always repay exactly the principal`() {
        repeat(400) {
            val loan = randomLoan()
            val prepayments = randomPrepayments(loan)
            val schedule = loan.schedule(prepayments)
            val rows = schedule.installments
            val context = "$loan with $prepayments"

            assertEquals(0, rows.last().balance.signum(), context)
            assertEquals(0, rows.sumOf { it.principal }.compareTo(loan.principal), context)
            assertTrue(rows.size <= loan.numberOfPayments, context)
            assertEquals(schedule.totalPaid, schedule.totalInterest + loan.principal, context)

            var previous = loan.principal
            for (row in rows) {
                val expectedInterest = (previous * loan.periodicRate).setScale(loan.scale, loan.rounding)
                assertEquals(expectedInterest, row.interest, "$context, payment ${row.number}")
                assertEquals(row.payment, row.interest + row.principal, context)
                assertTrue(row.principal.signum() > 0, context)
                assertTrue(row.extra.signum() >= 0 && row.extra <= row.principal, context)
                assertEquals(previous - row.principal, row.balance, context)
                previous = row.balance
            }

            // Every payment but the last is exactly the regular amount plus
            // whatever prepayment was scheduled for it.
            for (row in rows.dropLast(1)) {
                assertEquals(loan.payment + prepayments.extraFor(row.number).setScale(loan.scale), row.payment, context)
            }
        }
    }

    @Test
    fun `prepaying never costs more interest`() {
        repeat(200) {
            val loan = randomLoan()
            val baseline = loan.schedule()
            val prepaid = loan.schedule(randomPrepayments(loan))
            assertTrue(prepaid.interestSavedVersus(baseline).signum() >= 0, "$loan")
            assertTrue(prepaid.installments.size <= baseline.installments.size, "$loan")
        }
    }
}
