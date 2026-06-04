# Developer Memo

## 프로젝트 한 줄 정의

MediSection 3D는 GLB/GLTF 기반 3D 모델을 웹에서 관찰하고, 단면 보기와 학습 미션/퀴즈를 함께 제공하는 의학 학습용 3D 뷰어입니다.

## 왜 만들었는가

의학 구조 학습은 평면 이미지로는 공간 관계를 이해하기 어렵습니다. 특히 심장과 폐의 상대 위치, 앞뒤 방향, 장비 부품의 배치처럼 “깊이”가 중요한 내용은 직접 회전하고 잘라보는 상호작용이 필요합니다. 이 프로젝트는 3D 관찰 도구와 학습 콘텐츠를 결합해 “보는 뷰어”에서 “학습하는 뷰어”로 확장하는 것을 목표로 했습니다.

## 주요 설계

- Spring Boot가 정적 페이지와 REST API를 함께 제공합니다.
- 서버는 `src/main/resources/assets` 아래의 embedded GLTF 모델을 자동으로 스캔합니다.
- `/scenes` API는 등록된 모델 목록을 반환합니다.
- `/scenes/{sceneId}/model` API는 선택한 모델의 GLTF 파일을 반환합니다.
- 프론트엔드는 Three.js로 모델을 렌더링하고, clipping plane으로 단면 모드를 구현합니다.
- 학습 데이터는 `study-content.json`에 분리해 UI 코드와 콘텐츠를 느슨하게 결합했습니다.

## 핵심 구현 파일

- `src/main/resources/static/main.js`
  - Three.js renderer 생성
  - GLTFLoader 모델 로딩
  - OrbitControls 카메라 조작
  - 모델 bounds 계산 후 자동 프레이밍
  - X/Y/Z clipping plane 기반 section mode
  - 학습 목표, 구조 카드, 미션, 퀴즈 렌더링

- `src/main/java/com/medisection/backend/loader/SceneDataLoader.java`
  - 시작 시 bundled GLTF 모델 탐색
  - H2 dev DB에 씬 메타데이터 자동 등록

- `src/main/java/com/medisection/backend/controller/SceneModelController.java`
  - 모델 파일을 `model/gltf+json`으로 응답
  - classpath와 로컬 파일 시스템 양쪽을 고려해 모델 탐색

- `src/main/resources/static/study-content.json`
  - 모델별 학습 목표, 관찰 포인트, 미션, 퀴즈 정의

## 사용 기술과 선택 이유

- **Spring Boot**: 백엔드 API와 정적 웹앱을 하나의 실행 단위로 묶어 시연과 배포를 단순화했습니다.
- **H2 Database**: 포트폴리오 데모에서 별도 DB 설치 없이 실행되도록 dev profile에 사용했습니다.
- **Spring Data JPA**: 씬 메타데이터 조회와 저장 구조를 명확하게 관리했습니다.
- **Three.js**: 브라우저에서 WebGL 기반 3D 렌더링을 구현하기 위한 표준적인 라이브러리입니다.
- **GLTFLoader**: GLB/GLTF 모델 포맷을 직접 파싱하지 않고 검증된 로더를 사용했습니다.
- **OrbitControls**: 회전, 확대, 이동 같은 3D 뷰어 기본 조작을 안정적으로 제공합니다.
- **Docker Compose**: 발표 환경에서 명령 한 번으로 실행 가능한 배포 단위를 만들었습니다.

## 해결한 이슈

- **8080 포트 충돌**
  - bootRun과 Docker 컨테이너가 같은 포트를 쓰면 실행 실패가 발생합니다.
  - `docker ps`, `docker stop` 절차를 README에 명시했습니다.

- **GLTF 파일 경로 문제**
  - 일반 GLTF가 외부 `.bin`이나 이미지 파일을 참조하면 단일 업로드로는 깨질 수 있습니다.
  - 그래서 내장 모델은 embedded GLTF로 구성했고, 사용자 업로드는 GLB 또는 embedded GLTF를 권장합니다.

- **Three.js vendor 모듈 누락**
  - 네트워크가 불안정해도 실행되도록 Three.js 모듈을 정적 리소스에 포함했습니다.

- **단순 뷰어의 포트폴리오 한계**
  - 학습 목표, 구조 관찰, 미션, 퀴즈, 단면 모드를 연결해 “왜 3D가 필요한지”가 보이도록 리팩토링했습니다.

## 면접 답변 예시

“처음에는 3D 파일을 웹에서 띄우는 뷰어에 가까웠지만, 포트폴리오 프로젝트로 설득력이 부족하다고 판단했습니다. 그래서 의학 학습이라는 문제를 잡고, 3D 모델을 돌려보는 기능에 단면 관찰과 학습 미션, 퀴즈를 연결했습니다. 백엔드는 Spring Boot로 씬 목록과 모델 파일 API를 만들었고, 프론트는 Three.js의 GLTFLoader와 OrbitControls를 사용했습니다. 특히 section mode는 Three.js clipping plane을 이용해 축과 슬라이더 값에 따라 모델 내부를 관찰할 수 있게 구현했습니다.”

## 현재 한계

- 실제 의료용 정밀 모델이 아니라 학습 데모용 단순 모델입니다.
- 구조 하이라이트는 mesh name 기반이라 실제 모델마다 naming rule이 필요합니다.
- 학습 진행률은 현재 브라우저 화면 상태 중심이며 서버 저장까지 확장할 수 있습니다.

## 다음 개선

- 실제 anatomy GLB 모델 추가
- 구조별 mesh id 매핑 테이블 추가
- 사용자별 학습 기록 저장
- 퀴즈 난이도 자동 조절
- ngrok 또는 클라우드 배포 환경에서 HTTPS/CORS 검증
