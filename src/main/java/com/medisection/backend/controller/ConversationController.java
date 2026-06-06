package com.medisection.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medisection.backend.dto.ConversationDto.SendMessageRequest;
import com.medisection.backend.dto.ConversationDto.SendMessageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/scenes/{sceneId}")
@RequiredArgsConstructor
public class ConversationController {

    /**
     * 현재 시연 버전에서는 인증 없이 접근 가능하도록 대화 내역을 빈 상태로 반환합니다.
     * 실제 ConversationService 연결부는 남겨둔 서비스 계층을 통해 다시 확장할 수 있습니다.
     */
    @GetMapping("/conversation")
    public ResponseEntity<com.medisection.backend.dto.ConversationDto.ConversationResponse> getConversation(
        @PathVariable("sceneId")
        Long sceneId,
        @RequestParam(name = "limit", defaultValue = "5")
        int limit,
        @RequestParam(name = "cursor", required = false)
        Long cursor) {
        // 인증 정보를 사용하지 않고 대화 내역을 빈 상태로 응답합니다.
        com.medisection.backend.dto.ConversationDto.PageInfo pageInfo =
            new com.medisection.backend.dto.ConversationDto.PageInfo(null, null, false, false, limit);
        com.medisection.backend.dto.ConversationDto.ConversationResponse response =
            new com.medisection.backend.dto.ConversationDto.ConversationResponse(java.util.List.of(), pageInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * AI 대화 기능을 잠시 비활성화한 상태에서 프론트 흐름이 깨지지 않도록 고정 응답을 반환합니다.
     * OpenAI 연동을 다시 켤 때는 ConversationService.sendMessage 흐름으로 교체하면 됩니다.
     */
    @PostMapping("/conversation/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
        @PathVariable("sceneId")
        Long sceneId,
        @Valid @RequestBody
        SendMessageRequest request) {
        // AI 기능을 비활성화하여 고정된 응답을 반환합니다.
        String postedAt = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        SendMessageResponse response = new SendMessageResponse(
            "ASSISTANT",
            "AI 기능이 비활성화되었습니다. 답변을 제공할 수 없습니다.",
            postedAt,
            java.util.Map.of());
        return ResponseEntity.ok(response);
    }

    // 사용자 조회 로직을 제거했습니다. 로그인 없이도 접근 가능합니다.
}
