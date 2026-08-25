# P3 Host Public API Device Smoke

Run this script as an ordinary AutoJs6 Rhino script after installing the P3 host and 3-Stone AI builds. It is intentionally bounded and performs four checks in sequence:

1. The unified catalog has one consistent default and exposes both a configured local target and the configured PoloAPI target.
2. An exact online `target` returns the complete plugin response metadata.
3. `plugin: true` resolves to the plugin-declared default target.
4. An unknown exact target fails with `TARGET_NOT_FOUND`; only then does the script run one exact local CPU request.

The local request can include model initialization time. The script never changes profiles, credentials, models, or network settings.

```javascript
"rhino";

const EXPECTED_PROVIDER = "autojs6.three-stone-ai";
const EXPECTED_ONLINE_MODEL = "claude-opus-4-8";
const FINISH_REASONS = [
    "stop", "length", "tool_calls", "content_filter", "error", "other",
];

function check(condition, message) {
    if (!condition) throw new Error(message);
}

function checkTarget(target, locality) {
    check(target && typeof target === "object", "Missing " + locality + " target");
    check(typeof target.id === "string" && target.id.length > 0, "Invalid target ID");
    check(target.locality === locality, "Unexpected target locality: " + target.locality);
    check(target.configured === true, "Target is not configured: " + target.id);
    check(target.available === true, "Target is not available: " + target.id);
    check(target.availability === "available", "Target availability is inconsistent");
    check(Array.isArray(target.capabilities), "Target capabilities are absent");
    check(Array.isArray(target.supportedControls), "Target controls are absent");
    check(Array.isArray(target.origins), "Target origins are absent");
    check(Array.isArray(target.backendProfiles), "Target backend profiles are absent");
    check(target.limits.maximumContextBytes > 0, "Invalid context limit");
    check(target.limits.maximumOutputBytes > 0, "Invalid output limit");
}

function checkResponse(label, response, target) {
    check(response && typeof response === "object", label + ": response is not an object");
    check(response.route === "plugin", label + ": route is not plugin");
    check(typeof response.text === "string" && response.text.length > 0,
        label + ": response text is empty");
    check(typeof response.reasoning === "string", label + ": reasoning is absent");
    check(Array.isArray(response.toolCalls), label + ": toolCalls is absent");
    check(FINISH_REASONS.indexOf(response.finishReason) >= 0,
        label + ": invalid finishReason " + response.finishReason);
    check(response.target && response.target.id === target.id,
        label + ": target identity drifted");
    check(response.provider === target.provider, label + ": provider identity drifted");
    check(response.model === target.model, label + ": model identity drifted");
    check(response.plugin && response.plugin.provider === EXPECTED_PROVIDER,
        label + ": plugin identity is absent");
    check(response.plugin.component.packageName ===
        "io.github.supermonster003.autojs6.plugin.threestoneai",
        label + ": plugin package identity is incorrect");

    const usage = response.usage;
    check(usage && usage.inputTokens > 0, label + ": input usage is absent");
    check(usage.outputTokens > 0, label + ": output usage is absent");
    check(usage.totalTokens === usage.inputTokens + usage.outputTokens,
        label + ": total usage is inconsistent");
    check(typeof usage.durationMillis === "number" && usage.durationMillis >= 0,
        label + ": duration is absent");

    if (target.locality === "remote") {
        check(response.profile && response.profile.id === target.profile,
            label + ": remote profile metadata is absent");
    } else {
        check(response.profile === null, label + ": local response unexpectedly has a profile");
    }

    console.log(label + " OK", JSON.stringify({
        target: response.target.id,
        provider: response.provider,
        model: response.model,
        finishReason: response.finishReason,
        usage: response.usage,
    }, null, 2));
}

let state;

ai.catalog({ plugin: true, timeout: 120000 }).then((catalog) => {
    check(catalog && Array.isArray(catalog.targets), "Invalid ai.catalog() result");
    check(catalog.plugin.provider === EXPECTED_PROVIDER, "Unexpected catalog provider");
    const defaults = catalog.targets.filter((target) => target.isDefault === true);
    check(defaults.length === 1, "Catalog must expose exactly one default target");
    check(catalog.defaultTarget === defaults[0].id, "Catalog default marker is inconsistent");

    const local = catalog.targets.find((target) => {
        return target.locality === "local" && target.configured && target.available;
    });
    const online = catalog.targets.find((target) => {
        return target.locality === "remote" &&
            target.model === EXPECTED_ONLINE_MODEL &&
            target.configured && target.available;
    });
    checkTarget(local, "local");
    checkTarget(online, "remote");
    check(online.backendProfiles.length === 0,
        "Online target unexpectedly exposes a local backend profile");

    state = { catalog: catalog, local: local, online: online, defaultTarget: defaults[0] };
    console.log("CATALOG OK", JSON.stringify({
        generation: catalog.generation,
        defaultTarget: catalog.defaultTarget,
        targets: catalog.targets.map((target) => ({
            id: target.id,
            model: target.model,
            locality: target.locality,
            isDefault: target.isDefault,
        })),
    }, null, 2));

    return ai.chat("只回复 ONLINE_OK", {
        target: online.id,
        maxTokens: 16,
        timeout: 180000,
    });
}).then((response) => {
    checkResponse("EXACT ONLINE TARGET", response, state.online);
    return ai.chat("只回复 DEFAULT_OK", {
        plugin: true,
        maxTokens: 16,
        timeout: 600000,
    });
}).then((response) => {
    checkResponse("PLUGIN DEFAULT TARGET", response, state.defaultTarget);
    return ai.chat("This request must never generate.", {
        target: "local:missing-target-for-p3",
        maxTokens: 1,
        timeout: 120000,
    }).then(() => {
        throw new Error("Unknown target unexpectedly generated a response");
    }, (error) => {
        check(error && error.code === "TARGET_NOT_FOUND",
            "Expected TARGET_NOT_FOUND, got " + String(error && error.code));
        console.log("NO-FALLBACK ERROR OK", error.code);
    });
}).then(() => {
    return ai.chat("只回复 LOCAL_OK", {
        target: state.local.id,
        backend: "cpu",
        maxTokens: 16,
        timeout: 600000,
    }).then((response) => checkResponse("EXACT LOCAL TARGET", response, state.local));
}).then(() => {
    console.log("P3 PUBLIC API SMOKE OK");
}).catch((error) => {
    console.error("P3 PUBLIC API SMOKE FAILED", error);
});
```

Success ends with `P3 PUBLIC API SMOKE OK`. On failure, preserve the complete console output, especially the first `... FAILED` error and any stable `error.code`.
