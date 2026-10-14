---
label: Gemma 4 tools fix
order: 10
---

# Gemma 4 tools fix

Makes function calling work with Gemma 4 models whose API expects another role for the results of the calls.

## How it works

When the model calls a function, LingoLLM sends the result back to it in a message with the role `tool`. Some
servers that run Gemma 4 expect the role `tool_responses` instead, and answer an error or ignore the result otherwise.

The extension changes the role for such models: when the *Model* of an [inference provider](../inference-providers.md) contains `gemma4` or
`gemma-4` (in any letter case), the results of function calls to it are sent as `tool_responses`. Requests to other
models are not touched. It is a port of the SillyTavern extension
[SillyTavern-Gemma4-Tools-Fix](https://github.com/Enerccio/SillyTavern-Gemma4-Tools-Fix).

## Usage

There is nothing to configure. Load the extension in *Admin → Extensions*; it applies to the next request.
