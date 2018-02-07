package de.fearnixx.t3.service.permission.teamspeak;

import de.fearnixx.t3.service.permission.base.IPermission;

/**
 * Created by MarkL4YG on 04-Feb-18
 */
public interface ITS3Permission extends IPermission {

    Boolean getSkip();

    Boolean getNegate();

    PriorityType getPriorityType();

    enum PriorityType {

        SERVER_GROUP(1),
        CLIENT(2),
        CHANNEL(3),
        CHANNEL_GROUP(4),
        CHANNEL_CLIENT(5);

        private Integer weight;

        PriorityType(Integer weight) {
            this.weight = weight;
        }

        public Integer getWeight() {
            return weight;
        }
    }
}
