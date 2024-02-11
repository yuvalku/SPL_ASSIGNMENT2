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
        while(actions.size() >= MAX){
            try{
                this.wait();
            } catch (InterruptedException ignored){}
        }

        actions.add(slot);
        this.notifyAll();
    }

    public synchronized E take() {
        while(actions.size() == 0){
            try{
                this.wait();
            } catch (InterruptedException ignored){}
        }

        E action = actions.get(0);
        actions.remove(0);
        this.notifyAll();
        return action;
    }

    public synchronized void clearQueue(){
        while(actions.size() != 0){
            actions.remove(0);
        }
    }
}