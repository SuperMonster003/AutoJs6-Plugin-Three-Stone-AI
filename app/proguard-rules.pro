-dontwarn kotlinx.parcelize.Parcelize

-keep class io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiPluginInfoService { *; }
-keep class io.github.supermonster003.autojs6.plugin.ondeviceai.ModelManagerActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.ondeviceai.WakeActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.ondeviceai.provider.OnDeviceAiProviderService { *; }

# LiteRT-LM uses name-bound JNI entry points and reflective generated bindings.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class org.autojs.plugin.ai.** { *; }
-keep class org.autojs.plugin.common.api.** { *; }
