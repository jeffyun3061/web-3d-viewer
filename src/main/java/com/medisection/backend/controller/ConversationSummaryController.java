package com.medisection.backend.controller;

import org.springframework.http.ResponseEntity;
// 인증 정보 없이 접근할 수 있도록 하기 위해 AuthenticationPrincipal을 제거합니다.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medisection.backend.dto.ConversationDto.ConversationSummaryResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationSummaryController {
    // AI 요약 기능을 비활성화하고 사용자 인증을 제거합니다.

    @GetMapping("/summary")
    public ResponseEntity<ConversationSummaryResponse> summarizeAllConversations() {
        // 인증 없이도 접근 가능하도록 하며, AI 요약 기능을 사용하지 않습니다.
        // 요약 결과를 기본값으로 반환합니다.
        ConversationSummaryResponse response = new ConversationSummaryResponse(
            "AI 요약 기능이 비활성화되었습니다.",
            0,
            0);
        return ResponseEntity.ok(response);
    }
}
