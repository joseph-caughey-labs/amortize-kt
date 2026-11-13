package com.josephcaughey.amortize

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RatesTest {
    @Test
    fun `matching compounding and payment frequency divides the rate`() {
        val rate = periodicRate(BigDecimal("0.06"), Compounding.MONTHLY, 12)
        assertEquals(0, rate.compareTo(BigDecimal("0.005")))
    }

    @Test
    fun `semi-annual compounding converts to an equivalent monthly rate`() {
        // (1 + 0.05/2)^(1/6) - 1, computed independently to 40 digits.
        val expected = BigDecimal("0.004123915465144271401093578688687307083")
        val rate = periodicRate(BigDecimal("0.05"), Compounding.SEMI_ANNUAL, 12)
        assertEquals(expected.round(MathContext(30)), rate.round(MathContext(30)))
    }

    @Test
    fun `semi-annual compounding converts to a biweekly rate`() {
        val expected = BigDecimal("0.001901236800867836319446242087036743864")
        val rate = periodicRate(BigDecimal("0.05"), Compounding.SEMI_ANNUAL, 26)
        assertEquals(expected.round(MathContext(30)), rate.round(MathContext(30)))
    }

    @Test
    fun `equivalent rates compound to the same yearly growth`() {
        val annual = BigDecimal("0.0725")
        val monthly = periodicRate(annual, Compounding.ANNUAL, 12)
        val grown = (BigDecimal.ONE + monthly).pow(12, MathContext(40))
        assertEquals(BigDecimal("1.0725"), grown.round(MathContext(25)).stripTrailingZeros())
    }

    @Test
    fun `zero rate stays zero`() {
        assertEquals(BigDecimal.ZERO, periodicRate(BigDecimal.ZERO, Compounding.SEMI_ANNUAL, 26))
    }

    @Test
    fun `nth root is exact for perfect powers`() {
        assertEquals(0, nthRoot(BigDecimal("1.0000000000001800000000000081"), 2).compareTo(BigDecimal("1.00000000000009")))
        assertEquals(0, nthRoot(BigDecimal(1024), 10).compareTo(BigDecimal(2)))
    }

    @Test
    fun `monthly due dates keep the original day where the month allows`() {
        val first = LocalDate.of(2026, 1, 31)
        val dates = (0..3).map { PaymentFrequency.MONTHLY.dueDate(first, it) }
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), dates.map { it.toString() })
    }

    @Test
    fun `weekly and biweekly due dates step by days`() {
        val first = LocalDate.of(2026, 1, 2)
        assertEquals(LocalDate.of(2026, 1, 30), PaymentFrequency.BIWEEKLY.dueDate(first, 2))
        assertEquals(LocalDate.of(2026, 1, 30), PaymentFrequency.ACCELERATED_WEEKLY.dueDate(first, 4))
    }
}
