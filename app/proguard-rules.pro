-dontwarn kotlinx.parcelize.Parcelize

-keep class io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPluginInfoService { *; }
-keep class io.github.supermonster003.autojs6.plugin.threestoneai.ModelManagerActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.threestoneai.WakeActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.threestoneai.provider.ThreeStoneAiProviderService { *; }

# LiteRT-LM uses name-bound JNI entry points and reflective generated bindings.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class org.autojs.plugin.ai.** { *; }
-keep class org.autojs.plugin.common.api.** { *; }
