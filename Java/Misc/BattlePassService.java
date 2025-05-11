package Misc;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Spring Service to track player battle pass progress and manage status/rewards
 *
 * @author Cammy
 * @version 1.0-SNAPSHOT
 * @since 1.0-SNAPSHOT
 */
@Service
public class BattlePassService {

    private final Gson gson;
    private final Connection nats;
    private final Logger logger = LoggerFactory.getLogger(BattlePassService.class);

    private final BattlePassRepository battlePassRepository;
    private final PassProgressRepository passProgressRepository;
    private final BattlePassDataService dataService;

    @Autowired
    public BattlePassService(
            Gson gson,
            Connection nats,
            BattlePassRepository battlePassRepository,
            PassProgressRepository passProgressRepository,
            BattlePassDataService battlePassDataService
    ) {
        this.gson = gson;
        this.nats = nats;
        this.battlePassRepository = battlePassRepository;
        this.passProgressRepository = passProgressRepository;
        this.dataService = battlePassDataService;

        this.dataService.scanAndPopulateBattlePasses();
        this.dataService.reloadAllBattlePassTiers();
    }

    /* Battle Pass */

    /**
     * Retrieves an Battle Pass by its identifier.
     *
     * @param passIdentifier the identifier of the Battle Pass
     * @return the Battle Pass record, or null if not found
     */
    @Transactional
    public BattlePassData getBattlePass(String passIdentifier) {
        return battlePassRepository.findByIdentifier(passIdentifier)
                .map(BattlePassEntity::toData)
                .orElse(null);
    }

    /**
     * Retrieves a list of active Battle Passes.
     *
     * @return a list of active Battle Pass records
     */
    @Transactional
    public List<BattlePassData> getActivePasses() {
        return battlePassRepository.findActivePasses()
                .stream().map(BattlePassEntity::toData).toList();
    }

    /**
     * Retrieves a list of inactive Battle Passes.
     *
     * @return a list of inactive Battle Pass records
     */
    @Transactional
    public List<BattlePassData> getInactivePasses() {
        return battlePassRepository.findInactivePasses()
                .stream().map(BattlePassEntity::toData).toList();
    }


    /* Player Progression */

    private PassProgressEntity createNewPassProgress(UUID playerId, String battlePass) {
        // May need to look at this at a later date - currently assumes only 1 active
        if (battlePass == null) {
            List<BattlePassEntity> activePasses = battlePassRepository.findActivePasses();
            if (activePasses.isEmpty()) throw new IllegalStateException("No Active Battle Pass Found!");

            BattlePassEntity currentPass = activePasses.get(0);

            PassProgressEntity newProgressEntity = new PassProgressEntity(playerId, currentPass);
            return passProgressRepository.save(newProgressEntity);
        }
        return null;
    }

    /**
     * Retrieves or creates a Pass Progress entity for a player.
     *
     * @param playerId the ID of the player
     * @return the Pass Progress entity
     */
    @Transactional
    public PassProgressEntity getOrCreateProgressEntity(UUID playerId) {
        Optional<PassProgressEntity> optionalEntity = passProgressRepository.findByPlayerId(playerId);
        return optionalEntity.orElseGet(() -> createNewPassProgress(playerId, null));
    }

    /**
     * Retrieves the Battle Pass progress for a player.
     *
     * @param playerId the ID of the player
     * @return the Battle Pass progress record
     */
    @Transactional
    public BattlePassProgressRecord getPlayerProgress(UUID playerId) {
        return this.getOrCreateProgressEntity(playerId).toRecord();
    }

    /**
     * Should be used to update an existing progress entity/record with new data
     * @param playerId the ID of the player
     * @param progressRecord The {@link BattlePassProgressRecord} to update
     * @return The updated {@link BattlePassProgressRecord}
     */
    @Transactional
    public BattlePassProgressRecord updatePlayerProgress(UUID playerId, BattlePassProgressRecord progressRecord) {
        PassProgressEntity progressEntity = this.getOrCreateProgressEntity(playerId);
        progressEntity.updateFromRecord(progressRecord);
        this.passProgressRepository.save(progressEntity);
        return progressEntity.toRecord();
    }

    @Transactional
    public BattlePassProgressRecord resetPlayerProgress(UUID playerId) {
        PassProgressEntity progressEntity = this.getOrCreateProgressEntity(playerId);
        progressEntity.reset();
        this.passProgressRepository.save(progressEntity);
        return progressEntity.toRecord();
    }

    /**
     * Adds experience to a player's Battle Pass progress.
     *
     * @param playerId the ID of the player
     * @param xpAmount the amount of experience to add
     * @return The updated {@link BattlePassProgressRecord}
     */
    @Transactional
    public BattlePassProgressRecord addExperience(UUID playerId, long xpAmount) {
        PassProgressEntity progressEntity = getOrCreateProgressEntity(playerId);

        BattlePassTierData tier = calculateNewExperience(progressEntity, xpAmount);
        if (tier == null) {
            return null;
        }

        progressEntity.setTotalXp(progressEntity.getTotalXp() + xpAmount);

        if (progressEntity.getCurrentTier() != tier.getTier()) {
            publishPassProgression(progressEntity, tier);
        }

        this.passProgressRepository.save(progressEntity);
        return progressEntity.toRecord();
    }

    /**
     * Calculates the new experience and updates the progress entity.
     *
     * @param progressEntity the Pass Progress entity
     * @param xpAmount the amount of experience to add
     * @return the current tier data after updating the experience
     */
    private BattlePassTierData calculateNewExperience(PassProgressEntity progressEntity, long xpAmount) {
        long totalPassXp = dataService
                .getSortedTiers(progressEntity.getBattlePass().getIdentifier())
                .stream()
                .mapToLong(BattlePassTierData::getXpRequired)
                .sum();
        if (xpAmount > totalPassXp) {
            logger.warn("Attempted supersede allowed xp");
            return null;
        }

        BattlePassTierData currentTier = dataService.getCurrentTierFromPass(progressEntity);

        if (currentTier == null) {
            throw new IllegalStateException("Battle Pass Progression Entity Must Have a Valid Current Tier! Got NULL");
        }

        long currentXp = progressEntity.getCurrentXp();
        long newXp = currentXp + xpAmount;

        while (newXp >= currentTier.getXpRequired()) {

            newXp -= currentTier.getXpRequired();
            currentTier = dataService.getNextTierInProgress(progressEntity);
            if (currentTier == null) {
                processCompletion(progressEntity);
                return null;
            }

            progressEntity.setCurrentTier(currentTier.getTier());
        }

        progressEntity.setCurrentXp(newXp);
        return currentTier;
    }

    /**
     * Processes the completion of an Battle Pass for a player.
     *
     * @param progressEntity the Pass Progress entity
     */
    private BattlePassTierData processCompletion(PassProgressEntity progressEntity) {
        logger.error("Process completed not implemented");
        // todo completed final tier?
        // a bit unsure what we should actually do here tbh
        // nats the completed record?

        progressEntity.setComplete(true);
        passProgressRepository.save(progressEntity);

        return null;
    }


    /* Store Purchases | Premium Users */

    /**
     * Retrieves the progress records of all premium users.
     *
     * @return a list of Battle Pass progress records for premium users
     */
    @Transactional
    public List<BattlePassProgressRecord> getAllPremiumUserProgress() {
        return this.passProgressRepository.findByHasPremiumTrue().stream().map(PassProgressEntity::toRecord).toList();
    }

    /**
     * Retrieves the progress records of premium users for a specific Battle Pass.
     *
     * @param passIdentifier the identifier of the Battle Pass
     * @return a list of Battle Pass progress records for premium users in the specified pass
     */
    @Transactional
    public List<BattlePassProgressRecord> getPremiumUserProgress(String passIdentifier) {
        return this.passProgressRepository.findByHasPremiumTrueAndBattlePass_Identifier(passIdentifier)
                .stream().map(PassProgressEntity::toRecord).toList();
    }

    /**
     * Processes a player's purchase of the premium Battle Pass.
     *
     * @param playerId the ID of the player
     */
    @Transactional
    public BattlePassProgressRecord playerPurchasePremium(UUID playerId) {
        // todo payment verification?
        boolean paymentSuccess = true;

        if (paymentSuccess) {
            PassProgressEntity progressEntity = getOrCreateProgressEntity(playerId);
            progressEntity.setPremium(true);
            return passProgressRepository.save(progressEntity).toRecord();
        }

        return null;
    }


    /**
     * Processes a player's purchase of tiers in the Battle Pass.
     *
     * @param playerId the ID of the player
     * @param tiers the number of tiers purchased
     * @return The updated {@link BattlePassProgressRecord} or null
     */
    @Transactional
    public BattlePassProgressRecord playerPurchaseTiers(UUID playerId, int tiers) {
        // todo payment verification?
        boolean paymentSuccess = true;

        if (paymentSuccess) {
            PassProgressEntity progressEntity = getOrCreateProgressEntity(playerId);
            int currentTier = progressEntity.getCurrentTier();
            int newTier = currentTier + tiers;

            int maxTier = dataService.getSortedTiers(progressEntity.getBattlePass().getIdentifier()).size();
            if (newTier > maxTier) {
                newTier = maxTier;
                processCompletion(progressEntity);
            }

            BattlePassTierData tierData = dataService.getTierDataFromPass(progressEntity, newTier);
            if (tierData == null) {
                throw new IllegalStateException("Battle Pass Progression Entity Must Have a Valid Current Tier! Got NULL");
            }

            progressEntity.setCurrentTier(newTier);
            passProgressRepository.save(progressEntity);
            return progressEntity.toRecord();
        }

        return null;
    }


    /* Utils */

    private void publishPassProgression(PassProgressEntity progressEntity, BattlePassTierData rewardToGive) {
        nats.publish("namespace.rewards.battlepass.player_progress", gson.toJson(progressEntity.toRecord()).getBytes(
                StandardCharsets.UTF_8));
    }

}