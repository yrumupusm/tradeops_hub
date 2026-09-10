package io.tradeops.law;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/law-search")
public class LawController {
  private final LawService service;

  public LawController(LawService service) {
    this.service = service;
  }

  @PostMapping("/ask")
  public Object ask(
      @RequestBody LawService.Question question, Authentication actor, HttpServletRequest request) {
    return service.ask(
        question, actor.getName(), String.valueOf(request.getAttribute("correlationId")));
  }

  @GetMapping("/articles/{id}/history")
  public Object history(@PathVariable long id) {
    return service.history(id);
  }

  @GetMapping("/articles/{id}/diff")
  public Object diff(@PathVariable long id, @RequestParam long compareWith) {
    return service.diff(id, compareWith);
  }
}
