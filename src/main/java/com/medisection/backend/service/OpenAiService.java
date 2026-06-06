package com.medisection.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.medisection.backend.dto.OpenAiDto.AssistantResponse;
import com.medisection.backend.dto.OpenAiDto.ResponsesRequest;
import com.medisection.backend.dto.OpenAiDto.ResponsesResponse;
import com.medisection.backend.dto.OpenAiDto.SummaryResponse;
import com.medisection.backend.exception.BusinessException;
import com.medisection.backend.exception.CommonErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenAiService {

	private final RestClient openAiRestClient;
	private final ObjectMapper objectMapper;
	private final String model;
	private final int maxRetries;
	private final MeterRegistry meterRegistry;

	public OpenAiService(
		@Qualifier("openAiRestClient")
		RestClient openAiRestClient,
		@Qualifier("openAiObjectMapper")
		ObjectMapper objectMapper,
		@Value("${openai.model}")
		String model,
		@Value("${openai.max-retries:2}")
		int maxRetries,
		MeterRegistry meterRegistry) {
		this.openAiRestClient = openAiRestClient;
		this.objectMapper = objectMapper;
		this.model = model;
		this.maxRetries = maxRetries;
		this.meterRegistry = meterRegistry;
	}

	/**
	 * 3D 학습 어시스턴트 답변을 OpenAI API로 생성합니다.
	 * 응답은 AssistantResponse JSON으로 강제 파싱해 프론트가 answer/summary를 안정적으로 분리해서 사용할 수 있게 했습니다.
	 */
	public AssistantResponse chat(String systemPrompt, String userMessage) {
		ResponsesRequest request = ResponsesRequest.of(model, systemPrompt, userMessage);

		for (int attempt = 0; attempt <= maxRetries; attempt++) {
			try {
				ResponsesResponse response = openAiRestClient.post()
					.uri("/chat/completions")
					.body(request)
					.retrieve()
					.body(ResponsesResponse.class);

				if (response == null) {
					throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
				}

				String textContent = response.getTextContent();
				if (textContent == null) {
					throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
				}

				recordTokenUsage(response, "chat");

				if (!response.isComplete()) {
					String reason = response.getFinishReason();
					log.warn("OpenAI response truncated. finish_reason={}, attempting to parse partial response",
						reason);
					// Do NOT retry — length truncation yields the same result
					return parseAssistantResponse(textContent);
				}

				return parseAssistantResponse(textContent);

			} catch (RestClientException e) {
				log.error("OpenAI API call failed (attempt {}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
				if (attempt == maxRetries) {
					throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
				}
			}
		}

		throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
	}

	/**
	 * 여러 대화 기록을 하나의 학습 요약으로 압축합니다.
	 * 일반 채팅보다 짧은 출력 형식을 사용하고, 실패 시 동일한 retry 정책을 공유합니다.
	 */
	public SummaryResponse summarize(String systemPrompt, String userMessage) {
		ResponsesRequest request = ResponsesRequest.forSummary(model, systemPrompt, userMessage);

		for (int attempt = 0; attempt <= maxRetries; attempt++) {
			try {
				ResponsesResponse response = openAiRestClient.post()
					.uri("/chat/completions")
					.body(request)
					.retrieve()
					.body(ResponsesResponse.class);

				if (response == null) {
					throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
				}

				String textContent = response.getTextContent();
				if (textContent == null) {
					throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
				}

				recordTokenUsage(response, "summary");

				if (!response.isComplete()) {
					String reason = response.getFinishReason();
					log.warn(
						"OpenAI summary response truncated. finish_reason={}, attempting to parse partial response",
						reason);
					return parseSummaryResponse(textContent);
				}

				return parseSummaryResponse(textContent);

			} catch (RestClientException e) {
				log.error("OpenAI summary API call failed (attempt {}/{}): {}", attempt + 1, maxRetries + 1,
					e.getMessage());
				if (attempt == maxRetries) {
					throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
				}
			}
		}

		throw new BusinessException(CommonErrorCode.OPENAI_API_ERROR);
	}

	/**
	 * OpenAI 토큰 사용량을 Micrometer counter로 기록합니다.
	 * chat/summary 타입을 tag로 분리해 운영 시 어떤 기능에서 비용이 발생하는지 확인할 수 있습니다.
	 */
	private void recordTokenUsage(ResponsesResponse response, String type) {
		if (response.usage() == null) {
			return;
		}
		Counter.builder("openai.tokens.input")
			.tag("type", type)
			.description("OpenAI input tokens consumed")
			.register(meterRegistry)
			.increment(response.usage().inputTokens());

		Counter.builder("openai.tokens.output")
			.tag("type", type)
			.description("OpenAI output tokens consumed")
			.register(meterRegistry)
			.increment(response.usage().outputTokens());
	}

	/**
	 * 모델이 반환한 JSON 문자열을 서비스 DTO로 변환합니다.
	 * 파싱 실패를 별도 에러 코드로 분리해 API 호출 실패와 응답 형식 실패를 구분했습니다.
	 */
	private AssistantResponse parseAssistantResponse(String json) {
		try {
			return objectMapper.readValue(json, AssistantResponse.class);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse OpenAI response: {}", json, e);
			throw new BusinessException(CommonErrorCode.OPENAI_PARSE_ERROR);
		}
	}

	/**
	 * 전체 대화 요약 응답 JSON을 SummaryResponse로 변환합니다.
	 */
	private SummaryResponse parseSummaryResponse(String json) {
		try {
			return objectMapper.readValue(json, SummaryResponse.class);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse OpenAI summary response: {}", json, e);
			throw new BusinessException(CommonErrorCode.OPENAI_PARSE_ERROR);
		}
	}
}
