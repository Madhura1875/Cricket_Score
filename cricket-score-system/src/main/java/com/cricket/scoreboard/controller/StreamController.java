package com.cricket.scoreboard.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cricket.scoreboard.service.LiveUpdateService;

@RestController
@RequestMapping("/api")
public class StreamController {

    private final LiveUpdateService live;

    public StreamController(LiveUpdateService live) {
        this.live = live;
    }

    /** Server-Sent Events: every score change arrives here as an event named "match". */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return live.subscribe();
    }
}
