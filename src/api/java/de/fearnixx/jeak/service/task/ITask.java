package de.fearnixx.jeak.service.task;

import java.util.concurrent.TimeUnit;

/**
 * Created by MarkL4YG on 11.06.17.
 *
 * Abstract task representation used by the {@link ITaskService}s.
 * Allows plugins to register asynchronous tasks
 *
 * See {@link Builder} to construct one
 */
public interface ITask {

    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to construct an object implementing {@link ITask}
     */
    class Builder {
        private long l;
        private TimeUnit tu;
        private TaskType type;
        private Runnable r;
        private String name;

        public Builder() {
            reset();
        }

        /**
         * Resets this builder
         * Built objects should keep their values
         * @return this
         */
        public Builder reset() {
            l = 0;
            tu = TimeUnit.DAYS;
            type = TaskType.DELAY;
            r = null;
            name = null;
            return this;
        }

        /**
         * Set the task name - Should be somewhat unique
         * @param name The name
         * @return this
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the delay of this task - Resets interval!
         * @param l The delay
         * @param unit The {@link TimeUnit} of the delay (is converted to seconds)
         * @return this
         */
        public Builder delay(long l, TimeUnit unit) {
            type = TaskType.DELAY;
            this.tu = unit;
            this.l = l;
            return this;
        }

        /**
         * Set the interval of this task - Resets delay!
         * @param l The delay
         * @param unit The {@link TimeUnit} of the interval (is converted to seconds)
         * @return this
         */
        public Builder interval(long l, TimeUnit unit) {
            type = TaskType.REPEAT;
            this.tu = unit;
            this.l = l;
            return this;
        }

        /**
         * Set the {@link Runnable} for this task
         * @param r The Runnable
         * @return this
         */
        public Builder runnable(Runnable r) {
            this.r = r;
            return this;
        }

        /**
         * Construct the task
         * Can be used on {@link ITaskService#scheduleTask(ITask)}
         * @return The {@link ITask}
         */
        public ITask build() {
            final String fName = name;
            final TaskType fType = type;
            final long fL = l;
            final TimeUnit fTU = tu;
            final Runnable fR = r;
            return new ITask() {
                @Override
                public long getDelay() {
                    return fL;
                }

                @Override
                public long getInterval() {
                    return fL;
                }

                @Override
                public TimeUnit getTimeUnit() {
                    return tu;
                }

                @Override
                public TaskType getType() {
                    return fType;
                }

                @Override
                public Runnable getRunnable() {
                    return fR;
                }

                @Override
                public boolean shouldReschedule() {
                    return true;
                }

                @Override
                public String getName() {
                    return fName;
                }
            };
        }
    }

    /**
     * Determines the type of the task
     * Delayed or Repeated
     */
    enum TaskType {
        REPEAT,
        DELAY
    }

    /**
     * The tasks delay
     * @return The delay
     */
    long getDelay();

    /**
     * The tasks interval
     * @return The interval
     */
    long getInterval();

    /**
     * The {@link TimeUnit} of the interval or delay
     * @return The TimeUnit
     */
    TimeUnit getTimeUnit();

    /**
     * The {@link TaskType} of the task
     * @return The type
     */
    TaskType getType();

    /**
     * By default, tasks that are set to an interval will be rescheduled after they have been run.
     * Return false for when the task should no longer be re-scheduled.
     * @apiNote As this is for advanced tasks, this cannot be changed using the task builder. <br>Use {@link TaskType#DELAY} for the builder.
     */
    boolean shouldReschedule();

    /**
     * The {@link Runnable} of the task
     * @return The runnable
     */
    Runnable getRunnable();

    /**
     * The - somewhat - unique name of the task (For logging purposes mostly)
     * @return The name
     */
    String getName();
}
