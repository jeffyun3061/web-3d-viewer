package com.medisection.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medisection.backend.domain.scene.SceneCategory;
import com.medisection.backend.dto.SceneDetailResponse;
import com.medisection.backend.dto.SceneListOrder;
import com.medisection.backend.dto.SceneListResponse;
import com.medisection.backend.dto.SceneRankResponse;
import com.medisection.backend.dto.SceneResponse;
import com.medisection.backend.security.CustomUserDetails;
import com.medisection.backend.service.SceneService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SceneController {

	private final SceneService sceneService;

	@GetMapping("/my/recent/scenes")
	public ResponseEntity<SceneResponse> getScenes(@AuthenticationPrincipal
	CustomUserDetails userDetails) {
		SceneResponse response = sceneService.getLearningScenes(userDetails.getUserId());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/scenes/ranks")
	public ResponseEntity<SceneRankResponse> getSceneRanks(
		@RequestParam(name = "category", required = false)
		SceneCategory category) {
		SceneRankResponse response = sceneService.getSceneRanks(category);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/scenes")
	public ResponseEntity<SceneListResponse> getSceneList(
		@RequestParam(name = "category", required = false)
		SceneCategory category,
		@RequestParam(name = "page", defaultValue = "1")
		int page,
		@RequestParam(name = "limit", defaultValue = "9")
		int limit,
		@RequestParam(name = "query", required = false)
		String query,
		@RequestParam(name = "order", defaultValue = "alphabetical")
		SceneListOrder order) { // 가나다순 → alphabetical,
								// 인기순 →popularity
		SceneListResponse response = sceneService.getScenes(category, page, limit, query, order);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/scenes/{sceneId}")
	public ResponseEntity<SceneDetailResponse> getSceneDetail(@PathVariable(name = "sceneId", required = true)
	Long sceneId) {
		SceneDetailResponse response = sceneService.getSceneDetail(sceneId);
		return ResponseEntity.ok(response);
	}
}
