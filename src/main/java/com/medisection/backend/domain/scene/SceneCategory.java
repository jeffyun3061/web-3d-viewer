package com.medisection.backend.domain.scene;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

public enum SceneCategory {
	HUMAN_ANATOMY("human_anatomy", "Human Anatomy"),
	CARDIOVASCULAR_SYSTEM("cardiovascular_system", "Cardiovascular System"),
	NERVOUS_SYSTEM("nervous_system", "Nervous System"),
	MUSCULOSKELETAL_SYSTEM("musculoskeletal_system", "Musculoskeletal System"),
	DIGESTIVE_SYSTEM("digestive_system", "Digestive System"),
	MANUFACTURING_ENGINEERING("manufacturing_engineering", "Manufacturing Engineering"),
	ROBOTICS_ENGINEERING("robotics_engineering", "Robotics Engineering"),
	AUTOMOTIVE_ENGINEERING("automotive_engineering", "Automotive Engineering"),
	AEROSPACE_ENGINEERING("aerospace_engineering", "Aerospace Engineering");

	private final String value;

	@Getter
	private final String displayName;

	SceneCategory(String value, String displayName) {
		this.value = value;
		this.displayName = displayName;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	@JsonCreator
	public static SceneCategory fromValue(String value) {
		String normalizedInput = normalize(value);
		for (SceneCategory field : values()) {
			if (normalize(field.value).equals(normalizedInput)
				|| normalize(field.name()).equals(normalizedInput)
				|| normalize(field.displayName).equals(normalizedInput)) {
				return field;
			}
		}
		throw new IllegalArgumentException("Unknown SceneCategory: " + value);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.replace("-", "").replace("_", "").replace(" ", "").toLowerCase();
	}
}
