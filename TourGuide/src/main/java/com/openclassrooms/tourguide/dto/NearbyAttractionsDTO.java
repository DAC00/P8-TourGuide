package com.openclassrooms.tourguide.dto;

import gpsUtil.location.Location;

import java.util.List;

public class NearbyAttractionsDTO {

    private Location userLocation;

    private List<AttractionDTO> attractions;

    public NearbyAttractionsDTO() {
    }

    public NearbyAttractionsDTO(Location userLocation, List<AttractionDTO> attractions) {
        this.userLocation = userLocation;
        this.attractions = attractions;
    }

    public Location getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(Location userLocation) {
        this.userLocation = userLocation;
    }

    public List<AttractionDTO> getAttractions() {
        return attractions;
    }

    public void setAttractions(List<AttractionDTO> attractions) {
        this.attractions = attractions;
    }
}
