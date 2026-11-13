package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import java.math.BigDecimal;

/**
 * Per-passenger amounts of one PKFARE passenger type, scale 2.
 *
 * @param fare PriceDetail.BaseAmount
 * @param tax  PriceDetail.TotalAmount - BaseAmount (all taxes, fees and surcharges), else Taxes.Total
 */
public record PassengerFare(int passengerType, BigDecimal fare, BigDecimal tax) {
}
