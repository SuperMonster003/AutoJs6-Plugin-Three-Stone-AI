<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="{{ repo_url }}/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="{{ repo_url }}/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="{{ icon_alt }}" border="0" width="128" />
    </picture>
  </p>

  <p>{{ text_plugin_synopsis }}</p>

  <p>
    <a href="{{ repo_url }}/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/{{ repo_slug }}?label=Release"/></a>
    <a href="{{ repo_url }}/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/{{ repo_slug }}?color=A24232&label=Issues"/></a>
    <a href="{{ license_url }}"><img alt="GitHub License" src="https://img.shields.io/github/license/{{ repo_slug }}?color=534BAE&label=License"/></a>
  </p>
</div>

******

### {{ h3_languages_with_ascii }}

******

{{ p_languages_all_supported_for_readme }}:

{{ placeholder_ul_languages_all_supported }}

******

### {{ h3_introduction }}

******

{{ p_introduction }}

******

### {{ h3_functions }}

******

{{ placeholder_features }}

******

### {{ h3_formats }}

******

{{ p_formats }}:

```text
model package: {{ package_format }}
input: {{ input_format }}
output: {{ output_format }}
runtime: {{ runtime_dependency }}
```

******

### {{ h3_plugin_interface }}

******

{{ p_plugin_interface }}:

```text
service action: {{ plugin_action }}
plugin id: {{ plugin_id }}
protocol provider id: {{ protocol_provider_id }}
engine: {{ plugin_engine }}
variant: {{ plugin_variant }}
protocol: {{ protocol_version }}
required host build: {{ required_host_build }}
```

{{ p_plugin_scope }}

{{ p_plugin_packaging }}

******

### {{ h3_host_integration_status }}

******

> {{ p_host_integration_status }}

******

### {{ h3_security }}

******

{{ p_security }}

******

### {{ h3_security_limits }}

******

{{ placeholder_security_limits }}

******

### {{ h3_unsupported }}

******

{{ placeholder_unsupported_capabilities }}

******

### {{ h3_roadmap }}

******

{{ p_roadmap }}

- [{{ text_link_roadmap }}]({{ repo_url }}/blob/master/ROADMAP.md)

******

### {{ h3_release_history }}

******

{{ placeholder_latest_release_history }}

##### {{ h5_for_more_release_history }}

* {{ placeholder_read_more_in_changelog_md }}

******

### {{ h3_build }}

******

```powershell
.\gradlew.bat :app:assembleDebug
```

{{ text_release_build }}:

```powershell
.\gradlew.bat :app:assembleRelease
```

{{ p_build_params }}.

{{ p_local_aars }}:

```text
{{ local_aars }}
```

{{ p_build_architecture }}

******

### {{ h3_license }}

******

{{ p_license }}

******

### {{ h3_resource_layout }}

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

{{ p_resource_layout }}.

******

### {{ h3_links }}

******

- {{ text_link_autojs6_docs }}: {{ docs_autojs6_url }}
- {{ text_link_upstream }}: {{ upstream_url }}
