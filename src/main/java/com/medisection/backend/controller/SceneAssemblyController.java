package com.medisection.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medisection.backend.dto.scene.DisassemblyLevelDto;
import com.medisection.backend.dto.scene.SceneAssemblyDto;
import com.medisection.backend.dto.scene.SceneSyncDto;
import com.medisection.backend.security.CustomUserDetails;
import com.medisection.backend.service.SceneAssemblyService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/scenes")
@RequiredArgsConstructor
public class SceneAssemblyController {

	private final SceneAssemblyService sceneAssemblyService;

	/**
	 * 프론트에서 추출한 GLTF node 조립 정보를 서버에 저장하는 진입점입니다.
	 */
	@PostMapping("/assembly")
	public ResponseEntity<Void> saveAssembly(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@RequestBody
		SceneAssemblyDto dto) {

		sceneAssemblyService.saveAssembly(userDetails.getUserId(), dto);
		return ResponseEntity.ok().build();
	}

	/**
	 * 카메라 기준점과 컴포넌트 matrix를 동기화해 사용자의 3D 학습 상태를 보존합니다.
	 */
	@PutMapping("/{sceneId}/sync")
	public ResponseEntity<Void> syncScene(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable("sceneId")
		Long sceneId,
		@RequestBody
		SceneSyncDto dto) {

		sceneAssemblyService.syncSceneState(userDetails.getUserId(), sceneId, dto);
		return ResponseEntity.ok().build();
	}

	/**
	 * 기본 모델과 사용자 조립 모델을 ZIP으로 내려주는 다운로드 API입니다.
	 */
	@GetMapping("/{sceneId}/viewer")
	public ResponseEntity<byte[]> getViewer(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable("sceneId")
		Long sceneId,
		@RequestParam(name = "target", required = false, defaultValue = "both")
		String target) {

		byte[] zipBytes = sceneAssemblyService.getViewerZip(userDetails.getUserId(), sceneId, target);

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"viewer_assets.zip\"")
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.body(zipBytes);
	}

	/**
	 * 사용자가 저장한 분해 정도를 조회합니다.
	 */
	@GetMapping("/{sceneId}/disassembly-level")
	public ResponseEntity<DisassemblyLevelDto> getDisassemblyLevel(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable("sceneId")
		Long sceneId) {

		DisassemblyLevelDto response = sceneAssemblyService.getDisassemblyLevel(userDetails.getUserId(), sceneId);
		return ResponseEntity.ok(response);
	}

	/**
	 * 단면/분해 슬라이더 값을 서버에 저장합니다.
	 */
	@PutMapping("/{sceneId}/disassembly-level")
	public ResponseEntity<Void> updateDisassemblyLevel(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable("sceneId")
		Long sceneId,
		@RequestBody
		DisassemblyLevelDto dto) {

		sceneAssemblyService.updateDisassemblyLevel(userDetails.getUserId(), sceneId, dto.getDisassemblyLevel());
		return ResponseEntity.ok().build();
	}
}
