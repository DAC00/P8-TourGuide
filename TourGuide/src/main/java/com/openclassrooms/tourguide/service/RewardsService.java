package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    private Logger logger = LoggerFactory.getLogger(RewardsService.class);

    // proximity in miles
    private int defaultProximityBuffer = 10;
    private int proximityBuffer = defaultProximityBuffer;
    private int attractionProximityRange = 200;
    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
    }

    /**
     * Set the proximityBuffer.
     *
     * @param proximityBuffer the proximityBuffer to set.
     */
    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    /**
     * Sets a default proximity as the proximityBuffer.
     */
    public void setDefaultProximityBuffer() {
        proximityBuffer = defaultProximityBuffer;
    }

    /**
     * Calculate the rewards for a User.
     *
     * @param user for whom to calculate reward.
     */
    public void calculateRewards(User user) {
        CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
        CopyOnWriteArrayList<UserReward> userRewards = new CopyOnWriteArrayList<>(user.getUserRewards());
        List<Attraction> attractions = gpsUtil.getAttractions();
        List<UserReward> newRewardsTOAdd = new ArrayList<>();

        for (VisitedLocation visitedLocation : userLocations) {
            for (Attraction attraction : attractions) {
                if (nearAttraction(visitedLocation, attraction) && userRewards.stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
                    UserReward userReward = new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user));
                    userRewards.add(userReward);
                    newRewardsTOAdd.add(userReward);
                }
            }
        }
        for (UserReward userReward : newRewardsTOAdd) user.addUserReward(userReward);
    }

    /**
     * For each User on the list use a CompletableFuture to calculateRewards to be more efficient.
     * The thread pool is 50. Use only for testing highVolumeGetRewards.
     *
     * @param users is the list of User.
     */
    public void calculateRewardsForAllUsers(List<User> users) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        for (User user : users) {
            futures.add(CompletableFuture.runAsync(() -> {
                calculateRewards(user);
            }, executorService));
        }
        for (CompletableFuture<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.debug("Error : %s".formatted(e));
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Find if the Attraction is near a Location, using attractionProximityRange to determine the distance limit.
     *
     * @param attraction to be compared with.
     * @param location   to be compared with.
     * @return true if the Attraction is near the Location.
     */
    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return !(getDistance(attraction, location) > attractionProximityRange);
    }

    /**
     * Find if the Attraction is near the position of the User, using proximityBuffer to determine the distance limit.
     *
     * @param visitedLocation last position of a User.
     * @param attraction      the Attraction to search.
     * @return true if the Attraction is near User.
     */
    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return !(getDistance(attraction, visitedLocation.location) > proximityBuffer);
    }

    /**
     * Get the number of reward points a User can get from an Attraction.
     *
     * @param attraction to get points earned from.
     * @param user       to get rewards for.
     * @return the reward points.
     */
    private int getRewardPoints(Attraction attraction, User user) {
        return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    }

    /**
     * Return a distance in Miles between two locations.
     *
     * @param loc1 first Location.
     * @param loc2 second Location.
     * @return distance between loc1 and loc2.
     */
    public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
    }

}
