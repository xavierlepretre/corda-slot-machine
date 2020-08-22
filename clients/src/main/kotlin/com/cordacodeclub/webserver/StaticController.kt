package com.cordacodeclub.webserver

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class StaticController {
  @GetMapping("/login")
  fun login(): String {
    return "login.html"
  }

  @GetMapping("/cookies")
  fun cookies(): String {
    return "cookies.html"
  }
}
