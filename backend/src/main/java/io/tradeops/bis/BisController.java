package io.tradeops.bis;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BisController {
  private final BisCollectionService collection;
  private final BisRepository repository;
  private final BisSearchService search;

  public BisController(
      BisCollectionService collection, BisRepository repository, BisSearchService search) {
    this.collection = collection;
    this.repository = repository;
    this.search = search;
  }

  private String cid(HttpServletRequest r) {
    return r.getAttribute("correlationId").toString();
  }

  private Map<String, Object> publicRun(Map<String, Object> row) {
    var copy = new LinkedHashMap<>(row);
    copy.remove("file_path");
    return copy;
  }

  @GetMapping("/watchlist/sources")
  public Object sources() {
    return Map.of("items", repository.sources(), "settings", collection.settings());
  }

  @PostMapping("/watchlist/runs")
  public ResponseEntity<?> start(
      @RequestBody Start request, Authentication a, HttpServletRequest r) {
    return ResponseEntity.accepted().body(collection.start(request.source(), a.getName(), cid(r)));
  }

  @GetMapping("/watchlist/runs")
  public Object runs(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return page(page, size, false);
  }

  @GetMapping("/watchlist/files")
  public Object files(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return page(page, size, true);
  }

  @SuppressWarnings("unchecked")
  private Object page(int page, int size, boolean files) {
    BisSearchService.validatePage(page, size);
    var result = new LinkedHashMap<>(repository.runs(page, size, files));
    result.put(
        "items",
        ((List<Map<String, Object>>) result.get("items")).stream().map(this::publicRun).toList());
    return result;
  }

  @GetMapping("/watchlist/runs/{id}")
  public Object run(@PathVariable long id) {
    return Map.of("run", publicRun(repository.run(id)), "issues", repository.issues(id));
  }

  @GetMapping("/watchlist/runs/{id}/changes")
  public Object changes(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    BisSearchService.validatePage(page, size);
    return repository.changes(id, page, size);
  }

  @PostMapping("/watchlist/runs/{id}/approve")
  public void approve(@PathVariable long id, Authentication a, HttpServletRequest r) {
    collection.approve(id, a.getName(), cid(r));
  }

  @GetMapping("/watchlist/files/{id}/download")
  public ResponseEntity<Resource> download(
      @PathVariable long id, Authentication a, HttpServletRequest r) {
    var path = collection.file(id, a.getName(), cid(r));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(path.getFileName().toString())
                .build()
                .toString())
        .body(new FileSystemResource(path));
  }

  @PostMapping("/watchlist/search")
  public Object search(
      @RequestBody BisSearchService.Query query, Authentication a, HttpServletRequest request) {
    return search.search(query, a.getName(), cid(request));
  }

  @GetMapping("/watchlist/records/{id}")
  public Object detail(@PathVariable long id) {
    return search.detail(id);
  }

  @GetMapping("/watchlist/countries")
  public Object countries() {
    return search.countries();
  }

  @PostMapping("/watchlist/exports")
  public ResponseEntity<byte[]> export(
      @RequestBody BisSearchService.Query query, Authentication a, HttpServletRequest r) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bis-search.csv")
        .body(search.export(query, a.getName(), cid(r)));
  }

  @GetMapping("/search-history")
  public Object history(
      Authentication a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return search.history(a.getName(), page, size);
  }

  @DeleteMapping("/search-history/{id}")
  public void delete(Authentication a, @PathVariable long id, HttpServletRequest r) {
    search.deleteHistory(a.getName(), id, cid(r));
  }

  @GetMapping("/search-history/recent")
  public Object recent(Authentication a) {
    return search.recentHistory(a.getName());
  }

  @DeleteMapping("/search-history/recent/{id}")
  public void deleteRecent(Authentication a, @PathVariable long id, HttpServletRequest r) {
    search.deleteRecentHistory(a.getName(), id, cid(r));
  }

  @DeleteMapping("/search-history")
  public void clear(Authentication a, HttpServletRequest r) {
    search.deleteHistory(a.getName(), null, cid(r));
  }

  public record Start(String source) {}
}
