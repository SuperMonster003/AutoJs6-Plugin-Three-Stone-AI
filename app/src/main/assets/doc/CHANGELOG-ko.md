******

### 릴리스 기록

******

# v1.1.0

###### 2026/08/23

* `기능` 플러그인 이름을 On-Device AI (온디바이스 AI)로 변경하고 AutoJs6 공식 온디바이스 AI 플러그인으로 자리매김
* `기능` AutoJs6 `ai.ask`/`ai.chat`/`ai.stream`의 `plugin: true` 축약 선택자 및 `ai.models` 모델 열거 지원
* `기능` On-Device AI 프로토콜 1.1을 통해 `temperature`, `topK`, `topP`, `maxTokens`를 LiteRT-LM sampling 및 출력 token 제어까지 전달
* `기능` LiteRT-LM의 정확한 입력, 출력, 전체 token 수와 공급자 측에서 측정한 생성 시간을 `ai.chat().usage` 및 스트림 usage 이벤트로 보고
* `기능` On-Device AI 프로토콜 1.2 영구 세션과 AutoJs6 `ai.session` 여러 턴 Conversation 재사용을 추가하여 이전 기록 재전송 제거
* `기능` AutoJs6 `structuredJson`과 `responseSchema`를 통한 LiteRT-LM 네이티브 JSON Schema 제약 디코딩을 추가하고 단일 호출, 스트리밍, 영구 세션 및 완성 JSON의 엄격한 검증을 지원
* `기능` 프로토콜 1.3과 AutoJs6 생성 옵션을 통한 명시적 `cpu`, `gpu`, `npu` backend profile, 기기 호환성 보고, 모델/profile별 캐시 격리 및 사용 불가 profile의 CPU fallback 금지; GPU는 OpenCL 로드 검사 성공 후에만 선언하고 NPU는 EAP runtime 미포함으로 사용 불가 유지
* `기능` 고정 LiteRT Community 모델을 사용자가 선택한 SAF 위치로 직접 다운로드하고 진행률, 정확한 취소, 불완전 파일 정리, LiteRT-LM 헤더 및 정확한 크기/SHA-256 검증, 다운로드 후 직접 가져오기를 지원
* `기능` 런처에서 여는 대화 작업 공간에 스트리밍 Markdown, 영구 기록, 이전 메시지 편집 시 분기 교체 경고, 다중 결과 검색, 키보드 대응 입력을 추가
* `기능` 테마 색상, 다크 모드, 앱 언어, 앱 및 개발자 정보, 버전 기록을 위한 앱 설정을 추가하고 가능한 항목의 기본값을 AutoJs6 따르기로 지정
* `기능` 글꼴 크기, Enter 키 동작, 무제한 또는 사용자 지정 output token, 모델 기본값 또는 사용자 지정 `temperature`, `topK`, `topP`를 대화 설정에 추가
* `기능` 스트리밍 중 인라인 `$\text{...}$` 콘텐츠를 렌더링하고 일반 수학 명령과 위 첨자 및 아래 첨자 스타일을 지원
* `수정` 실행 가능한 안내 예제의 암묵적 256 token 및 4 KiB 출력 제한을 제거하여 `maxTokens` 생략 시 모델 또는 engine 기본값을 사용하고 raw Binder 예제는 provider의 전체 64 KiB 출력 허용량을 사용하도록 수정
* `수정` 10개 언어로 현지화된 플러그인 안내의 저수준 Binder 예제가 프로토콜 1.1의 14개 인자 `AiGenerationOptions` 생성자를 계속 호출하여 프로토콜 1.3 API에서 실패하던 문제 수정
* `수정` 시스템 다크 모드에서도 모델 관리 화면이 라이트 테마 텍스트 색상을 유지해 본문, 체크박스, 모델 행을 어두운 배경에서 읽을 수 없던 문제 수정
* `수정` 작성 영역을 소프트 키보드 위에 유지하고 활성 테마 색상 대비에 따라 보내기 버튼 글자색을 선택하며 이전, 다음, 닫기 검색 컨트롤을 통일
* `개선` 플러그인 설명, 사용 안내 및 10개 언어 README를 호스트 `ai.*` 로컬 플러그인 경로 정식화에 맞게 갱신
* `개선` ROADMAP을 항목별로 체크 가능한 기능 로드맵으로 재작성
* `개선` 앱과 생성된 현지화 문서의 문장 부호를 ASCII로 통일하고 패키지 및 생성 문서를 검사하는 회귀 테스트를 추가

# v1.0.0

###### 2026/08/08

* `기능` 플러그인 ID 및 엔진 `on-device-ai`, provider ID `autojs6.on-device-ai`, 변형 `default`인 기기 내 On-Device AI 프로토콜 V1 provider
* `기능` System, user 및 assistant 기록과 credit 제어 스트리밍을 지원하는 CPU-only LiteRT-LM 일반 텍스트 생성
* `기능` 8 GiB 상한, 여유 공간 예약, SHA-256, fsync 및 원자적 활성화를 포함한 `.litertlm`의 앱 전용 저장소 SAF 가져오기
* `기능` 단일 활성 세션, 제한된 I/O, descriptor quota, 취소, timeout, 단일 종료 상태 및 동일 서명 AutoJs6 호출자 검증
* `기능` Reasoning, tools, structured JSON, usage, 네트워크 및 credential 기능의 명시적 미지원
* `기능` arm64-v8a, x86_64 및 universal APK와 10개 언어 README, changelog, Android UI 및 플러그인 안내
* `기능` 전체 모델 카탈로그와 앱 전용 저장소 사용량을 확인하고 모델 파일을 복사하지 않은 채 현재 모델을 원자적으로 전환하는 모델 관리 화면
* `개선` `:provider`와의 프로세스 간 경쟁을 피하기 위해 교체 가져오기 후에도 이전 SHA-256 hash 이름 모델 세대를 보존하며 보존 파일은 앱 전용 저장소를 계속 사용
* `개선` Activity 재생성 연속성, 콜드 스타트 복구, stale 임시 파일 cleanup 및 현재 시도가 만들고 게시하지 않은 destination으로 제한된 삭제를 위해 application scope 단일 가져오기 coordinator와 fsync된 pending journal을 추가하고 게시됨, current 및 이전 hash 세대를 보존
* `의존성` 기기 내 CPU 텍스트 생성을 위해 LiteRT-LM 0.15.0 추가
