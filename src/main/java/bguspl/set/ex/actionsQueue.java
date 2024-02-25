package bguspl.set.ex;
import java.util.Vector;

class actionsQueue<E> {

    private Vector<E> actions;
    private final int MAX;

    public actionsQueue() {
        MAX = 3;
        actions = new Vector<>();
    }

    public synchronized void put(E slot){
        try{
            while(actions.size() >= MAX){
                this.wait();
            }
        } catch (InterruptedException ignored){}

        if (actions.size() < MAX){
            actions.add(slot);
            this.notifyAll();
        }
    }

    public synchronized E take() {
        try{
            while(actions.size() == 0){
                this.wait();
            }
        } catch (InterruptedException ignored){}

        if (actions.size() != 0){
            E action = actions.get(0);
            actions.remove(0);
            this.notifyAll();
            return action;
        }
        return null;
    }

    public synchronized void clearQueue(){
        while(actions.size() != 0){
            actions.remove(0);
        }
        this.notifyAll();
    }
}