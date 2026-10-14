# Quick start

From a fresh installation to your first language profile. It assumes LingoLLM is [installed](index.md) and you are
logged in as the [administrator](first-start.md).

## 1. Connect a model

1. Open the **Inference Providers** tab and click **Add Inference Provider**.
2. Enter a **Name**, for example `OpenRouter - Claude`.
3. In **General Settings**, set **Max Context Size** to the context size of the model, for example `32000`, and
   **Max Response Tokens** to the longest answer you want, for example `2000`.
4. In **Per Type Settings**, enter the **OpenAI URL** (it ends with `/v1`) and the **Api Key**, click **Refresh** and
   pick the **Model**.
5. Click **Test Connection** to check the address and the key, then **OK**.

The fields are described in [Inference providers](../inference-providers.md), with URLs for common services.

## 2. Set the default model

Open the **Settings** tab, pick the new provider as **Default Model** and click **Save**.

## 3. Create a language profile

Open the **Learning** tab. The **New language** window opens (the first time only; later use the **+** button next to
the selector). Pick the **Language**, keep or change the **Profile name**, and click **OK**.

The profile is now selected. See [Language profiles](../language-profiles.md).

## Next steps

- Add more profiles, or other [users](../administration/users.md).
- Set up [database backups](../administration/database-backups.md).
