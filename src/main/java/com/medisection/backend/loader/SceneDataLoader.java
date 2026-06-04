package com.medisection.backend.loader;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.medisection.backend.domain.scene.SceneCategory;
import com.medisection.backend.domain.scene.SceneInformation;
import com.medisection.backend.domain.user.User;
import com.medisection.backend.repository.SceneInformationRepository;
import com.medisection.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("!test")
public class SceneDataLoader implements CommandLineRunner {

	private final UserRepository userRepository;
	private final SceneInformationRepository sceneRepository;
	private final PasswordEncoder passwordEncoder;
	private final ResourcePatternResolver resourcePatternResolver;

	public SceneDataLoader(
		UserRepository userRepository,
		SceneInformationRepository sceneRepository,
		PasswordEncoder passwordEncoder,
		ResourcePatternResolver resourcePatternResolver) {
		this.userRepository = userRepository;
		this.sceneRepository = sceneRepository;
		this.passwordEncoder = passwordEncoder;
		this.resourcePatternResolver = resourcePatternResolver;
	}

	@Override
	public void run(String... args) {
		log.info("Starting Scene Data Loader...");

		User defaultUser = userRepository.findById(1L).orElseGet(() -> userRepository.save(User.builder()
			.username("admin")
			.password(passwordEncoder.encode("admin1234!"))
			.name("Administrator")
			.isMockUser(false)
			.onBoardingCompleted(true)
			.build()));

		Set<String> sceneNames = findSceneNames();
		if (sceneNames.isEmpty()) {
			log.warn("No bundled GLTF scenes found.");
			return;
		}

		for (String sceneName : sceneNames) {
			processScene(defaultUser, sceneName);
		}

		log.info("Scene Data Loader finished.");
	}

	private void processScene(User user, String sceneName) {
		try {
			log.info("Processing Scene: {}", sceneName);
			sceneRepository.findByEngTitle(sceneName)
				.orElseGet(() -> sceneRepository.save(SceneInformation.builder()
					.title(sceneName)
					.engTitle(sceneName)
					.category(SceneCategory.HUMAN_ANATOMY)
					.assetPath(sceneName)
					.description("Bundled 3D study model for anatomy and medical observation")
					.participantsCount(0L)
					.defaultAlignmentId(0L)
					.build()));
		} catch (Exception e) {
			log.error("Failed to process scene: {}", sceneName, e);
		}
	}

	private Set<String> findSceneNames() {
		Set<String> sceneNames = new LinkedHashSet<>();
		findFileSystemSceneNames(sceneNames);
		findClasspathSceneNames(sceneNames);
		return sceneNames;
	}

	private void findFileSystemSceneNames(Set<String> sceneNames) {
		File assetsDir = new File("src/main/resources/assets");
		if (!assetsDir.exists() || !assetsDir.isDirectory()) {
			return;
		}

		File[] sceneDirs = assetsDir.listFiles(File::isDirectory);
		if (sceneDirs == null) {
			return;
		}

		for (File sceneDir : sceneDirs) {
			File configFile = new File(sceneDir, "config/assembly_config.json");
			if (configFile.exists() || hasEmbeddedGltf(sceneDir)) {
				sceneNames.add(sceneDir.getName());
			}
		}
	}

	private void findClasspathSceneNames(Set<String> sceneNames) {
		try {
			Resource[] resources = resourcePatternResolver.getResources("classpath*:assets/**/*_embedded*.gltf");
			for (Resource resource : resources) {
				String sceneName = extractSceneName(resource);
				if (sceneName != null && !sceneName.isBlank()) {
					sceneNames.add(sceneName);
				}
			}
		} catch (IOException e) {
			log.warn("Could not scan classpath GLTF assets.", e);
		}
	}

	private String extractSceneName(Resource resource) throws IOException {
		String url = URLDecoder.decode(resource.getURL().toExternalForm(), StandardCharsets.UTF_8);
		String marker = "/assets/";
		int start = url.indexOf(marker);
		if (start < 0) {
			marker = "assets/";
			start = url.indexOf(marker);
		}
		if (start < 0) {
			return null;
		}
		String rest = url.substring(start + marker.length());
		int end = rest.indexOf('/');
		if (end < 0) {
			return null;
		}
		return rest.substring(0, end);
	}

	private boolean hasEmbeddedGltf(File sceneDir) {
		File[] children = sceneDir.listFiles();
		if (children == null) {
			return false;
		}
		for (File child : children) {
			if (child.isDirectory() && hasEmbeddedGltf(child)) {
				return true;
			}
			String name = child.getName().toLowerCase();
			if (child.isFile() && name.contains("_embedded") && name.endsWith(".gltf")) {
				return true;
			}
		}
		return false;
	}
}
