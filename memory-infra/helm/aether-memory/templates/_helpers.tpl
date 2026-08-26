{{/*
Expand the name of the chart.
*/}}
{{- define "aether-memory.name" -}}
{{- default .Chart.Name .Values.global.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a fully-qualified release name, capped at 63 characters.
*/}}
{{- define "aether-memory.fullname" -}}
{{- $name := default .Chart.Name .Values.global.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Resolve the namespace: prefer the explicit override, fall back to the Helm release namespace.
*/}}
{{- define "aether-memory.namespace" -}}
{{- .Values.global.namespaceOverride | default .Release.Namespace }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "aether-memory.labels" -}}
app.kubernetes.io/name: {{ include "aether-memory.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: aether-memory
{{- end }}

{{/*
Selector labels — stable subset used in matchLabels and selector.
*/}}
{{- define "aether-memory.selectorLabels" -}}
app.kubernetes.io/name: {{ include "aether-memory.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Fully-qualified image reference for memory-api.
*/}}
{{- define "aether-memory.app.image" -}}
{{- $registry := .Values.global.imageRegistry | trimSuffix "/" }}
{{- $repo := .Values.app.image.repository }}
{{- $tag  := .Values.app.image.tag | toString }}
{{- printf "%s/%s:%s" $registry (base $repo) $tag }}
{{- end }}

{{/*
ServiceAccount name for memory-api.
*/}}
{{- define "aether-memory.app.serviceAccountName" -}}
{{- if .Values.app.serviceAccount.create }}
{{- include "aether-memory.fullname" . | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- "default" }}
{{- end }}
{{- end }}
