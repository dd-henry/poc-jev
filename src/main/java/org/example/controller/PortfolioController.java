package org.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortfolioController {

    @GetMapping({"/jev", "/jev/"})
    public String jev() {
        return "forward:/dashboard.html";
    }
}
