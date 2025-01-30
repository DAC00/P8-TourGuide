package com.openclassrooms.tourguide.dto;

import gpsUtil.location.Location;

public class AttractionDTO {

    private String name;

    private Location location;

    private double distance;

    private int reward;

    public AttractionDTO(String name, Location location, double distance, int reward) {
        this.name = name;
        this.location = location;
        this.distance = distance;
        this.reward = reward;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getReward() {
        return reward;
    }

    public void setReward(int reward) {
        this.reward = reward;
    }
}
