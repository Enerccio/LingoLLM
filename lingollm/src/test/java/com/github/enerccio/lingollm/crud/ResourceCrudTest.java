package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Resource;
import com.github.enerccio.lingollm.domain.service.FreeChatMessageService;
import com.github.enerccio.lingollm.domain.service.FreeChatService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceCrudTest extends OwnedCrudContract<Resource> {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private Configuration configuration;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private FreeChatService freeChatService;

    @Autowired
    private FreeChatMessageService freeChatMessageService;

    @Override
    protected OwnedService<Resource, ?> service() {
        return resourceService;
    }

    @Override
    protected Resource newEntity() {
        Resource resource = new Resource();
        resource.setMimeType("text/plain");
        resource.setOriginalName("notes.txt");
        resource.setPath("stored.txt");
        resource.setHash("hash");
        resource.setSize(42);
        return resource;
    }

    @Override
    protected void assertCreated(Resource loaded) {
        assertThat(loaded.getMimeType()).isEqualTo("text/plain");
        assertThat(loaded.getOriginalName()).isEqualTo("notes.txt");
        assertThat(loaded.getPath()).isEqualTo("stored.txt");
        assertThat(loaded.getHash()).isEqualTo("hash");
        assertThat(loaded.getSize()).isEqualTo(42);
    }

    @Override
    protected void modify(Resource entity) {
        entity.setOriginalName("renamed.txt");
    }

    @Override
    protected void assertModified(Resource loaded) {
        assertThat(loaded.getOriginalName()).isEqualTo("renamed.txt");
        assertThat(loaded.getPath()).isEqualTo("stored.txt");
    }

    @Test
    void uploadStoresFileInUserFolder() throws Exception {
        byte[] content = "hello resource".getBytes(StandardCharsets.UTF_8);

        Resource resource = resourceService.upload("hello.txt", content, "text/plain");

        assertThat(resource.getSize()).isEqualTo(content.length);
        assertThat(resource.getOriginalName()).isEqualTo("hello.txt");
        assertThat(resource.getPath()).endsWith(".txt");
        assertThat(new File(configuration.getResourcesFolder(owner), resource.getPath())).exists();
        assertThat(resourceService.getResourceData(reload(resource))).isEqualTo(content);
        assertThat(resourceService.findByHash(resource.getHash(), owner).getId()).isNotNull();
    }

    @Test
    void imagesGoToImagesFolder() throws Exception {
        Resource image = resourceService.upload("pic.png", new byte[]{1, 2, 3}, "image/png");

        assertThat(new File(configuration.getImagesFolder(owner), image.getPath())).exists();
        assertThat(new File(configuration.getResourcesFolder(owner), image.getPath())).doesNotExist();
    }

    @Test
    void identicalUploadReusesStoredFile() throws Exception {
        byte[] content = uniqueName("same").getBytes(StandardCharsets.UTF_8);

        Resource first = resourceService.upload("a.txt", content, "text/plain");
        Resource second = resourceService.upload("b.txt", content, "text/plain");

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getPath()).isEqualTo(first.getPath());
        assertThat(second.getHash()).isEqualTo(first.getHash());
    }

    @Test
    void sameContentOfOtherUserIsStoredSeparately() throws Exception {
        byte[] content = uniqueName("shared").getBytes(StandardCharsets.UTF_8);
        Resource mine = resourceService.upload("a.txt", content, "text/plain");

        loginAs(createUser());
        Resource theirs = resourceService.upload("a.txt", content, "text/plain");

        assertThat(theirs.getPath()).isNotEqualTo(mine.getPath());
        assertThat(resourceService.getResourceData(theirs)).isEqualTo(content);
    }

    // 1x1 lossless WebP
    private static final String WEBP = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    private static byte[] png(int seed) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, seed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void uploadImageDetectsTypeFromContent() throws Exception {
        Resource image = resourceService.uploadImage("cover.jpg", png(uniqueName("png").hashCode()));

        assertThat(image.getMimeType()).isEqualTo("image/png");
        assertThat(image.getPath()).endsWith(".png");
        assertThat(new File(configuration.getImagesFolder(owner), image.getPath())).exists();
        assertThat(resourceService.findImage(image.getUuid())).isNotNull();
    }

    @Test
    void uploadImageConvertsWebpToPng() throws Exception {
        Resource image = resourceService.uploadImage("art.webp", Base64.getDecoder().decode(WEBP));

        assertThat(image.getMimeType()).isEqualTo("image/png");
        assertThat(image.getPath()).endsWith(".png");
        byte[] stored = resourceService.getResourceData(image);
        assertThat(ImageIO.read(new ByteArrayInputStream(stored))).isNotNull();
    }

    @Test
    void uploadImageRejectsNonImagesAndOversizedFiles() {
        assertThatThrownBy(() -> resourceService.uploadImage("notes.png", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        // starts like a PNG, but is not one
        assertThatThrownBy(() -> resourceService.uploadImage("fake.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resourceService.uploadImage("big.png", new byte[ResourceService.MAX_IMAGE_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findImageIgnoresOtherUsersAndNonImages() throws Exception {
        Resource text = resourceService.upload("a.txt", uniqueName("txt").getBytes(StandardCharsets.UTF_8), "text/plain");
        Resource image = resourceService.uploadImage("a.png", png(uniqueName("other").hashCode()));

        assertThat(resourceService.findImage(text.getUuid())).isNull();

        loginAs(createUser());
        assertThat(resourceService.findImage(image.getUuid())).isNull();
    }

    @Test
    void linkIsLooseBookkeeping() throws Exception {
        Resource image = resourceService.uploadImage("a.png", png(uniqueName("link").hashCode()), FreeChatMessage.class, 987654L);

        Resource loaded = reload(image);
        assertThat(loaded.getClazz()).isEqualTo(FreeChatMessage.class.getName());
        assertThat(loaded.getObjectId()).isEqualTo(987654L);
        // nothing with such id: the link is reported as missing, the resource still works
        assertThat(resourceService.describeLink(loaded)).isEqualTo(new ResourceService.ResourceLink("FreeChatMessage #987654", false));
        assertThat(resourceService.findImage(image.getUuid())).isNotNull();

        Language language = new Language();
        language.setLanguage(Languages.GERMAN);
        language = languageService.save(language);
        FreeChat chat = new FreeChat();
        chat.setLanguage(language);
        chat = freeChatService.save(chat);
        FreeChatMessage message = new FreeChatMessage();
        message.setLanguage(language);
        message.setParentChat(chat);
        message = freeChatMessageService.save(message);
        resourceService.link(loaded, FreeChatMessage.class, message.getId());
        assertThat(resourceService.describeLink(reload(image)).present()).isTrue();

        freeChatMessageService.delete(message, false);
        assertThat(resourceService.describeLink(reload(image)).present()).isFalse();

        resourceService.unlink(reload(image));
        assertThat(resourceService.describeLink(reload(image))).isNull();
    }

    @Test
    void unknownClassIsNeverLoaded() throws Exception {
        Resource image = resourceService.uploadImage("a.png", png(uniqueName("unknown").hashCode()));
        image.setClazz("java.lang.Runtime");
        image.setObjectId(1L);

        assertThat(resourceService.describeLink(image).present()).isFalse();
    }

    @Test
    void pagesAreSortedAndOnlyOfTheUser() throws Exception {
        Resource small = resourceService.upload("b.txt", "x".getBytes(StandardCharsets.UTF_8), "text/plain");
        Resource big = resourceService.upload("a.txt", uniqueName("big").repeat(50).getBytes(StandardCharsets.UTF_8), "text/plain");
        long before = resourceService.countForUser();
        loginAs(createUser());
        resourceService.upload("theirs.txt", uniqueName("theirs").getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(resourceService.countForUser()).isEqualTo(1);

        loginAs(owner);
        assertThat(resourceService.countForUser()).isEqualTo(before);
        assertThat(resourceService.findPageForUser(0, 1000, "size", false)).extracting(Resource::getUuid)
                .containsSubsequence(big.getUuid(), small.getUuid());
        assertThat(resourceService.findPageForUser(0, 1000, "originalName", true)).extracting(Resource::getOriginalName)
                .doesNotContain("theirs.txt").containsSubsequence("a.txt", "b.txt");
        assertThat(resourceService.findPageForUser(0, 1, "creation", false)).hasSize(1);
        // a property that is not a column of the table is not put into the query
        assertThat(resourceService.findPageForUser(0, 5, "owner.password; DROP TABLE resources", true)).isNotEmpty();
    }

    @Test
    void replaceKeepsTheResourceAndWritesANewFile() throws Exception {
        Resource image = resourceService.uploadImage("old.png", png(uniqueName("old").hashCode()), FreeChatMessage.class, 5L);
        String oldPath = image.getPath();
        String oldHash = image.getHash();
        byte[] replacement = png(uniqueName("new").hashCode());

        Resource replaced = resourceService.replace(image, "new.png", replacement, "image/png");

        assertThat(replaced.getId()).isEqualTo(image.getId());
        assertThat(replaced.getUuid()).isEqualTo(image.getUuid());
        assertThat(replaced.getPath()).isNotEqualTo(oldPath);
        assertThat(replaced.getHash()).isNotEqualTo(oldHash);
        assertThat(replaced.getSize()).isEqualTo(replacement.length);
        assertThat(replaced.getOriginalName()).isEqualTo("new.png");
        assertThat(replaced.getObjectId()).isEqualTo(5L);
        assertThat(resourceService.getResourceData(reload(image))).isEqualTo(replacement);
        // the old file stays for now
        assertThat(new File(configuration.getImagesFolder(owner), oldPath)).exists();
        assertThat(resourceService.findImage(image.getUuid()).getPath()).isEqualTo(replaced.getPath());
    }

    @Test
    void imageIsReplacedByImageOnly() throws Exception {
        Resource image = resourceService.uploadImage("old.png", png(uniqueName("img").hashCode()));
        Resource text = resourceService.upload("a.txt", uniqueName("txt").getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThatThrownBy(() -> resourceService.replace(image, "x.png", "not an image".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resourceService.replace(text, "x.png", png(1), "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(resourceService.getResourceData(reload(image))).isNotEmpty();

        Resource replacedText = resourceService.replace(text, "b.txt", "other".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(new String(resourceService.getResourceData(replacedText), StandardCharsets.UTF_8)).isEqualTo("other");
    }

    @Test
    void replaceOfSomeoneElsesResourceIsRefused() throws Exception {
        Resource mine = resourceService.uploadImage("mine.png", png(uniqueName("mine").hashCode()));
        loginAs(createUser());

        assertThatThrownBy(() -> resourceService.replace(mine, "x.png", png(2), "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void softDeleteHidesTheResourceAndKeepsTheFile() throws Exception {
        Resource image = resourceService.uploadImage("a.png", png(uniqueName("soft").hashCode()));
        Resource other = resourceService.uploadImage("b.png", png(uniqueName("soft2").hashCode()));
        long before = resourceService.countForUser();
        loginAs(createUser());
        Resource theirs = resourceService.uploadImage("c.png", png(uniqueName("theirs").hashCode()));
        // somebody else's uuid is ignored
        assertThat(resourceService.softDelete(List.of(image.getUuid()))).isZero();
        loginAs(owner);

        assertThat(resourceService.softDelete(List.of(image.getUuid(), other.getUuid(), "no-such-uuid", theirs.getUuid()))).isEqualTo(2);

        assertThat(resourceService.countForUser()).isEqualTo(before - 2);
        assertThat(resourceService.findImage(image.getUuid())).isNull();
        assertThat(reload(image).isDeleted()).isTrue();
        assertThat(new File(configuration.getImagesFolder(owner), image.getPath())).exists();
        loginAs(theirs.getOwner());
        assertThat(resourceService.findImage(theirs.getUuid())).isNotNull();
    }
}
