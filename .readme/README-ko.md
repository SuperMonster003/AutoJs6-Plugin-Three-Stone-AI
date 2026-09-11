<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>통합 AI 플러그인. LiteRT-LM은 항상 로컬이며 온라인 대상은 명시적으로 선택됩니다</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어

******

현재 README.md는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### 소개

******

3-Stone AI는 AutoJs6의 공식 AI 텍스트 생성 플러그인입니다. 사용자가 가져온 LiteRT-LM 모델을 명시적으로 선택한 CPU 또는 호환 GPU backend에서 실행하고 사용자가 구성한 online profile에만 연결합니다. 로컬 및 온라인 실행 위치는 하나의 AI Provider V2 target catalog, 제어된 스트리밍 파이프라인, 명시적 선택 경계를 공유합니다. 로컬 대상은 네트워크에 접근하거나 데이터를 업로드하지 않으며, 온라인 대상은 사용자가 명시적으로 선택한 경우에만 플러그인이 관리하는 자격 증명과 선언된 HTTPS origin에 고정되어 실행됩니다.

******

### 기능

******

- Android 시스템 선택기로 `.litertlm` 모델 패키지를 가져오고 검증된 복사본을 앱 전용 저장소에 보관합니다.
- 고정 버전이며 인증이 필요 없는 LiteRT Community 모델을 사용자가 선택한 SAF 위치로 직접 다운로드하고 진행률, 취소, 불완전 파일 정리, 정확한 크기와 SHA-256 검증을 제공합니다.
- 시스템 선택기를 열기 전에 비공개 저장 공간을 사전 확인하고, 현재 가져오기 예산과 비공개 사본의 예상 사용량을 표시하며, 복사 전에 선택한 파일을 다시 확인합니다.
- 일반 텍스트 system, user 및 assistant 기록으로 로컬 생성 요청을 만듭니다.
- AutoJs6 `ai.ask`, `ai.chat`, `ai.stream`의 `temperature`, `topK`, `topP`, `maxTokens`를 LiteRT-LM까지 전달합니다.
- AutoJs6 `structuredJson`과 `responseSchema`를 통해 LiteRT-LM 네이티브 JSON Schema 제약 디코딩을 사용하며, 완성된 값은 `JSON.parse`용 JSON 텍스트로 유지합니다.
- LiteRT-LM의 정확한 입력, 출력, 전체 token 수와 공급자 측 생성 시간을 AutoJs6 `ai.chat().usage` 및 스트림 usage 이벤트로 반환합니다.
- AutoJs6 `ai.session`으로 하나의 LiteRT-LM 네이티브 Conversation에 여러 턴의 컨텍스트를 유지하고 이후 턴에는 새 사용자 프롬프트만 전송합니다.
- 모델 SHA-256을 키로 초기화된 Engine을 재사용하여 같은 모델에 대한 연속 요청의 반복 콜드 스타트를 없앱니다.
- 가져온 각 모델을 선택적으로 한 번 초기화하고 사용 가능/호환되지 않음 상태를 저장하며 모델 관리자에서 다시 확인합니다.
- credit 역압력으로 텍스트 chunk를 순서대로 전달하고 완료, 실패 또는 취소 중 하나의 종료 상태만 게시합니다.
- 가져온 모델을 나열하고 선택하고 이름을 변경하며, 선택되지 않은 모델을 삭제하고 관리 화면에서 참조되지 않은 모델 파일을 회수합니다.
- AutoJs6에서 `cpu`, `gpu`, `npu` backend를 명시적으로 선택합니다. CPU가 기본값이며 GPU는 OpenCL 로드 검사를 통과할 때만 노출되고, NPU는 EAP runtime 미포함으로 사용 불가가 명시됩니다.
- 앱 설정에서 기본 제공 및 사용자 지정 온라인 profile, Android Keystore 자격 증명, 기본 온라인 대상, 종량제 네트워크 및 명시적인 제한형 연결 테스트를 관리합니다.
- 각 런처 대화를 하나의 로컬 또는 온라인 대상 스냅샷에 고정합니다. 메시지가 있는 대화에서 변경하면 새 대화를 권장하고 계속하려면 명시적 확인과 변경 기록이 필요합니다.
- 각 어시스턴트 응답에 실제 target/provider/model/locality 스냅샷을 저장합니다. 다시 생성은 기록된 원래 대상을 정확히 재사용하며 대상 정체성이 바뀌거나 사용할 수 없으면 명확히 실패하고 현재 대화 대상으로 자동 대체하지 않습니다.
- 로컬 및 클라우드 생성 실패를 선택된 경계에 유지합니다. 런처 chat은 민감한 정보가 없는 제한된 실패 원인을 추가하고 경계를 넘는 자동 대체가 없었음을 명시하며 부분 출력을 유지한 채 명시적인 수동 대상 전환을 제공합니다.

******

### 모델 및 데이터 형식

******

버전 1은 다음 모델 및 텍스트 범위만 선언합니다:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### 플러그인 인터페이스

******

호스트는 다음 식별자로 플러그인을 검색하고 호출합니다:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2
required host build: 5276
```

AI Provider V2는 `local:*` 및 `profile:*` 대상을 하나의 페이지형 catalog로 공개합니다. 각 대상은 provider, model, locality, 구성/가용성, capabilities, limits, controls, HTTPS origins를 개별적으로 선언합니다. 로컬 전용 catalog는 ON_DEVICE/NONE을 선언하고 online profile이 있으면 HYBRID/PLUGIN_MANAGED와 HTTPS origins의 정확한 합집합을 선언합니다. 로컬 backend profile은 선택적 target control이며 사용할 수 없는 profile이나 대상으로 자동 전환하지 않습니다.

호스트 build 5276 이상이 필요합니다. 릴리스에는 arm64-v8a, x86_64, universal APK 변형이 포함됩니다.

******

### 호스트 통합 상태

******

> AutoJs6(빌드 5276 이상)에서 `ai.catalog()`는 가져온 로컬 모델과 구성된 온라인 profile을 하나의 대상 카탈로그로 반환하며 정확한 ID, 공급자, 모델, 위치, 구성/가용 상태, 기능, 제어, 제한, origin 및 로컬 backend profile을 포함합니다. 정확한 `target`을 `ai.ask`, `ai.chat`, `ai.stream` 또는 `ai.session`에 전달합니다. `target`만 지정하면 공식 3-Stone AI 플러그인을 선택하고 `plugin: true`는 플러그인이 선언한 기본 대상을 사용합니다. 로컬 대상은 `cpu`, `gpu`, 사용 불가 `npu`를 제공할 수 있지만 온라인 대상에는 로컬 실행 profile이 없습니다. backend 또는 대상을 사용할 수 없으면 fallback 없이 실패하며 로컬/온라인 경로도 자동으로 전환되지 않습니다. 완료 및 스트리밍 응답은 target, plugin, profile, reasoning, finish reason, 전체 usage 및 공급자 측정 시간을 제공합니다. 안정된 오류는 공급자 누락/비활성화, 대상 알 수 없음/미구성/사용 불가/기능 불일치 및 backend 사용 불가를 구분합니다. `responseSchema`는 구조화 출력을 활성화하고 schema 없는 `structuredJson: true`는 기본 object 루트를 사용하며 영구 세션은 모든 턴에서 같은 target, schema 및 선택 backend를 고정합니다.

******

### 보안 및 개인정보 보호

******

사용자가 시작한 권장 모델 다운로드와 사용자가 구성한 online 대상 요청에 `INTERNET` 권한을 사용하지만 로컬 생성은 네트워크를 사용하지 않습니다. 광범위한 저장소 권한은 요청하지 않습니다. 다운로드는 변경 불가능한 HTTPS 리비전, 고정 크기와 SHA-256을 사용하고 선택한 SAF 위치에만 쓰며 LiteRT-LM 헤더, 크기, 다이제스트, flush, fsync가 모두 통과해야 완료됩니다. 가져오기는 계속 선택기 URI만 읽고 검증 사본을 `files/models`에 써서 원자적으로 활성화합니다. Provider는 AutoJs6 패키지, UID, 서명, target metadata 및 선언된 origin 경계도 검증합니다.

******

### 운영 제한

******

- 모델 가져오기에는 8 GiB의 엄격한 상한이 있고 완료 후 최소 256 MiB의 여유 공간이 필요합니다.
- 앱 프로세스에서는 다운로드 하나만 실행됩니다. Activity 재생성 후에도 진행률과 취소 소유권을 유지하며 취소 또는 실패 시 대상을 삭제하거나 비웁니다. 프로세스가 종료되면 외부의 불완전 문서가 남을 수 있어 직접 삭제해야 합니다.
- Application scope 단일 가져오기 coordinator가 Activity 재생성 중에도 작업을 유지합니다. Fsync된 pending journal로 콜드 스타트 복구와 stale `.incoming`, `.current`, `.pending` 임시 파일 cleanup을 수행합니다. 복구는 현재 시도가 새로 만들고 current metadata로 게시한 적 없는 destination만 삭제하며 게시됨, current 및 이전 hash 세대는 보존합니다.
- 분리된 `:provider` 프로세스와의 프로세스 간 경쟁을 피하기 위해 가져오기 중에는 이전 SHA-256 hash 이름 모델 세대를 자동 삭제하지 않습니다. 모델 관리 화면에서 선택되지 않은 catalog 모델을 삭제하고 catalog에서 더 이상 참조하지 않는 hash 이름 파일을 회수할 수 있습니다.
- 프로세스에서 활성 생성 세션은 최대 1개입니다. 요청 descriptor는 비동기 작업 전에 복제되고 프로토콜 quota에 따라 닫힙니다.
- Provider는 모델 SHA-256과 backend profile 쌍을 키로 초기화된 Engine을 최대 하나만 캐시합니다. 같은 쌍은 재사용하며, 어느 키든 바뀌거나 5분 유휴 또는 명시적 메모리 압박 시 안전하게 해제합니다.
- 모델 확인은 현재 기기와 번들 런타임에서 `Engine.initialize()`가 성공하는지만 증명합니다. 출력 품질은 평가하지 않으며 기기 또는 런타임 변경 후 다시 확인할 수 있습니다.
- Provider가 선언하는 컨텍스트 상한은 256 KiB, 출력 상한은 64 KiB입니다. 요청과 모델은 더 낮은 상한을 적용할 수 있습니다.
- 응답 schema는 64 KiB 이하의 JSON object여야 합니다. 지원 keyword는 포함된 LiteRT-LM/LLGuidance 런타임 구현을 따르며, 완성된 출력은 엄격히 parse 및 검증하므로 전체 JSON 값에 충분한 `maxTokens`를 확보해야 합니다.
- `maxTokens`는 1부터 2,147,483,647까지의 정수입니다. 생략하면 출력 token 수는 모델/엔진 기본값에 맡기며, provider의 64 KiB 출력 안전 한도는 유지됩니다. `temperature`는 유한한 0 이상의 값, `topK`는 양의 정수, `topP`는 0부터 1까지의 유한한 값이어야 합니다. 세 sampling 설정을 모두 생략하면 모델/엔진 기본값을 유지하고, 일부만 지정하면 생략된 항목을 LiteRT-LM 기준값 `topK: 1`, `topP: 0.95`, `temperature: 1`로 채웁니다.
- 스트리밍은 유한 credit과 제한된 chunk를 사용해 무제한 버퍼 또는 역압력 없는 callback을 방지합니다.
- Usage token 수는 LiteRT-LM Conversation의 KV cache 및 decode 카운터에서 직접 가져오며 문자 수로 추정하지 않습니다. `durationMillis`는 플러그인 생성 호출만 측정하고 호스트 탐색, 바인딩, 모델 열거 및 디스패치 시간은 제외합니다.
- 영구적인 `ai.session`은 하나의 활성 턴만 허용하고 정상 완료 후 네이티브 Conversation을 유지합니다. 취소, timeout, 생성 실패 또는 명시적 닫기 후에는 다시 만들어야 합니다.
- 취소, 세션 닫기 및 timeout은 결과 게시를 중단하고 하나의 종료 상태로 요청을 끝냅니다.

******

### 선언하지 않는 기능

******

- Reasoning 및 tools는 선언하지 않습니다.
- Tool 역할 메시지, tool schema, tool call 및 tool result를 받지 않습니다.
- 네트워크 모델 검색이나 임의 URL 모델 다운로드는 없습니다. 런처 채팅과 AI Provider V2에는 가져온 로컬 모델과 명시적으로 설정된 온라인 profile만 표시되며 고정된 내장 권장 카탈로그만 다운로드할 수 있습니다.
- NPU 추론은 선언하지 않습니다. profile은 `npu-runtime-not-packaged` 사유의 `unavailable`로 검색됩니다. GPU는 `libOpenCL.so`를 로드할 수 있을 때만 선언되며 `.litertlm` 확장자만으로 모델 초기화를 보장하지 않습니다.

******

### 로드맵

******

로드맵은 제공 가능한 사용자 기능 단위로 구성되며 각 항목은 개별적으로 체크하고 검수할 수 있습니다

- [ROADMAP.md 보기](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 릴리스 기록

******

# v1.1.1

###### 2026/09/11

* `개선` 64비트 네이티브 라이브러리의 16 KB 페이지 정렬을 빌드 시 검증, manifest 계약 검사 및 JSON 보고서 지원

# v1.1.0

###### 2026/09/01

* `기능` AutoJs6 공식 로컬 AI 플러그인의 브랜드와 런타임 식별자를 3-Stone AI로 통일
* `기능` 프로세스 간 통합에 중립적인 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` 및 `IAiProvider`/`IAiSession`/`IAiCallback` 식별자를 사용하고 교체된 식별자의 alias는 유지하지 않음
* `기능` 서명 권한으로 보호되고 export된 무인자 AI 설정 진입점을 추가하여 AutoJs6가 profile 또는 자격 증명 데이터를 보내지 않고 플러그인 통합 설정을 열 수 있음
* `기능` AI Provider V2 페이지형 target catalog를 통해 `local:*` 및 `profile:*`을 직접 공개하고 각 target의 provider/model/locality, 구성 및 가용 상태, capabilities, limits, controls, HTTPS origins와 정확한 `isDefault` 표식을 독립적으로 선언
* `기능` AI Provider V2 생성 요청을 통해 `temperature`, `topK`, `topP`, `maxTokens`를 LiteRT-LM sampling 및 출력 token 제어까지 전달
* `기능` LiteRT-LM의 정확한 입력, 출력, 전체 token 수와 공급자 측에서 측정한 생성 시간을 `ai.chat().usage` 및 스트림 usage 이벤트로 보고
* `기능` AI Provider V2 영구 세션과 AutoJs6 `ai.session` 여러 턴 Conversation 재사용을 추가하여 이전 기록 재전송 제거
* `기능` AutoJs6 `structuredJson`과 `responseSchema`를 통한 LiteRT-LM 네이티브 JSON Schema 제약 디코딩을 추가하고 단일 호출, 스트리밍, 영구 세션 및 완성 JSON의 엄격한 검증을 지원
* `기능` 명시적 `cpu`, `gpu`, `npu` backend profile을 AI Provider V2 선택형 target control로 제공하며 기기 호환성 보고, 모델/profile별 캐시 격리 및 사용 불가 profile의 CPU fallback 금지; GPU는 OpenCL 로드 검사 성공 후에만 선언하고 NPU는 EAP runtime 미포함으로 사용 불가 유지
* `기능` 고정 LiteRT Community 모델을 사용자가 선택한 SAF 위치로 직접 다운로드하고 진행률, 정확한 취소, 불완전 파일 정리, LiteRT-LM 헤더 및 정확한 크기/SHA-256 검증, 다운로드 후 직접 가져오기를 지원
* `기능` 런처에서 여는 대화 작업 공간에 스트리밍 Markdown, 영구 기록, 이전 메시지 편집 시 분기 교체 경고, 다중 결과 검색, 키보드 대응 입력을 추가
* `기능` 테마 색상, 다크 모드, 앱 언어, 앱 및 개발자 정보, 버전 기록을 위한 앱 설정을 추가하고 가능한 항목의 기본값을 AutoJs6 따르기로 지정
* `기능` 글꼴 크기, Enter 키 동작, 무제한 또는 사용자 지정 output token, 모델 기본값 또는 사용자 지정 `temperature`, `topK`, `topP`를 대화 설정에 추가
* `기능` 스트리밍 중 인라인 `$\text{...}$` 콘텐츠를 렌더링하고 일반 수학 명령과 위 첨자 및 아래 첨자 스타일을 지원
* `기능` 플러그인이 관리하는 Android Keystore 자격 증명 저장소를 추가하고 AES-256-GCM, profile에 바인딩된 인증 암호문, 프로세스 간 원자적 비공개 파일, configured 상태만 조회 및 평문 즉시 삭제를 지원
* `기능` HTTPS 전용 OpenAI Compatible endpoint를 위한 엄격한 비밀정보 비포함 온라인 profile 저장소를 추가하고 canonical UUID, 프로세스 간 원자적 metadata, provider 또는 origin 변경 시 자격 증명의 명시적 교체 또는 삭제를 강제
* `기능` 사용자 지정 baseUrl, 자격 증명, 모델 profile을 위한 플러그인 내부 OpenAI Compatible HTTPS 실행 backend를 추가하고 제한된 SSE 및 JSON fallback 스트리밍, 정확한 취소, provider usage, 완료된 턴만의 영구 기록, JSON Schema 요청 mapping, 민감 정보 없는 고정 오류를 지원; 구성된 `profile:*` target이 AI Provider V2를 통해 직접 호출
* `기능` 호스트 카탈로그와 정렬된 OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter 프리셋을 추가; 통합 온라인 실행 계층은 OpenAI-compatible 프로토콜을 재사용하고 Anthropic Messages와 Gemini GenerateContent 고유의 인증, 요청, SSE 종료, usage, JSON Schema를 각각 어댑트하며 프로토콜 간 또는 로컬/온라인 자동 fallback은 제공하지 않음
* `기능` 10개 언어 온라인 서비스 설정 UI를 추가하여 profile 추가, 편집, 삭제, API 키 비노출 교체 및 지우기, 기본 대상 선택, 자격 증명 접근 전 종량제 네트워크 허용 적용, 취소 가능한 최대 120초 명시 연결 테스트를 지원; 설정은 프로세스 간 원자 문서를 공유하고 V2 target catalog를 동적으로 갱신
* `기능` 런처 채팅에 통합 로컬/클라우드 대상 선택기를 추가: 각 대화는 하나의 대상 스냅샷을 저장하고, 메시지가 있는 대화의 전환은 새 대화를 권장하며, 기존 문맥으로 계속하려면 명시적 확인과 변경 기록이 필요
* `기능` 각 어시스턴트 응답에 실제 target/provider/model/locality 스냅샷을 추가: 다시 생성은 기록된 응답 대상을 정확히 재사용하며 대상 정체성이 바뀌거나 사용할 수 없으면 명확히 실패하고 현재 대화 대상으로 자동 대체하지 않음
* `기능` 로컬 및 클라우드 생성 실패를 선택된 경계에 유지: 런처 chat은 민감한 정보가 없는 제한된 실패 원인을 추가하고 경계를 넘는 자동 대체가 없었음을 명시하며 부분 출력을 유지한 채 명시적인 수동 대상 전환을 제공
* `수정` 실행 가능한 안내 예제의 암묵적 256 token 및 4 KiB 출력 제한을 제거하여 `maxTokens` 생략 시 모델 또는 engine 기본값을 사용하고 raw Binder 예제는 provider의 전체 64 KiB 출력 허용량을 사용하도록 수정
* `수정` 10개 언어로 현지화된 플러그인 안내의 저수준 Binder 예제를 최종 AI Provider V2 요청 및 target 목록 API로 갱신
* `수정` 시스템 다크 모드에서도 모델 관리 화면이 라이트 테마 텍스트 색상을 유지해 본문, 체크박스, 모델 행을 어두운 배경에서 읽을 수 없던 문제 수정
* `수정` 작성 영역을 소프트 키보드 위에 유지하고 활성 테마 색상 대비에 따라 보내기 버튼 글자색을 선택하며 이전, 다음, 닫기 검색 컨트롤을 통일
* `수정` 생성 listener callback 안에서 session을 close할 때 callback quiescence가 자기 자신을 무한히 기다리던 교착을 수정; close는 다른 스레드에서 이미 실행 중인 callback을 계속 대기
* `수정` Android가 신뢰된 `/data/user/0` 앱 데이터 루트를 `/data/data`로 canonicalize할 때 앱 비공개 온라인 profile 및 자격 증명 저장소를 잘못 거부하던 문제를 수정; 직접 자식 링크와 containment 이탈은 계속 거부
* `개선` 플러그인 설명, 사용 안내 및 10개 언어 README를 호스트 `ai.*` 통합 target 경로 정식화에 맞게 갱신
* `개선` ROADMAP을 항목별로 체크 가능한 기능 로드맵으로 재작성
* `개선` 앱과 생성된 현지화 문서의 문장 부호를 ASCII로 통일하고 패키지 및 생성 문서를 검사하는 회귀 테스트를 추가
* `개선` 공유 `AiBackend`/`AiTarget`/`AiBackendSession` 계층을 도입해 런처 채팅과 Binder provider가 `LiteRtLocalBackend`의 catalog, capabilities, session 생성, streaming 및 취소 경로를 공유
* `개선` 로컬 `local:*` 및 온라인 `profile:*` target을 Application 수준의 단일 catalog와 dispatcher로 통합하고 AI Provider V2에서 둘 다 직접 공개; 현재 catalog에서 provider locality, credential mode, HTTPS origins를 동적으로 도출하며 자격 증명 byte는 공개하지 않음
* `개선` README 레이아웃과 Gradle 플랫폼 버전 관리 방식을 통일
* `개선` 업데이트 대화상자의 릴리스 기록 버튼에서 내장 릴리스 기록 페이지를 열도록 변경

# v1.0.0

###### 2026/08/08

* `기능` 플러그인 ID 및 엔진 `three-stone-ai`, provider ID `autojs6.three-stone-ai`, 변형 `default`인 기기 내 AI Provider 기반
* `기능` System, user 및 assistant 기록과 credit 제어 스트리밍을 지원하는 CPU-only LiteRT-LM 일반 텍스트 생성
* `기능` 8 GiB 상한, 여유 공간 예약, SHA-256, fsync 및 원자적 활성화를 포함한 `.litertlm`의 앱 전용 저장소 SAF 가져오기
* `기능` 단일 활성 세션, 제한된 I/O, descriptor quota, 취소, timeout, 단일 종료 상태 및 동일 서명 AutoJs6 호출자 검증
* `기능` Reasoning, tools, structured JSON, usage, 네트워크 및 credential 기능의 명시적 미지원
* `기능` arm64-v8a, x86_64 및 universal APK와 10개 언어 README, changelog, Android UI 및 플러그인 안내
* `기능` 전체 모델 카탈로그와 앱 전용 저장소 사용량을 확인하고 모델 파일을 복사하지 않은 채 현재 모델을 원자적으로 전환하는 모델 관리 화면
* `개선` `:provider`와의 프로세스 간 경쟁을 피하기 위해 교체 가져오기 후에도 이전 SHA-256 hash 이름 모델 세대를 보존하며 보존 파일은 앱 전용 저장소를 계속 사용
* `개선` Activity 재생성 연속성, 콜드 스타트 복구, stale 임시 파일 cleanup 및 현재 시도가 만들고 게시하지 않은 destination으로 제한된 삭제를 위해 application scope 단일 가져오기 coordinator와 fsync된 pending journal을 추가하고 게시됨, current 및 이전 hash 세대를 보존
* `의존성` 기기 내 CPU 텍스트 생성을 위해 LiteRT-LM 0.15.0 추가

##### 추가 릴리스

* [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

******

### 빌드

******

```powershell
.\gradlew.bat :app:assembleDebug
```

릴리스 빌드:

```powershell
.\gradlew.bat :app:assembleRelease
```

빌드 매개변수는 `version.properties`에서 가져옵니다. 현재 최소 SDK는 24, 대상 SDK는 36이고 JDK 21 이상이 필요합니다.

프로토콜 ABI는 `libs`의 저장소 로컬 AAR에서 제공됩니다:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-provider-api.aar
```

Runtime은 Maven의 LiteRT-LM 0.15.0을 사용합니다. 릴리스 빌드는 LiteRT-LM runtime 클래스를 유지하고 ABI APK 2개와 universal APK 1개를 만듭니다.

******

### 라이선스

******

프로젝트 소스는 MPL-2.0으로 배포됩니다. LiteRT-LM 및 기타 타사 구성 요소에는 각각의 라이선스가 계속 적용됩니다.

******

### 리소스 구성

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py`는 JSON 소스에서 10개 언어 README와 앱 내 changelog를 생성합니다. Android 문자열은 각 리소스 디렉터리에서 관리합니다.

******

### 링크

******

- AutoJs6 문서: https://docs.autojs6.com
- LiteRT-LM 프로젝트: https://github.com/google-ai-edge/LiteRT-LM


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/ui-redesign/docs/16kb.md)
