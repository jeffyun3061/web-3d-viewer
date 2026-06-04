package com.medisection.backend.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.medisection.backend.domain.scene.SceneInformation;
import com.medisection.backend.repository.SceneInformationRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SceneModelController {

	private final SceneInformationRepository sceneInformationRepository;
	private final ResourcePatternResolver resourcePatternResolver;

	@GetMapping("/scenes/{sceneId}/model")
	public ResponseEntity<byte[]> getSceneModel(@PathVariable("sceneId") Long sceneId) throws IOException {
		SceneInformation scene = sceneInformationRepository.findById(sceneId)
			.orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));

		Resource model = findEmbeddedModel(scene)
			.orElseThrow(() -> new IllegalArgumentException("Embedded GLTF not found for scene: " + scene.getAssetPath()));

		byte[] body;
		try (var inputStream = model.getInputStream()) {
			body = inputStream.readAllBytes();
		}

		return ResponseEntity.ok()
			.contentType(MediaType.valueOf("model/gltf+json"))
			.cacheControl(CacheControl.noCache())
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + model.getFilename() + "\"")
			.body(body);
	}

	private Optional<Resource> findEmbeddedModel(SceneInformation scene) throws IOException {
		String assetPath = scene.getAssetPath();
		String classpathPattern = "classpath*:assets/" + assetPath + "/**/*_embedded*.gltf";
		Resource[] resources = resourcePatternResolver.getResources(classpathPattern);

		Optional<Resource> classpathModel = Arrays.stream(resources)
			.filter(Resource::exists)
			.sorted(Comparator.<Resource>comparingInt(this::modelPriority).thenComparing(Resource::getFilename,
				Comparator.nullsLast(String::compareToIgnoreCase)))
			.findFirst();
		if (classpathModel.isPresent()) {
			return classpathModel;
		}

		File assetDir = new File("src/main/resources/assets/" + assetPath);
		if (!assetDir.exists()) {
			return Optional.empty();
		}

		try (Stream<java.nio.file.Path> paths = Files.walk(assetDir.toPath())) {
			return paths
				.filter(path -> Files.isRegularFile(path))
				.filter(path -> path.getFileName().toString().toLowerCase().contains("_embedded"))
				.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".gltf"))
				.sorted(Comparator.<java.nio.file.Path>comparingInt(this::modelPriority)
					.thenComparing(path -> path.getFileName().toString()))
				.map(path -> (Resource)new FileSystemResource(path))
				.findFirst();
		}
	}

	private int modelPriority(Resource resource) {
		return modelPriority(resource.getFilename());
	}

	private int modelPriority(java.nio.file.Path path) {
		return modelPriority(path.getFileName().toString());
	}

	private int modelPriority(String filename) {
		String name = filename == null ? "" : filename.toLowerCase();
		if (name.contains("flatten")) {
			return 0;
		}
		if (name.contains("_embedded")) {
			return 1;
		}
		return 2;
	}
}
