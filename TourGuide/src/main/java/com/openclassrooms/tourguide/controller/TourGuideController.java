package com.openclassrooms.tourguide.controller;

import com.openclassrooms.tourguide.dto.NearbyAttractionsDTO;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.location.VisitedLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tripPricer.Provider;

import java.util.List;

@RestController
public class TourGuideController {

    @Autowired
    TourGuideService tourGuideService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }

    /**
     * Get the closest five tourist attractions to the user - no matter how far away they are in a DTO object.
     * Contains user Location and a list of (Attraction name / Location / Distance from User and Reward Points)
     * for each of the five Attractions.
     *
     * @param userName name of the User.
     * @return a DTO Object / JSON.
     */
    @RequestMapping("/getNearbyAttractions")
    public NearbyAttractionsDTO getNearbyAttractions(@RequestParam String userName) {
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        return tourGuideService.getNearbyAttractionsDTO(visitedLocation);
    }

    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }

    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
    }

    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }

}