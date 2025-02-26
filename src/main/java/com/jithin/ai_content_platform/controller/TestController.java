package com.jithin.ai_content_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ping")
public class TestController {

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return new ResponseEntity(HttpStatus.OK);
    }

}
