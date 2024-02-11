package bguspl.set.ex;
import java.util.Vector;

class setsQueue {

    private Vector<Pair<Integer, int[]>> sets;

    public setsQueue() {
        sets = new Vector<>();
    }

    public synchronized void put(Pair<Integer, int[]> set){

        sets.add(set);
        this.notifyAll();
    }

    public synchronized Pair<Integer, int[]> take() {
        while(sets.size() == 0){
            try{
                this.wait();
            } catch (InterruptedException ignored){}
        }

        Pair<Integer, int[]> set = sets.get(0);
        sets.remove(0);
        return set;
    }

}