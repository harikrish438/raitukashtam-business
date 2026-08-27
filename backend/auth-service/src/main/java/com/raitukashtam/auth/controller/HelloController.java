package com.raitukashtam.auth.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, secured world!";
    }
}

