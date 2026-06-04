package com.medisection.backend.controller;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TripoSplatStatusController {

	private static final int TRIPOSPLAT_PORT = 7860;

	@GetMapping("/integrations/triposplat/status")
	public ResponseEntity<Map<String, Object>> getStatus() {
		List<String> hosts = List.of(
			"host.docker.internal",
			"127.0.0.1",
			"localhost");

		for (String host : hosts) {
			if (isReachable(host, TRIPOSPLAT_PORT)) {
				return ResponseEntity.ok(Map.of(
					"running", true,
					"url", "http://127.0.0.1:7860",
					"message", "TripoSplat server is running."));
			}
		}

		return ResponseEntity.ok(Map.of(
			"running", false,
			"url", "http://127.0.0.1:7860",
			"message", "TripoSplat server is not running."));
	}

	private boolean isReachable(String host, int port) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), (int)Duration.ofSeconds(2).toMillis());
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}
}
