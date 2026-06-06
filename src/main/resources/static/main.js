import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

const TRIPOSPLAT_URL = 'http://127.0.0.1:7860';

const els = {
  sceneList: document.getElementById('scene-list'),
  sceneCount: document.getElementById('scene-count'),
  sceneTitle: document.getElementById('scene-title'),
  sceneDescription: document.getElementById('scene-description'),
  viewer: document.getElementById('viewer'),
  status: document.getElementById('status'),
  resetCamera: document.getElementById('reset-camera'),
  fileInput: document.getElementById('file-input'),
  modeChip: document.getElementById('mode-chip'),
  sectionToggle: document.getElementById('section-toggle'),
  sectionSlider: document.getElementById('section-slider'),
  studySummary: document.getElementById('study-summary'),
  objectives: document.getElementById('objectives-list'),
  structures: document.getElementById('structures-list'),
  missions: document.getElementById('mission-list'),
  checklist: document.getElementById('checklist'),
  quizQuestion: document.getElementById('quiz-question'),
  quizOptions: document.getElementById('quiz-options'),
  quizFeedback: document.getElementById('quiz-feedback'),
  imageInput: document.getElementById('image-input'),
  imagePreview: document.getElementById('image-preview'),
  connectSplat: document.getElementById('connect-splat'),
  openSplat: document.getElementById('open-splat'),
  splatStatus: document.getElementById('splat-status'),
  splatFrame: document.getElementById('splat-frame'),
};

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0f1314);

const camera = new THREE.PerspectiveCamera(42, 1, 0.01, 2000);
const loader = new GLTFLoader();
const clipPlane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);

let renderer = null;
let controls = null;
let currentModel = null;
let currentScenes = [];
let activeSceneId = null;
let currentObjectUrl = null;
let currentImageUrl = null;
let currentContent = null;
let studyContent = {};
let currentBounds = {
  size: new THREE.Vector3(1, 1, 1),
  maxSize: 1,
};

const sectionState = {
  enabled: false,
  axis: 'z',
  value: 0,
};

// 화면 하단 상태 문구를 한 곳에서 관리해서 비동기 로딩, 오류, 사용자 액션 결과가 같은 방식으로 표시되도록 했습니다.
function setStatus(message) {
  els.status.textContent = message;
}

// 선택된 학습 모델의 제목과 설명을 갱신합니다. 서버 모델과 로컬 업로드 모델이 같은 UI를 재사용합니다.
function setTitle(title, description = '') {
  els.sceneTitle.textContent = title;
  els.sceneDescription.textContent = description;
}

function normalizeKey(value) {
  return String(value || '').trim().toLowerCase();
}

function getStudyContent(sceneInfo) {
  const candidates = [
    sceneInfo?.engTitle,
    sceneInfo?.title,
    sceneInfo?.name,
  ].map(normalizeKey);

  const foundKey = Object.keys(studyContent).find((key) => candidates.includes(normalizeKey(key)));
  return foundKey ? studyContent[foundKey] : studyContent.default;
}

// Three.js 렌더러와 OrbitControls를 초기화합니다.
// renderer는 WebGL 품질과 성능을 위해 pixelRatio를 제한하고, 단면 보기 기능을 위해 localClipping을 켰습니다.
function createRenderer() {
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.localClippingEnabled = true;
  renderer.shadowMap.enabled = true;
  els.viewer.appendChild(renderer.domElement);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.screenSpacePanning = true;
}

// 의료/해부학 모델은 내부 구조를 봐야 하므로 한 방향 조명만 쓰지 않고
// 반구광, 키라이트, 림라이트, 포인트라이트를 섞어 표면과 윤곽이 모두 보이도록 구성했습니다.
function setupLights() {
  scene.add(new THREE.HemisphereLight(0xf5f7f2, 0x35414b, 2.4));

  const keyLight = new THREE.DirectionalLight(0xffffff, 2.8);
  keyLight.position.set(4, 7, 6);
  keyLight.castShadow = true;
  scene.add(keyLight);

  const rimLight = new THREE.DirectionalLight(0x7bd3c7, 1.4);
  rimLight.position.set(-5, 3, -4);
  scene.add(rimLight);

  const warmLight = new THREE.PointLight(0xffc66d, 1.2, 12);
  warmLight.position.set(0, 3, 4);
  scene.add(warmLight);

  const grid = new THREE.GridHelper(10, 20, 0x476166, 0x20292c);
  grid.name = 'reference-grid';
  grid.position.y = -0.02;
  scene.add(grid);
}

// 뷰어 컨테이너 크기에 맞춰 카메라 비율과 WebGL 캔버스 크기를 동기화합니다.
// 창 크기 변경 시 모델이 찌그러지지 않도록 projection matrix까지 함께 갱신합니다.
function resize() {
  const width = els.viewer.clientWidth || 1;
  const height = els.viewer.clientHeight || 1;
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
  renderer?.setSize(width, height, false);
}

// GLTF 모델을 교체할 때 텍스처와 머티리얼을 직접 해제해 브라우저 메모리 누수를 줄입니다.
function disposeMaterial(material) {
  if (!material) return;
  Object.keys(material).forEach((key) => {
    const value = material[key];
    if (value?.isTexture) value.dispose();
  });
  material.dispose();
}

// 현재 장면의 모델과 Object URL을 정리합니다.
// 서버 모델 선택과 로컬 파일 업로드를 반복해도 이전 geometry/material이 남지 않게 만든 부분입니다.
function clearModel() {
  if (currentObjectUrl) {
    URL.revokeObjectURL(currentObjectUrl);
    currentObjectUrl = null;
  }

  if (!currentModel) return;
  scene.remove(currentModel);
  currentModel.traverse((node) => {
    if (!node.isMesh) return;
    node.geometry?.dispose();
    const materials = Array.isArray(node.material) ? node.material : [node.material];
    materials.forEach(disposeMaterial);
  });
  currentModel = null;
}

// 로드된 GLTF의 모든 mesh에 그림자, 양면 렌더링, clipping 설정을 적용합니다.
// 원래 emissive 값은 구조 하이라이트를 해제할 때 되돌리기 위해 userData에 보관합니다.
function prepareMaterials(object) {
  object.traverse((node) => {
    if (!node.isMesh) return;
    node.castShadow = true;
    node.receiveShadow = true;
    const materials = Array.isArray(node.material) ? node.material : [node.material];
    materials.filter(Boolean).forEach((material) => {
      material.side = THREE.DoubleSide;
      material.clipShadows = true;
      material.userData.originalEmissive = material.emissive?.clone?.() || null;
      material.userData.originalEmissiveIntensity = material.emissiveIntensity || 0;
    });
  });
  applySectionClipping();
}

// 모델의 bounding box를 기준으로 중심을 원점에 맞추고 카메라 거리/near/far/컨트롤 범위를 자동 계산합니다.
// 모델마다 크기가 달라도 첫 화면에서 적절한 비율로 보이도록 한 핵심 뷰어 로직입니다.
function frameObject(object) {
  const box = new THREE.Box3().setFromObject(object);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxSize = Math.max(size.x, size.y, size.z) || 1;

  object.position.sub(center);
  currentBounds = { size, maxSize };

  const distance = maxSize / (2 * Math.tan((camera.fov * Math.PI) / 360));
  camera.position.set(distance * 0.85, distance * 0.55, distance * 1.35);
  camera.near = Math.max(maxSize / 1000, 0.001);
  camera.far = maxSize * 120;
  camera.updateProjectionMatrix();

  if (controls) {
    controls.target.set(0, 0, 0);
    controls.minDistance = maxSize * 0.08;
    controls.maxDistance = maxSize * 18;
    controls.update();
  }

  const grid = scene.getObjectByName('reference-grid');
  if (grid) grid.scale.setScalar(Math.max(maxSize / 4, 1));
  updateClipPlane();
}

// 단면 보기 토글 상태를 각 mesh material의 clippingPlanes에 반영합니다.
// Three.js clipping plane은 material 단위로 적용되기 때문에 traverse로 전체 mesh를 순회합니다.
function applySectionClipping() {
  if (!currentModel) return;
  currentModel.traverse((node) => {
    if (!node.isMesh) return;
    const materials = Array.isArray(node.material) ? node.material : [node.material];
    materials.filter(Boolean).forEach((material) => {
      material.clippingPlanes = sectionState.enabled ? [clipPlane] : [];
      material.needsUpdate = true;
    });
  });
}

// X/Y/Z 축과 슬라이더 값을 실제 clipping plane의 normal/constant 값으로 변환합니다.
// 모델 크기 기준으로 offset을 계산해서 작은 모델과 큰 모델에서 단면 이동 감각이 비슷하도록 맞췄습니다.
function updateClipPlane() {
  const normalMap = {
    x: new THREE.Vector3(1, 0, 0),
    y: new THREE.Vector3(0, 1, 0),
    z: new THREE.Vector3(0, 0, 1),
  };
  const axisSize = currentBounds.size[sectionState.axis] || currentBounds.maxSize || 1;
  const offset = (sectionState.value / 100) * (axisSize * 0.5);

  clipPlane.normal.copy(normalMap[sectionState.axis]);
  clipPlane.constant = -offset;
  applySectionClipping();

  els.modeChip.textContent = sectionState.enabled
    ? `단면 ${sectionState.axis.toUpperCase()}축 ${sectionState.value}%`
    : '회전 모드';
}

// 서버에서 받은 학습 모델 목록을 좌측 목록 UI로 렌더링합니다.
// 현재 선택된 모델은 active 클래스로 표시해서 사용자가 보고 있는 씬을 바로 구분할 수 있게 했습니다.
function renderSceneList() {
  els.sceneList.innerHTML = '';
  els.sceneCount.textContent = String(currentScenes.length);

  if (currentScenes.length === 0) {
    els.sceneList.innerHTML = '<div class="empty-state">등록된 학습 모델이 없습니다.</div>';
    return;
  }

  currentScenes.forEach((item) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `scene-button${String(item.id) === String(activeSceneId) ? ' active' : ''}`;
    button.innerHTML = `
      <strong>${item.engTitle || item.title}</strong>
      <span>${item.description || item.title || '학습 모델'}</span>
    `;
    button.addEventListener('click', () => selectScene(item.id));
    els.sceneList.appendChild(button);
  });
}

// study-content.json의 학습 목표, 주요 구조, 관찰 미션, 체크리스트, 퀴즈를 화면에 바인딩합니다.
// 학습 콘텐츠를 JS 코드와 분리해 모델이 추가되어도 UI 로직을 크게 수정하지 않도록 설계했습니다.
function renderStudyPanel(content) {
  currentContent = content || studyContent.default;
  els.studySummary.textContent = currentContent.summary || '';
  els.objectives.innerHTML = '';
  els.structures.innerHTML = '';
  els.missions.innerHTML = '';
  els.checklist.innerHTML = '';
  els.quizOptions.innerHTML = '';
  els.quizFeedback.textContent = '';

  (currentContent.objectives || []).forEach((objective) => {
    const li = document.createElement('li');
    li.textContent = objective;
    els.objectives.appendChild(li);
  });

  (currentContent.structures || []).forEach((structure) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'structure-button';
    button.innerHTML = `
      <strong>${structure.name}</strong>
      <span>${structure.description}</span>
      <em>${structure.observation}</em>
    `;
    button.addEventListener('click', () => highlightStructure(structure.name));
    els.structures.appendChild(button);
  });

  (currentContent.missions || []).forEach((mission, index) => {
    const row = document.createElement('label');
    row.className = 'mission-item';
    row.innerHTML = `
      <input type="checkbox" />
      <span><strong>${index + 1}. ${mission.title}</strong>${mission.instruction}</span>
    `;
    els.missions.appendChild(row);
  });

  (currentContent.checklist || []).forEach((item) => {
    const row = document.createElement('label');
    row.className = 'check-item';
    row.innerHTML = `<input type="checkbox" /><span>${item}</span>`;
    els.checklist.appendChild(row);
  });

  const quiz = currentContent.quiz;
  els.quizQuestion.textContent = quiz?.question || '이 모델에는 아직 퀴즈가 없습니다.';
  (quiz?.options || []).forEach((option, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'quiz-option';
    button.textContent = option;
    button.addEventListener('click', () => answerQuiz(index));
    els.quizOptions.appendChild(button);
  });
}

// 객관식 퀴즈의 정답 여부를 즉시 피드백합니다.
// 3D 관찰 직후 확인 학습이 이어지도록 서버 왕복 없이 프론트에서 가볍게 처리합니다.
function answerQuiz(index) {
  const quiz = currentContent?.quiz;
  if (!quiz) return;
  const isCorrect = index === quiz.answer;
  els.quizFeedback.textContent = isCorrect ? `정답입니다. ${quiz.explanation}` : `다시 생각해 보세요. ${quiz.explanation}`;
  els.quizFeedback.className = `quiz-feedback ${isCorrect ? 'correct' : 'wrong'}`;
}

// 구조 하이라이트를 초기 상태로 되돌립니다.
// 하이라이트 적용 전에 저장해 둔 emissive 값을 사용해 여러 구조를 연속 클릭해도 색이 누적되지 않습니다.
function resetHighlights() {
  if (!currentModel) return;
  currentModel.traverse((node) => {
    if (!node.isMesh) return;
    const materials = Array.isArray(node.material) ? node.material : [node.material];
    materials.filter(Boolean).forEach((material) => {
      if (!material.emissive) return;
      const original = material.userData.originalEmissive || new THREE.Color(0x000000);
      material.emissive.copy(original);
      material.emissiveIntensity = material.userData.originalEmissiveIntensity || 0;
    });
  });
}

// 학습 패널에서 구조 이름을 클릭하면 GLTF node 이름과 비교해 관련 mesh를 강조합니다.
// 모델마다 naming이 완벽히 같지 않을 수 있어 단어 단위 토큰 매칭으로 느슨하게 연결했습니다.
function highlightStructure(label) {
  if (!currentModel) return;
  resetHighlights();
  const tokens = normalizeKey(label)
    .split(/[^a-z0-9가-힣]+/)
    .filter((token) => token.length > 1);
  let highlighted = 0;

  currentModel.traverse((node) => {
    if (!node.isMesh) return;
    const meshName = normalizeKey(node.name);
    const shouldHighlight = tokens.some((token) => meshName.includes(token));
    if (!shouldHighlight) return;
    const materials = Array.isArray(node.material) ? node.material : [node.material];
    materials.filter(Boolean).forEach((material) => {
      if (!material.emissive) return;
      material.emissive.set(0xffb84a);
      material.emissiveIntensity = 0.55;
      highlighted += 1;
    });
  });

  setStatus(highlighted > 0
    ? `선택한 구조: ${label}`
    : `${label}: 회전, 확대, 단면 모드를 사용해 이 영역을 관찰해 보세요.`);
}

// 초기 구동 시 학습 콘텐츠 JSON과 서버 모델 목록을 병렬로 불러옵니다.
// 두 데이터가 모두 있어야 첫 모델을 자동 선택할 수 있으므로 Promise.all로 로딩 순서를 단순화했습니다.
async function loadScenes() {
  setStatus('학습 모델 목록을 불러오는 중...');
  const [contentResponse, scenesResponse] = await Promise.all([
    fetch('/study-content.json'),
    fetch('/scenes?limit=50'),
  ]);

  if (!contentResponse.ok) throw new Error(`학습 콘텐츠 로딩 실패: ${contentResponse.status}`);
  if (!scenesResponse.ok) throw new Error(`모델 목록 로딩 실패: ${scenesResponse.status}`);

  studyContent = await contentResponse.json();
  const data = await scenesResponse.json();
  currentScenes = data.scenes || [];
  renderSceneList();

  if (currentScenes.length > 0) {
    await selectScene(currentScenes[0].id);
  } else {
    setTitle('등록된 모델이 없습니다', 'GLB 또는 embedded GLTF 모델을 추가하면 학습을 시작할 수 있습니다.');
    renderStudyPanel(studyContent.default);
    setStatus('서버에 등록된 모델이 없습니다.');
  }
}

// 좌측 목록에서 선택한 sceneId를 기준으로 서버의 GLTF 모델을 불러오고 학습 패널을 함께 갱신합니다.
// 모델 데이터, 카메라 프레이밍, 학습 콘텐츠가 하나의 사용자 흐름으로 맞물리도록 묶은 함수입니다.
async function selectScene(sceneId) {
  activeSceneId = sceneId;
  renderSceneList();
  clearModel();
  resetHighlights();

  const sceneInfo = currentScenes.find((item) => String(item.id) === String(sceneId));
  setTitle(sceneInfo?.engTitle || sceneInfo?.title || `모델 ${sceneId}`, sceneInfo?.description || '3D 관찰 학습 모델');
  renderStudyPanel(getStudyContent(sceneInfo));
  setStatus('3D 학습 모델을 불러오는 중...');

  if (!renderer || !controls) {
    setStatus('모델 목록은 불러왔지만, 이 브라우저에서 WebGL을 사용할 수 없습니다.');
    return;
  }

  loader.load(
    `/scenes/${sceneId}/model`,
    (gltf) => {
      currentModel = gltf.scene;
      scene.add(currentModel);
      prepareMaterials(currentModel);
      frameObject(currentModel);
      setStatus('모델 로딩 완료. 회전과 확대 후 단면 모드로 내부 구조를 관찰해 보세요.');
    },
    undefined,
    (error) => {
      console.error(error);
      setStatus('모델을 불러오지 못했습니다. GLTF 형식과 에셋 경로를 확인하세요.');
    },
  );
}

// 사용자가 직접 선택한 GLB 또는 embedded GLTF 파일을 Object URL로 로드합니다.
// 포트폴리오 시연에서 기본 제공 모델 외에도 임의 모델 확장 가능성을 보여주는 기능입니다.
function loadLocalFile(file) {
  if (!file) return;
  const lowerName = file.name.toLowerCase();
  if (!lowerName.endsWith('.glb') && !lowerName.endsWith('.gltf')) {
    setStatus('.glb 파일 또는 embedded .gltf 파일을 선택하세요.');
    return;
  }

  clearModel();
  activeSceneId = null;
  renderSceneList();
  setTitle(file.name, '내 3D 파일을 같은 학습 도구로 미리 봅니다.');
  renderStudyPanel(studyContent.default);
  setStatus('로컬 GLB / GLTF 파일을 불러오는 중...');

  currentObjectUrl = URL.createObjectURL(file);
  loader.load(
    currentObjectUrl,
    (gltf) => {
      currentModel = gltf.scene;
      scene.add(currentModel);
      prepareMaterials(currentModel);
      frameObject(currentModel);
      setStatus('로컬 모델 로딩 완료. 단면 모드와 퀴즈/체크리스트를 사용할 수 있습니다.');
    },
    undefined,
    (error) => {
      console.error(error);
      setStatus('파일을 불러오지 못했습니다. GLTF는 embedded 파일이거나 참조 파일이 함께 있어야 합니다.');
    },
  );
}

// 2D 이미지 기반 3D 생성 기능에 넘길 입력 이미지를 미리 보여줍니다.
// 실제 생성은 TripoSplat 화면에서 진행하고, 여기서는 파일 형식 확인과 미리보기만 담당합니다.
function previewInputImage(file) {
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    els.splatStatus.textContent = '이미지 파일을 선택하세요.';
    return;
  }

  if (currentImageUrl) URL.revokeObjectURL(currentImageUrl);
  currentImageUrl = URL.createObjectURL(file);
  els.imagePreview.innerHTML = `<img src="${currentImageUrl}" alt="선택한 입력 이미지 미리보기" />`;
  els.splatStatus.textContent = `${file.name} 입력 이미지가 준비되었습니다. TripoSplat 생성 화면에서 같은 이미지를 선택해 실행하세요.`;
}

// Spring Boot를 통해 로컬 TripoSplat 서버 상태를 확인한 뒤 iframe으로 연결합니다.
// 브라우저가 직접 7860 포트를 추측하지 않고 백엔드 status API를 거쳐 실행 여부를 안내합니다.
async function connectTripoSplat() {
  els.splatStatus.textContent = 'TripoSplat 서버 상태를 확인하는 중...';
  els.splatFrame.classList.remove('active');
  els.splatFrame.removeAttribute('src');

  try {
    const response = await fetch('/integrations/triposplat/status');
    const status = await response.json();

    if (!status.running) {
      els.splatStatus.textContent = 'TripoSplat 서버가 실행 중이 아닙니다. 아래 경로에서 python run_gradio.py를 먼저 실행한 뒤 다시 연결하세요.';
      return;
    }

    els.splatFrame.src = status.url || TRIPOSPLAT_URL;
    els.splatFrame.classList.add('active');
    els.splatStatus.textContent = 'TripoSplat 생성 화면을 연결했습니다. 이미지 생성은 연결된 화면에서 진행하세요.';
  } catch (error) {
    console.error(error);
    els.splatStatus.textContent = 'TripoSplat 상태 확인에 실패했습니다. Python 서버 실행 여부와 7860 포트를 확인하세요.';
  }
}

// 버튼, 파일 업로드, 단면 슬라이더, 탭 전환 이벤트를 한 곳에서 등록합니다.
// 초기화 순서를 명확히 하기 위해 DOM 이벤트 연결을 별도 함수로 분리했습니다.
function bindEvents() {
  els.resetCamera.addEventListener('click', () => {
    if (currentModel) frameObject(currentModel);
  });

  els.fileInput.addEventListener('change', (event) => {
    loadLocalFile(event.target.files?.[0]);
    event.target.value = '';
  });

  els.imageInput?.addEventListener('change', (event) => {
    previewInputImage(event.target.files?.[0]);
  });

  els.connectSplat?.addEventListener('click', connectTripoSplat);

  els.openSplat?.addEventListener('click', () => {
    window.open(TRIPOSPLAT_URL, '_blank', 'noopener,noreferrer');
    els.splatStatus.textContent = 'TripoSplat 생성 화면을 새 창으로 열었습니다.';
  });

  els.sectionToggle.addEventListener('change', (event) => {
    sectionState.enabled = event.target.checked;
    updateClipPlane();
    setStatus(sectionState.enabled
      ? '단면 모드가 켜졌습니다. 슬라이더를 움직여 깊이에 따른 구조를 관찰하세요.'
      : '단면 모드가 꺼졌습니다. 회전 모드로 돌아왔습니다.');
  });

  els.sectionSlider.addEventListener('input', (event) => {
    sectionState.value = Number(event.target.value);
    updateClipPlane();
  });

  document.querySelectorAll('.axis-button').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('.axis-button').forEach((item) => item.classList.remove('active'));
      button.classList.add('active');
      sectionState.axis = button.dataset.axis || 'z';
      updateClipPlane();
    });
  });

  document.querySelectorAll('.tab-button').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('.tab-button').forEach((item) => item.classList.remove('active'));
      document.querySelectorAll('.tab-panel').forEach((item) => item.classList.remove('active'));
      button.classList.add('active');
      document.querySelector(`[data-panel="${button.dataset.tab}"]`)?.classList.add('active');
    });
  });

  window.addEventListener('resize', resize);
}

// requestAnimationFrame 루프입니다. OrbitControls의 damping 효과를 위해 매 프레임 update 후 렌더링합니다.
function animate() {
  requestAnimationFrame(animate);
  controls?.update();
  renderer?.render(scene, camera);
}

try {
  createRenderer();
  setupLights();
  bindEvents();
  resize();
  animate();
  loadScenes().catch((error) => {
    console.error(error);
    setTitle('시작 실패', error.message || String(error));
    renderStudyPanel(studyContent.default || {});
    setStatus(`뷰어 시작 실패: ${error.message || error}`);
  });
} catch (error) {
  console.error(error);
  setTitle('WebGL 시작 실패', '브라우저 하드웨어 가속을 켜거나 Chrome / Edge에서 다시 시도하세요.');
  setStatus('브라우저에서 WebGL 렌더러를 시작하지 못했습니다.');
}
