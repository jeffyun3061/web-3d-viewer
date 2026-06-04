package com.medisection.backend.dto;

import com.medisection.backend.domain.scene.SceneCategory;
import com.medisection.backend.domain.user.EducationLevel;
import com.medisection.backend.domain.user.Persona;
import com.medisection.backend.domain.user.ThemeColor;

public class OnboardDto {

	private OnboardDto() {}

	public record OnboardRequest(
		String name,
		SceneCategory preferCategory,
		EducationLevel educationLevel,
		String specialized,
		Persona persona,
		ThemeColor themeColor) {
	}
}
