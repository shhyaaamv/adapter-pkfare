package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.verteil.schema.pkfare.QteChangeCondition;
import com.verteil.schema.pkfare.QteChangeInfo;
import com.verteil.schema.pkfare.QteFreeBaggage;
import com.verteil.schema.pkfare.QteOtherFareRule;
import com.verteil.schema.pkfare.QtePrice;
import com.verteil.schema.pkfare.QteRefundCondition;
import com.verteil.schema.pkfare.QteRefundInfo;
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
 * Shared offer-mapping records -> PKFARE QTE routing models.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QteTicketMapper {

    /** {@code minFare} is not sent: VDC has no fare range. */
    static QtePrice mapPrice(PassengerFare passengerFare) {
        QtePrice price = new QtePrice();
        price.setPassengerType(passengerFare.passengerType());
        price.setFare(passengerFare.fare());
        price.setTax(passengerFare.tax());
        return price;
    }

    static QteFreeBaggage mapFreeBaggage(BaggageAllowance allowance) {
        QteFreeBaggage baggage = new QteFreeBaggage();
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

    static QteRefundInfo mapRefundInfo(RuleCondition condition) {
        QteRefundCondition refundCondition = new QteRefundCondition();
        refundCondition.setStart(PK_NO_TIME_LIMIT);
        refundCondition.setEnd(PK_NO_TIME_LIMIT);
        refundCondition.setUnit(PK_TIME_UNIT_HOUR);
        refundCondition.setRefundable(condition.allowed());
        refundCondition.setRefundFee(condition.fee());
        refundCondition.setCurrency(condition.currency());
        QteRefundInfo refundInfo = new QteRefundInfo();
        refundInfo.setPassengerType(condition.passengerType());
        refundInfo.setRefundType(condition.type());
        refundInfo.getConditionList().add(refundCondition);
        return refundInfo;
    }

    static QteChangeInfo mapChangeInfo(RuleCondition condition) {
        QteChangeCondition changeCondition = new QteChangeCondition();
        changeCondition.setStart(PK_NO_TIME_LIMIT);
        changeCondition.setEnd(PK_NO_TIME_LIMIT);
        changeCondition.setUnit(PK_TIME_UNIT_HOUR);
        changeCondition.setChangeable(condition.allowed());
        changeCondition.setChangeFee(condition.fee());
        changeCondition.setCurrency(condition.currency());
        QteChangeInfo changeInfo = new QteChangeInfo();
        changeInfo.setPassengerType(condition.passengerType());
        changeInfo.setChangeType(condition.type());
        changeInfo.getConditionList().add(changeCondition);
        return changeInfo;
    }

    static QteOtherFareRule mapOtherFareRule(int passengerType, List<String> details) {
        QteOtherFareRule rule = new QteOtherFareRule();
        rule.setPassengerType(passengerType);
        rule.setDetail(new ArrayList<>(details));
        return rule;
    }
}
