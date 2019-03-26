package parallel.actor;

import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class Dispatcher {
    static final Dispatcher instance = new Dispatcher();
    private ExecutorService pool = Executors.newFixedThreadPool(10);

    private Dispatcher() {}

    void readyToExecute(Actor actor) {
        if (actor.exited()) return;
        if (actor.getContext().status.compareAndExchange(ActorContext.WAITING, ActorContext.EXECUTING) == ActorContext.WAITING)
            pool.submit(() -> Dispatcher.instance.execute(actor));
    }

    private void execute(Actor actor) {
        actor.handle();
        if (actor.exited()) {
            actor.getContext().status.set(ActorContext.EXITED);
        } else {
            actor.getContext().status.set(ActorContext.WAITING);
            if (actor.getMessageCount() > 0) readyToExecute(actor);
        }
    }
}

class ActorContext {
    static final int WAITING = 0;
    static final int EXECUTING = 1;
    static final int EXITED = 2;
    AtomicInteger status = new AtomicInteger();
    ActorContext() {
        this.status.set(WAITING);
    }
}

class Message {
    Method method;
    Object[] arguments;
    Message(Class<?> type, String method, Object... arguments) {
        this.arguments = arguments;
        Class<?>[] parameterTypes = new Class[arguments.length];
        for (int i = 0; i < arguments.length; i ++) parameterTypes[i] = arguments[i].getClass();
        try {
            this.method = type.getDeclaredMethod(method, parameterTypes);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
}

public abstract class Actor {
    private final Class<? extends Actor> type;
    private boolean exited;
    private ActorContext context;
    private ConcurrentLinkedQueue<Message> mailbox = new ConcurrentLinkedQueue<>();

    protected Actor() {
        this.type = getClass();
        this.context = new ActorContext();
    }

    boolean exited() {
        return exited;
    }

    int getMessageCount() {
        return mailbox.size();
    }

    ActorContext getContext() {
        return context;
    }

    protected void exit() {
        exited = true;
    }

    void handle() {
        Message m = mailbox.poll();
        executeMethod(m.method, m.arguments);
    }

    public void post(String method, Object... arguments) {
        if (exited) return;
        this.mailbox.add(new Message(type, method, arguments));
        Dispatcher.instance.readyToExecute(this);
    }

    protected abstract void executeMethod(Method m, Object[] arguments);
}
