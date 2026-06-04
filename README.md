# MediSection 3D

MediSection 3D는 3D 인체 모델을 직접 회전하고 단면으로 확인하면서 의학 구조를 학습할 수 있는 WebGL 기반 학습 뷰어입니다. 단순히 GLB/GLTF 파일을 보여주는 뷰어에서 끝내지 않고, 관찰 가이드, 미션, 퀴즈, 단면 학습, 2D 이미지 기반 3D 생성 확장 기능까지 하나의 포트폴리오 프로젝트로 구성했습니다.

## 프로젝트 목표

의학 구조는 평면 이미지로만 보면 앞뒤 깊이와 위치 관계를 이해하기 어렵습니다. 특히 두개골처럼 여러 뼈와 공간 구조가 입체적으로 연결된 대상은 직접 돌려보고 단면을 확인하는 과정이 학습에 도움이 된다고 판단했습니다.

이 프로젝트는 다음 목표로 만들었습니다.

- GLB/GLTF 3D 모델을 브라우저에서 바로 확인할 수 있게 만들기
- 회전, 확대, 시점 초기화, 단면 보기로 구조를 입체적으로 관찰하기
- 3D 조작과 학습 콘텐츠가 따로 놀지 않도록 가이드, 미션, 퀴즈를 연결하기
- Spring Boot 하나로 백엔드 API와 정적 프론트엔드를 함께 제공하기
- Docker Compose로 회사나 면접 자리에서 빠르게 실행 가능한 상태로 만들기
- TripoSplat을 확장 기능으로 연결해 2D 이미지 기반 3D 생성 흐름까지 보여주기

## 주요 기능

### 1. 3D 모델 뷰어

Three.js 기반으로 GLB/GLTF 모델을 렌더링합니다. 사용자는 모델을 회전, 확대, 이동하면서 구조를 관찰할 수 있습니다.

### 2. GLB / GLTF 파일 열기

기본 제공 모델뿐 아니라 사용자가 가진 `.glb` 또는 embedded `.gltf` 파일도 열 수 있습니다. 포트폴리오 시연에서는 의학 모델을 기본으로 보여주고, 추가 모델을 직접 업로드해 확장성을 설명할 수 있습니다.

### 3. 단면 관찰 모드

Three.js clipping plane을 이용해 X/Y/Z 축 기준으로 모델을 잘라 볼 수 있습니다. 단면 위치는 슬라이더로 조절할 수 있어 내부 구조나 깊이감을 설명하기 좋습니다.

### 4. 학습 가이드

선택한 모델에 맞춰 학습 초점, 주요 구조, 관찰 포인트를 제공합니다. 현재 테스트 콘텐츠는 두개골 관찰 흐름에 맞춰 구성했습니다.

### 5. 관찰 미션

사용자가 단순히 모델을 구경하는 데서 끝나지 않도록 관찰 과제를 제공합니다. 예를 들어 정면, 측면, 아래쪽 시점에서 서로 다른 구조를 확인하도록 유도합니다.

### 6. 의학 퀴즈

3D 관찰 후 바로 확인할 수 있는 퀴즈를 제공합니다. 두개골 모델 기준으로 눈확, 광대뼈, 위턱뼈, 아래턱뼈처럼 실제 관찰 대상과 연결되는 문제를 구성했습니다.

### 7. 2D 이미지 기반 3D 생성 확장

TripoSplat을 별도 Python 추론 서버로 실행하고, MediSection 3D의 `2D 생성` 탭에서 연결할 수 있게 만들었습니다.

중요한 점은 TripoSplat의 결과물이 GLB/GLTF mesh가 아니라 `.ply` 또는 `.splat` 기반 Gaussian Splat 결과라는 점입니다. 그래서 기존 GLB/GLTF 뷰어에 억지로 합치지 않고, 확장 탭으로 분리했습니다. 이 구조가 더 안정적이고 면접에서 기술적 판단 근거를 설명하기 좋습니다.

## 기술 스택

### Backend

- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA
- H2 Database
- Gradle

### Frontend

- HTML
- CSS
- JavaScript ES Modules
- Three.js
- GLTFLoader
- OrbitControls
- WebGL clipping plane

### AI / 3D 생성 확장

- Python
- PyTorch
- Gradio
- TripoSplat
- Hugging Face model weights
- Gaussian Splatting output: PLY / SPLAT

### 실행 / 배포

- Docker
- Docker Compose
- ngrok 연결 가능 구조

## 실행 방법

### Docker 실행

```powershell
cd C:\Users\dba35\Desktop\3ddemo
docker compose up -d --build
```

접속 주소:

```text
http://localhost:8080
```

### 로컬 Gradle 실행

```powershell
cd C:\Users\dba35\Desktop\3ddemo
.\gradlew.bat bootRun
```

8080 포트가 이미 사용 중이면 기존 컨테이너나 프로세스를 종료해야 합니다.

```powershell
docker ps
docker stop <container_id>
```

## TripoSplat 확장 실행

TripoSplat은 Spring Boot 안에 포함된 모델이 아니라 별도 Python 추론 서버입니다. LLM이나 OpenAI API가 필요한 기능이 아니라, 이미지 기반 3D 생성 모델을 로컬에서 실행하는 방식입니다.

현재 구성 위치:

```powershell
cd C:\Users\dba35\Downloads\TripoSplat-main\TripoSplat-main
.\.venv\Scripts\python.exe run_gradio.py
```

실행 후 접속 주소:

```text
http://127.0.0.1:7860
```

MediSection 3D에서는 `2D 생성` 탭의 `생성 화면 연결` 버튼으로 이 서버를 연결합니다.

주의할 점:

- TripoSplat은 모델 가중치가 필요합니다.
- GPU가 있으면 훨씬 빠르게 생성됩니다.
- 현재 PC에서는 CUDA가 잡히지 않아 CPU 모드로 실행되도록 보정했습니다.
- CPU 모드는 서버는 실행되지만 실제 생성 시간이 길 수 있습니다.
- 결과물은 GLB/GLTF가 아니라 PLY/SPLAT입니다.

## 프로젝트 구조

```text
src/main/java/com/medisection/backend
  controller
    SceneController.java
    SceneModelController.java
    TripoSplatStatusController.java
  loader
    SceneDataLoader.java
  domain
  repository
  service

src/main/resources/static
  index.html
  styles.css
  main.js
  study-content.json
  vendor/three

src/main/resources/assets
  Anatomy Study Model
  Medical Device Model
```

## 구현 포인트

### SceneDataLoader

서버 시작 시 `src/main/resources/assets` 안의 모델 데이터를 읽고 H2 DB에 기본 학습 모델을 등록합니다. 별도 관리자 화면 없이도 실행하면 모델 목록이 자동으로 준비되도록 구성했습니다.

### SceneController

프론트엔드가 사용할 3D 학습 모델 목록을 API로 제공합니다. 화면의 왼쪽 모델 목록은 이 API 응답을 기반으로 구성됩니다.

### SceneModelController

선택한 모델의 GLTF 파일을 응답합니다. classpath 리소스와 개발 환경 파일 경로를 함께 고려해 Docker와 로컬 실행 모두에서 동작하도록 구성했습니다.

### main.js

프론트엔드의 핵심 로직입니다.

- Three.js renderer 초기화
- GLTFLoader로 모델 로딩
- OrbitControls로 카메라 조작
- 모델 크기 계산 후 자동 프레이밍
- clipping plane 기반 단면 모드
- 학습 콘텐츠 JSON 로딩
- 가이드, 미션, 체크리스트, 퀴즈 렌더링
- TripoSplat 생성 서버 연결 UI 처리

### study-content.json

학습 콘텐츠를 코드와 분리했습니다. 모델별 학습 목표, 주요 구조, 관찰 미션, 퀴즈를 JSON으로 관리하므로 다른 의학 모델을 추가할 때 JavaScript 코드를 크게 바꾸지 않아도 됩니다.

### TripoSplatStatusController

Spring Boot 앱이 TripoSplat 서버 실행 여부를 확인하는 API입니다. Docker 컨테이너 내부에서 호스트의 `7860` 포트를 감지해야 하므로 `host.docker.internal`을 기준으로 포트 연결 상태를 확인합니다.

## 문제 해결 경험

### 1. 단순 3D 뷰어만으로는 포트폴리오 의미가 약한 문제

처음에는 3D 파일을 열어보는 기능 중심이었지만, 신입 개발자 포트폴리오로는 문제 해결 의도가 약하다고 판단했습니다. 그래서 의학 학습이라는 목적을 잡고, 3D 관찰과 학습 콘텐츠를 연결하는 방향으로 리팩토링했습니다.

### 2. 프론트엔드와 백엔드를 따로 실행해야 하는 불편함

시연 상황에서 실행 절차가 복잡하면 실패 가능성이 커집니다. 그래서 Spring Boot가 정적 프론트엔드까지 함께 제공하도록 구성했고, Docker Compose로 한 번에 실행할 수 있게 만들었습니다.

### 3. GLTF 파일 로딩 문제

일반 `.gltf`는 외부 `.bin`이나 텍스처 파일을 참조하는 경우가 많습니다. 파일 하나만 업로드하면 깨질 수 있기 때문에 기본적으로 `.glb`를 권장하고, embedded `.gltf`도 사용할 수 있게 정리했습니다.

### 4. Three.js CDN 의존 문제

네트워크가 불안한 환경에서도 시연이 가능해야 하므로 Three.js 관련 파일을 `static/vendor/three` 아래에 포함해 로컬 정적 리소스로 로딩하도록 구성했습니다.

### 5. 8080 포트 충돌

Spring Boot 직접 실행과 Docker 컨테이너가 모두 8080을 사용하기 때문에 기존 프로세스가 남아 있으면 실행에 실패합니다. 이 문제는 `docker ps`, `docker stop`으로 확인하고 정리하는 방식으로 해결했습니다.

### 6. TripoSplat GPU 의존 문제

원본 TripoSplat은 `cuda` 실행을 전제로 되어 있어 GPU가 없으면 서버가 뜨지 않았습니다. 현재 환경에서는 CUDA가 잡히지 않았기 때문에 실행 장치를 자동 선택하도록 바꾸고, CPU 실행 시 dtype을 `float32`로 조정했습니다.

### 7. Docker 내부에서 호스트 추론 서버를 감지하는 문제

브라우저에서는 `127.0.0.1:7860`이 열려도 Docker 컨테이너 내부의 `127.0.0.1`은 컨테이너 자신을 의미합니다. 그래서 Spring Boot 상태 체크는 `host.docker.internal:7860` 포트를 확인하도록 구성했습니다.

## 면접 설명 예시

### 어떤 프로젝트인가요?

3D 인체 모델을 브라우저에서 직접 조작하면서 의학 구조를 학습할 수 있는 WebGL 기반 학습 뷰어입니다. 단순히 모델을 보여주는 기능보다, 3D를 왜 써야 하는지에 초점을 맞춰 단면 보기, 관찰 미션, 퀴즈를 함께 구성했습니다.

### 왜 만들었나요?

의학 구조는 평면 이미지로만 보면 깊이와 위치 관계를 이해하기 어렵다고 생각했습니다. 그래서 학습자가 모델을 직접 돌려보고, 단면을 잘라보고, 바로 퀴즈로 확인하는 흐름을 만들었습니다.

### 어떤 기술을 사용했나요?

백엔드는 Java 17과 Spring Boot로 만들었고, 모델 목록과 모델 파일 제공 API를 구성했습니다. 프론트엔드는 HTML, CSS, JavaScript, Three.js를 사용했습니다. 3D 모델 로딩은 GLTFLoader, 카메라 조작은 OrbitControls, 단면 기능은 Three.js clipping plane으로 구현했습니다. 실행 환경은 Docker Compose로 정리했습니다.

### 가장 신경 쓴 부분은 무엇인가요?

첫 번째는 시연 안정성입니다. 프론트엔드와 백엔드를 따로 실행하지 않고 Spring Boot와 Docker로 바로 보여줄 수 있게 만들었습니다. 두 번째는 기능의 목적성입니다. 단순 뷰어가 아니라 3D 관찰이 학습으로 이어지도록 가이드, 미션, 퀴즈를 연결했습니다.

### TripoSplat은 어떻게 붙였나요?

TripoSplat은 2D 이미지를 Gaussian Splatting 결과로 변환하는 Python 기반 추론 서버입니다. 결과물이 GLB/GLTF가 아니기 때문에 기존 모델 뷰어에 억지로 합치지 않고, `2D 생성` 확장 탭으로 분리했습니다. Spring Boot에서는 해당 서버의 실행 여부를 감지하고, 프론트엔드에서는 생성 화면을 연결하는 방식으로 구성했습니다.

## 현재 상태

- Docker 앱 실행 확인: `http://localhost:8080`
- TripoSplat 서버 실행 확인: `http://127.0.0.1:7860`
- 3D 파일 형식: `.glb`, embedded `.gltf`
- TripoSplat 출력 형식: `.ply`, `.splat`
- ngrok으로 공유할 때는 `8080` 포트를 연결하면 됩니다.

