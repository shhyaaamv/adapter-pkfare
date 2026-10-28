package com.verteil.thirdparties.adapter.pkfare.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * NDC {@code @XmlIDREF} fields are typed {@code Object}: unresolved they hold the referenced key as a String,
 * resolved (JaxbResolver) they hold the referenced object. Converters only ever need the key, so this reads it
 * from either form.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RefUtil {

    private static final List<String> KEY_GETTERS = List.of(
            "getSegmentKey", "getListKey", "getObjectKey", "getFlightKey", "getOriginDestinationKey");

    public static String key(Object ref) {
        if (isNull(ref)) {
            return null;
        }
        if (ref instanceof String value) {
            return value;
        }
        return KEY_GETTERS.stream()
                .flatMap(getter -> keyFromGetter(ref, getter).stream())
                .findFirst()
                .orElseGet(() -> String.valueOf(ref));
    }

    public static List<String> keys(Collection<?> refs) {
        return Optional.ofNullable(refs).stream()
                .flatMap(Collection::stream)
                .map(RefUtil::key)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Optional<String> keyFromGetter(Object ref, String getter) {
        try {
            Method method = ref.getClass().getMethod(getter);
            return Optional.ofNullable(method.invoke(ref)).filter(String.class::isInstance).map(String.class::cast);
        } catch (ReflectiveOperationException _) {
            // not this kind of object; the caller tries the next key accessor
            return Optional.empty();
        }
    }
}
