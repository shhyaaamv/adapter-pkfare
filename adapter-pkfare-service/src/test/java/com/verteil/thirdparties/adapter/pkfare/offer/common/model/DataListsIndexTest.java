package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import com.verteil.schemas.ndc16_1.ApplicableFlight;
import com.verteil.schemas.ndc16_1.DataListType;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.FareDetailType;
import com.verteil.schemas.ndc16_1.FareList;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferAssocType;
import com.verteil.schemas.ndc16_1.SegmentReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DataListsIndexTest {

    private final DataListsIndex index = new DataListsIndex(dataLists());

    @Test
    void segmentKeyOfPrefersTheSegmentReference() {
        FareComponentType component = fareComponent("FG1", "SEG2");
        SegmentReference segmentReference = new SegmentReference();
        segmentReference.setValue("SEG1");
        component.setSegmentReference(segmentReference);

        assertThat(index.segmentKeyOf(component)).contains("SEG1");
    }

    @Test
    void segmentKeyOfFallsBackToTheFirstSegmentInRefs() {
        // FlightPriceRS style: no SegmentReference, the segment is one of the refs
        assertThat(index.segmentKeyOf(fareComponent("FG1", "PM0", "SEG2", "SEG1"))).contains("SEG2");
    }

    @Test
    void segmentKeyOfIsEmptyWhenNoRefIsASegment() {
        assertThat(index.segmentKeyOf(fareComponent("FG1", "UNKNOWN"))).isEmpty();
    }

    @Test
    void fareGroupsComeFromFareComponentAndClassOfServiceRefs() {
        OfferPriceLeadType offerPrice = new OfferPriceLeadType();
        FareDetailType fareDetail = new FareDetailType();
        fareDetail.getFareComponent().add(fareComponent("FG1", "SEG1"));
        offerPrice.setFareDetail(fareDetail);
        OfferPriceLeadType.RequestedDate requestedDate = new OfferPriceLeadType.RequestedDate();
        requestedDate.getAssociations().add(association("FG2", "FG1", "UNKNOWN"));
        offerPrice.setRequestedDate(requestedDate);

        assertThat(index.fareGroups(offerPrice)).extracting(FareList.FareGroup::getListKey).containsExactly("FG1", "FG2");
    }

    @Test
    void fareGroupsOfAnEmptyOfferPriceAreEmpty() {
        assertThat(index.fareGroups(new OfferPriceLeadType())).isEmpty();
    }

    private static DataListType dataLists() {
        DataListType dataLists = new DataListType();
        DataListType.FlightSegmentList segments = new DataListType.FlightSegmentList();
        Stream.of("SEG1", "SEG2").map(DataListsIndexTest::segment).forEach(segments.getFlightSegment()::add);
        dataLists.setFlightSegmentList(segments);
        FareList fareList = new FareList();
        Stream.of("FG1", "FG2").map(DataListsIndexTest::fareGroup).forEach(fareList.getFareGroup()::add);
        dataLists.setFareList(fareList);
        return dataLists;
    }

    private static ListOfFlightSegmentType segment(String key) {
        ListOfFlightSegmentType segment = new ListOfFlightSegmentType();
        segment.setSegmentKey(key);
        return segment;
    }

    private static FareList.FareGroup fareGroup(String key) {
        FareList.FareGroup fareGroup = new FareList.FareGroup();
        fareGroup.setListKey(key);
        return fareGroup;
    }

    private static FareComponentType fareComponent(String... refs) {
        FareComponentType component = new FareComponentType();
        component.getRefs().addAll(List.of(refs));
        return component;
    }

    private static PricedFlightOfferAssocType association(String... classOfServiceRefs) {
        FlightCOSCoreType classOfService = new FlightCOSCoreType();
        classOfService.getRefs().addAll(List.of(classOfServiceRefs));
        FlightSegmentReference reference = new FlightSegmentReference();
        reference.setRef("SEG1");
        reference.setClassOfService(classOfService);
        ApplicableFlight applicableFlight = new ApplicableFlight();
        applicableFlight.getFlightSegmentReference().add(reference);
        PricedFlightOfferAssocType association = new PricedFlightOfferAssocType();
        association.setApplicableFlight(applicableFlight);
        return association;
    }
}
