package com.cricket.scoreboard.service;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes score updates to every connected browser using Server-Sent Events (SSE).
 * Browsers reconnect automatically, so no client library is needed.
 *
 * To run several app instances, replace publish() with a Redis / Kafka topic
 * and let each instance forward the messages to its own emitters.
 */
@Service
public class LiveUpdateService {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("connected"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
        log.debug("Published '{}' to {} client(s)", eventName, emitters.size());
    }

    public int clients() {
        return emitters.size();
    }

    /** Keeps proxies and load balancers from closing idle connections. */
    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
