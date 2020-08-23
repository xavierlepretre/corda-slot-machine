package com.cordacodeclub.webserver

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class StaticController {
  @GetMapping("/test")
  fun login(): String {
    return "test.html"
  }

  // we could add more here -- e.g. /about and /legal etc.
}
