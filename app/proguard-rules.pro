-dontwarn kotlinx.parcelize.Parcelize

-keep class io.github.supermonster003.autojs6.plugin.ai.text.AiTextPluginInfoService { *; }
-keep class io.github.supermonster003.autojs6.plugin.ai.text.ModelManagerActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.ai.text.WakeActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.ai.text.provider.AiTextProviderService { *; }

# LiteRT-LM uses name-bound JNI entry points and reflective generated bindings.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class org.autojs.plugin.ai.** { *; }
-keep class org.autojs.plugin.common.api.** { *; }
