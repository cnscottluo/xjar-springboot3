package io.github.cnscottluo.xjar.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of(
                "message", "Hello from XJar + Spring Boot 3.1.5 + JDK 21!",
                "javaVersion", System.getProperty("java.version")
        );
    }

}
