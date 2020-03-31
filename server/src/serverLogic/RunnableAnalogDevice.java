package serverLogic;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RunnableAnalogDevice implements Runnable{
    private double value = 0.0;
    private ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
    private Lock wLock = rwlock.writeLock();
    private Lock rLock = rwlock.readLock();

    public double sample (){
        rLock.lock();
        double tempValue = value;
        rLock.unlock();
        return tempValue;
    }

    @Override
    public void run (){
        double randomValue = 110.0;
        while (true){
            wLock.lock();
            value = randomValue;
            wLock.unlock();
            randomValue = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            try {
                Thread.sleep(542);
            }
            catch (InterruptedException ignored) {
                break;
            }
        }
    }
}
