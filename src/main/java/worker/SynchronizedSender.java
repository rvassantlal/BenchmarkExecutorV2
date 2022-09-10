package worker;

import messages.Message;

import java.io.IOException;
import java.io.ObjectOutput;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Robin
 */
class SynchronizedSender {
    private final Lock lock;
    private final ObjectOutput out;

    SynchronizedSender(ObjectOutput out) {
        this.out = out;
        this.lock = new ReentrantLock();
    }

    void send(Message message) {
        try {
            lock.lock();
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
