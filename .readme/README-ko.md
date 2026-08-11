<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>로컬 AI 텍스트 생성 플러그인. LiteRT-LM으로 기기 내 일반 텍스트 스트리밍 생성</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어

******

현재 README.md는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### 소개

******

AI Text Generation은 AutoJs6 AI Text Generation 프로토콜 V1을 위한 독립 기기 내 provider입니다. 사용자가 가져온 LiteRT-LM 모델을 CPU에서 실행하고 일반 텍스트 메시지 기록을 받아 제어된 스트리밍 세션으로 일반 텍스트를 반환합니다.

******

### 기능

******

- Android 시스템 선택기로 `.litertlm` 모델 패키지를 가져오고 검증된 복사본을 앱 전용 저장소에 보관합니다.
- 일반 텍스트 system, user 및 assistant 기록으로 로컬 생성 요청을 만듭니다.
- credit 역압력으로 텍스트 chunk를 순서대로 전달하고 완료, 실패 또는 취소 중 하나의 종료 상태만 게시합니다.
- 현재 가져온 모델을 나열하고 교체 후 새 모델 목록 generation을 제공합니다.
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
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

플러그인은 ON_DEVICE 실행과 NONE credential 모드를 선언합니다. `streaming` 기능과 `text/plain` 입출력만 선언합니다.

호스트 build 5270 이상이 필요합니다. 릴리스에는 arm64-v8a, x86_64, universal APK 변형이 포함됩니다.

******

### 호스트 통합 상태

******

> AutoJs6는 현재 `ai.ask(..., { plugin: ... })`와 `ai.stream(..., { plugin: ... })`를 위한 명시적 public production route를 제공하며 exact component/provider/model을 고정하고 cloud fallback을 하지 않습니다. 실제 release plugin/model one-shot ask가 QV710AF65F (API 31, arm64-v8a)에서 통과했고, 같은 장치에서 deterministic fake provider public stream smoke도 initial 8-credit window를 넘는 8개 초과 chunks 후 자연 완료했습니다. plugin만 설치해서는 legacy `ai.*`가 전환되지 않으며 script에 명시적 plugin selector와 import한 modelId가 필요합니다. 기존 JavaAdapter/raw Binder 예제, clipboard, UI, `chat`, real-model streaming, device active cancel은 아직 검증하지 않았습니다.

******

### 보안 및 개인정보 보호

******

네트워크 또는 저장소 권한을 요청하지 않습니다. 시스템 선택기가 허용한 URI로만 모델을 읽고 SHA-256을 계산하며 앱 전용 `files/models`로 복사한 뒤 fsync하고 같은 디렉터리의 pointer를 원자적으로 교체해 활성화합니다. Provider 서비스는 AutoJs6 패키지 이름, 호출 UID 소유권 및 양쪽 서명 일치도 검증합니다.

******

### 운영 제한

******

- 모델 가져오기에는 8 GiB의 엄격한 상한이 있고 완료 후 최소 256 MiB의 여유 공간이 필요합니다.
- Application scope 단일 가져오기 coordinator가 Activity 재생성 중에도 작업을 유지합니다. Fsync된 pending journal로 콜드 스타트 복구와 stale `.incoming`, `.current`, `.pending` 임시 파일 cleanup을 수행합니다. 복구는 현재 시도가 새로 만들고 current metadata로 게시한 적 없는 destination만 삭제하며 게시됨, current 및 이전 hash 세대는 보존합니다.
- 분리된 `:provider` 프로세스와의 프로세스 간 경쟁을 피하기 위해 교체 가져오기 후에도 이전 SHA-256 hash 이름 모델 세대를 보존합니다. 이 파일들은 앱 전용 저장소를 계속 사용합니다.
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

`R0`은 계속 진행 중입니다. 2026-08-10 build gate는 32 tests/0 failures, lint 0 error, Debug/Release `BUILD SUCCESSFUL` (3m37s)로 통과했고, `VERSION_BUILD`/`BUILD_TIME`도 변경되지 않았습니다; 실제 plugin/model의 public `ai.ask` device smoke는 통과했지만 clipboard 쓰기와 Activity 재생성 후 복사는 아직 검증하지 않았습니다. `R1`은 프로덕션 호출 지점이 없는 기본 비활성화 및 미연결 호스트 슬라이스 5개를 포함합니다: 읽기 전용 PackageManager `exact-action discovery`/`exact-component reinspection`, 명시적 컴포넌트 전용 metadata-only Binder 바인딩, 전송 계층과 독립적인 model-list transcript policy/catalog, 그리고 `IAiModelListCallback` Binder transport를 갖춘 Android model-list coordinator. 기존 metadata handshake는 도달한 ID 경계를 재검사하며, absolute deadline이 만료될 때 interface descriptor, `getProviderInfo()` 또는 `getCapabilities()` 동기 Binder RPC가 계속 실행 중인 경우에만 fuse합니다. model-list policy는 제한된 단일/다중 페이지 결과와 provider error를 엄격하게 디코딩하고, generation 변경, token replay/cycle, 중복 model ID, capability mismatch 및 stale/duplicate callback을 거부하며, 경쟁하는 terminal state 중 하나만 승리하도록 합니다. coordinator는 listing 초기화 시에만 descriptor가 검증된 동일한 Binder에서 provider metadata를 재검증하고, 이후 initial/continuation 각 dispatch 전에 `AiTextProviderPackageSnapshot.samePackageIdentityAs`로 exact package/component identity를 재검사합니다. 각 callback은 유일한 payload copy 전에 calling UID, typed page/error envelope size 및 제한된 flood slot을 검증합니다; 제한된 page-token ledger는 완전하고 안정적인 pinned identity별로 격리됩니다. 기존 handshake와 달리 coordinator는 exact PackageManager inspect, bind, prepare, dispatch, callback admission을 포함해 admission된 모든 `operationsInFlight`에 watchdog을 유지하고 deadline 전에 unwind되지 않으면 exact component를 fuse합니다. 어떤 fuse도 멈춘 operation을 강제 중단할 수 없으며 coordinator gate는 늦은 unwind까지 `BUSY`로 유지됩니다. 다섯 번째는 기본 비활성화, 미연결, transport-independent `AiTextProviderSessionPolicy`입니다: plan/request/provider/model/context를 고정하고 동기 `openSession` 반환 전 callback을 명시적 commit gate 뒤에 보관하며 UID, typed envelope 한도, descriptor ownership 순서로 검증합니다. initial 8 credits와 chunk별 제한된 backpressure를 자동 관리하고 started/chunk/usage/completed/failed/cancelled를 제한적으로 수락하며 provider completion, cancel, timeout, death 중 하나만 terminal winner가 되게 하고 descriptor, remote control, cleanup이 모두 settled된 뒤 terminal을 게시하며 tools는 fail-closed로 거부합니다. Android `IAiTextCallback`/`openSession`/PFD adapter, session dispatch 전 package reinspection, 실제 session dispatch, runtime/UI 통합 또는 `ai.*` 라우팅은 포함하지 않습니다. 증거에는 source/static 및 focused JVM Gradle이 포함됩니다: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 compile, 40/40 JUnit, 동일 artifact 30 runs의 1200/1200; focused AutoJs6 Gradle `:app:testAppDebugUnitTest`는 exit 0이었고 XML은 40 tests/0 skipped/0 failures/0 errors를 기록했으며 Kotlin daemon retry 후에도 fallback compile이 성공했습니다. Android `IAiTextCallback`/`openSession`/PFD adapter, ADB/device, runtime/UI 또는 `ai.*` 증거는 여전히 없고 광범위한 R1 item과 exit gate는 체크되지 않습니다. 2026-08-10 isolated AutoJs6 Gradle gate는 coordinator 15/0을 통과하고 version metadata 변경 없이 host Debug, androidTest 및 fake APK를 assemble했습니다. QV710AF65F (API 31, arm64-v8a)의 metadata/model-list는 positive PARTIAL로 각각 `OK (1 test)`였고, `pageSize=1`로 4페이지/4개 fake model을 수집했습니다; signer/hash, 전후 identity, non-main callback 및 sole terminal은 host evidence에 상세히 기록되었습니다. 세 package는 install 전에 없었고 cleanup 후에도 없는 상태로 복원되었습니다. 이 증거는 narrow R1 item만 지원하며 광범위한 item과 exit gate는 체크되지 않습니다. QV710AF65F에는 실제 plugin/model이 없어 R0은 아직 열려 있습니다. `R2`부터 `R8`까지는 계속 계획 항목입니다. 여섯 번째 narrow slice는 기본 비활성화 및 미연결 상태의 Android exact-component session coordinator와 `IAiTextCallback`/PFD transport입니다. standalone K2는 18/18, 동일 artifact의 30 rounds는 540/540을 통과했고, focused Gradle은 18 tests/0 failures를 기록했으며 3개 APK assemble도 성공했습니다. QV710AF65F (API 31, arm64-v8a)에서는 두 test method가 각각 `OK (1 test)`였고, request reliable-pipe PFD, initial 8 credits를 넘어선 약 18 chunks, descriptor exact/short/trailing/reliable producer error 및 idempotent close를 확인했습니다. 이 증거는 `PARTIAL`에 한정됩니다: callback completion/tool PFD의 cross-process, wrong UID, hostile death/update, 실제 plugin/model, runtime/UI/`ai.*`는 아직 검증되지 않았으므로 광범위한 R1 item과 exit gate는 체크되지 않은 상태입니다. 이후 체크된 narrow hostile Android session conformance slice가 추가되었으며 isolated H1 commits `edd10008f`/`06ebc788c`와 통합 commits `0cbc19d9f`/`72eb4d0c0`에 기록되어 있습니다. focused Gradle은 coordinator 18/0과 fake provider 31/0을 통과했고 3개 APK assemble도 성공했습니다. QV710AF65F (API 31, arm64-v8a)에서 7개 exact instrumentation method가 각각 `OK (1 test)`였습니다: ordinary-pipe completion callback은 cross-process PFD ownership transfer, exact length/EOF, SHA-256, UTF-8 materialization 및 cleanup을 검증했고, tool PFD는 ownership을 넘겨받은 뒤 `TOOLS_UNSUPPORTED`로 한 번만 거부되었으며, chunk-before-start, sequence-gap 및 invalid descriptor reference는 fail-closed 처리되었습니다. duplicate terminal은 bounded single-terminal smoke 결과로만 확인했고, stall은 `Started` 이후 host cancel 및 owner/gate reuse를 확인했습니다. 이 `[x]`는 여전히 narrow evidence입니다: cross-process reliable-pipe status, wrong UID, no-credit, provider death, package update/uninstall, 실제 plugin/model, runtime/UI 및 `ai.*`는 검증하지 않았습니다. 광범위한 R1 item과 exit gate는 계속 체크되지 않은 상태입니다. 체크 상태는 프로젝트 로드맵을 기준으로 합니다. 추가로 체크된 H2 lifecycle narrow slice는 integrated commits `e5bd92b16`/`10dad3e39`/`0b9a94742`에 기록되어 있습니다. focused Gradle은 coordinator 18/0과 fake provider 31/0을 통과했고 3개 APK assemble도 성공했습니다. QV710AF65F (API 31, arm64-v8a)에서 `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner`와 `providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner`는 각각 `OK (1 test)`를 반환했습니다. 첫 번째는 transcript를 게시하기 전에 isolated-process callback을 유일한 `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH` terminal로 거부하고 owner/gate를 재사용합니다. 두 번째는 `Started`, 유효한 sequence 0 chunk 및 credit replenishment acknowledgement 이후 provider process를 종료하며, host는 유일한 `BinderDied`를 게시하고 owner/gate를 재사용합니다. local/device SHA-256은 host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629`, androidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178`, fake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`에서 정확히 일치했고, 세 v2 signer certificate SHA-256은 모두 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`였습니다. host, androidTest, fake package는 설치 전과 cleanup 후 모두 absent였습니다. 이 Android evidence는 wrong-UID/provider-death 두 방법에만 해당하는 PARTIAL입니다. no-credit은 JVM-only이고, update/uninstall 형태도 final-reinspection JVM case (`lastUpdateTime` drift 및 `Completed(emptyList())`)만 다루며 device package mutation은 수행하지 않았습니다. cross-process no-credit, 실제 update/uninstall, 실제 plugin/model, runtime/UI 및 `ai.*`는 아직 검증하지 않았고, 광범위한 R1 item과 exit gate는 계속 체크되지 않은 상태입니다. 추가로 체크된 좁은 슬라이스는 AutoJs6 메인 저장소 commits `e4297a688`/`64db31ea5`에 기록되며 명시적 `ai.ask(..., { plugin: ... })` production source route를 연결한다. selector는 exact component/provider/model을 엄격히 고정하고, `plugin`이 없으면 기존 cloud 동작을 유지하지만 존재하면 vault를 읽거나 HTTP/cloud로 진입하거나 fallback하지 않는다. 현재는 단일 plain-text user message와 non-stream request만 허용하며 engine close를 remote session teardown으로 전파한다. standalone K2 15/15, focused Gradle 19/19 및 Android main compile이 통과했지만 모두 source-only 증거이다. 실제 plugin/model/device 실행, UI, `chat` 또는 `stream` route는 포함하지 않으며 광범위한 R1 항목과 exit gate는 계속 미체크 상태이다. 2026-08-11, 검증한 APK와 실제 device 실행의 직접 source는 격리 트리 commit `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`이다; 같은 test blob이 현재 main parent의 `7ce26ceea`로 통합되었고 app tree는 동일하지만, 후자는 APK의 직접 build source가 아니다. QV710AF65F (API 31, arm64-v8a)에서 실제 Rhino global `ai.ask(..., { plugin: ... })` one-shot을 release plugin과 2,583,085,056-byte LiteRT-LM으로 실행했다. model SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`에서 modelId `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`를 파생했고, `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin`는 7.071s에 `OK (1 test)`를 반환했다. local/device APK SHA-256는 host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`, androidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`, plugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`에서 정확히 일치했으며, 세 APK의 v2 signer certificate SHA-256은 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`로 같고 device base도 후보 APK와 일치했다. plugin test/lint/Debug/Release gate와 host focused 19/19 tests/assemble이 통과했고, users 0/10의 exact host/test/plugin package와 전용 staging/UI-dump path는 검증 전후 모두 absent였다. 이는 R0 raw Binder/JavaAdapter 예제 baseline 항목을 체크하지 않고 R6의 제한적 optional real-model smoke/local-entry gate만 체크하지만, single-device/single-model/non-stream one-shot/appDebug host 증거에 한정된다. clipboard, streaming, chat, tools, structured output, usage, release-host/API matrix, 실제 update/uninstall, performance, soak는 다루지 않았다. DocumentsUI last-location state는 무손실로 복원할 수 없었다. 광범위한 R1 항목과 exit gate는 계속 미체크 상태이다. AutoJs6 commits `2ea3360a2`/`50b43d00c`는 추가로 좁은 checked PARTIAL public plugin stream slice를 기록합니다. focused Gradle은 25/25를 통과했고 QV710AF65F (API 31, arm64-v8a)의 exact method `AiTextPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider`는 1.645s에 `OK (1 test)`를 반환했습니다. fake provider (`fake.local`/`fake.stream`)는 initial 8 credits를 넘어 8개 초과 chunks를 보냈고 owned Rhino execution은 자연 완료했습니다. public cancel과 engine teardown은 JVM-only이며 real-model streaming, device active cancel, release/API matrix는 검증하지 않았습니다. broad R1 items와 exit gates는 계속 미체크입니다.

- [ROADMAP.md 보기](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### 릴리스 기록

******

# v1.0.0

###### 2026/08/08

* `기능` 플러그인 ID 및 엔진 `ai-text-generation`, provider ID `autojs6.local.text`, 변형 `default`인 기기 내 AI Text Generation 프로토콜 V1 provider
* `기능` System, user 및 assistant 기록과 credit 제어 스트리밍을 지원하는 CPU-only LiteRT-LM 일반 텍스트 생성
* `기능` 8 GiB 상한, 여유 공간 예약, SHA-256, fsync 및 원자적 활성화를 포함한 `.litertlm`의 앱 전용 저장소 SAF 가져오기
* `기능` 단일 활성 세션, 제한된 I/O, descriptor quota, 취소, timeout, 단일 종료 상태 및 동일 서명 AutoJs6 호출자 검증
* `기능` Reasoning, tools, structured JSON, usage, 네트워크 및 credential 기능의 명시적 미지원
* `기능` arm64-v8a, x86_64 및 universal APK와 10개 언어 README, changelog, Android UI 및 플러그인 안내
* `개선` `:provider`와의 프로세스 간 경쟁을 피하기 위해 교체 가져오기 후에도 이전 SHA-256 hash 이름 모델 세대를 보존하며 보존 파일은 앱 전용 저장소를 계속 사용
* `개선` Activity 재생성 연속성, 콜드 스타트 복구, stale 임시 파일 cleanup 및 현재 시도가 만들고 게시하지 않은 destination으로 제한된 삭제를 위해 application scope 단일 가져오기 coordinator와 fsync된 pending journal을 추가하고 게시됨, current 및 이전 hash 세대를 보존
* `의존성` 기기 내 CPU 텍스트 생성을 위해 LiteRT-LM 0.15.0 추가

##### 추가 릴리스

* [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

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
ai-text-generation-api.aar
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
