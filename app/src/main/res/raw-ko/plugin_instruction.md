3-Stone AI는 AutoJs6에 AI 채팅과 모델 관리를 제공합니다. 로컬 모델은 휴대전화에서 오프라인으로 실행되고, 온라인 서비스 (예: OpenAI, Anthropic, Gemini)는 직접 발급한 API 키를 사용합니다. 플러그인에는 자체 채팅 화면이 있으며 AutoJs6 스크립트의 `ai` 모듈에도 모델을 제공합니다.

### 시작하기

1. 홈 화면 아이콘으로 3-Stone AI를 열고, 채팅 화면 메뉴에서 "설정"을 누른 다음 "모델 및 서비스"를 누릅니다.
2. 로컬 모델을 쓰려면 "로컬 모델" 섹션에서 "모델 다운로드"를 누르고 추천 모델을 고릅니다. 다운로드와 검증이 끝나면 자동으로 가져옵니다. `.litertlm` 파일이 이미 있으면 "모델 파일 가져오기"를 누릅니다.
3. 온라인 서비스를 쓰려면 "온라인 서비스" 섹션에서 "서비스 추가"를 누르고, 제공자를 고른 뒤 모델 ID와 API 키를 입력해 저장합니다. 이후 서비스 메뉴에서 "연결 테스트"를 선택할 수 있습니다.
4. 채팅 화면으로 돌아가 상단의 모델 이름을 눌러 모델을 고르고, 질문을 입력해 보냅니다.

### 주요 기능

- **채팅**: 대화는 자동으로 저장되고 모델의 추론 과정을 볼 수 있습니다. 다시 생성하거나 편집한 메시지는 모든 버전이 남아 서로 전환할 수 있습니다.
- **첨부 파일**: 입력란 왼쪽의 "+"를 눌러 이미지, 텍스트 파일, PDF를 추가합니다. 이미지는 "이미지 입력"이 켜진 모델로, PDF는 Anthropic과 Gemini 서비스로만 보냅니다. 로컬 모델은 아직 첨부 파일을 지원하지 않습니다.
- **모델 전환**: 채팅 중 언제든 모델을 바꿀 수 있습니다. 스크립트는 "모델 및 서비스" 화면의 "기본 대상"을 사용합니다.
- **모델 관리**: "로컬 모델" 섹션에서 모델을 확인, 이름 변경, 삭제할 수 있습니다. API 키는 기기에만 저장되며 서비스를 내보낼 때 포함되지 않습니다.
- **요청 헤더**: 서비스 세부정보 화면의 "요청 헤더"에서 OpenCode Go에 필요한 세션 헤더 같은 사용자 지정 요청 헤더를 추가합니다. 비밀 값은 기기에만 저장되며 내보낼 때 포함되지 않습니다.
- **음성**: 마이크를 누르면 음성 입력, 길게 누르면 음성 대화를 시작합니다. 응답을 길게 누르면 "읽어 주기"를 선택할 수 있습니다. 3-Macaw Speech를 설치하면 입력란에 실시간으로 받아쓰고, 음성 대화도 인식 화면을 열지 않으며, 음성 입력과 읽어 주기가 오프라인으로 동작합니다.
- **내보내기와 공유**: 채팅 화면 메뉴에서 "Markdown으로 내보내기" 또는 "이미지로 공유"를 선택합니다.

### 스크립트에서 사용하기

AutoJs6 스크립트에서는 `ai` 모듈로 이 플러그인을 호출합니다. 대상을 지정하지 않으면 "기본 대상"을 사용합니다:

```js
ai.ask('Introduce AutoJs6 in one sentence.').then((text) => {
    console.log(text);
});
```

특정 모델로 고정하려면 `ai.catalog()`에서 대상을 찾아 그 `id`를 `target`으로 전달합니다. 다음 예시는 사용할 수 있는 첫 번째 로컬 모델을 사용합니다:

```js
ai.catalog().then((catalog) => {
    let target = catalog.targets.find((item) => item.available && item.locality === 'local');
    if (!target) throw new Error('No available local model');
    return ai.ask('Reply with OK.', { target: target.id, temperature: 0.7 });
}).then((text) => console.log(text));
```

여러 턴의 대화에는 `ai.session`을 사용하고, 턴마다 새 프롬프트만 보내며, 끝나면 `session.close()`를 호출합니다:

```js
ai.session({ system: 'Keep every answer short.' }).then((session) => {
    return session.ask('Remember this code: Orion.')
        .then(() => session.ask('What code should you remember?'))
        .then((text) => {
            console.log(text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

3-Macaw Speech가 설치되어 있으면 `speech` 모듈로 받아쓰기, 질문, 읽어 주기를 이을 수 있습니다. 한 문장을 들으면 받아쓰기를 멈추고, 그 텍스트를 `ai.chat`에 넘긴 뒤 `speech.speak`로 답을 읽어 줍니다:

```js
let session = speech.listen({ timeoutMillis: 8000 });
session.on('final', () => session.stop());
session.on('end', () => {
    let question = session.result();
    if (!question) return;
    ai.chat(question, { maxTokens: 256 })
        .then((response) => speech.speak(response.text))
        .catch((error) => console.error(error.code, error.message));
});
```

- `ai.chat`은 사용량을 포함한 전체 응답을 반환하고, `ai.stream`은 생성되는 대로 텍스트를 반환합니다.
- `temperature`, `topK`, `topP`, `maxTokens`로 샘플링과 출력 길이를 조절합니다. 대상이 구조화된 출력을 지원하면 `responseSchema`를 전달해 해당 JSON Schema에 맞는 텍스트를 받을 수 있습니다.
- 대상을 사용할 수 없거나 구성되지 않았으면 요청은 바로 실패하며 (예: `TARGET_UNAVAILABLE`), 다른 모델로 바뀌지 않습니다.
- `ai.stream`도 `speech.listen` 뒤에 쓸 수 있습니다. `done` 이벤트에서 `response.text`를 읽어 주거나, `delta`를 문장 단위로 나누어 `speech.speak(sentence, { queue: 'add' })`로 생성하면서 읽어 줍니다. `speech` 모듈은 3-Macaw Speech가 제공하며, 설치되어 있지 않으면 호출이 실패합니다(`PLUGIN_UNAVAILABLE`).

### 요구 사항

- Android 7.0 이상, AutoJs6 6.8.0 이상.
- 로컬 모델에는 64비트 기기 (arm64-v8a 또는 x86_64)와 충분한 저장 공간이 필요합니다. 다운로드 확인 창에 필요한 최소 메모리가 표시됩니다. 32비트 기기에서는 온라인 서비스만 사용할 수 있습니다.
- 온라인 서비스에는 직접 발급한 API 키가 필요하며, 제공자가 사용량에 따라 요금을 청구할 수 있습니다.
- 네트워크 권한은 모델 다운로드, 온라인 서비스 연결, 기본 제공 모델 목록 업데이트에 사용합니다. 로컬 추론은 네트워크를 사용하지 않습니다. 저장소나 마이크 권한은 요청하지 않습니다.

### 자주 묻는 질문

**로컬 모델과 온라인 서비스의 차이**

로컬 모델은 휴대전화에서 실행되어 대화가 기기 밖으로 나가지 않지만, 속도와 품질은 기기 성능에 따라 달라집니다. 온라인 서비스는 선택한 제공자에게 대화를 보내므로 네트워크와 API 키가 필요합니다.

**실패해도 다른 모델로 바꾸지 않는 이유**

대화가 모르는 사이에 다른 서비스로 전송되지 않도록 요청은 선택한 모델만 사용합니다. 실패하면 "모델 및 서비스"에서 해당 모델을 확인하거나 직접 모델을 바꾸세요.

**로컬 모델이 실행되지 않는 경우**

"로컬 모델" 섹션에서 해당 모델의 메뉴를 열고 "모델 확인"을 눌러 원인을 확인합니다. 메모리가 부족하면 더 작은 추천 모델을 사용하세요.

**다른 앱이 AI 사용을 요청하는 경우**

AutoJs6으로 패키징하고 별도로 서명한 앱이 AI를 사용하기 전에 3-Stone AI가 허용 여부를 확인합니다. 허용한 앱은 로컬 모델과 저장된 온라인 서비스를 사용할 수 있으며 유료 사용량이 소모될 수 있습니다. 허용은 "설정 > 개인정보 및 권한 > AI 접근을 허용한 앱"에서 취소할 수 있습니다.

**OpenCode Go가 MissingSessionID를 반환하는 경우**

OpenCode Go는 모든 요청에 세션 헤더 x-opencode-session을 요구합니다. 오류 설명에서 "세션 헤더 추가"를 누르거나, 서비스 세부정보 화면의 "요청 헤더"에서 "OpenCode Go 세션" 프리셋을 추가하고 저장한 뒤 다시 시도하세요.

모든 옵션, 이벤트, 오류 코드는 AutoJs6 문서의 [인공지능 (AI)](https://docs.autojs6.com/#/ai) 장에, 개인정보와 제한 사항은 [프로젝트 페이지](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI)에 있습니다.
