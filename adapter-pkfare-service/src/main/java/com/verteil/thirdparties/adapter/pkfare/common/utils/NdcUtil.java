package com.verteil.thirdparties.adapter.pkfare.common.utils;

import com.verteil.schemas.ndc16_1.CurrencyAmountOptType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadDetailType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NdcUtil {

    public static final int AMOUNT_SCALE = 2;
    private static final DateTimeFormatter PK_DATE_TIME = DateTimeFormatter.ofPattern(PkfareConstants.PK_DATE_TIME_FORMAT);
    private static final DateTimeFormatter NDC_TIME = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Local date-time of an NDC departure/arrival: the calendar's date plus the separate {@code Time} ("HH:mm") when
     * present, otherwise the calendar's own time fields.
     */
    public static LocalDateTime toLocalDateTime(XMLGregorianCalendar date, String time) {
        return Optional.ofNullable(date).map(calendar -> LocalDateTime.of(
                LocalDate.of(calendar.getYear(), calendar.getMonth(), calendar.getDay()),
                parseTime(time).orElseGet(() -> calendar.getHour() == DatatypeConstants.FIELD_UNDEFINED
                        ? LocalTime.MIDNIGHT
                        : LocalTime.of(calendar.getHour(), Math.max(calendar.getMinute(), 0)))))
                .orElse(null);
    }

    public static String toPkDateTime(LocalDateTime dateTime) {
        return Optional.ofNullable(dateTime).map(localDateTime -> localDateTime.format(PK_DATE_TIME)).orElse(null);
    }

    public static Integer toMinutes(Duration duration) {
        return Optional.ofNullable(duration)
                .map(value -> Math.toIntExact((long) value.getDays() * 24 * 60 + (long) value.getHours() * 60 + value.getMinutes()))
                .orElse(null);
    }

    /** Flight numbers go to PKFARE as integers without carrier code or leading zeros ("0930" -> 930). */
    public static Integer toFlightNumber(String flightNumber) {
        if (!isNotBlank(flightNumber)) {
            return null;
        }
        String digits = flightNumber.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    public static BigDecimal amount(CurrencyAmountOptType amount) {
        return Optional.ofNullable(amount).map(CurrencyAmountOptType::getValue).map(NdcUtil::scale).orElse(null);
    }

    public static BigDecimal scale(BigDecimal value) {
        return Optional.ofNullable(value).map(amount -> amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)).orElse(null);
    }

    public static Optional<CurrencyAmountOptType> totalAmount(OfferPriceLeadDetailType.PriceDetail priceDetail) {
        return Optional.ofNullable(priceDetail)
                .map(OfferPriceLeadDetailType.PriceDetail::getTotalAmount)
                .flatMap(totalAmount -> Optional.<CurrencyAmountOptType>ofNullable(totalAmount.getSimpleCurrencyPrice())
                        .or(() -> Optional.ofNullable(totalAmount.getDetailCurrencyPrice()).map(detail -> detail.getTotal())));
    }

    private static Optional<LocalTime> parseTime(String time) {
        if (!isNotBlank(time)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalTime.parse(time.trim().length() > 5 ? time.trim().substring(0, 5) : time.trim(), NDC_TIME));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }
}
