package de.fearnixx.jeak.service.permission.framework.index;

import de.fearnixx.jeak.service.permission.base.IGroup;
import de.fearnixx.jeak.service.permission.except.CircularInheritanceException;
import de.mlessmann.confort.api.IConfig;
import de.mlessmann.confort.api.IConfigNode;
import de.mlessmann.confort.api.IValueHolder;
import de.mlessmann.confort.api.except.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigIndex extends SubjectIndex {

    private static final Logger logger = LoggerFactory.getLogger(ConfigIndex.class);
    private IConfig config;
    private boolean modified;

    public void setConfig(IConfig config) {
        if (this.config != null) {
            throw new IllegalStateException("#setConfig is unsafe and must not be re-used!");
        }
        this.config = config;
    }

    public boolean load() {
        try {
            config.load();
            if (config.getRoot().isVirtual()) {
                config.getRoot().getNode("parents").setMap();
                config.getRoot().getNode("admins").setList();
                setModified();
            }
            return true;
        } catch (IOException | ParseException e) {
            logger.error("Failed to load membership index!", e);
            return false;
        }
    }

    public synchronized boolean isAdmin(UUID uuid) {
        final String suid = uuid.toString();
        return config.getRoot().getNode("admins")
                .optList()
                .orElseGet(Collections::emptyList)
                .stream()
                .map(IValueHolder::asString)
                .anyMatch(str -> str.equals(suid));
    }

    @Override
    public synchronized List<UUID> getMembersOf(UUID subjectUUID) {
        final String sUID = subjectUUID.toString();
        List<UUID> members = new LinkedList<>();
        config.getRoot()
                .getNode("parents")
                .optMap()
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> {
                    final boolean isMember = value
                            .optList()
                            .orElseGet(Collections::emptyList)
                            .stream()
                            .map(IValueHolder::asString)
                            .anyMatch(str -> str.equals(sUID));
                    if (isMember) {
                        members.add(UUID.fromString(key));
                    }
                });
        return new ArrayList<>(members);
    }

    @Override
    public synchronized List<UUID> getParentsOf(UUID subjectUUID) {
        List<UUID> parents = new LinkedList<>();
        final String subjectSUID = subjectUUID.toString();
        config.getRoot()
                .getNode("parents", subjectSUID)
                .optList()
                .orElseGet(Collections::emptyList)
                .stream()
                .map(IValueHolder::asString)
                .map(UUID::fromString)
                .forEach(parents::add);
        return new ArrayList<>(parents);
    }

    @Override
    public synchronized void addParent(UUID parent, UUID toSubject) {
        final String parentSUID = parent.toString();
        final String subjectSUID = toSubject.toString();
        final IConfigNode subjectNode = config.getRoot().getNode("parents", subjectSUID);
        final boolean alreadyAssigned = subjectNode.optList()
                .orElseGet(Collections::emptyList)
                .stream()
                .map(IValueHolder::asString)
                .anyMatch(existing -> existing.equals(parentSUID));

        addParentCircularityCheck(parent, toSubject);

        if (!alreadyAssigned) {
            final IConfigNode entry = subjectNode.createNewInstance();
            entry.setString(parent.toString());
            subjectNode.append(entry);

            setModified();
        }
    }

    private void addParentCircularityCheck(UUID parent, UUID toSubject) {
        getParentsOf(parent)
                .forEach(parentsParents -> {
                    if (!parentsParents.equals(toSubject)) {
                        // This will abort when the above becomes true during recursion.
                        addParentCircularityCheck(parentsParents, toSubject);
                    } else {
                        throw new CircularInheritanceException("Subject " + toSubject + " must not inherit from " + parent);
                    }
                });
    }

    @Override
    public void removeParent(UUID parent, UUID fromSubject) {
        final String parentSUID = parent.toString();
        final String subjectSUID = fromSubject.toString();
        final IConfigNode subjectNode = config.getRoot().getNode("parents", subjectSUID);
        subjectNode.removeIf(node -> node.asString().equals(parentSUID));
    }

    @Override
    public UUID createGroup(String name) {
        if (findGroupByName(name).isPresent()) {
            throw new IllegalArgumentException("Group " + name + " already exists.");
        }

        final UUID uuid = UUID.randomUUID();
        config.getRoot().getNode("groups", uuid.toString(), "name").setString(name);
        return uuid;
    }

    @Override
    public void deleteSubject(UUID uniqueId) {
        final String subjectSUID = uniqueId.toString();
        config.getRoot().getNode("groups").remove(subjectSUID);
        config.getRoot().getNode("parents")
                .optMap()
                .orElseGet(Collections::emptyMap)
                .forEach((suid, parents) -> parents.removeIf(parent -> parent.asString().equals(subjectSUID)));
        config.getRoot().getNode("parents").remove(subjectSUID);
        setModified();
    }

    @Override
    public void linkServerGroup(IGroup group, int serverGroupID) {
        final String groupSUID = group.getUniqueID().toString();
        final IConfigNode groupNode = config.getRoot().getNode("groups", groupSUID);

        if (serverGroupID <= 0) {
            groupNode.remove("linkedId");
        } else {
            groupNode.getNode("linkedId").setInteger(serverGroupID);
        }
        setModified();
    }

    @Override
    public Optional<UUID> findGroupByName(String name) {
        final String loweredName = name.toLowerCase();
        final List<String> matches = config.getRoot().getNode("groups")
                .optMap()
                .orElseGet(Collections::emptyMap)
                .entrySet()
                .stream()
                .filter(grp -> grp.getValue().getNode("name").optString("").toLowerCase().equals(loweredName))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (matches.size() == 1) {
            return Optional.of(UUID.fromString(matches.get(0)));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<UUID> getGroupsLinkedTo(Integer ts3GroupId) {
        return config.getRoot().getNode("groups")
                .optMap()
                .orElseGet(Collections::emptyMap)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().getNode("linkedId").optInteger(-1).equals(ts3GroupId))
                .map(Map.Entry::getKey)
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    private synchronized void setModified() {
        modified = true;
    }

    private synchronized boolean isModified() {
        return modified;
    }

    @Override
    public synchronized void saveIfModified() {
        if (isModified()) {
            try {
                config.save();
                modified = false;
            } catch (IOException e) {
                logger.warn("Failed to save configuration!", e);
            }
        }
    }
}
