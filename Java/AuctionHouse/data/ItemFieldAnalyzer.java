package AuctionHouse.data;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Provides a specialized {@link Analyzer} for item fields such as item names and descriptions. This
 * analyzer customizes the tokenization process to handle different types of item text data to
 * optimize for search functionalities such as partial matches.
 *
 * @author Cammy
 */
public class ItemFieldAnalyzer extends Analyzer {

  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    Tokenizer source = new StandardTokenizer();
    TokenStream filter = source;

    if (fieldName.equals("item_name") || fieldName.equals("item_description")) {
      // Apply lowercasing and NGram filter for partial matches
      filter = new LowerCaseFilter(filter);
      filter = new NGramTokenFilter(filter, 3, 5, true);
    } else {
      filter = new LowerCaseFilter(filter);
      filter = new ASCIIFoldingFilter(filter);
    }

    return new TokenStreamComponents(source, filter);
  }
}
