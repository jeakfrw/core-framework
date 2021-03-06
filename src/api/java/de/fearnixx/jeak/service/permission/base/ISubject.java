package de.fearnixx.jeak.service.permission.base;

import de.fearnixx.jeak.profile.IUserProfile;
import de.fearnixx.jeak.service.permission.teamspeak.ITS3Subject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subjects are "things" that can have or lack permissions.
 * At the moment, these are users (and thus, clients) and groups.
 *
 * Subjects are always assigned with a specific permission system.
 * Most of the time, this will be the internal framework permission system.
 */
public interface ISubject {

    // === Reading operations === //

    /**
     * The UUID of the subject.
     *
     * @implNote for clients that own a {@link IUserProfile}, this will also be the profile identifier.
     */
    UUID getUniqueID();

    /**
     * Returns parent groups directly assigned to this subject.
     * This does not transitive parents.
     */
    List<IGroup> getParents();

    /**
     * Whether or not the subject has a positive value set for the provided permission.
     * If the subject has parents, this should also evaluate the parent permissions when the permission is unset on the subject.
     * @apiNote  It is <strong>not possible</strong> to check TS3 permissions with this. Use {@link ITS3Subject} for that.
     */
    boolean hasPermission(String permission);

    /**
     * @see #hasPermission(String) but with more control over whether or not the permission is set and what exact value is defined.
     * If the subject has parents, this should also evaluate the parent permissions when the permission is unset on the subject.
     */
    Optional<IPermission> getPermission(String permission);

    /**
     * Same as {@link #getPermission(String)} but with control over whether or not parents should be evaluated.
     */
    Optional<IPermission> getPermission(String permission, boolean allowTransitive);

    /**
     * Same as {@link #getPermission(String, boolean)} but with more control over whether or not being administrator should be evaluated.
     */
    Optional<IPermission> getPermission(String permission, boolean allowTransitive, boolean allowAdmin);

    /**
     * Returns all permissions directly assigned to this subject.
     * This does not include transitive permissions.
     */
    List<IPermission> getPermissions();

    // === Writing operations === //

    /**
     * Sets the given permission on this subject to the given value.
     * If the permission is not directly assigned to this subject, it is added to the subject.
     *
     * @apiNote Use {@link ITS3Subject#assignPermission(String, int)} and its overloaded signatures for TeamSpeak 3 permissions.
     * @implNote If the permission system/provider is read-only, this will return {@code false}.
     *           This may only be the case for third party permission providers as the internal provider allows rw-access.
     */
    boolean setPermission(String permission, int value);

    /**
     * Removes the given permission from this subject if directly assigned to it.
     *
     * @apiNote This is not the same as setting the permission to 0 as a 0-value can override other values.
     * @apiNote Use {@link ITS3Subject#revokePermission(String)} for TeamSpeak 3 permissions.
     * @implNote If the permission system/provider is read-only, this will return {@code false}.
     *           This may only be the case for third party permission providers as the internal provider allows rw-access.
     */
    boolean removePermission(String permission);

    /**
     * Returns whether or not the given subject unique ID is a parent of this subject.
     * @apiNote Transitive parents are not included in this list.
     */
    boolean hasParent(UUID uniqueID);
}
