package com.github.enerccio.lingollm.domain.service.dto;

import java.util.Base64;

/**
 * Non-text content of an {@link LLMChatMessage} (image, audio, document) for models that accept it. The content is
 * either held as bytes ({@link #of}) or referenced by an URL ({@link #ofUrl}).
 */
public class LLMAttachment {

    public enum Kind {
        IMAGE, AUDIO, FILE;

        /** Kind guessed from the MIME type; anything that is not an image or audio is a file. */
        public static Kind fromMimeType(String mimeType) {
            if (mimeType != null) {
                String lower = mimeType.toLowerCase();
                if (lower.startsWith("image/")) {
                    return IMAGE;
                }
                if (lower.startsWith("audio/")) {
                    return AUDIO;
                }
            }
            return FILE;
        }
    }

    private Kind kind;
    private String mimeType;
    private String name;
    private byte[] data;
    private String url;

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /** File name, used for {@link Kind#FILE} attachments. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    /** Remote location, used instead of {@link #getData()} when the provider should fetch the content itself. */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String toBase64() {
        return data == null ? null : Base64.getEncoder().encodeToString(data);
    }

    /** {@code data:<mime>;base64,...} for the bytes, otherwise the url. */
    public String toDataUrl() {
        if (data == null) {
            return url;
        }
        return "data:" + (mimeType != null ? mimeType : "application/octet-stream") + ";base64," + toBase64();
    }

    public static LLMAttachment of(String mimeType, byte[] data) {
        return of(Kind.fromMimeType(mimeType), mimeType, null, data);
    }

    public static LLMAttachment of(Kind kind, String mimeType, String name, byte[] data) {
        LLMAttachment attachment = new LLMAttachment();
        attachment.kind = kind;
        attachment.mimeType = mimeType;
        attachment.name = name;
        attachment.data = data;
        return attachment;
    }

    public static LLMAttachment ofUrl(Kind kind, String mimeType, String url) {
        LLMAttachment attachment = new LLMAttachment();
        attachment.kind = kind;
        attachment.mimeType = mimeType;
        attachment.url = url;
        return attachment;
    }

}
