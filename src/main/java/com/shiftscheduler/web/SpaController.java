package com.shiftscheduler.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Redirects SPA routes to index.html for client-side routing.
// Ignores APIs and static files.
@Controller
public class SpaController {

    @GetMapping({"/", "/{path:^(?!api|assets)[^.]*}", "/{path:^(?!api|assets)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}