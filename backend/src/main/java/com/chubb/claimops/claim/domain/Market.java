package com.chubb.claimops.claim.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The six APAC markets and their local currencies.
 *
 * <p>A claim in any market may be filed in the local currency or in USD (for example, a USD-denominated policy).
 * Amounts are never converted between currencies. That's why liability exposure is reported per market, claim type
 * and currency rather than as one APAC total: adding SGD to JPY without an FX rate would produce a meaningless
 * number.
 */
@Getter
@RequiredArgsConstructor
public enum Market {
    SG(Currency.SGD),
    TH(Currency.THB),
    MY(Currency.MYR),
    HK(Currency.HKD),
    AU(Currency.AUD),
    JP(Currency.JPY);

    private final Currency localCurrency;

    /** Whether a claim in this market may be filed in {@code currency}: the local currency or USD. */
    public boolean accepts(Currency currency) {
        return currency == localCurrency || currency == Currency.USD;
    }
}
