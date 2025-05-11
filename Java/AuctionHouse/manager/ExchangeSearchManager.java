package AuctionHouse.manager;

import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.util.Timer;
import com.pixelhideaway.commons.server.injection.Manager;
import com.pixelhideaway.core.data.item.ItemDao;
import com.pixelhideaway.core.data.item.ItemData;
import com.pixelhideaway.core.data.player.HideawayPlayer;
import com.pixelhideaway.core.data.player.PlayerDao;
import com.pixelhideaway.core.util.item.ItemUtil;
import com.pixelhideaway.grandexchange.GrandExchange;
import com.pixelhideaway.grandexchange.utils.ItemFieldAnalyzer;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manager for handling Lucene Indexing and Search Queries for the Grand Exchange
 *
 * @author Cammy
 */
@Slf4j
@Singleton
@AutoService(Manager.class)
public final class ExchangeSearchManager implements Manager {

  private final ItemDao itemDao;
  private final PlayerDao playerDao;

  @Getter private final Directory itemIndexDirectory;
  @Getter private final Directory shopIndexDirectory;

  @Inject
  public ExchangeSearchManager(
      @NotNull GrandExchange plugin, @NotNull PlayerDao playerDao, @NotNull ItemDao itemDao) {
    this.itemDao = itemDao;
    this.playerDao = playerDao;

    log.info("Initializing Grand Exchange Lucene Search Manager - Validating Index Directories...");
    try {
      File itemIndexDir = new File(plugin.getDataFolder(), "exchange_item_index");
      if (!itemIndexDir.exists()) {
        if (!itemIndexDir.mkdirs()) log.error("Could not make item index directory");
      }

      File shopIndexDir = new File(plugin.getDataFolder(), "exchange_shop_index");
      if (!shopIndexDir.exists()) {
        if (!shopIndexDir.mkdirs()) log.error("Could not make shop index directory");
      }

      this.itemIndexDirectory = FSDirectory.open(itemIndexDir.toPath());
      this.shopIndexDirectory = FSDirectory.open(shopIndexDir.toPath());
    } catch (SecurityException | IOException | InvalidPathException e) {
      log.error("Failed to create Grand Exchange Index Directories: ", e);
      Sentry.captureException(e);
      throw new IllegalStateException("Failed to instantiate Exchange Search Manager");
    }
  }

  /**
   * Initializes the Lucene indexes. Load all items using the item DAO and builds an index for them.
   */
  @Override
  public void onEnable() {
    this.itemDao
        .loadAll()
        .thenAcceptAsync(
            items -> {
              log.info("Building Lucene Search Index Document for {} items", items.size());
              try (Analyzer analyzer = new ItemFieldAnalyzer()) {
                buildItemIndex(itemIndexDirectory, analyzer, items);
                printSampleIndexedData(itemIndexDirectory);
                testItemSearchFunctionality();
              } catch (IOException e) {
                log.error("Failed to create an item data index document: ", e);
                throw new RuntimeException(e);
              }
            })
        .thenRun(
            () -> {
              // todo shop index
            })
        .exceptionally(
            e -> {
              log.error("Failed to create Lucene Index Analyzer");
              Sentry.captureException(e);
              return null;
            });
  }

  /* Setup */

  /**
   * Builds a Lucene index in the specified directory using the provided analyzer and item data.
   *
   * @param directory The directory to store the Lucene index.
   * @param analyzer The analyzer to use for indexing.
   * @param dataItems The item data to index.
   * @throws IOException if there is an issue writing to the index.
   */
  private void buildItemIndex(
      @NonNull Directory directory, @NonNull Analyzer analyzer, Set<ItemData> dataItems)
      throws IOException {
    Timer.record(
        () -> {
          IndexWriterConfig config = new IndexWriterConfig(analyzer);

          // Clear existing to overwrite
          config.setOpenMode(OpenMode.CREATE);

          try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (ItemData item : dataItems) {
              Document doc = createItemDocument(item);
              writer.addDocument(doc);
            }
            writer.commit();
          } catch (Exception e) {
            Sentry.captureException(e);
            log.error(
                "Failed to build item index dir: {} with items: {}",
                directory,
                dataItems.size(),
                e);
          }
        });
  }

  /**
   * Creates a Lucene Document from an item data object. <br>
   * This method should always be called async
   *
   * @param item The item data to convert into a document.
   * @return A Lucene Document representing the item data.
   */
  private Document createItemDocument(@NonNull ItemData item) {
    Document doc = new Document();

    // Indexing and storing the item ID
    doc.add(new StringField("item_id", item.getId(), Store.YES));

    doc.add(
        new TextField("item_name", ItemUtil.getReadableComponent(item.getDisplayName()), Store.NO));
    doc.add(new StringField("item_type", item.getType().toString(), Store.NO));
    doc.add(new StringField("item_rarity", item.getRarity().toString(), Store.NO));

    if (item.getCollection() != null && !item.getCollection().isEmpty()) {
      doc.add(new StringField("item_collection", item.getCollection(), Store.NO));
    }

    if (item.getLore() != null && item.getLore().length > 0) {
      doc.add(new TextField("item_description", item.getLoreString(), Store.NO));
    }

    return doc;
  }

  /**
   * Creates a Lucene Document from an item data object. <br>
   * This method should always be called async
   *
   * @param shopData The shop data to create an index document from.
   * @return An Index Document for Lucene Lookup Queries
   */
  private Document createShopDocument(@NonNull PlayerShopData shopData) {
    Document doc = new Document();

    // Indexing and storing the shop ID
    doc.add(new StringField("shop_id", String.valueOf(shopData.id()), Store.YES));

    doc.add(new StringField("shop_name", shopData.shopName(), Store.NO));
    doc.add(new TextField("shop_description", shopData.description(), Store.NO));

    HideawayPlayer owner = this.playerDao.load(shopData.shopOwner()).join();
    if (owner != null) {
      String ownerName = owner.getUsername();
      doc.add(new StringField("shop_owner", ownerName, Store.NO));
    }

    return doc;
  }

  /* Integrity */

  /**
   * Updates the index for a single item.
   *
   * @param updatedItem The item data to update in the index.
   */
  public void updateItemIndex(@NonNull ItemData updatedItem) {
    try (IndexWriter writer =
        new IndexWriter(itemIndexDirectory, new IndexWriterConfig(new ItemFieldAnalyzer()))) {
      Document doc = createItemDocument(updatedItem);
      writer.updateDocument(new Term("id", updatedItem.getId()), doc);
      writer.commit();
    } catch (IOException e) {
      Sentry.captureException(e);
      log.error("Failed to create an item data index document: {}", updatedItem.getId(), e);
    }
  }

  /**
   * Removes an item from the index by its ID.
   *
   * @param itemId The ID of the item to remove from the index.
   */
  public void removeFromItemIndex(@NonNull String itemId) {
    try (IndexWriter writer =
        new IndexWriter(itemIndexDirectory, new IndexWriterConfig(new StandardAnalyzer()))) {
      writer.deleteDocuments(new Term("id", itemId));
      writer.commit();
    } catch (IOException e) {
      Sentry.captureException(e);
      log.error("Failed to delete item data from index: {}", itemId, e);
    }
  }

  /**
   * Optimizes the index by merging all segments into a single segment. This operation is
   * resource-intensive and should be used sparingly, and called async.
   *
   * @param directory The directory containing the index to optimize.
   */
  public void optimizeItems(@NonNull Directory directory) {
    Timer.record(
        "Optimize Item Search Index",
        () -> {
          try (IndexWriter writer =
              new IndexWriter(directory, new IndexWriterConfig(new ItemFieldAnalyzer()))) {
            writer.forceMerge(1);
          } catch (IOException e) {
            Sentry.captureException(e);
            log.error("Failed to optimize the index: {}", directory, e);
          }
        });
  }

  /* Query */

  /**
   * Searches the item index for items matching the specified query text.
   *
   * @param queryText The text to search for.
   * @param limit The maximum number of results to return.
   * @return A list of item IDs that match the query.
   */
  public CompletableFuture<List<String>> searchItemIds(@NonNull String queryText, int limit) {
    return CompletableFuture.supplyAsync(
        () -> {
          List<String> itemIds = new ArrayList<>();

          Timer.record(
              "ItemData Search for '" + queryText + "'",
              () -> {
                try (IndexReader reader = DirectoryReader.open(itemIndexDirectory)) {
                  IndexSearcher searcher = new IndexSearcher(reader);
                  Map<String, Float> boosts = new HashMap<>();
                  boosts.put("item_name", 2.0f);
                  boosts.put("item_type", 1.5f);
                  boosts.put("item_description", 1.25f);
                  boosts.put("item_collection", 1.0f);
                  boosts.put("item_rarity", 1.0f);

                  MultiFieldQueryParser parser =
                      new MultiFieldQueryParser(
                          new String[] {
                            "item_name",
                            "item_description",
                            "item_collection",
                            "item_type",
                            "item_rarity"
                          },
                          new ItemFieldAnalyzer(),
                          boosts);

                  // Add fuzzy query logic (spelling errors/differences)
                  String fuzzyQuery =
                      Arrays.stream(queryText.split("\\s+"))
                          .map(word -> word + "~")
                          .collect(Collectors.joining(" "));

                  Query query = parser.parse(fuzzyQuery);
                  TopDocs topDocs = searcher.search(query, limit);
                  for (ScoreDoc sd : topDocs.scoreDocs) {
                    Document doc = searcher.doc(sd.doc);
                    itemIds.add(doc.get("item_id"));
                  }
                } catch (IOException | ParseException e) {
                  Sentry.captureException(e);
                  log.error("Failed to search item ids: {}", queryText, e);
                }
              });
          return itemIds;
        });
  }

  /**
   * Searches the item index for shops matching the specified query text.
   *
   * @param queryText The text to search for.
   * @param limit The maximum number of results to return.
   * @return A list of shop IDs that match the query.
   */
  public CompletableFuture<List<String>> searchShopIds(@NonNull String queryText, int limit) {
    return CompletableFuture.supplyAsync(
        () -> {
          List<String> shopIds = new ArrayList<>();
          try (IndexReader reader = DirectoryReader.open(shopIndexDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("shop_name", 2.0F);
            boosts.put("shop_owner", 1.3F);
            boosts.put("shop_description", 1.0F);

            MultiFieldQueryParser parser =
                new MultiFieldQueryParser(
                    new String[] {"shop_name", "shop_description", "shop_owner"},
                    new StandardAnalyzer(),
                    boosts);

            Query query = parser.parse(queryText);
            TopDocs topDocs = searcher.search(query, limit);
            for (ScoreDoc sd : topDocs.scoreDocs) {
              Document doc = searcher.doc(sd.doc);
              shopIds.add(doc.get("shop_id"));
            }

          } catch (ParseException | IOException e) {
            Sentry.captureException(e);
            log.error("Failed to search shop ids: {}", queryText, e);
          }
          return shopIds;
        });
  }

  /* Testing - todo move to tests? */

  public void printSampleIndexedData(@NonNull Directory directory) {
    try (IndexReader reader = DirectoryReader.open(directory)) {
      if (reader.maxDoc() > 0) {
        Document doc = reader.document(0);
        log.info("Sample Document: {}", doc);
      } else {
        log.warn("No documents in index!");
      }
    } catch (IOException e) {
      Sentry.captureException(e);
      log.error("Failed to read index for debugging", e);
    }
  }

  public void testItemSearchFunctionality() {
    String testQuery = "cheese";
    searchItemIds(testQuery, 10)
        .thenAccept(
            itemIds -> {
              if (itemIds.isEmpty()) {
                log.warn("No results found for known good query! -> {}", testQuery);
              } else {
                log.info("Test Search results for {} -> {}", testQuery, itemIds);
              }
            });
  }
}
