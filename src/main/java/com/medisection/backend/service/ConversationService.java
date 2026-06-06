package com.medisection.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medisection.backend.domain.alignment.Component;
import com.medisection.backend.domain.conversation.Conversation;
import com.medisection.backend.domain.conversation.Message;
import com.medisection.backend.domain.conversation.Reference;
import com.medisection.backend.domain.conversation.Sender;
import com.medisection.backend.domain.user.User;
import com.medisection.backend.dto.ConversationDto.ComponentInfo;
import com.medisection.backend.dto.ConversationDto.ConversationResponse;
import com.medisection.backend.dto.ConversationDto.ConversationSummaryResponse;
import com.medisection.backend.dto.ConversationDto.MessageResponse;
import com.medisection.backend.dto.ConversationDto.PageInfo;
import com.medisection.backend.dto.ConversationDto.SendMessageRequest;
import com.medisection.backend.dto.ConversationDto.SendMessageResponse;
import com.medisection.backend.dto.OpenAiDto.AssistantResponse;
import com.medisection.backend.dto.OpenAiDto.SummaryResponse;
import com.medisection.backend.exception.BusinessException;
import com.medisection.backend.exception.CommonErrorCode;
import com.medisection.backend.repository.ComponentRepository;
import com.medisection.backend.repository.ConversationRepository;
import com.medisection.backend.repository.MessageRepository;
import com.medisection.backend.repository.ReferenceRepository;
import com.medisection.backend.repository.SceneInformationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService {

	private final ConversationRepository conversationRepository;
	private final MessageRepository messageRepository;
	private final ComponentRepository componentRepository;
	private final ReferenceRepository referenceRepository;
	private final SceneInformationRepository sceneInformationRepository;
	private final OpenAiService openAiService;
	private final PromptService promptService;

	/**
	 * 씬별 대화 내역을 cursor 기반으로 조회합니다.
	 * 오래된 메시지를 추가로 불러오는 무한 스크롤 구조를 위해 id 기준 pagination을 사용했습니다.
	 */
	@Transactional(readOnly = true)
	public ConversationResponse getConversation(User user, Long sceneId, Long cursor, int limit) {
		Conversation conversation = conversationRepository.findByUserAndSceneId(user, sceneId)
			.orElse(null);

		if (conversation == null) {
			return new ConversationResponse(List.of(), emptyPageInfo(limit));
		}

		Slice<Message> messageSlice;
		if (cursor != null) {
			messageSlice = messageRepository.findByConversationAndIdLessThanOrderByIdDesc(
				conversation, cursor, PageRequest.of(0, limit + 1));
		} else {
			messageSlice = messageRepository.findByConversationOrderByIdDesc(
				conversation, PageRequest.of(0, limit + 1));
		}

		List<Message> messages = messageSlice.getContent();
		boolean hasNext = messages.size() > limit;
		if (hasNext) {
			messages = messages.subList(0, limit);
		}

		List<Long> messageIds = messages.stream().map(Message::getId).toList();
		Map<Long, List<Reference>> referencesByMessageId = loadReferences(messageIds);

		List<MessageResponse> messageResponses = messages.stream()
			.map(msg -> MessageResponse.from(msg, buildComponentInfoMap(referencesByMessageId.get(msg.getId()))))
			.toList();

		List<MessageResponse> reversedMessages = new ArrayList<>(messageResponses);
		Collections.reverse(reversedMessages);

		String prevCursor = messages.isEmpty() ? null : String.valueOf(messages.get(0).getId());
		String nextCursor = hasNext ? String.valueOf(messages.get(messages.size() - 1).getId()) : null;

		PageInfo pageInfo = new PageInfo(
			prevCursor,
			nextCursor,
			cursor != null,
			hasNext,
			limit);

		return new ConversationResponse(reversedMessages, pageInfo);
	}

	/**
	 * 사용자의 질문을 저장하고, 선택된 3D 컴포넌트 정보를 프롬프트에 포함해 AI 답변을 생성합니다.
	 * 대화 요약을 함께 갱신해 다음 질문에서도 이전 맥락을 짧게 이어갈 수 있게 했습니다.
	 */
	@Transactional
	public SendMessageResponse sendMessage(User user, Long sceneId, SendMessageRequest request) {
		Conversation conversation = conversationRepository.findByUserAndSceneId(user, sceneId)
			.orElseGet(() -> {
				var scene = sceneInformationRepository.findById(sceneId)
					.orElseThrow(() -> new BusinessException(CommonErrorCode.SCENE_NOT_FOUND));
				return conversationRepository.save(
					Conversation.builder()
						.user(user)
						.scene(scene)
						.build());
			});

		List<Component> components = loadComponents(request.getComponentIds());
		Map<String, ComponentInfo> componentInfoMap = components.stream()
			.collect(Collectors.toMap(
				c -> String.valueOf(c.getId()),
				ComponentInfo::from));

		Message userMessage = Message.builder()
			.conversation(conversation)
			.sender(Sender.USER)
			.content(request.content())
			.postedAt(java.time.LocalDateTime.now())
			.build();
		messageRepository.save(userMessage);

		for (Component component : components) {
			Reference reference = Reference.builder()
				.message(userMessage)
				.component(component)
				.build();
			referenceRepository.save(reference);
		}

		String systemPrompt = promptService.buildSystemPrompt(sceneId, user);
		String userPrompt = promptService.buildUserPrompt(
			conversation.getSummary(),
			components,
			request.content());

		AssistantResponse aiResponse = openAiService.chat(systemPrompt, userPrompt);

		Message assistantMessage = Message.builder()
			.conversation(conversation)
			.sender(Sender.ASSISTANT)
			.content(aiResponse.answer())
			.postedAt(java.time.LocalDateTime.now())
			.build();
		messageRepository.save(assistantMessage);

		conversation.updateSummary(aiResponse.summary());

		return SendMessageResponse.from(assistantMessage, componentInfoMap);
	}

	/**
	 * 사용자가 뷰어에서 선택한 컴포넌트 id들을 검증합니다.
	 * 일부 id가 누락되면 잘못된 학습 맥락으로 AI 답변이 생성될 수 있어 예외를 발생시킵니다.
	 */
	private List<Component> loadComponents(List<Long> componentIds) {
		if (componentIds == null || componentIds.isEmpty()) {
			return List.of();
		}

		List<Component> components = componentRepository.findByIdIn(componentIds);
		if (components.size() != componentIds.size()) {
			throw new BusinessException(CommonErrorCode.COMPONENT_NOT_FOUND);
		}
		return components;
	}

	/**
	 * 메시지에 연결된 컴포넌트 Reference를 한 번에 조회합니다.
	 * N+1 조회를 피하면서 대화 화면에 "어떤 구조를 참고했는지" 표시할 수 있게 합니다.
	 */
	private Map<Long, List<Reference>> loadReferences(List<Long> messageIds) {
		if (messageIds.isEmpty()) {
			return Map.of();
		}

		List<Reference> references = referenceRepository.findByMessageIdInWithComponent(messageIds);
		return references.stream()
			.collect(Collectors.groupingBy(ref -> ref.getMessage().getId()));
	}

	/**
	 * Reference 엔티티를 응답 DTO에서 바로 사용할 수 있는 component map 형태로 변환합니다.
	 */
	private Map<String, ComponentInfo> buildComponentInfoMap(List<Reference> references) {
		if (references == null || references.isEmpty()) {
			return Map.of();
		}

		Map<String, ComponentInfo> result = new HashMap<>();
		for (Reference ref : references) {
			Component component = ref.getComponent();
			result.put(String.valueOf(component.getId()), ComponentInfo.from(component));
		}
		return result;
	}

	/**
	 * 사용자의 모든 씬 대화를 모아 학습 기록 요약을 생성합니다.
	 * 개별 채팅방이 아니라 전체 학습 흐름을 복습 카드처럼 보여주기 위한 기능입니다.
	 */
	@Transactional(readOnly = true)
	public ConversationSummaryResponse summarizeAllConversations(User user) {
		List<Conversation> conversations = conversationRepository.findByUser(user);

		if (conversations.isEmpty()) {
			return new ConversationSummaryResponse("대화 내역이 없습니다.", 0, 0);
		}

		List<Message> allMessages = messageRepository.findByConversationInOrderByPostedAtAsc(conversations);

		if (allMessages.isEmpty()) {
			return new ConversationSummaryResponse("대화 내역이 없습니다.", conversations.size(), 0);
		}

		String conversationText = buildConversationText(allMessages);

		String systemPrompt = """
			당신은 대화 내역을 요약하는 AI입니다.
			사용자와 AI 어시스턴트 간의 전체 대화 내역을 분석하여 핵심 내용을 종합적으로 요약해주세요.

			## 규칙
			1. 한국어로 요약합니다.
			2. 주요 학습 주제, 질문한 내용, AI가 설명한 핵심 개념을 포함합니다.
			3. 시간순으로 대화 흐름을 반영합니다.
			4. 요약은 2~3문단으로 작성합니다.
			""";

		SummaryResponse summaryResponse = openAiService.summarize(systemPrompt, conversationText);

		return new ConversationSummaryResponse(
			summaryResponse.summary(),
			conversations.size(),
			allMessages.size());
	}

	/**
	 * AI 요약 요청에 넣을 대화 원문을 시간순 문자열로 구성합니다.
	 */
	private String buildConversationText(List<Message> messages) {
		StringBuilder sb = new StringBuilder();
		for (Message message : messages) {
			String role = message.getSender() == Sender.USER ? "사용자" : "AI";
			sb.append(String.format("[%s] %s: %s\n",
				message.getPostedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
				role,
				message.getContent()));
		}
		return sb.toString();
	}

	/**
	 * 대화가 아직 없는 씬에서도 프론트가 동일한 pagination 구조를 받을 수 있게 빈 PageInfo를 반환합니다.
	 */
	private PageInfo emptyPageInfo(int limit) {
		return new PageInfo(null, null, false, false, limit);
	}
}
