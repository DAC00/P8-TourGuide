package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.AttractionDTO;
import com.openclassrooms.tourguide.dto.NearbyAttractionsDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TourGuideService {
    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    public final Tracker tracker;
    boolean testMode = true;

    @Autowired
    private RewardCentral rewardCentral;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }

        tracker = new Tracker(this);
        addShutDownHook();
    }

    /**
     * Get the list of UserReward from a User.
     *
     * @param user to get the list from.
     * @return a list of UserReward.
     */
    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    /**
     * Get the VisitedLocation from the User, which is his last location.
     *
     * @param user to get location from.
     * @return User location.
     */
    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    /**
     * Get the User with userName from the internalUserMap.
     *
     * @param userName of the User to get.
     * @return the User with userName.
     */
    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    /**
     * Get a list of all the User from internalUserMap.
     *
     * @return a list of all User.
     */
    public List<User> getAllUsers() {
        return internalUserMap.values().stream().collect(Collectors.toList());
    }

    /**
     * Add a User to the internalUserMap.
     *
     * @param user to add.
     */
    public void addUser(User user) {
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }

    /**
     * Get a list of Provider for the User. Use TripPricer to get trip deals.
     *
     * @param user to get deals for.
     * @return a list of Provider.
     */
    public List<Provider> getTripDeals(User user) {
        int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    /**
     * Update the data of a User and return the new VisitedLocation.
     *
     * @param user to be updated.
     * @return the last visitedLocation.
     */
    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    /**
     * Update data of a list of User. Each call of trackUserLocation is a CompletableFuture to be more efficient.
     * The thread pool is 30.
     *
     * @param users a list of User.
     */
    public void trackUsersLocation(List<User> users) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(30);
        for (User user : users) {
            futures.add(CompletableFuture.runAsync(() -> {
                trackUserLocation(user);
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
     * Get the closest five Attraction to the user no matter how far away.
     *
     * @param visitedLocation for the Location of the User.
     * @return a List of Attraction.
     */
    public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
        List<Attraction> nearbyAttractions = new ArrayList<>();
        List<Attraction> attractions = gpsUtil.getAttractions();

        attractions.sort((o1, o2) -> Double.compare(
                Math.abs(rewardsService.getDistance(visitedLocation.location, new Location(o1.latitude, o1.longitude))),
                Math.abs(rewardsService.getDistance(visitedLocation.location, new Location(o2.latitude, o2.longitude)))
        ));

        for (int i = 0; i < Math.min(5, attractions.size()); i++) {
            nearbyAttractions.add(attractions.get(i));
        }
        return nearbyAttractions;
    }

    /**
     * Get the closest five tourist attractions to the user - no matter how far away they are in a DTO object.
     * Contains user Location and a list of (Attraction name / Location / Distance from User and Reward Points)
     * for each of the five Attractions.
     *
     * @param visitedLocation for the Location of the User.
     * @return a DTO Object.
     */
    public NearbyAttractionsDTO getNearbyAttractionsDTO(VisitedLocation visitedLocation) {
        List<Attraction> attractions = getNearByAttractions(visitedLocation);
        List<AttractionDTO> attractionDTOS = new ArrayList<>();
        NearbyAttractionsDTO dto = new NearbyAttractionsDTO();

        for (Attraction attraction : attractions) {
            Location attLocation = new Location(attraction.latitude, attraction.longitude);
            attractionDTOS.add(new AttractionDTO(
                    attraction.attractionName,
                    attLocation,
                    rewardsService.getDistance(visitedLocation.location, attLocation),
                    rewardCentral.getAttractionRewardPoints(visitedLocation.userId, attraction.attractionId))
            );
        }

        dto.setUserLocation(visitedLocation.location);
        dto.setAttractions(attractionDTOS);
        return dto;
    }

    /**
     * Stop the Tracker.
     */
    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                tracker.stopTracking();
            }
        });
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";
    // Database connection will be used for external users, but for testing purposes
    // internal users are provided and stored in memory
    private final Map<String, User> internalUserMap = new HashMap<>();

    /**
     * Initialize internal users, populate the internalUserMap with a number of User.
     */
    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    /**
     * Generate a multiple VisitedLocation and add it to the User.
     *
     * @param user to modify.
     */
    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
        });
    }

    /**
     * Get a random longitude.
     *
     * @return a random longitude.
     */
    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    /**
     * Get a random latitude.
     *
     * @return a random latitude.
     */
    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    /**
     * Get a random Date.
     *
     * @return a random Date.
     */
    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

}
