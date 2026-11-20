3-Stone AI brings AI chat and model management to AutoJs6: local models run offline on the phone, and online services (such as OpenAI, Anthropic and Gemini) use your own API key. The plugin has its own chat screen and also provides the models for the `ai` module in AutoJs6 scripts.

### Getting started

1. Open 3-Stone AI from its home screen icon, tap "Settings" in the chat screen menu, then tap "Models and services".
2. To use a local model, tap "Download model" in the "Local models" section and choose a recommended model; it is imported automatically after it is downloaded and verified. If you already have a `.litertlm` file, tap "Import model file".
3. To use an online service, tap "Add service" in the "Online services" section, choose a provider, enter the model ID and API key, and save. You can then choose "Test connection" from the service menu.
4. Go back to the chat screen, tap the model name at the top to choose a model, then type a question and send it.

### Main features

- **Chat**: conversations are saved automatically and the model's reasoning can be shown; regenerating or editing a message keeps every version, and you can switch between them.
- **Attachments**: tap "+" on the left of the input box to add images, text files or PDFs. Images go to models with "Image input" turned on and PDFs only to Anthropic and Gemini services; local models do not support attachments yet.
- **Switching models**: switch models at any time while chatting; scripts use the "Default target" on the "Models and services" page.
- **Model management**: check, rename or delete models in the "Local models" section. API keys are stored only on the device and are left out when you export services.
- **Voice**: tap the microphone to dictate, long-press it to start a voice conversation, and long-press a reply to choose "Read aloud"; with 3-Macaw Speech installed, dictation and reading aloud work offline.
- **Export and share**: choose "Export as Markdown" or "Share as image" in the chat screen menu.

### Using it in scripts

AutoJs6 scripts use this plugin through the `ai` module. Without a target, the "Default target" is used:

```js
ai.ask('Introduce AutoJs6 in one sentence.').then((text) => {
    console.log(text);
});
```

To pin a specific model, find its target with `ai.catalog()` and pass its `id` as `target`. This example uses the first available local model:

```js
ai.catalog().then((catalog) => {
    let target = catalog.targets.find((item) => item.available && item.locality === 'local');
    if (!target) throw new Error('No available local model');
    return ai.ask('Reply with OK.', { target: target.id, temperature: 0.7 });
}).then((text) => console.log(text));
```

For multi-turn conversations use `ai.session`, send only the new prompt each turn, and call `session.close()` when you are done:

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

- `ai.chat` returns the full response including usage, and `ai.stream` returns text as it is generated.
- `temperature`, `topK`, `topP` and `maxTokens` control sampling and output length; when the target supports structured output, passing `responseSchema` returns text that matches that JSON Schema.
- If the target is unavailable or not configured, the request fails directly (for example with `TARGET_UNAVAILABLE`) and never switches to another model.

### Requirements

- Android 7.0 or later, AutoJs6 6.8.0 or later.
- Local models need a 64-bit device (arm64-v8a or x86_64) and enough storage; the download confirmation shows the minimum memory required. 32-bit devices can only use online services.
- Online services need your own API key, and the provider may charge for usage.
- The network permission is used to download models, reach online services and update the preset model list; local inference does not use the network. The plugin does not request storage or microphone permissions.

### FAQ

**Differences between local models and online services**

Local models run on the phone, so conversations never leave the device, but speed and quality depend on the phone. Online services send conversations to the chosen provider and need a network connection and an API key.

**Why a failed request does not switch to another model**

Requests only use the model you chose, so conversations are never sent to another service without your knowledge. When a request fails, check that model in "Models and services" or switch models yourself.

**When a local model cannot run**

Open that model's menu in the "Local models" section and tap "Check model" to see the reason; if memory is short, use a smaller recommended model.

**When other apps ask to use AI**

Before an app packaged with AutoJs6 and signed separately can use AI, 3-Stone AI asks you to confirm. Once allowed, the app can use local models and saved online services, which may use paid quota; remove the access in "Settings > Privacy and permissions > Apps with AI access".

All options, events and error codes are in the [Artificial intelligence (AI)](https://docs.autojs6.com/#/ai) chapter of the AutoJs6 documentation, and privacy details and limits are on the [project page](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI).
