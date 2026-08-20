<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>온디바이스 AI 플러그인. LiteRT-LM으로 로컬 기기에서 텍스트를 스트리밍 생성, 네트워크 불필요</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어

******

현재 README.md는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### 소개

******

On-Device AI (온디바이스 AI)는 AutoJs6의 공식 온디바이스 AI 텍스트 생성 플러그인입니다. 사용자가 가져온 LiteRT-LM 모델을 CPU에서 실행하고, 일반 텍스트 메시지 기록을 받아 제어된 스트리밍 세션으로 일반 텍스트를 반환합니다. 모든 추론은 로컬에서 이루어지며 네트워크 접근이나 데이터 업로드가 없습니다.

******

### 기능

******

- Android 시스템 선택기로 `.litertlm` 모델 패키지를 가져오고 검증된 복사본을 앱 전용 저장소에 보관합니다.
- 일반 텍스트 system, user 및 assistant 기록으로 로컬 생성 요청을 만듭니다.
- credit 역압력으로 텍스트 chunk를 순서대로 전달하고 완료, 실패 또는 취소 중 하나의 종료 상태만 게시합니다.
- 가져온 모델을 나열하고 선택하며, 선택되지 않은 모델을 삭제하고 관리 화면에서 참조되지 않은 모델 파일을 회수합니다.
- 모델 다운로드나 원격 추론 서비스 없이 CPU backend로 완전히 기기 내에서 실행합니다.

******

### 모델 및 데이터 형식

******

버전 1은 다음 모델 및 텍스트 범위만 선언합니다:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### 플러그인 인터페이스

******

호스트는 다음 식별자로 플러그인을 검색하고 호출합니다:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1
required host build: 5270
```

플러그인은 ON_DEVICE 실행과 NONE credential 모드를 선언합니다. `streaming` 기능과 `text/plain` 입출력만 선언합니다.

호스트 build 5270 이상이 필요합니다. 릴리스에는 arm64-v8a, x86_64, universal APK 변형이 포함됩니다.

******

### 호스트 통합 상태

******

> AutoJs6 (빌드 5276 이상)의 `ai.ask`, `ai.chat`, `ai.stream`은 로컬 플러그인 경로를 지원합니다: `plugin: true`를 전달하면 이 플러그인이 선택되고, 모델이 하나뿐인 경우 모델 ID를 생략할 수 있습니다. `ai.models({ plugin: true })`로 가져온 모델을 열거할 수 있습니다. 플러그인이 설치되지 않았거나 플러그인 센터에서 비활성화되었거나 모델이 없으면 스크립트에 명확한 오류가 전달됩니다. `plugin: { component, providerId, modelId }`로 명시적 고정도 가능합니다.

******

### 보안 및 개인정보 보호

******

네트워크 또는 저장소 권한을 요청하지 않습니다. 시스템 선택기가 허용한 URI로만 모델을 읽고 SHA-256을 계산하며 앱 전용 `files/models`로 복사한 뒤 fsync하고 같은 디렉터리의 pointer를 원자적으로 교체해 활성화합니다. Provider 서비스는 AutoJs6 패키지 이름, 호출 UID 소유권 및 양쪽 서명 일치도 검증합니다.

******

### 운영 제한

******

- 모델 가져오기에는 8 GiB의 엄격한 상한이 있고 완료 후 최소 256 MiB의 여유 공간이 필요합니다.
- Application scope 단일 가져오기 coordinator가 Activity 재생성 중에도 작업을 유지합니다. Fsync된 pending journal로 콜드 스타트 복구와 stale `.incoming`, `.current`, `.pending` 임시 파일 cleanup을 수행합니다. 복구는 현재 시도가 새로 만들고 current metadata로 게시한 적 없는 destination만 삭제하며 게시됨, current 및 이전 hash 세대는 보존합니다.
- 분리된 `:provider` 프로세스와의 프로세스 간 경쟁을 피하기 위해 가져오기 중에는 이전 SHA-256 hash 이름 모델 세대를 자동 삭제하지 않습니다. 모델 관리 화면에서 선택되지 않은 catalog 모델을 삭제하고 catalog에서 더 이상 참조하지 않는 hash 이름 파일을 회수할 수 있습니다.
- 프로세스에서 활성 생성 세션은 최대 1개입니다. 요청 descriptor는 비동기 작업 전에 복제되고 프로토콜 quota에 따라 닫힙니다.
- Provider가 선언하는 컨텍스트 상한은 256 KiB, 출력 상한은 64 KiB입니다. 요청과 모델은 더 낮은 상한을 적용할 수 있습니다.
- 스트리밍은 유한 credit과 제한된 chunk를 사용해 무제한 버퍼 또는 역압력 없는 callback을 방지합니다.
- 취소, 세션 닫기 및 timeout은 결과 게시를 중단하고 하나의 종료 상태로 요청을 끝냅니다.

******

### 선언하지 않는 기능

******

- Reasoning, tools, structured JSON 및 usage는 선언하지 않습니다.
- Tool 역할 메시지, tool schema, tool call 및 tool result를 받지 않습니다.
- 네트워크 모델 검색, 모델 다운로드, cloud 추론 또는 credential 흐름이 없습니다.
- GPU 또는 NPU backend를 선언하지 않습니다. `.litertlm` 확장자만으로 현재 LiteRT-LM runtime이 모델을 로드할 수 있음을 보장하지 않습니다.

******

### 로드맵

******

로드맵은 제공 가능한 사용자 기능 단위로 구성되며 각 항목은 개별적으로 체크하고 검수할 수 있습니다

- [ROADMAP.md 보기](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

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

##### 추가 릴리스

* [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

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
on-device-ai-api.aar
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
