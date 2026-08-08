# AutoJs6 AI 텍스트 생성

이 플러그인은 Android Storage Access Framework (SAF)로 로컬 `.litertlm` 모델 패키지를 가져와 앱 전용 저장소에 복사합니다. CPU-only LiteRT-LM으로 일반 텍스트 기록을 처리하고 텍스트를 스트리밍 출력합니다.

AutoJs6 호스트 build 5270 이상과 Android API 24 이상이 필요합니다.

보안 및 운영 제한:

- 모델 가져오기 상한은 8 GiB이며 완료 후 최소 256 MiB의 여유 공간이 필요합니다.
- 컨텍스트 상한은 256 KiB, 출력 상한은 64 KiB이며 동시에 활성화할 수 있는 생성 세션은 1개입니다.
- Provider는 token 수 상한을 선언하지 않습니다. `maximumOutputTokens`를 설정하는 요청은 지원하지 않습니다.
- Streaming과 `text/plain`만 선언합니다. Reasoning, tools, structured JSON 및 usage는 지원하지 않습니다.
- 네트워크 또는 저장소 권한을 요청하지 않습니다.
- 동일한 서명의 AutoJs6 호스트만 provider 서비스에 bind할 수 있습니다.
- 프로세스 간 안전을 위해 이전 SHA-256 hash 이름 모델 세대를 보존합니다. 이 파일들은 앱 전용 저장소를 계속 사용합니다.
