package io.github.supermonster003.autojs6.plugin.threestoneai;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.autojs.plugin.ai.provider.api.IAiProvider;
import org.autojs.plugin.common.api.IPluginInfoProvider;
import org.autojs.plugin.common.api.PluginInfo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** No AndroidX runner, Kotlin runtime names, app implementation classes or debug resource IDs. */
public class ReleaseAbiSmokeInstrumentation extends Instrumentation {
    private static final String PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai";
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            String expectedBitness = arguments.getString("process64");
            if (expectedBitness != null) {
                require(Boolean.parseBoolean(expectedBitness) == Process.is64Bit(), "Unexpected process bitness");
            }
            verifyWake();
            verifyInfo();
            verifyProvider();
            verifyModelManager();
            try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
                String line;
                while ((line = maps.readLine()) != null) {
                    require(!line.contains("liblitertlm_jni.so"), "Smoke unexpectedly loaded LiteRT-LM");
                }
            }
            result.putString("releaseAbiSmoke", "passed");
            result.putString("process64", Boolean.toString(Process.is64Bit()));
            result.putLong("pageSize", Os.sysconf(OsConstants._SC_PAGESIZE));
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("releaseAbiSmoke", "failed");
            result.putString("failure", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyWake() throws Exception {
        Intent wake = new Intent("org.autojs.plugin.action.WAKE")
                .addCategory(Intent.CATEGORY_DEFAULT).setPackage(PACKAGE);
        List<ResolveInfo> matches = getTargetContext().getPackageManager()
                .queryIntentActivities(wake, PackageManager.MATCH_DEFAULT_ONLY);
        require(matches.size() == 1, "Wake discovery differs");
        require("org.autojs.permission.PLUGIN".equals(matches.get(0).activityInfo.permission), "Wake permission differs");
        require(matches.get(0).activityInfo.theme == android.R.style.Theme_NoDisplay, "Wake theme differs");
        // Exercise activation from the plugin UID; OEM/host-UID activation is separate evidence.
        runOnMainSync(() -> getTargetContext().startActivity(wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        waitForIdleSync();
    }

    @SuppressWarnings("deprecation")
    private void verifyInfo() throws Exception {
        withBinder(new Intent("org.autojs.plugin.INFO").addCategory("three-stone-ai"), binder -> {
            require(IPluginInfoProvider.DESCRIPTOR.equals(binder.getInterfaceDescriptor()), "INFO descriptor differs");
            PluginInfo info = IPluginInfoProvider.Stub.asInterface(binder).getInfo();
            PackageInfo installed = getTargetContext().getPackageManager().getPackageInfo(PACKAGE, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            require(info != null && info.getVersionCode() == code, "INFO installed version code differs");
            require(installed.versionName.equals(info.getVersionName()), "INFO installed version name differs");
            require("three-stone-ai".equals(info.getId()), "INFO identity differs");
            require(new HashSet<>(Arrays.asList("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
                    .equals(new HashSet<>(Arrays.asList(info.getSupportedAbis()))), "INFO ABIs differ");
        });
    }

    private void verifyProvider() throws Exception {
        withBinder(new Intent("org.autojs.plugin.AI_PROVIDER"), binder -> {
            require("org.autojs.plugin.ai.provider.api.IAiProvider".equals(binder.getInterfaceDescriptor()), "AI descriptor differs");
            try {
                IAiProvider.Stub.asInterface(binder).getCapabilities();
                throw new AssertionError("Non-host UID unexpectedly accepted");
            } catch (SecurityException expected) {
                // This is a real cross-process Binder call, with the production caller verifier.
            }
        });
    }

    private void verifyModelManager() throws Exception {
        boolean runtimeExpected = false;
        try (ZipFile apk = new ZipFile(getTargetContext().getApplicationInfo().sourceDir)) {
            if (Process.is64Bit()) {
                for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
                    runtimeExpected |= apk.getEntry("lib/" + abi + "/liblitertlm_jni.so") != null;
                }
            }
        }
        final boolean hasRuntime = runtimeExpected;
        Activity activity = startActivitySync(new Intent().setClassName(PACKAGE, PACKAGE + ".ModelManagerActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            waitForIdleSync();
            runOnMainSync(() -> {
                View root = activity.findViewById(android.R.id.content);
                TextView notice = findText(root, text(activity, "model_local_runtime_unavailable"));
                require((notice == null) == hasRuntime, "Local runtime notice differs from installed APK");
                if (!hasRuntime) {
                    for (String name : Arrays.asList("button_import_model", "button_browse_litert_models")) {
                        TextView button = findText(root, text(activity, name));
                        require(button != null && !button.isEnabled(), "Unavailable local action enabled");
                    }
                }
            });
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void withBinder(Intent intent, BinderCheck check) throws Exception {
        intent.setPackage(PACKAGE);
        List<ResolveInfo> matches = getTargetContext().getPackageManager().queryIntentServices(intent, 0);
        require(matches.size() == 1, "Service discovery differs");
        require("org.autojs.permission.PLUGIN".equals(matches.get(0).serviceInfo.permission), "Service permission differs");
        intent.setComponent(new ComponentName(PACKAGE, matches.get(0).serviceInfo.name));
        CountDownLatch ready = new CountDownLatch(1);
        IBinder[] remote = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                remote[0] = binder;
                ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(getTargetContext().bindService(intent, connection, Context.BIND_AUTO_CREATE), "Service bind rejected");
        try {
            require(ready.await(10, TimeUnit.SECONDS), "Service bind timed out");
            check.run(remote[0]);
        } finally {
            getTargetContext().unbindService(connection);
        }
    }

    private static String text(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "string", PACKAGE);
        require(id != 0, "Missing resource " + name);
        return activity.getString(id);
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface BinderCheck {
        void run(IBinder binder) throws Exception;
    }
}
