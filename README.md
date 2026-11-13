# amortize-kt

[![CI](https://github.com/joseph-caughey-labs/amortize-kt/actions/workflows/ci.yml/badge.svg)](https://github.com/joseph-caughey-labs/amortize-kt/actions/workflows/ci.yml)

Loan amortization schedules in Kotlin, to the cent.

- `BigDecimal` throughout. Rates are carried to 34 significant digits and money
  is rounded only where a lender would round it: the regular payment and each
  period's interest.
- The final payment absorbs the rounding, so the balance ends at exactly zero.
- Monthly, semi-annual or annual compounding. Canadian fixed-rate mortgages
  compound semi-annually, which changes the payment noticeably.
- Monthly, biweekly and weekly payments, plus the accelerated variants that
  pay half or a quarter of the monthly amount.
- Recurring and lump-sum prepayments, with the interest they save.
- No dependencies beyond the Kotlin standard library.

## Example

```kotlin
val mortgage = Loan(
    principal = BigDecimal("500000"),
    annualRate = BigDecimal("0.05"),
    termMonths = 300,
    firstPayment = LocalDate.of(2026, 2, 1),
    compounding = Compounding.SEMI_ANNUAL,
)

mortgage.payment                        // 2908.02
val schedule = mortgage.schedule()
schedule.totalInterest                  // 372409.00
schedule.balanceAfter(LocalDate.of(2031, 1, 1))   // owed at a five-year renewal

val faster = mortgage.copy(frequency = PaymentFrequency.ACCELERATED_BIWEEKLY)
faster.payment                          // 1454.01, half the monthly payment
faster.schedule().installments.size     // 559 payments instead of 650 biweekly
faster.schedule().interestSavedVersus(schedule)   // 60436.36
```

The same loan compounded monthly, as a US lender would quote it, costs
$2,922.95 a month.

Prepayments are passed to `schedule`:

```kotlin
val loan = Loan(BigDecimal("200000"), BigDecimal("0.06"), 360, LocalDate.of(2026, 2, 1))

loan.schedule(
    Prepayment.Recurring(BigDecimal("200")),                 // every payment
    Prepayment.Lump(BigDecimal("10000"), withPayment = 12),  // once, with payment 12
)
```

`Schedule.toCsv()` writes one row per payment:

```
number,date,payment,interest,principal,extra,balance
1,2026-02-01,1199.10,1000.00,199.10,0.00,199800.90
2,2026-03-01,1199.10,999.00,200.10,0.00,199600.80
...
```

## How the numbers are worked out

The rate per payment period is `(1 + r/m)^(m/p) - 1`, where `r` is the
nominal annual rate, `m` the compounding periods a year and `p` the payments a
year. When `m` and `p` differ the fractional power is taken with a
`BigDecimal` Newton's-method root rather than `Math.pow`.

The regular payment is the standard level payment `P·i / (1 - (1+i)^-n)`,
rounded to the cent. Accelerated payments are the monthly payment divided by 2
or 4. Each period's interest is the opening balance times the periodic rate,
rounded to the cent; the rest of the payment reduces the balance.

Rounding defaults to `HALF_UP` at two decimal places. Both are parameters of
`Loan`, so a currency without cents uses `scale = 0`.

Monthly due dates are counted from the first payment, so a loan first paid on
31 January is due 28 February, then 31 March.

## Testing

The expected payments, totals and final payments in `ScheduleTest` were
computed separately with Python's `decimal` module, and they match published
figures: a $200,000, 30-year loan at 6% is $1,199.10 a month. `InvariantTest`
generates hundreds of random loans and prepayment plans and checks that every
schedule repays exactly the principal, recomputes each period's interest, and
never charges more interest with a prepayment than without.

```bash
./gradlew build    # compile, ktlint, tests
```

Needs JDK 21.

## Using it

It isn't on Maven Central. [JitPack](https://jitpack.io) builds it from a tag:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.joseph-caughey-labs:amortize-kt:v0.1.0")
}
```

## License

MIT
