package bguspl.set.ex;
import java.util.Vector;

class setsQueue {

    private Vector<Triple<Integer, int[], int[]>> sets;

    public setsQueue() {
        sets = new Vector<>();
    }

    public synchronized void put(Triple<Integer, int[], int[]> set){
        sets.add(set);
    }

    public synchronized Triple<Integer, int[], int[]> take() {
        if (sets.size() == 0){
            return null;
        }

        Triple<Integer, int[], int[]> set = sets.get(0);
        sets.remove(0);
        return set;
    }

    public synchronized boolean isEmpty(){
        return sets.isEmpty();
    }

}