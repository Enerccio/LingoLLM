package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Resource;
import com.github.enerccio.lingollm.domain.repository.ResourceRepository;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.ResourceService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;
import com.github.enerccio.lingollm.domain.traits.NoTx;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

public class ResourceServiceImpl extends OwnedServiceImpl<Resource, ResourceRepository> implements ResourceService {

    private static final long MAX_IMAGE_PIXELS = 50_000_000L;

    @Override
    @CommonTx
    public Resource upload(String filename, byte[] content, String contentType) throws Exception {
        Resource resource = new Resource();
        resource.setMimeType(contentType);
        resource.setOriginalName(filename);
        store(resource, filename, content);
        return save(resource);
    }

    /**
     * Writes the content as a file of the current user, or reuses the file with the same content, and sets what
     * describes it on the resource (hash, size, path). Files are never changed after they are written, so they can be
     * shared by more resources.
     */
    private void store(Resource resource, String filename, byte[] content) throws Exception {
        resource.setSize(content.length);
        resource.setHash(DigestUtils.sha256Hex(content));
        resource.setPath(null);

        Resource existing = findByHash(resource.getHash(), currentUser);
        if (existing != null && existing.getPath() != null && existing.getMimeType() != null) {
            try {
                byte[] existingContent = getResourceData(existing);
                if (existingContent.length == content.length && Arrays.equals(existingContent, content)) {
                    resource.setPath(existing.getPath());
                }
            } catch (java.io.IOException e) {
                // the file of the other resource is gone, this one gets its own
            }
        }

        if (resource.getPath() == null) {
            String extension = FilenameUtils.getExtension(filename);
            String name = UUID.randomUUID() + "." + extension;
            File storeFile;
            if (resource.getMimeType().startsWith("image/")) {
                storeFile = new File(configuration.getImagesFolder(currentUser), name);
            } else {
                storeFile = new File(configuration.getResourcesFolder(currentUser), name);
            }
            FileUtils.writeByteArrayToFile(storeFile, content);
            resource.setPath(name);
        }
    }

    @Override
    @CommonTx
    public Resource uploadImage(String filename, byte[] content) throws Exception {
        return uploadImage(filename, content, null, null);
    }

    @Override
    @CommonTx
    public Resource uploadImage(String filename, byte[] content, Class<?> clazz, Long objectId) throws Exception {
        Image image = checkImage(filename, content);
        Resource resource = upload(image.filename(), image.content(), image.mimeType());
        return clazz == null ? resource : link(resource, clazz, objectId);
    }

    /**
     * @param filename name with the extension of the detected type, not the one sent by the client
     */
    private record Image(String filename, byte[] content, String mimeType) {
    }

    private static Image checkImage(String filename, byte[] content) throws Exception {
        if (content == null || content.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image is missing or larger than " + MAX_IMAGE_BYTES + " bytes");
        }
        String mimeType = detectImageType(content);
        if (mimeType == null) {
            throw new IllegalArgumentException("Not a supported image (PNG, JPEG, GIF or WebP)");
        }
        BufferedImage decoded = decode(content);
        if ("image/webp".equals(mimeType)) {
            // browsers show WebP, but the exports (Word, PDF) can't embed it, so it is stored as PNG
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(decoded, "png", png)) {
                throw new IllegalArgumentException("Can't convert the image to PNG");
            }
            content = png.toByteArray();
            mimeType = "image/png";
        }
        return new Image(FilenameUtils.getBaseName(filename) + "." + mimeType.substring("image/".length()), content, mimeType);
    }

    @Override
    @CommonTx
    public Resource link(Resource resource, Class<?> clazz, Long objectId) throws Exception {
        resource.setClazz(clazz.getName());
        resource.setObjectId(objectId);
        return save(resource);
    }

    @Override
    @CommonTx
    public Resource unlink(Resource resource) throws Exception {
        resource.setClazz(null);
        resource.setObjectId(null);
        return save(resource);
    }

    @Override
    @CommonTxReadOnly
    public ResourceLink describeLink(Resource resource) throws Exception {
        if (resource.getClazz() == null || resource.getObjectId() == null) {
            return null;
        }
        String name = resource.getClazz().substring(resource.getClazz().lastIndexOf('.') + 1);
        return new ResourceLink(name + " #" + resource.getObjectId(), getRepository().entityExists(resource.getClazz(), resource.getObjectId()));
    }

    @Override
    @CommonTxReadOnly
    public List<Resource> findPageForUser(int offset, int limit, String sortProperty, boolean ascending) throws Exception {
        return getRepository().findPage(currentUser, offset, limit, sortProperty, ascending);
    }

    @Override
    @CommonTxReadOnly
    public long countForUser() throws Exception {
        return getRepository().count(currentUser);
    }

    @Override
    @CommonTx
    public Resource replace(Resource resource, String filename, byte[] content, String contentType) throws Exception {
        Resource current = resource == null ? null : findForUser(resource.getUuid());
        if (current == null) {
            throw new IllegalArgumentException("Resource doesn't exist");
        }
        if (current.getMimeType() != null && current.getMimeType().startsWith("image/")) {
            Image image = checkImage(filename, content);
            current.setMimeType(image.mimeType());
            current.setOriginalName(image.filename());
            store(current, image.filename(), image.content());
        } else {
            current.setMimeType(StringUtils.defaultIfBlank(contentType, "application/octet-stream"));
            if (current.getMimeType().startsWith("image/")) {
                // the file would be looked up in the images folder, uploadImage is the way to store an image
                throw new IllegalArgumentException("An image can't replace another kind of file");
            }
            current.setOriginalName(filename);
            store(current, filename, content);
        }
        return save(current);
    }

    @Override
    @CommonTx
    public int softDelete(Collection<String> uuids) throws Exception {
        int deleted = 0;
        for (String uuid : uuids) {
            Resource resource = findForUser(uuid);
            if (resource != null) {
                delete(resource, false);
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Reads the whole image, so a file that only starts like an image is refused, and refuses huge dimensions that a
     * small file can decompress to.
     */
    private static BufferedImage decode(byte[] content) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("No reader for the image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("Image has too many pixels");
                }
                return reader.read(0);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("The image can't be read", e);
            } finally {
                reader.dispose();
            }
        }
    }

    @Override
    @CommonTxReadOnly
    public Resource findImage(String uuid) throws Exception {
        Resource resource = findForUser(uuid);
        return resource != null && resource.getMimeType() != null && resource.getMimeType().startsWith("image/")
                ? resource : null;
    }

    private static String detectImageType(byte[] c) {
        if (c.length >= 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G') {
            return "image/png";
        }
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (c.length >= 6 && c[0] == 'G' && c[1] == 'I' && c[2] == 'F' && c[3] == '8') {
            return "image/gif";
        }
        if (c.length >= 12 && c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
                && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    @Override
    @CommonTxReadOnly
    public Resource findByHash(String hash, User owner) throws Exception {
        return getRepository().findByHash(hash, owner);
    }

    @Override
    @NoTx
    public byte[] getResourceData(Resource resource) throws Exception {
        return FileUtils.readFileToByteArray(getResourceFile(resource));
    }

    @Override
    @NoTx
    public File getResourceFile(Resource resource) throws Exception {
        if (resource.getMimeType().startsWith("image/")) {
            return new File(configuration.getImagesFolder(currentUser), resource.getPath());
        }
        return new File(configuration.getResourcesFolder(currentUser), resource.getPath());
    }

    @Override
    @CommonTx
    public Resource copy(Resource resource) throws Exception {
        Resource current = resource == null ? null : findForUser(resource.getUuid());
        if (current == null) {
            throw new IllegalArgumentException("Resource doesn't exist");
        }
        Resource copy = new Resource();
        copy.setMimeType(current.getMimeType());
        copy.setOriginalName(current.getOriginalName());
        copy.setHash(current.getHash());
        copy.setSize(current.getSize());
        copy.setPath(current.getPath());
        return save(copy);
    }

}
