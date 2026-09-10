package io.tradeops.bis;

import io.tradeops.error.OperationException;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class BisDiscovery {
  public String discover(String html, String base, String source) {
    var document = Jsoup.parse(html, base);
    Set<String> candidates = new LinkedHashSet<>();
    String heading = source.equals("DPL") ? "denied persons list" : "entity list";
    for (Element title : document.select("h1,h2,h3,h4,h5,h6")) {
      if (!title.text().toLowerCase(Locale.ROOT).contains(heading)) continue;
      Element block = title.nextElementSibling();
      while (block != null && !block.tagName().matches("h[1-6]")) {
        for (Element link : block.select("a[href]")) {
          String href = link.absUrl("href");
          String context = (link.text() + " " + block.text() + " " + href).toLowerCase(Locale.ROOT);
          boolean type = source.equals("DPL") ? context.contains(".txt") : context.contains(".csv");
          if (type
              && (href.toLowerCase(Locale.ROOT).matches(".*\\.(txt|csv)([?#].*)?$")
                  || link.text().toLowerCase(Locale.ROOT).contains("download")))
            candidates.add(href);
        }
        block = block.nextElementSibling();
      }
    }
    if (candidates.isEmpty())
      for (Element link : document.select("a[href]")) {
        String path = link.absUrl("href").toLowerCase(Locale.ROOT);
        if (source.equals("DPL") && path.matches(".*denied[-_]persons[-_]list[^/]*\\.txt([?#].*)?$")
            || source.equals("EL") && path.matches(".*entity[-_]list[^/]*\\.csv([?#].*)?$"))
          candidates.add(link.absUrl("href"));
      }
    if (candidates.size() != 1)
      throw new OperationException(
          candidates.isEmpty() ? "DOWNLOAD_LINK_MISSING" : "DOWNLOAD_LINK_AMBIGUOUS", 422);
    return candidates.iterator().next();
  }
}
