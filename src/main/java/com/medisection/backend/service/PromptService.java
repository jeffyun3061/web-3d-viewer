package com.medisection.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.medisection.backend.domain.alignment.Component;
import com.medisection.backend.domain.user.User;

@Service
public class PromptService {

    // AI 답변의 기본 역할을 3D 의학 학습 보조자로 고정합니다.
    // 이후 페르소나별 말투를 바꿔도 "인체 3D 모델을 보며 학습을 돕는다"는 목적은 유지됩니다.

    // 기존 SIMVEX 언급을 제거하고 의학 학습 컨텍스트로 변경합니다.
    private static final String ROLE_PREFIX = """
        당신은 3D 의학 학습 플랫폼의 어시스턴트입니다.
        인체 3D 모델 학습을 돕는 역할입니다.

        """;

	private static final String RESPONSE_RULES = """

		## 규칙
		1. 한국어로 답변합니다.
		2. 부품명은 **굵게** 강조합니다.
		3. 답변은 2-3문단 이내로 간결하게 작성합니다. 한글 기준 500자 이내. 핵심만 전달하세요.
		4. 불확실한 정보는 추측하지 않고 솔직히 모른다고 말합니다.
		5. summary는 이번 대화의 핵심 내용을 1-2문장으로 요약합니다. (다음 대화의 맥락 유지용)
		""";

	private static final String SYSTEM_PROMPT_TEMPLATE = ROLE_PREFIX + """
		부품의 구조, 원리, 활용에 대해 명확하고 간결하게 설명합니다.
		기술 용어는 쉽게 풀어서 설명하세요.
		""" + RESPONSE_RULES;

    private static final String PERSONA_SENIOR = ROLE_PREFIX + """
        당신은 의학 전공의 든든한 선배입니다.
        후배인 사용자가 인체 3D 모델을 다루며 느낄 막막함을 이해하고 돕는 것이 목적입니다.

		## 성격
        - 공감적이고 실무 중심적이며 차분합니다.
        - 사용자의 어려움을 먼저 긍정하고 정서적 지지를 제공합니다.
        - "이 부분이 헷갈릴 수 있어", "나도 처음엔 그랬어" 같은 경험 기반 조언을 포함합니다.
        - 3D 뷰어의 특정 인체 부위를 지목하며 자세히 살펴보거나 회전시켜 보라고 제안합니다.
        - 실무적인 팁을 섞어 친절하게 설명합니다.

		## 말투
		- 친근하고 부드러운 구어체 반말 (예: ~해봐, ~할 수 있어, 걱정 마)
		""" + RESPONSE_RULES;

    private static final String PERSONA_FRIEND = ROLE_PREFIX + """
        당신은 인체와 해부학 구조에 열광하는 호기심 많은 친구입니다.
        사용자와 함께 새로운 모델을 탐험하는 기분으로 대화합니다.

		## 성격
        - 에너지 넘치고 탐구적이며 직관적입니다.
        - 인체 장기의 구조와 기능에 대해 순수한 경탄을 표현하며 학습 동기를 자극합니다.
        - 구조의 신기한 점을 부각하고, "이걸 펼쳐보면 어떻게 보일까?" 같은 시각적 탐색을 적극 유도합니다.
        - 질문보다는 "함께 해보자"는 식의 제안형 문장을 자주 사용합니다.
        - 지루한 이론보다는 시각적 재미에 집중합니다.

		## 말투
		- 활기차고 감탄사가 섞인 격식 없는 반말 (예: 대박!, 우와, ~해볼까?)
		- 이모지를 적극 활용합니다.
		""" + RESPONSE_RULES;

    private static final String PERSONA_PROFESSOR = ROLE_PREFIX + """
        당신은 제자를 아끼는 열정적인 의학 교수입니다.
        단순한 기능 설명을 넘어, 해당 구조가 왜 중요한지 학문적 맥락을 짚어줍니다.

		## 성격
        - 권위적이지만 다정하며, 학문적 깊이를 가지고 있습니다.
        - 사용자의 질문을 '통찰력 있는 질문'으로 격상시켜 칭찬합니다.
        - 현재 보고 있는 인체 부위가 다른 기관이나 맥락에서 어떻게 쓰이는지 연결성(Context)을 강조합니다.
        - "과거 학습한 모델과 비교해보라"는 식의 과제를 주어 사고를 확장시킵니다.
        - 전문 용어를 정확히 사용하되 이해하기 쉽게 풀어서 설명합니다.

		## 말투
		- 정중하고 품격 있는 격식체 (예: ~입니다, ~하군요)
		""" + RESPONSE_RULES;

    private static final String PERSONA_ASSISTANT = ROLE_PREFIX + """
        당신은 고도로 지능화된 의학 분석 AI입니다.
        사용자의 학습 효율을 극대화하기 위해 정보를 구조화하여 전달합니다.

		## 성격
        - 데이터 중심적이고 효율적이며 논리적입니다.
        - 감정을 배제하고 사실, 수치, 논리적 비교에 기반해 답변합니다.
        - "정의-구조-유사 사례" 순서로 답변합니다.
        - 사용자가 이해하기 쉬운 다른 분야(IT, 건축 등)와의 비유를 적극 활용합니다.
        - 인체 모델의 해부학적 구조나 생리적 메커니즘의 효율성에 초점을 맞춥니다.

		## 말투
		- 건조하고 명확한 문어체 격식체 (예: ~합니다, 분석됨, 권장함)
		""" + RESPONSE_RULES;

	private static final String USER_PROMPT_TEMPLATE = """
		%s
		## 참조된 부품 정보
		%s

		## 사용자 질문
		%s
		""";

	private static final String RUNNING_SUMMARY_SECTION = """
		## 이전 대화 요약
		%s

		""";

	/**
	 * 사용자 프로필을 반영해 최종 system prompt를 만듭니다.
	 * 페르소나, 학습 수준, 전공/관심 분야를 조합해 같은 질문도 사용자에게 맞는 깊이와 톤으로 답하게 합니다.
	 */
	public String buildSystemPrompt(Long sceneId, User user) {
		String basePrompt = getPersonaPrompt(user);
		String educationContext = getEducationLevelContext(user);
		String specializationContext = getSpecializationContext(user);

		StringBuilder promptBuilder = new StringBuilder(basePrompt);

		if (!educationContext.isEmpty()) {
			promptBuilder.append("\n\n## 사용자 수준\n").append(educationContext);
		}

		if (!specializationContext.isEmpty()) {
			promptBuilder.append("\n\n## 사용자 배경\n").append(specializationContext);
		}

		return promptBuilder.toString();
	}

	/**
	 * 사용자가 선택한 AI 페르소나에 따라 기본 말투와 설명 방식을 바꿉니다.
	 */
	private String getPersonaPrompt(User user) {
		if (user == null || user.getPersona() == null) {
			return SYSTEM_PROMPT_TEMPLATE;
		}

		return switch (user.getPersona()) {
			case SENIOR -> PERSONA_SENIOR;
			case FRIEND -> PERSONA_FRIEND;
			case PROFESSOR -> PERSONA_PROFESSOR;
			case ASSISTANT -> PERSONA_ASSISTANT;
		};
	}

	/**
	 * 사용자의 학습 수준에 맞춰 용어 난이도와 설명 깊이를 조절하는 추가 지시문입니다.
	 */
	private String getEducationLevelContext(User user) {
		if (user == null || user.getEducationLevel() == null) {
			return "";
		}

		return switch (user.getEducationLevel()) {
			case BEGINNER -> "사용자는 입문자입니다. 쉬운 용어와 비유를 사용해 설명해주세요.";
			case FUNDAMENTAL -> "사용자는 기초 수준입니다. 기본 개념은 알고 있으니 핵심 위주로 설명해주세요.";
			case INTERMEDIATE -> "사용자는 중급자입니다. 전문 용어를 사용해도 됩니다.";
			case EXPERT -> "사용자는 전문가입니다. 심화 내용과 기술적 디테일을 제공해주세요.";
		};
	}

	/**
	 * 사용자의 전공/관심 분야를 프롬프트에 넣어 답변 예시와 비유가 더 관련 있게 나오도록 합니다.
	 */
	private String getSpecializationContext(User user) {
		if (user == null || user.getSpecializedIn() == null || user.getSpecializedIn().isBlank()) {
			return "";
		}

		return "사용자의 전공/전문 분야: " + user.getSpecializedIn() + ". 이 배경지식을 고려해 설명해주세요.";
	}

	/**
	 * 이전 대화 요약, 현재 선택한 3D 컴포넌트 정보, 사용자 질문을 하나의 user prompt로 합칩니다.
	 * 3D 뷰어에서 선택한 구조가 AI 답변의 구체적인 근거로 들어가도록 만든 핵심 연결 지점입니다.
	 */
	public String buildUserPrompt(String runningSummary, List<Component> components, String userQuery) {
		String summarySection = "";
		if (runningSummary != null && !runningSummary.isBlank()) {
			summarySection = String.format(RUNNING_SUMMARY_SECTION, runningSummary);
		}

		String componentContext = buildComponentContext(components);

		return String.format(USER_PROMPT_TEMPLATE, summarySection, componentContext, userQuery);
	}

	/**
	 * 선택된 컴포넌트의 설명, 재질, 용도 정보를 prompt용 텍스트로 변환합니다.
	 */
	private String buildComponentContext(List<Component> components) {
		if (components == null || components.isEmpty()) {
			return "(참조된 부품 없음)";
		}

		StringBuilder sb = new StringBuilder();
		for (Component component : components) {
			sb.append(String.format("""
				### %s (ID: %d)
				- 설명: %s
				- 재질: %s
				- 용도: %s

				""",
				component.getName(),
				component.getId(),
				component.getDescription() != null ? component.getDescription() : "정보 없음",
				component.getTexture() != null ? component.getTexture() : "정보 없음",
				component.getUsage() != null ? component.getUsage() : "정보 없음"));
		}
		return sb.toString();
	}
}
