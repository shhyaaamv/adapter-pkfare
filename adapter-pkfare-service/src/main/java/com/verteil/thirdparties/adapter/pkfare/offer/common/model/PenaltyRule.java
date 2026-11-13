package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import java.util.function.Predicate;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PENALTY_CANCEL;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PENALTY_CHANGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PENALTY_REFUND;

/** The PKFARE fare rules built from VDC penalties, each with the penalty Detail types it covers. */
public enum PenaltyRule {
    REFUND(type -> VDC_PENALTY_CANCEL.equalsIgnoreCase(type) || VDC_PENALTY_REFUND.equalsIgnoreCase(type)),
    CHANGE(VDC_PENALTY_CHANGE::equalsIgnoreCase);

    private final Predicate<String> detailType;

    PenaltyRule(Predicate<String> detailType) {
        this.detailType = detailType;
    }

    public boolean isDetailType(String type) {
        return detailType.test(type);
    }
}
