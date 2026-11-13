package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

/**
 * One refund or change condition of a passenger type, spanning the whole period (VDC has no time windows).
 *
 * @param type     0 ticket entirely unused, 1 outbound flown (round trips only)
 * @param allowed  refundable / changeable
 * @param fee      whole currency units
 * @param currency fee currency
 */
public record RuleCondition(int passengerType, int type, boolean allowed, int fee, String currency) {
}
