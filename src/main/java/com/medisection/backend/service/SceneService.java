package com.medisection.backend.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medisection.backend.domain.scene.SceneCategory;
import com.medisection.backend.domain.scene.SceneInformation;
import com.medisection.backend.domain.scene.SceneStatistics;
import com.medisection.backend.domain.scene.UserScene;
import com.medisection.backend.dto.SceneListOrder;
import com.medisection.backend.dto.SceneListResponse;
import com.medisection.backend.dto.SceneRankResponse;
import com.medisection.backend.dto.SceneResponse;
import com.medisection.backend.repository.SceneInformationRepository;
import com.medisection.backend.repository.SceneStatisticsRepository;
import com.medisection.backend.repository.UserSceneRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SceneService {

	private final UserSceneRepository userSceneRepository;
	private final SceneStatisticsRepository sceneStatisticsRepository;
	private final SceneInformationRepository sceneInformationRepository;

	private static final long POPULAR_SCENE_PARTICIPANTS_THRESHOLD = 5L;
	private static final LocalTime AGGREGATION_TIME = LocalTime.of(7, 0);

	/**
	 * 사용자가 최근 학습한 씬 3개를 홈 화면용 데이터로 변환합니다.
	 * UserScene의 마지막 접근 시간을 기준으로 이어서 학습할 모델을 빠르게 보여주기 위한 기능입니다.
	 */
	public SceneResponse getLearningScenes(Long userId) {
		List<UserScene> top3UserScenes = userSceneRepository.findTop3ByUserIdOrderByLastAccessedAtDesc(userId,
			PageRequest.of(0, 3));

		List<SceneResponse.SceneDto> sceneDtos = top3UserScenes.stream()
			.map(userScene -> {
				SceneInformation sceneInfo = userScene.getScene();
				return SceneResponse.SceneDto.builder()
					.id(sceneInfo.getId().toString())
					.title(sceneInfo.getTitle())
					.engTitle(sceneInfo.getEngTitle())
					.category(sceneInfo.getCategory())
					.imageUrl(sceneInfo.getThumbnailUrl())
					// TODO: 진척도 로직 실제 데이터 기반으로 수정 필요
					.progress(35)
					.popular(sceneInfo
						.getParticipantsCount() >= POPULAR_SCENE_PARTICIPANTS_THRESHOLD)
					.lastAccessedAt(userScene.getLastAccessedAt())
					.build();
			})
			.collect(Collectors.toList());

		return SceneResponse.builder()
			.scenes(sceneDtos)
			.build();
	}

	/**
	 * 오늘 날짜의 인기 학습 오브젝트 순위 조회 (1~5위)
	 * - 현재 시각이 07:00 이후 → 어제 07:00 기준 집계 데이터 사용
	 * - 현재 시각이 07:00 이전 → 그제 07:00 기준 집계 데이터 사용
	 */
	/**
	 * 카테고리별 인기 학습 오브젝트 TOP 5를 조회합니다.
	 * 통계 집계가 매일 07:00 기준으로 쌓인다는 전제를 코드에 반영해 랭킹 기준 시간을 계산합니다.
	 */
	public SceneRankResponse getSceneRanks(SceneCategory category) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime aggregatedTime = calculateAggregatedTime(now);

		List<SceneStatistics> statistics = sceneStatisticsRepository
			.findTop5ByAggregatedTimeAndCategory(aggregatedTime, category);

		List<SceneRankResponse.SceneRankDto> rankDtos = IntStream.range(0, statistics.size())
			.mapToObj(i -> {
				SceneStatistics stat = statistics.get(i);
				return SceneRankResponse.SceneRankDto.builder()
					.id(stat.getScene().getId().toString())
					.rank(i + 1)
					.title(stat.getScene().getTitle())
					.engTitle(stat.getScene().getEngTitle())
					.rankDiff(stat.getDifference())
					.build();
			})
			.toList();

		String todayFormatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

		return SceneRankResponse.builder()
			.today(todayFormatted)
			.scenes(rankDtos)
			.build();
	}

	/**
	 * 전체 씬 목록을 카테고리, 검색어, 정렬 기준에 맞춰 페이지 단위로 제공합니다.
	 * 프론트 목록 화면에서 인기순/가나다순 전환과 검색을 같은 API로 처리하기 위한 메서드입니다.
	 */
	public SceneListResponse getScenes(SceneCategory category, int page, int limit, String query,
		SceneListOrder order) {
		Sort sort;
		switch (order) {
			case POPULARITY:
				sort = Sort.by("participantsCount").descending();
				break;
			case ALPHABETICAL:
			default:
				sort = Sort.by("title").ascending();
				break;
		}

		PageRequest pageRequest = PageRequest.of(page - 1, limit, sort);
		Page<SceneInformation> scenePage = sceneInformationRepository.findByCategoryAndQuery(category, query,
			pageRequest);

		List<SceneListResponse.SceneDto> sceneDtos = scenePage.getContent().stream()
			.map(sceneInfo -> SceneListResponse.SceneDto.builder()
				.id(sceneInfo.getId().toString())
				.isPopular(sceneInfo
					.getParticipantsCount() >= POPULAR_SCENE_PARTICIPANTS_THRESHOLD)
				.title(sceneInfo.getTitle())
				.engTitle(sceneInfo.getEngTitle())
				.category(sceneInfo.getCategory())
				.description(sceneInfo.getDescription())
				.imageUrl(sceneInfo.getThumbnailUrl())
				.participantsCount(sceneInfo.getParticipantsCount())
				.build())
			.collect(Collectors.toList());

		return SceneListResponse.builder()
			.totalPages(scenePage.getTotalPages())
			.scenes(sceneDtos)
			.build();
	}

	/**
	 * aggregatedTime 계산 로직
	 * - 현재 시각이 07:00 이후 → 어제 07:00
	 * - 현재 시각이 07:00 이전 → 그제 07:00
	 */
	/**
	 * 랭킹 집계 기준 시간을 계산합니다.
	 * 현재 시간이 07:00 이후면 전날 07:00, 이전이면 이틀 전 07:00 데이터를 사용합니다.
	 */
	private LocalDateTime calculateAggregatedTime(LocalDateTime now) {
		if (!now.toLocalTime().isBefore(AGGREGATION_TIME)) {
			// 07:00 이후 → 어제 07:00
			return now.minusDays(1).with(AGGREGATION_TIME);
		} else {
			// 07:00 이전 → 그제 07:00
			return now.minusDays(2).with(AGGREGATION_TIME);
		}
	}

	/**
	 * 상세 화면에서 필요한 제목, 영문명, 설명만 가볍게 조회합니다.
	 */
	public com.medisection.backend.dto.SceneDetailResponse getSceneDetail(Long sceneId) {
		SceneInformation sceneInformation = sceneInformationRepository.findById(sceneId)
			.orElseThrow(() -> new IllegalArgumentException("Scene not found with id: " + sceneId));

		return com.medisection.backend.dto.SceneDetailResponse.from(
			sceneInformation.getTitle(),
			sceneInformation.getEngTitle(),
			sceneInformation.getDescription());
	}
}
