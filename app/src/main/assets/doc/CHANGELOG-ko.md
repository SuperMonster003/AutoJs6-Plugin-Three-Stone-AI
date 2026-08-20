******

### 릴리스 기록

******

# v1.1.0

###### 2026/08/20

* `기능` 플러그인 이름을 On-Device AI (온디바이스 AI)로 변경하고 AutoJs6 공식 온디바이스 AI 플러그인으로 자리매김
* `기능` AutoJs6 `ai.ask`/`ai.chat`/`ai.stream`의 `plugin: true` 축약 선택자 및 `ai.models` 모델 열거 지원
* `개선` 플러그인 설명, 사용 안내 및 10개 언어 README를 호스트 `ai.*` 로컬 플러그인 경로 정식화에 맞게 갱신
* `개선` ROADMAP을 항목별로 체크 가능한 기능 로드맵으로 재작성

# v1.0.0

###### 2026/08/08

* `기능` 플러그인 ID 및 엔진 `ai-text-generation`, provider ID `autojs6.local.text`, 변형 `default`인 기기 내 AI Text Generation 프로토콜 V1 provider
* `기능` System, user 및 assistant 기록과 credit 제어 스트리밍을 지원하는 CPU-only LiteRT-LM 일반 텍스트 생성
* `기능` 8 GiB 상한, 여유 공간 예약, SHA-256, fsync 및 원자적 활성화를 포함한 `.litertlm`의 앱 전용 저장소 SAF 가져오기
* `기능` 단일 활성 세션, 제한된 I/O, descriptor quota, 취소, timeout, 단일 종료 상태 및 동일 서명 AutoJs6 호출자 검증
* `기능` Reasoning, tools, structured JSON, usage, 네트워크 및 credential 기능의 명시적 미지원
* `기능` arm64-v8a, x86_64 및 universal APK와 10개 언어 README, changelog, Android UI 및 플러그인 안내
* `기능` 전체 모델 카탈로그와 앱 전용 저장소 사용량을 확인하고 모델 파일을 복사하지 않은 채 현재 모델을 원자적으로 전환하는 모델 관리 화면
* `개선` `:provider`와의 프로세스 간 경쟁을 피하기 위해 교체 가져오기 후에도 이전 SHA-256 hash 이름 모델 세대를 보존하며 보존 파일은 앱 전용 저장소를 계속 사용
* `개선` Activity 재생성 연속성, 콜드 스타트 복구, stale 임시 파일 cleanup 및 현재 시도가 만들고 게시하지 않은 destination으로 제한된 삭제를 위해 application scope 단일 가져오기 coordinator와 fsync된 pending journal을 추가하고 게시됨, current 및 이전 hash 세대를 보존
* `의존성` 기기 내 CPU 텍스트 생성을 위해 LiteRT-LM 0.15.0 추가
