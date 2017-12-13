package com.yiyir.smoothiemaster;

import android.provider.SyncStateContract;
import android.util.Log;

import java.util.Observable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by yiyiren on 12/12/17.
 */

public class ObservableBlockingQueue extends Observable {
    LinkedBlockingQueue<float[]> linkedBlockingQueue = new
            LinkedBlockingQueue<>();

    public void enqueueData(float[] data) throws InterruptedException {
        linkedBlockingQueue.put(data);
        notifyObservers(data);
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }

    public boolean isEmpty() {
        return linkedBlockingQueue.size() == 0;
    }

    public float[] dequeueData() throws InterruptedException {
        float[] data = linkedBlockingQueue.take();
        return data;
    }
}
