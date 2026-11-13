package com.josephcaughey.amortize

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Keeps the figures quoted in README.md honest. */
class ReadmeTest {
    private val mortgage =
        Loan(
            principal = BigDecimal("500000"),
            annualRate = BigDecimal("0.05"),
            termMonths = 300,
            firstPayment = LocalDate.of(2026, 2, 1),
            compounding = Compounding.SEMI_ANNUAL,
        )

    @Test
    fun `mortgage example`() {
        val schedule = mortgage.schedule()
        assertEquals(BigDecimal("2908.02"), mortgage.payment)
        assertEquals(BigDecimal("372409.00"), schedule.totalInterest)

        val faster = mortgage.copy(frequency = PaymentFrequency.ACCELERATED_BIWEEKLY)
        assertEquals(BigDecimal("1454.01"), faster.payment)
        assertEquals(559, faster.schedule().installments.size)
        assertEquals(650, faster.numberOfPayments)
        assertEquals(BigDecimal("60436.36"), faster.schedule().interestSavedVersus(schedule))

        val monthlyCompounding = mortgage.copy(compounding = Compounding.MONTHLY)
        assertEquals(BigDecimal("2922.95"), monthlyCompounding.payment)
    }

    @Test
    fun `csv example`() {
        val loan = Loan(BigDecimal("200000"), BigDecimal("0.06"), 360, LocalDate.of(2026, 2, 1))
        val lines = loan.schedule().toCsv().lines()
        assertEquals("1,2026-02-01,1199.10,1000.00,199.10,0.00,199800.90", lines[1])
        assertEquals("2,2026-03-01,1199.10,999.00,200.10,0.00,199600.80", lines[2])
    }
}
