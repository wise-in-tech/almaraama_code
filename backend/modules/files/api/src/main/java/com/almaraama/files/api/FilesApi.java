package com.almaraama.files.api;

import com.almaraama.shared.actor.Actor;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What other modules may ask of the {@code files} module (CF-1, ADR-038). The owning module (tickets, equipment)
 * checks its own permissions and visibility, then attaches files and builds their links; {@code files} never decides
 * who may see a file.
 */
public interface FilesApi {

    /**
     * Checks that a file can be attached by this actor: uploaded by them, for this purpose, less than 24 hours ago, never
     * attached. {@code FILE_NOT_USABLE} otherwise, including for an unknown file.
     */
    UsableFile requireUsable(UUID fileId, Actor actor, FilePurpose purpose);

    /**
     * Marks a usable file as attached to its owner, in the caller's transaction, so that it can never be used twice
     * (assumed exception to CF-4, like the start of a circuit). Same check and error as {@link #requireUsable}.
     *
     * @param ownerModule module that owns the file from now on ({@code maintenance}, {@code assets})
     * @param ownerId     identifier of the owning object (ticket, equipment)
     */
    UsableFile attach(UUID fileId, Actor actor, FilePurpose purpose, String ownerModule, UUID ownerId);

    /** A link valid 5 minutes to read one attached file; {@code NOT_FOUND} for an unknown file. */
    SignedLink signedLink(UUID fileId);

    /** Links valid 5 minutes for several files; unknown files are left out. */
    Map<UUID, SignedLink> signedLinks(Collection<UUID> fileIds);
}
