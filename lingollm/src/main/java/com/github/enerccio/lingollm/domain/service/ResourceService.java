package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Resource;
import com.github.enerccio.lingollm.domain.repository.ResourceRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.io.File;
import java.util.Collection;
import java.util.List;

public interface ResourceService extends OwnedService<Resource, ResourceRepository> {

    Resource upload(String filename, byte[] content, String contentType) throws Exception;

    Resource findByHash(String hash, User owner) throws Exception;

    byte[] getResourceData(Resource resource) throws Exception;

    /**
     * @return the file with the content of the resource of the current user, for streaming it (it may not exist)
     */
    File getResourceFile(Resource resource) throws Exception;

    /**
     * Makes another resource of the current user for the same file, for an object that must not share the resource
     * with the original (a cloned chat). Files are never changed, so the copy does not copy the file.
     *
     * @return the copy, not linked to anything
     * @throws IllegalArgumentException if the resource is not the user's
     */
    Resource copy(Resource resource) throws Exception;

    /**
     * Largest image {@link #uploadImage} accepts.
     */
    int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    /**
     * Stores an image for the current user. The format (PNG, JPEG, GIF or WebP) is detected from the content, the
     * type sent by the client is not trusted.
     *
     * @throws IllegalArgumentException if the content is not a supported image or is larger than {@link #MAX_IMAGE_BYTES}
     */
    Resource uploadImage(String filename, byte[] content) throws Exception;

    /**
     * Same as {@link #uploadImage(String, byte[])}, the resource is linked to the object that uses the image.
     *
     * @see #link
     */
    Resource uploadImage(String filename, byte[] content, Class<?> clazz, Long objectId) throws Exception;

    /**
     * Notes which object uses the resource. Bookkeeping only: it is not checked, nothing is cleaned up with it.
     */
    Resource link(Resource resource, Class<?> clazz, Long objectId) throws Exception;

    /**
     * Forgets the object linked by {@link #link}.
     */
    Resource unlink(Resource resource) throws Exception;

    /**
     * @param description the linked object as text (class and id)
     * @param present     false if the object is deleted or doesn't exist
     */
    record ResourceLink(String description, boolean present) {
    }

    /**
     * @return the object the resource is linked to, null if it is not linked
     */
    ResourceLink describeLink(Resource resource) throws Exception;

    /**
     * Resources of the current user for a lazy table.
     *
     * @param sortProperty one of creation, originalName, mimeType, size
     */
    List<Resource> findPageForUser(int offset, int limit, String sortProperty, boolean ascending) throws Exception;

    long countForUser() throws Exception;

    /**
     * Replaces the content of the resource of the current user: the new content is written as a new file and the
     * resource points to it, so everything that references the resource keeps working. The old file stays in the
     * folder. An image can be replaced only by an image (see {@link #uploadImage}).
     *
     * @throws IllegalArgumentException if the resource is not the user's, or the content is not acceptable
     */
    Resource replace(Resource resource, String filename, byte[] content, String contentType) throws Exception;

    /**
     * Soft deletes the resources of the current user with given uuids (cleanup purges them later), the files stay.
     * Other uuids are ignored.
     *
     * @return number of the deleted resources
     */
    int softDelete(Collection<String> uuids) throws Exception;

    /**
     * @return image of the current user, null if there is none with such uuid (or it is not an image, or not theirs)
     */
    Resource findImage(String uuid) throws Exception;

}
