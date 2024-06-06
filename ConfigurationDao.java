/**
 * Represents a Data Access Object for Configuration Data retrieved from the ConfigService.
 *
 * @author Cammy
 * @version 1.0-SNAPSHOT
 * @since 1.0-SNAPSHOT
 * @param <T> The Config Data Transfer Object
 * @param <U> The data type to transform the DTO into
 */
public abstract class ConfigurationDao<T extends ConfigDTO, U extends Config>
        extends InMemoryDao<U, String> {

    protected final ConfigurationClient configClient;
    protected final JavaPlugin plugin;
    protected final Logger logger;

    protected final Transformer<T, U> transformer;
    protected final ConfigType configType;

    private static final String SERVER_ENVIRONMENT = System.getenv("SERVER_ENVIRONMENT");

    protected final ExponentialBackoff backoff =
            ExponentialBackoff.builder()
                    .baseTime(300, TimeUnit.MILLISECONDS)
                    .maximumTime(5, TimeUnit.SECONDS)
                    .maximumRetries(3)
                    .build();

    public ConfigurationDao(
            @NotNull final ConfigType configType,
            @NotNull final JavaPlugin plugin,
            @NotNull final Transformer<T, U> transformer) {
        this.transformer = transformer;
        this.configType = configType;
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
        this.configClient =
                ClientManager.getClient(ConfigurationClient.class, "http://configservice:8080");
        // todo move service URL to env variable
    }

    protected Set<U> getConfigList() {
        return new HashSet<>(this.dataById.values());
    }

    public Set<String> getDataIds() {
        return this.dataById.keySet();
    }

    protected CompletableFuture<Set<U>> loadFromService() {
        return this.backoff
                .<Set<ConfigDTO>>attempt(
                        callback -> {
                            try {
                                return callback.success(configClient.getAllConfigs(configType));
                            } catch (Exception e) {
                                logger.error("Exception occurred while in the backoff: {}", e.getCause());
                                return callback.failure(e);
                            }
                        })
                .exceptionally(
                        e -> {
                            logger.error(
                                    "An error occurred while fetching all configs for type {} \n{}", configType, e);
                            Sentry.captureException(e);
                            return null;
                        })
                .thenApply(
                        cfgs -> {
                            Set<T> mapped = findDataType(cfgs);
                            return mapped.stream().map(this.transformer::transform).collect(Collectors.toSet());
                        });
    }

    public CompletableFuture<Set<U>> loadAll() {
        if (!this.dataById.isEmpty()) return CompletableFuture.completedFuture(getConfigList());
        long startTime = System.nanoTime();
        return loadFromService()
                .thenApply(
                        configs -> {
                            configs.forEach(
                                    cfg -> {
                                        if (cfg.hasScope(SERVER_ENVIRONMENT)) {
                                            dataById.put(cfg.getId(), cfg);
                                        }
                                    });
                            long endTime = System.nanoTime();
                            logger.info(
                                    "Loaded {} configs for {} - Took: {}ms",
                                    getConfigList().size(),
                                    configType,
                                    TimeUnit.NANOSECONDS.toMillis(endTime - startTime));
                            return getConfigList();
                        });
    }

    @SuppressWarnings("unchecked")
    private T convertType(@NotNull ConfigDTO cfg) {
        if (configType.getDataClass().isInstance(cfg)) {
            return (T) cfg;
        }
        throw new IllegalArgumentException(
                "ConfigDTO type attempting conversion is not of the expected type: "
                        + cfg
                        + " for type: "
                        + configType);
    }

    private Set<T> findDataType(Set<ConfigDTO> configs) {
        if (configs == null) {
            return new HashSet<>();
        }
        return configs.stream().map(this::convertType).collect(Collectors.toSet());
    }

    public @Nullable Map<String, U> getByIds(List<String> ids) {
        return ids.stream()
                .filter(dataById::containsKey)
                .collect(Collectors.toMap(id -> id, dataById::get));
    }

    // unused
    @Override
    public CompletableFuture<U> insert(U data) {
        return null;
    }

    // unused
    @Override
    public CompletableFuture<U> load(String s) {
        return null;
    }
}