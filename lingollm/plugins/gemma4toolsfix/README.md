# Gemma 4 tools fix

Sends the results of function calls to Gemma 4 models with the role `tool_responses`.

Some APIs of Gemma 4 expect that role for the messages with the result of a function call instead of the `tool` of the
OpenAI chat API. When the model of the AI contains `gemma4` or `gemma-4` (any case), the extension changes the role of
every tool message in the requests to that AI. Requests to other models are not touched.

A port of the SillyTavern extension
[SillyTavern-Gemma4-Tools-Fix](https://github.com/Enerccio/SillyTavern-Gemma4-Tools-Fix).

## Usage

Nothing to configure. Install the plugin (see [LingoLLM plugins](../README.md)); it applies to the next request.

## For developers

The plugin is the smallest user of `InferenceServices.addToolRoleResolver`: a `ToolRoleResolver` answers the role for
an AI (or `null` to leave it), and is removed again when the extension is unloaded. Any other model that wants a
different name for tool messages can be handled the same way.
