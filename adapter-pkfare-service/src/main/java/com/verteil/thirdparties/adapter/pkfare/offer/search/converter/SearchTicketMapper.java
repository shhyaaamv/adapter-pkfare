package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchChangeCondition;
import com.verteil.schema.pkfare.SearchChangeInfo;
import com.verteil.schema.pkfare.SearchFreeBaggage;
import com.verteil.schema.pkfare.SearchOtherFareRule;
import com.verteil.schema.pkfare.SearchPrice;
import com.verteil.schema.pkfare.SearchRefundCondition;
import com.verteil.schema.pkfare.SearchRefundInfo;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.BaggageAllowance;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerFare;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.RuleCondition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_NO_TIME_LIMIT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_TIME_UNIT_HOUR;

/**
 * Shared offer-mapping records -> PKFARE SEARCH ticket models.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SearchTicketMapper {

    /** {@code fare} doubles as {@code maxFare}: VDC has no fare range. */
    static SearchPrice mapPrice(PassengerFare passengerFare) {
        SearchPrice price = new SearchPrice();
        price.setPassengerType(passengerFare.passengerType());
        price.setFare(passengerFare.fare());
        price.setMaxFare(passengerFare.fare());
        price.setTax(passengerFare.tax());
        return price;
    }

    static SearchFreeBaggage mapFreeBaggage(BaggageAllowance allowance) {
        SearchFreeBaggage baggage = new SearchFreeBaggage();
        baggage.setTravelDirection(allowance.travelDirection());
        baggage.setFlightSeq(allowance.flightSeq());
        baggage.setPassengerType(allowance.passengerType());
        baggage.setBaggagePiece(allowance.baggagePiece());
        baggage.setBaggageWeight(allowance.baggageWeight());
        baggage.setCarryOnPiece(allowance.carryOnPiece());
        baggage.setCarryOnWeight(allowance.carryOnWeight());
        if (!allowance.carryOnSize().isEmpty()) {
            baggage.setCarryOnSize(new ArrayList<>(allowance.carryOnSize()));
        }
        return baggage;
    }

    static SearchRefundInfo mapRefundInfo(RuleCondition condition) {
        SearchRefundCondition refundCondition = new SearchRefundCondition();
        refundCondition.setStart(PK_NO_TIME_LIMIT);
        refundCondition.setEnd(PK_NO_TIME_LIMIT);
        refundCondition.setUnit(PK_TIME_UNIT_HOUR);
        refundCondition.setRefundable(condition.allowed());
        refundCondition.setRefundFee(condition.fee());
        refundCondition.setCurrency(condition.currency());
        SearchRefundInfo refundInfo = new SearchRefundInfo();
        refundInfo.setPassengerType(condition.passengerType());
        refundInfo.setRefundType(condition.type());
        refundInfo.getConditionList().add(refundCondition);
        return refundInfo;
    }

    static SearchChangeInfo mapChangeInfo(RuleCondition condition) {
        SearchChangeCondition changeCondition = new SearchChangeCondition();
        changeCondition.setStart(PK_NO_TIME_LIMIT);
        changeCondition.setEnd(PK_NO_TIME_LIMIT);
        changeCondition.setUnit(PK_TIME_UNIT_HOUR);
        changeCondition.setChangeable(condition.allowed());
        changeCondition.setChangeFee(condition.fee());
        changeCondition.setCurrency(condition.currency());
        SearchChangeInfo changeInfo = new SearchChangeInfo();
        changeInfo.setPassengerType(condition.passengerType());
        changeInfo.setChangeType(condition.type());
        changeInfo.getConditionList().add(changeCondition);
        return changeInfo;
    }

    static SearchOtherFareRule mapOtherFareRule(int passengerType, List<String> details) {
        SearchOtherFareRule rule = new SearchOtherFareRule();
        rule.setPassengerType(passengerType);
        rule.setDetail(new ArrayList<>(details));
        return rule;
    }
}
