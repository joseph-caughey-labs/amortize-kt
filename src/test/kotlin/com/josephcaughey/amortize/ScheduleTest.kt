package com.josephcaughey.amortize

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Expected figures were computed separately with Python's decimal module,
// using the same rounding rules.
class ScheduleTest {
    private val start = LocalDate.of(2026, 2, 1)

    private fun usMortgage() =
        Loan(
            principal = BigDecimal("200000"),
            annualRate = BigDecimal("0.06"),
            termMonths = 360,
            firstPayment = start,
        )

    private fun canadianMortgage(frequency: PaymentFrequency = PaymentFrequency.MONTHLY) =
        Loan(
            principal = BigDecimal("500000"),
            annualRate = BigDecimal("0.05"),
            termMonths = 300,
            firstPayment = start,
            frequency = frequency,
            compounding = Compounding.SEMI_ANNUAL,
        )

    private fun assertMoney(
        expected: String,
        actual: BigDecimal,
    ) = assertEquals(BigDecimal(expected), actual)

    @Test
    fun `thirty year US mortgage`() {
        val schedule = usMortgage().schedule()
        assertMoney("1199.10", schedule.loan.payment)
        assertEquals(360, schedule.installments.size)
        assertMoney("1200.14", schedule.installments.last().payment)
        assertMoney("231677.04", schedule.totalInterest)
        assertMoney("431677.04", schedule.totalPaid)
        assertEquals(LocalDate.of(2056, 1, 1), schedule.payoffDate)
    }

    @Test
    fun `first payment splits into interest and principal`() {
        val first = usMortgage().schedule().installments.first()
        assertMoney("1000.00", first.interest)
        assertMoney("199.10", first.principal)
        assertMoney("199800.90", first.balance)
        assertMoney("0.00", first.extra)
    }

    @Test
    fun `canadian mortgage compounds semi-annually`() {
        val schedule = canadianMortgage().schedule()
        assertMoney("2908.02", schedule.loan.payment)
        assertEquals(300, schedule.installments.size)
        assertMoney("2911.02", schedule.installments.last().payment)
        assertMoney("372409.00", schedule.totalInterest)
    }

    @Test
    fun `biweekly payments over the same amortization`() {
        val schedule = canadianMortgage(PaymentFrequency.BIWEEKLY).schedule()
        assertMoney("1340.68", schedule.loan.payment)
        assertEquals(650, schedule.installments.size)
        assertMoney("1338.27", schedule.installments.last().payment)
        assertMoney("371439.59", schedule.totalInterest)
    }

    @Test
    fun `accelerated biweekly pays half the monthly amount and finishes early`() {
        val schedule = canadianMortgage(PaymentFrequency.ACCELERATED_BIWEEKLY).schedule()
        assertMoney("1454.01", schedule.loan.payment)
        assertEquals(559, schedule.installments.size)
        assertMoney("635.06", schedule.installments.last().payment)
        assertMoney("311972.64", schedule.totalInterest)
        assertMoney("60436.36", schedule.interestSavedVersus(canadianMortgage().schedule()))
    }

    @Test
    fun `recurring prepayment shortens the loan`() {
        val baseline = usMortgage().schedule()
        val schedule = usMortgage().schedule(Prepayment.Recurring(BigDecimal("200")))
        assertEquals(252, schedule.installments.size)
        assertMoney("702.08", schedule.installments.last().payment)
        assertMoney("151876.18", schedule.totalInterest)
        assertMoney("79800.86", schedule.interestSavedVersus(baseline))
        assertMoney("200.00", schedule.installments.first().extra)
        assertMoney("1399.10", schedule.installments.first().payment)
    }

    @Test
    fun `lump sum is applied once`() {
        val loan = usMortgage()
        val schedule = loan.schedule(Prepayment.Lump(BigDecimal("10000"), withPayment = 12))
        val rows = schedule.installments
        assertMoney("10000.00", rows[11].extra)
        assertMoney("0.00", rows[10].extra)
        assertMoney("0.00", rows[12].extra)
        assertEquals(rows[10].balance - rows[11].principal, rows[11].balance)
        assertTrue(rows.size < 360)
    }

    @Test
    fun `balance at renewal`() {
        val schedule = canadianMortgage().schedule()
        val renewal = LocalDate.of(2031, 1, 1)
        assertEquals(schedule.installments[59].balance, schedule.balanceAfter(renewal))
        assertEquals(schedule.installments[59].balance, schedule.balanceAfter(renewal.plusDays(30)))
        assertMoney("500000", schedule.balanceAfter(start.minusDays(1)))
    }

    @Test
    fun `zero interest divides the principal evenly`() {
        val loan = Loan(BigDecimal("1000"), BigDecimal.ZERO, 3, start)
        val payments = loan.schedule().installments.map { it.payment }
        assertEquals(listOf("333.33", "333.33", "333.34").map(::BigDecimal), payments)
    }

    @Test
    fun `rounding mode and scale are configurable`() {
        val loan =
            Loan(
                principal = BigDecimal("1000000"),
                annualRate = BigDecimal("0.0123"),
                termMonths = 12,
                firstPayment = start,
                rounding = RoundingMode.HALF_EVEN,
                scale = 0,
            )
        val schedule = loan.schedule()
        assertTrue(schedule.installments.all { it.payment.scale() == 0 })
        assertEquals(BigDecimal.ZERO, schedule.installments.last().balance)
    }

    @Test
    fun `csv lists every installment`() {
        val loan = Loan(BigDecimal("300"), BigDecimal("0.12"), 3, LocalDate.of(2026, 1, 15))
        assertEquals(
            """
            number,date,payment,interest,principal,extra,balance
            1,2026-01-15,102.01,3.00,99.01,0.00,200.99
            2,2026-02-15,102.01,2.01,100.00,0.00,100.99
            3,2026-03-15,102.00,1.01,100.99,0.00,0.00

            """.trimIndent(),
            loan.schedule().toCsv(),
        )
    }

    @Test
    fun `invalid loans are rejected`() {
        assertFailsWith<IllegalArgumentException> { Loan(BigDecimal.ZERO, BigDecimal("0.05"), 12, start) }
        assertFailsWith<IllegalArgumentException> { Loan(BigDecimal("100.005"), BigDecimal("0.05"), 12, start) }
        assertFailsWith<IllegalArgumentException> { Loan(BigDecimal("100"), BigDecimal("-0.01"), 12, start) }
        assertFailsWith<IllegalArgumentException> { Loan(BigDecimal("100"), BigDecimal("0.05"), 0, start) }
        val oddBiweekly =
            assertFailsWith<IllegalArgumentException> {
                Loan(BigDecimal("100"), BigDecimal("0.05"), 7, start, frequency = PaymentFrequency.BIWEEKLY)
            }
        assertEquals("a 7-month term isn't a whole number of biweekly payments", oddBiweekly.message)
        assertFailsWith<IllegalArgumentException> { Prepayment.Lump(BigDecimal("-5"), 1) }
        assertFailsWith<IllegalArgumentException> { Prepayment.Recurring(BigDecimal("5"), startingWith = 0) }
    }
}
